import { mkdtemp, readFile, readdir, rm, writeFile, mkdir } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { spawn, spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = path.dirname(fileURLToPath(import.meta.url));
const markdownlintEntry = path.join(
    toolRoot,
    'node_modules',
    'markdownlint-cli2',
    'markdownlint-cli2-bin.mjs',
);
const defaultReportPath = path.join(toolRoot, 'target', 'docs-check-report.json');

function usage() {
    return [
        '用法：node check.mjs [--root <绝对路径>] [--files <相对路径,...>]',
        '      [--mode format|full] [--report <绝对路径>] [--self-test]',
    ].join('\n');
}

function parseArgs(argv) {
    const options = {
        root: process.cwd(),
        files: [],
        mode: 'format',
        reportPath: defaultReportPath,
        selfTest: false,
    };

    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        if (argument === '--self-test') {
            options.selfTest = true;
            continue;
        }
        if (!['--root', '--files', '--mode', '--report'].includes(argument)) {
            throw new Error(`未知参数：${argument}\n${usage()}`);
        }
        const value = argv[index + 1];
        if (!value || value.startsWith('--')) {
            throw new Error(`参数 ${argument} 缺少值\n${usage()}`);
        }
        index += 1;
        if (argument === '--root') {
            options.root = value;
        } else if (argument === '--files') {
            options.files = value.split(',').filter(Boolean);
        } else if (argument === '--mode') {
            options.mode = value;
        } else if (argument === '--report') {
            options.reportPath = value;
        }
    }
    return options;
}

function isAbsolutePath(value) {
    return path.isAbsolute(value);
}

function ensureInside(root, candidate, label) {
    const relative = path.relative(root, candidate);
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
        throw new Error(`${label} 超出执行根目录：${candidate}`);
    }
}

function validateOptions({ root, files, mode, reportPath }) {
    if (!isAbsolutePath(root)) {
        throw new Error(`--root 必须是绝对路径：${root}`);
    }
    if (!['format', 'full'].includes(mode)) {
        throw new Error(`不支持的检查模式：${mode}`);
    }
    if (!isAbsolutePath(reportPath)) {
        throw new Error(`--report 必须是绝对路径：${reportPath}`);
    }
    const resolvedRoot = path.resolve(root);
    for (const file of files) {
        const candidate = path.resolve(resolvedRoot, file);
        ensureInside(resolvedRoot, candidate, `受检文件 ${file}`);
        if (path.isAbsolute(file)) {
            throw new Error(`受检文件必须是相对路径：${file}`);
        }
    }
    return resolvedRoot;
}

function trackedMarkdownFiles(root) {
    const result = spawnSync('git', ['-C', root, 'ls-files', '-z', '--', '*.md', '*.markdown', '*.mdx'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
    });
    if (result.status === 0) {
        return result.stdout.split('\0').filter(Boolean);
    }
    return walkMarkdownFiles(root);
}

async function walkMarkdownFiles(root) {
    const files = [];
    const excluded = new Set(['.git', '.worktrees', 'node_modules', 'target', 'build', 'mimir-quality']);

    async function visit(directory) {
        const entries = await readdir(directory, { withFileTypes: true });
        for (const entry of entries) {
            if (entry.isDirectory() && excluded.has(entry.name)) {
                continue;
            }
            const absolutePath = path.join(directory, entry.name);
            if (entry.isDirectory()) {
                await visit(absolutePath);
            } else if (entry.isFile() && /\.(?:md|markdown|mdx)$/u.test(entry.name)) {
                files.push(path.relative(root, absolutePath));
            }
        }
    }

    await visit(root);
    return files.sort();
}

async function resolveFiles(root, files) {
    if (files.length > 0) {
        return files;
    }
    return trackedMarkdownFiles(root);
}

function toolVersion() {
    try {
        const packageJson = JSON.parse(requireFile(path.join(toolRoot, 'node_modules', 'markdownlint-cli2', 'package.json')));
        return packageJson.version;
    } catch {
        return null;
    }
}

function requireFile(filePath) {
    return readFileSync(filePath, 'utf8');
}

function runProcess(command, args, cwd) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { cwd, stdio: ['ignore', 'pipe', 'pipe'] });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (chunk) => {
            const text = chunk.toString();
            stdout += text;
            process.stdout.write(text);
        });
        child.stderr.on('data', (chunk) => {
            const text = chunk.toString();
            stderr += text;
            process.stderr.write(text);
        });
        child.on('error', reject);
        child.on('close', (exitCode, signal) => resolve({ exitCode, signal, stdout, stderr }));
    });
}

async function runSelfTest(root) {
    const temporaryRoot = await mkdtemp(path.join(tmpdir(), 'mimir-docs-check-'));
    const fixture = path.join(temporaryRoot, 'fixture.md');
    try {
        await writeFile(fixture, '# 标题\n没有空行\n', 'utf8');
        const failed = await runProcess(
            process.execPath,
            [markdownlintEntry, '--no-globs', '--config', path.join(root, '.markdownlint.json'), fixture],
            root,
        );
        if (failed.exitCode === 0) {
            throw new Error('自检负例未被 markdownlint-cli2 拒绝');
        }

        await writeFile(fixture, '# 标题\n\n正文\n', 'utf8');
        const passed = await runProcess(
            process.execPath,
            [markdownlintEntry, '--no-globs', '--config', path.join(root, '.markdownlint.json'), fixture],
            root,
        );
        if (passed.exitCode !== 0) {
            throw new Error('自检正例未通过 markdownlint-cli2');
        }
    } finally {
        await rm(temporaryRoot, { recursive: true, force: true });
    }
}

