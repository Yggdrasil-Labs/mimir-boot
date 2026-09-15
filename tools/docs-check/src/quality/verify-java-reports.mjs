import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';

const SCHEMA_VERSION = 1;
const SKIPPED_DIRECTORIES = new Set(['.git', '.worktrees', 'node_modules', 'target', '.maven-node']);

function usage() {
    return [
        '用法：node verify-java-reports.mjs --root <绝对路径> --generate-expected <绝对路径> --run-id <id>',
        '      node verify-java-reports.mjs --root <绝对路径> --record-artifacts <绝对路径>',
        '      node verify-java-reports.mjs --root <绝对路径> --clean-expected <绝对路径>',
        '      node verify-java-reports.mjs --root <绝对路径> --expected <绝对路径> --report <绝对路径>',
    ].join('\n');
}

function assertAbsolute(value, label) {
    if (!value || !path.isAbsolute(value)) {
        throw new Error(`${label} 必须是绝对路径`);
    }
    return path.resolve(value);
}

function parseArgs(argv) {
    const options = { root: null, expected: null, report: null, generateExpected: null, recordArtifacts: null, cleanExpected: null, runId: null };
    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        if (!['--root', '--expected', '--report', '--generate-expected', '--record-artifacts', '--clean-expected', '--run-id'].includes(argument)) {
            throw new Error(`未知参数：${argument}\n${usage()}`);
        }
        const value = argv[index + 1];
        if (!value || value.startsWith('--')) {
            throw new Error(`参数 ${argument} 缺少值\n${usage()}`);
        }
        index += 1;
        if (argument === '--root') options.root = value;
        if (argument === '--expected') options.expected = value;
        if (argument === '--report') options.report = value;
        if (argument === '--generate-expected') options.generateExpected = value;
        if (argument === '--record-artifacts') options.recordArtifacts = value;
        if (argument === '--clean-expected') options.cleanExpected = value;
        if (argument === '--run-id') options.runId = value;
    }
    options.root = assertAbsolute(options.root, '--root');
    const operations = [options.expected, options.generateExpected, options.recordArtifacts, options.cleanExpected].filter(Boolean);
    if (operations.length !== 1 || options.expected && !options.report) {
        throw new Error(`必须选择且仅选择一个操作；--expected 需要同时提供 --report\n${usage()}`);
    }
    for (const key of ['expected', 'report', 'generateExpected', 'recordArtifacts', 'cleanExpected']) {
        if (options[key]) options[key] = assertAbsolute(options[key], `--${key.replace(/[A-Z]/gu, (letter) => `-${letter.toLowerCase()}`)}`);
    }
    if (options.generateExpected && !options.runId) {
        throw new Error('--generate-expected 需要 --run-id');
    }
    return options;
}

function relativePath(root, absolutePath) {
    const relative = path.relative(root, absolutePath);
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
        throw new Error(`路径超出执行根目录：${absolutePath}`);
    }
    return relative.split(path.sep).join('/') || '.';
}

function resolveArtifact(root, relative) {
    const absolute = path.resolve(root, relative);
    relativePath(root, absolute);
    return absolute;
}

async function exists(file) {
    try {
        return (await stat(file)).isFile();
    } catch {
        return false;
    }
}

async function sha256(file) {
    const hash = createHash('sha256');
    hash.update(await readFile(file));
    return hash.digest('hex');
}

async function walk(root, relative = '', files = []) {
    const directory = path.join(root, relative);
    for (const entry of await readdir(directory, { withFileTypes: true })) {
        if (entry.isDirectory() && SKIPPED_DIRECTORIES.has(entry.name)) continue;
        const next = path.join(relative, entry.name);
        if (entry.isDirectory() && next.split(path.sep).join('/').startsWith('scripts/tests/fixtures/')) continue;
        if (entry.isDirectory()) await walk(root, next, files);
        if (entry.isFile()) files.push(next.split(path.sep).join('/'));
    }
    return files;
}

function modulePathFromSource(source) {
    const marker = '/src/main/java/';
    const index = source.indexOf(marker);
    return index < 0 || index === 0 ? '.' : source.slice(0, index);
}

