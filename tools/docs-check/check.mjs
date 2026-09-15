import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir, rm, writeFile, mkdir, stat } from 'node:fs/promises';
import { readFileSync, existsSync } from 'node:fs';
import { spawn, spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { addFinding, createCheck, finalizeReport } from './results.mjs';
import { classifyPath, defaultPolicy, isFormatExempt, loadPolicy, normalizePath } from './policy.mjs';

const moduleRoot = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(moduleRoot, '..', '..');
const toolRoot = path.resolve(process.env.MIMIR_DOCS_TOOL_ROOT || moduleRoot);
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

export function parseArgs(argv) {
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

function ensureInside(root, candidate, label) {
    const relative = path.relative(root, candidate);
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
        throw new Error(`${label} 超出执行根目录：${candidate}`);
    }
}

function validateOptions({ root, files, mode, reportPath }) {
    if (!path.isAbsolute(root)) {
        throw new Error(`--root 必须是绝对路径：${root}`);
    }
    if (!['format', 'full'].includes(mode)) {
        throw new Error(`不支持的检查模式：${mode}`);
    }
    if (!path.isAbsolute(reportPath)) {
        throw new Error(`--report 必须是绝对路径：${reportPath}`);
    }
    const resolvedRoot = path.resolve(root);
    for (const file of files) {
        if (path.isAbsolute(file)) {
            throw new Error(`受检文件必须是相对路径：${file}`);
        }
        ensureInside(resolvedRoot, path.resolve(resolvedRoot, file), `受检文件 ${file}`);
    }
    return resolvedRoot;
}

function trackedMarkdownFiles(root) {
    const result = spawnSync('git', ['-C', root, 'ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', '*.md', '*.markdown', '*.mdx'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
    });
    if (result.status === 0 && !result.error) {
        return [...new Set(result.stdout.split('\0').filter((file) => file && existsSync(path.join(root, file))))];
    }
    return null;
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
                files.push(normalizePath(path.relative(root, absolutePath)));
            }
        }
    }
    await visit(root);
    return files.sort();
}

async function resolveFiles(root, files) {
    if (files.length > 0) {
        return files.map(normalizePath);
    }
    const discovered = trackedMarkdownFiles(root) || await walkMarkdownFiles(root);
    return discovered.filter((file) => !['tools/docs-check/test/fixtures/', 'scripts/tests/fixtures/'].some((prefix) => normalizePath(file).startsWith(prefix)));
}

function readToolPackageVersion(packageName) {
    try {
        const packageJson = JSON.parse(readFileSync(path.join(toolRoot, 'node_modules', packageName, 'package.json'), 'utf8'));
        return packageJson.version;
    } catch {
        return null;
    }
}

function runProcess(command, args, cwd) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { cwd, stdio: ['ignore', 'pipe', 'pipe'] });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (chunk) => {
            const output = chunk.toString();
            stdout += output;
            process.stdout.write(output);
        });
        child.stderr.on('data', (chunk) => {
            const output = chunk.toString();
            stderr += output;
            process.stderr.write(output);
        });
        child.on('error', reject);
        child.on('close', (exitCode, signal) => resolve({ exitCode, signal, stdout, stderr }));
    });
}

async function runMarkdownlint(args) {
    const entry = pathToFileURL(path.join(toolRoot, 'node_modules', 'markdownlint-cli2', 'markdownlint-cli2.mjs')).href;
    const { main } = await import(entry);
    const stdout = [];
    const stderr = [];
    const exitCode = await main({
        argv: args,
        logMessage: (message) => {
            stdout.push(message);
            process.stdout.write(`${message}\n`);
        },
        logError: (message) => {
            stderr.push(message);
            process.stderr.write(`${message}\n`);
        },
        allowStdin: true,
    });
    return { exitCode, signal: null, stdout: stdout.join('\n'), stderr: stderr.join('\n') };
}