export async function runDocsCheck({ root, files = [], mode = 'format', reportPath = defaultReportPath }) {
    const startedAt = new Date().toISOString();
    const report = {
        schemaVersion: 1,
        runId: `docs-${Date.now()}-${process.pid}`,
        startedAt,
        finishedAt: null,
        reportDirectory: path.dirname(reportPath),
        toolVersions: {
            node: process.version,
            'markdownlint-cli2': toolVersion(),
        },
        toolPaths: {
            managedNode: path.join(toolRoot, '.maven-node', 'node', 'node'),
            dependencyCache: path.join(toolRoot, 'node_modules'),
        },
        source: 'worktree',
        commit: null,
        tree: null,
        configurationHash: null,
        checks: [],
        findings: [],
        overall: 'error',
    };

    try {
        const resolvedRoot = validateOptions({ root, files, mode, reportPath });
        if (mode === 'full') {
            report.checks.push({
                id: 'markdown-format',
                command: [],
                dependsOn: [],
                logPath: null,
                status: 'not_run',
                required: true,
                exitCode: null,
                reason: 'T1 仅实现 format 模式，full 模式由后续任务提供',
                durationMs: 0,
            });
            report.overall = 'error';
            return report;
        }
        if (!await fileExists(markdownlintEntry)) {
            report.checks.push({
                id: 'markdown-format',
                command: [],
                dependsOn: [],
                logPath: null,
                status: 'error',
                required: true,
                exitCode: null,
                reason: '未找到受管 markdownlint-cli2，请先运行 Maven docs-check profile',
                durationMs: 0,
            });
            report.overall = 'error';
            return report;
        }

        if (process.env.DOCS_SELF_TEST === 'true') {
            const selfTestStarted = Date.now();
            try {
                await runSelfTest(resolvedRoot);
                report.checks.push({
                    id: 'tool-self-test',
                    command: ['node', 'check.mjs', '--self-test'],
                    dependsOn: [],
                    logPath: null,
                    status: 'passed',
                    required: true,
                    exitCode: 0,
                    reason: null,
                    durationMs: Date.now() - selfTestStarted,
                });
            } catch (error) {
                report.checks.push({
                    id: 'tool-self-test',
                    command: ['node', 'check.mjs', '--self-test'],
                    dependsOn: [],
                    logPath: null,
                    status: 'error',
                    required: true,
                    exitCode: null,
                    reason: error.message,
                    durationMs: Date.now() - selfTestStarted,
                });
            }
        }

        const resolvedFiles = await resolveFiles(resolvedRoot, files);
        const command = [
            markdownlintEntry,
            '--no-globs',
            '--config',
            path.join(resolvedRoot, '.markdownlint-cli2.jsonc'),
            ...resolvedFiles,
        ];
        const checkStarted = Date.now();
        try {
            const result = await runProcess(process.execPath, command, resolvedRoot);
            report.checks.push({
                id: 'markdown-format',
                command: ['node', ...command],
                dependsOn: [],
                logPath: null,
                status: result.exitCode === 0 ? 'passed' : 'failed',
                required: true,
                exitCode: result.exitCode,
                reason: result.exitCode === 0 ? null : 'markdownlint-cli2 发现格式问题',
                durationMs: Date.now() - checkStarted,
            });
        } catch (error) {
            report.checks.push({
                id: 'markdown-format',
                command: ['node', ...command],
                dependsOn: [],
                logPath: null,
                status: 'error',
                required: true,
                exitCode: null,
                reason: `无法启动 markdownlint-cli2：${error.message}`,
                durationMs: Date.now() - checkStarted,
            });
        }
        report.overall = report.checks.every((check) => ['passed', 'not_applicable'].includes(check.status))
            ? 'passed'
            : 'failed';
    } catch (error) {
        report.checks.push({
            id: 'docs-tool',
            command: [],
            dependsOn: [],
            logPath: null,
            status: 'error',
            required: true,
            exitCode: 2,
            reason: error.message,
            durationMs: 0,
        });
        report.overall = 'error';
    } finally {
        report.finishedAt = new Date().toISOString();
        await writeReport(reportPath, report);
    }
    return report;
}

async function fileExists(filePath) {
    try {
        await readFile(filePath);
        return true;
    } catch {
        return false;
    }
}

async function writeReport(reportPath, report) {
    await mkdir(path.dirname(reportPath), { recursive: true });
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('/check.mjs')) {
    try {
        const options = parseArgs(process.argv.slice(2));
        if (options.selfTest) {
            process.env.DOCS_SELF_TEST = 'true';
        }
        const report = await runDocsCheck(options);
        process.exitCode = report.overall === 'passed' ? 0 : report.checks.some((check) => check.status === 'error') ? 2 : 1;
    } catch (error) {
        process.stderr.write(`文档检查失败：${error.message}\n`);
        process.exitCode = 2;
    }
}