function countTestFiles(files, modulePath, matcher) {
    const prefix = modulePath === '.' ? 'src/test/java/' : `${modulePath}/src/test/java/`;
    return files.filter((file) => file.startsWith(prefix) && /\.java$/u.test(file) && matcher.test(path.posix.basename(file))).length;
}

export async function generateExpected(root, runId) {
    const files = await walk(root);
    const modules = new Set(files.filter((file) => (file.startsWith('src/main/java/') || file.includes('/src/main/java/')) && file.endsWith('.java')).map(modulePathFromSource));
    return {
        schemaVersion: SCHEMA_VERSION,
        runId,
        executionRoot: root,
        startedAt: new Date().toISOString(),
        modules: [...modules].sort().map((modulePath) => {
            const base = modulePath === '.' ? '' : `${modulePath}/`;
            const unitCount = countTestFiles(files, modulePath, /^(?!.*(?:IT|IntegrationTest)\.java$).*(?:Test|Tests)\.java$/u);
            const integrationCount = countTestFiles(files, modulePath, /(?:IT|IntegrationTest)\.java$/u);
            return {
                path: modulePath,
                javaSources: true,
                tests: {
                    unit: { required: unitCount > 0, reason: unitCount > 0 ? null : '未发现 Surefire 命名的单元测试源码' },
                    integration: { required: integrationCount > 0, reason: integrationCount > 0 ? null : '未发现 Failsafe 命名的集成测试源码' },
                },
                artifacts: {
                    jacocoXml: { path: `${base}target/site/jacoco/jacoco.xml`, sha256: null },
                    jacocoExec: { path: `${base}target/jacoco.exec`, sha256: null },
                },
            };
        }),
    };
}

async function readExpected(expectedPath) {
    let expected;
    try {
        expected = JSON.parse(await readFile(expectedPath, 'utf8'));
    } catch (error) {
        throw new Error(`无法读取期望报告矩阵：${error.message}`);
    }
    if (expected.schemaVersion !== SCHEMA_VERSION || !expected.runId || !Array.isArray(expected.modules) || !expected.executionRoot) {
        throw new Error('期望报告矩阵缺少 schemaVersion、runId、executionRoot 或 modules');
    }
    return expected;
}