function markdownlintConfig(root) {
    const target = path.join(root, '.markdownlint-cli2.jsonc');
    try {
        readFileSync(target);
        return target;
    } catch {
        const fallbackRules = path.join(projectRoot, '.markdownlint.json');
        try {
            readFileSync(fallbackRules);
            return fallbackRules;
        } catch {
            const fallback = path.join(projectRoot, '.markdownlint-cli2.jsonc');
            try {
                readFileSync(fallback);
                return fallback;
            } catch {
                return null;
            }
        }
    }
}

function parseMarkdownlintFindings(output, root) {
    const findings = [];
    for (const line of output.split(/\r?\n/u)) {
        const match = line.match(/^(.*?):(\d+)(?::(\d+))?\s+(?:error|warning)\s+(MD\d+)(?:\/[^\s]+)?\s+(.*)$/u);
        if (!match) {
            continue;
        }
        const absoluteOrRelative = match[1];
        const absolutePath = path.isAbsolute(absoluteOrRelative)
            ? absoluteOrRelative
            : path.resolve(process.cwd(), absoluteOrRelative);
        const relativePath = path.relative(root, absolutePath);
        const findingPath = relativePath.startsWith('..') || path.isAbsolute(relativePath)
            ? normalizePath(absoluteOrRelative)
            : normalizePath(relativePath);
        findings.push({
            severity: 'error',
            rule: `markdown-format:${match[4]}`,
            path: findingPath,
            line: Number(match[2]),
            message: match[5],
        });
    }
    return findings;
}

async function runFormatCheck(root, files, policy) {
    const formatFiles = files.filter((file) => /\.(?:md|markdown|mdx)$/iu.test(file) && !isFormatExempt(file, policy));
    if (formatFiles.length === 0) {
        return {
            check: createCheck({ id: 'markdown-format', status: 'not_applicable', required: false, durationMs: 0 }),
            findings: [],
        };
    }
    if (!await fileExists(markdownlintEntry)) {
        return {
            check: createCheck({ id: 'markdown-format', status: 'error', exitCode: 2, reason: '未找到受管 markdownlint-cli2，请先运行 Maven docs-check profile', durationMs: 0 }),
            findings: [{ severity: 'error', rule: 'markdown-tool', path: null, line: null, message: '未找到受管 markdownlint-cli2，请先运行 Maven docs-check profile' }],
        };
    }
    const config = markdownlintConfig(root);
    if (!config) {
        return {
            check: createCheck({ id: 'markdown-format', status: 'error', exitCode: 2, reason: '缺少 markdownlint 配置文件', durationMs: 0 }),
            findings: [{ severity: 'error', rule: 'markdown-config', path: null, line: null, message: '缺少 .markdownlint-cli2.jsonc 和 .markdownlint.json 配置' }],
        };
    }
    const args = ['--no-globs', '--config', config, ...formatFiles.map((file) => path.join(root, file))];
    const command = [markdownlintEntry, ...args];
    const startedAt = Date.now();
    try {
        const result = await runMarkdownlint(args);
        const output = `${result.stdout}\n${result.stderr}`;
        const findings = parseMarkdownlintFindings(output, root);
        const failed = result.exitCode !== 0;
        const executionError = result.exitCode === 2 || result.signal;
        if (failed && findings.length === 0) {
            findings.push({ severity: 'error', rule: 'markdown-format', path: formatFiles[0], line: 1, message: 'markdownlint-cli2 发现格式问题，但未能解析具体诊断' });
        }
        return {
            check: createCheck({
                id: 'markdown-format',
                command: ['node', ...command],
                status: executionError ? 'error' : failed ? 'failed' : 'passed',
                exitCode: executionError ? 2 : result.exitCode,
                reason: executionError ? 'markdownlint-cli2 执行错误' : failed ? 'markdownlint-cli2 发现格式问题' : null,
                durationMs: Date.now() - startedAt,
            }),
            findings,
        };
    } catch (error) {
        return {
            check: createCheck({ id: 'markdown-format', command: ['node', ...command], status: 'error', exitCode: 2, reason: `无法启动 markdownlint-cli2：${error.message}`, durationMs: Date.now() - startedAt }),
            findings: [{ severity: 'error', rule: 'markdown-tool', path: null, line: null, message: `无法启动 markdownlint-cli2：${error.message}` }],
        };
    }
}

async function runSelfTest(root) {
    if (!await fileExists(markdownlintEntry)) {
        throw new Error('未找到受管 markdownlint-cli2');
    }
    const config = markdownlintConfig(root);
    if (!config) {
        throw new Error('缺少 markdownlint 配置');
    }
    const temporaryRoot = await mkdtemp(path.join(tmpdir(), 'mimir-docs-check-'));
    const fixture = path.join(temporaryRoot, 'fixture.md');
    try {
        await writeFile(fixture, '# 标题\n没有空行\n', 'utf8');
        const failed = await runProcess(process.execPath, [markdownlintEntry, '--no-globs', '--config', config, fixture], root);
        if (failed.exitCode === 0) {
            throw new Error('自检负例未被 markdownlint-cli2 拒绝');
        }
        if (failed.exitCode !== 1) {
            throw new Error(`自检负例执行错误，退出码为 ${failed.exitCode}`);
        }
        await writeFile(fixture, '# 标题\n\n正文\n', 'utf8');
        const passed = await runProcess(process.execPath, [markdownlintEntry, '--no-globs', '--config', config, fixture], root);
        if (passed.exitCode !== 0) {
            throw new Error('自检正例未通过 markdownlint-cli2');
        }
    } finally {
        await rm(temporaryRoot, { recursive: true, force: true });
    }
}

async function runMaintenanceCheck(files, policy) {
    const findings = [];
    for (const file of files) {
        const classification = classifyPath(file, policy);
        if (classification === 'historical') {
            findings.push({ severity: 'warning', rule: 'archive-review', path: file, line: 1, message: '归档/历史文档只按历史语义检查，发布状态需要人工核对' });
        }
    }
    return {
        check: createCheck({
            id: 'maintenance-policy',
            status: 'passed',
            exitCode: 0,
            reason: null,
            durationMs: 0,
        }),
        findings,
    };
}

async function reportTree(root, files) {
    const hash = createHash('sha256');
    for (const file of [...files].sort()) {
        hash.update(file);
        try {
            hash.update(await readFile(path.join(root, file)));
        } catch {
            hash.update('<missing>');
        }
    }
    return hash.digest('hex');
}

async function configurationHash(root) {
    const hash = createHash('sha256');
    for (const relative of ['.markdownlint-cli2.jsonc', '.markdownlint.json', 'tools/docs-check/package.json', 'tools/docs-check/package-lock.json', 'tools/docs-check/policy.json', 'tools/docs-check/debt-id-registry.json']) {
        hash.update(relative);
        try {
            hash.update(await readFile(path.join(root, relative)));
        } catch {
            hash.update('<missing>');
        }
    }
    return hash.digest('hex');
}

function appendResult(report, result) {
    report.checks.push(result.check);
    for (const finding of result.findings || []) {
        addFinding(report, finding);
    }
}