async function writeJson(file, value) {
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

async function recordArtifacts(root, expectedPath) {
    const expected = await readExpected(expectedPath);
    if (path.resolve(expected.executionRoot) !== root) throw new Error('期望报告矩阵不属于当前执行根目录');
    for (const module of expected.modules) {
        for (const artifact of Object.values(module.artifacts)) {
            const artifactPath = resolveArtifact(root, artifact.path);
            artifact.sha256 = await exists(artifactPath) ? await sha256(artifactPath) : null;
        }
    }
    expected.recordedAt = new Date().toISOString();
    await writeJson(expectedPath, expected);
}

async function cleanExpected(root, expectedPath) {
    const expected = await readExpected(expectedPath);
    if (path.resolve(expected.executionRoot) !== root) throw new Error('期望报告矩阵不属于当前执行根目录');
    for (const module of expected.modules) {
        for (const artifact of Object.values(module.artifacts)) {
            const artifactPath = resolveArtifact(root, artifact.path);
            await rm(artifactPath, { force: true });
            if (await exists(artifactPath)) throw new Error(`无法清理旧产物：${artifact.path}`);
        }
    }
    for (const module of expected.modules) {
        for (const kind of ['surefire', 'failsafe']) {
            for (const file of await reportFiles(root, `${module.path}/target/${kind}-reports`)) await rm(file);
        }
    }
    expected.cleanedAt = new Date().toISOString();
    await writeJson(expectedPath, expected);
}

async function reportFiles(root, relativeDirectory) {
    const directory = resolveArtifact(root, relativeDirectory);
    try {
        const entries = await readdir(directory, { withFileTypes: true });
        return entries.filter((entry) => entry.isFile() && /^TEST-.*\.xml$/u.test(entry.name)).map((entry) => path.join(directory, entry.name));
    } catch (error) {
        if (error.code === 'ENOENT') return [];
        throw error;
    }
}

async function validateTestReports(root, module, kind, findings) {
    const directory = module.path === '.' ? `target/${kind}-reports` : `${module.path}/target/${kind}-reports`;
    const reports = await reportFiles(root, directory);
    const expected = module.tests[kind === 'surefire' ? 'unit' : 'integration'];
    if (expected.required && reports.length === 0) {
        findings.push({ rule: 'test-report-missing', module: module.path, message: `缺少 ${kind} 测试报告` });
    }
    for (const report of reports) {
        const source = await readFile(report, 'utf8');
        if (!/<testsuite\b[^>]*\btests=["'][1-9]\d*["']/u.test(source) || !/<\/testsuite>/u.test(source)) {
            findings.push({ rule: 'test-report-invalid', module: module.path, message: `${relativePath(root, report)} 为空或缺少有效测试套件` });
        }
        if (/(?:failures|errors|skipped)="[1-9]\d*"/u.test(source) || /<(?:failure|error|skipped)(?:\s|\/|>)/u.test(source)) {
            findings.push({ rule: 'test-report-invalid', module: module.path, message: `${relativePath(root, report)} 包含失败、错误或跳过测试` });
        }
    }
}

async function validateArtifact(root, module, artifactName, findings) {
    const artifact = module.artifacts[artifactName];
    const file = resolveArtifact(root, artifact.path);
    if (!await exists(file)) {
        findings.push({ rule: 'coverage-artifact-missing', module: module.path, message: `缺少 ${artifact.path}` });
        return null;
    }
    const actualHash = await sha256(file);
    if (!artifact.sha256 || actualHash !== artifact.sha256) {
        findings.push({ rule: 'coverage-artifact-stale', module: module.path, message: `${artifact.path} 与本次构建清单摘要不一致` });
    }
    return file;
}

export async function verifyReports(root, expectedPath) {
    const expected = await readExpected(expectedPath);
    if (path.resolve(expected.executionRoot) !== root) throw new Error('期望报告矩阵不属于当前执行根目录');
    if (!expected.recordedAt) throw new Error('期望报告矩阵尚未记录本次构建产物摘要');
    const findings = [];
    for (const module of expected.modules) {
        await validateTestReports(root, module, 'surefire', findings);
        await validateTestReports(root, module, 'failsafe', findings);
        const xml = await validateArtifact(root, module, 'jacocoXml', findings);
        await validateArtifact(root, module, 'jacocoExec', findings);
        if (xml) {
            const source = await readFile(xml, 'utf8');
            const hasReportRoot = /<report\b[^>]*>[\s\S]*<\/report>/u.test(source);
            const hasInstructionCounter = /<counter\b[^>]*\btype=["']INSTRUCTION["'][^>]*\/?\s*>/u.test(source);
            const hasBranchCounter = /<counter\b[^>]*\btype=["']BRANCH["'][^>]*\/?\s*>/u.test(source);
            if (!hasReportRoot || !hasInstructionCounter || !hasBranchCounter) findings.push({ rule: 'coverage-report-invalid', module: module.path, message: `${relativePath(root, xml)} 缺少有效 report 根元素或 INSTRUCTION/BRANCH counter` });
        }
    }
    const testsStatus = findings.some((f) => f.rule.startsWith('test-report')) ? 'failed' : 'passed';
    const coverageStatus = findings.some((f) => f.rule.startsWith('coverage-')) ? 'failed' : 'passed';
    return { testsStatus, coverageStatus, sonarStatus: 'not_applicable', schemaVersion: SCHEMA_VERSION, runId: expected.runId, executionRoot: root, expectedPath, checks: expected.modules.map((module) => ({ id: `module:${module.path}`, status: findings.some((finding) => finding.module === module.path) ? 'failed' : 'passed' })), findings, overall: findings.length === 0 ? 'passed' : 'failed', exitCode: findings.length === 0 ? 0 : 1 };
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    if (options.generateExpected) {
        await writeJson(options.generateExpected, await generateExpected(options.root, options.runId));
        return 0;
    }
    if (options.recordArtifacts) {
        await recordArtifacts(options.root, options.recordArtifacts);
        return 0;
    }
    if (options.cleanExpected) {
        await cleanExpected(options.root, options.cleanExpected);
        return 0;
    }
    const result = await verifyReports(options.root, options.expected);
    await writeJson(options.report, result);
    return result.exitCode;
}

main().then((exitCode) => { process.exitCode = exitCode; }).catch((error) => { process.stderr.write(`报告核验失败：${error.message}\n`); process.exitCode = 2; });