function dependencyError(id, error) {
    return {
        check: createCheck({ id, status: 'error', exitCode: 2, reason: `文档解析器不可用：${error.message}`, durationMs: 0 }),
        findings: [{ severity: 'error', rule: 'markdown-parser-tool', path: null, line: null, message: `文档解析器不可用：${error.message}` }],
    };
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
            'markdownlint-cli2': readToolPackageVersion('markdownlint-cli2'),
            'markdown-it': readToolPackageVersion('markdown-it'),
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
        exitCode: 2,
    };
    try {
        const resolvedRoot = validateOptions({ root, files, mode, reportPath });
        const resolvedFiles = await resolveFiles(resolvedRoot, files);
        report.tree = await reportTree(resolvedRoot, resolvedFiles);
        report.configurationHash = await configurationHash(resolvedRoot);
        let policy = defaultPolicy;
        if (mode === 'full') {
            try {
                policy = await loadPolicy(resolvedRoot);
            } catch (error) {
                appendResult(report, {
                    check: createCheck({ id: 'maintenance-policy', status: 'error', exitCode: 2, reason: error.message, durationMs: 0 }),
                    findings: [{ severity: 'error', rule: 'policy-tool', path: null, line: null, message: error.message }],
                });
            }
        }
        if (process.env.DOCS_SELF_TEST === 'true') {
            const selfTestStarted = Date.now();
            try {
                await runSelfTest(resolvedRoot);
                report.checks.push(createCheck({ id: 'tool-self-test', command: ['node', 'check.mjs', '--self-test'], status: 'passed', exitCode: 0, durationMs: Date.now() - selfTestStarted }));
            } catch (error) {
                appendResult(report, {
                    check: createCheck({ id: 'tool-self-test', command: ['node', 'check.mjs', '--self-test'], status: 'error', exitCode: 2, reason: error.message, durationMs: Date.now() - selfTestStarted }),
                    findings: [{ severity: 'error', rule: 'tool-self-test', path: null, line: null, message: error.message }],
                });
            }
        }
        appendResult(report, await runFormatCheck(resolvedRoot, resolvedFiles, policy));
        if (mode === 'full') {
            if (resolvedFiles.length === 0) {
                appendResult(report, {
                    check: createCheck({ id: 'full-input', status: 'failed', exitCode: 1, reason: 'full 模式没有可检查的 Markdown 文件', durationMs: 0 }),
                    findings: [{ severity: 'error', rule: 'full-empty', path: null, line: null, message: 'full 模式不能以空检查集合通过' }],
                });
            }
            let linksModule;
            try {
                linksModule = await import('./links.mjs');
            } catch (error) {
                appendResult(report, dependencyError('internal-links', error));
                appendResult(report, dependencyError('navigation', error));
                appendResult(report, dependencyError('technical-debt', error));
                linksModule = null;
            }
            if (linksModule) {
                try {
                    appendResult(report, await linksModule.checkLinks({ root: resolvedRoot, files: resolvedFiles, policy }));
                } catch (error) {
                    appendResult(report, dependencyError('internal-links', error));
                }
                try {
                    const navigationModule = await import('./navigation.mjs');
                    appendResult(report, await navigationModule.checkNavigation({ root: resolvedRoot, files: resolvedFiles, policy }));
                } catch (error) {
                    appendResult(report, dependencyError('navigation', error));
                }
                try {
                    const debtModule = await import('./debt.mjs');
                    appendResult(report, await debtModule.checkDebt({ root: resolvedRoot, files: resolvedFiles, policy }));
                } catch (error) {
                    appendResult(report, dependencyError('technical-debt', error));
                }
            }
            if (!report.checks.some((check) => check.id === 'maintenance-policy')) {
                appendResult(report, await runMaintenanceCheck(resolvedFiles, policy));
            }
        }
        finalizeReport(report, { emptyFull: mode === 'full' && resolvedFiles.length === 0 });
    } catch (error) {
        appendResult(report, {
            check: createCheck({ id: 'docs-tool', status: 'error', exitCode: 2, reason: error.message, durationMs: 0 }),
            findings: [{ severity: 'error', rule: 'docs-tool', path: null, line: null, message: error.message }],
        });
        finalizeReport(report);
    } finally {
        report.finishedAt = new Date().toISOString();
        try {
            await writeReport(reportPath, report);
        } catch (error) {
            report.reportWriteError = true;
            report.overall = 'error';
            report.exitCode = 2;
            report.findings.push({ severity: 'error', rule: 'report-write', path: reportPath, line: null, message: `报告写入失败：${error.message}` });
        }
    }
    return report;
}

async function fileExists(filePath) {
    try {
        await stat(filePath);
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
        process.exitCode = report.exitCode;
    } catch (error) {
        process.stderr.write(`文档检查失败：${error.message}\n`);
        process.exitCode = 2;
    }
}
