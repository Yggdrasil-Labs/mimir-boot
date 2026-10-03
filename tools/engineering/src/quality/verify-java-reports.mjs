import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { DOMImplementation, DOMParser, XMLSerializer } from '@xmldom/xmldom';
import { generateExpectedFromModel } from './java-build-model.mjs';

const SCHEMA_VERSION = 1;
const execFileAsync = promisify(execFile);

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

export function parseArgs(argv) {
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

export async function generateExpected(root, runId, effectivePomSource = null) {
    if (effectivePomSource !== null) return generateExpectedFromModel(root, runId, effectivePomSource);
    const temporary = await mkdtemp(path.join(tmpdir(), 'mimir-java-model-'));
    try {
        const output = path.join(temporary, 'effective-pom.xml');
        try {
            await execFileAsync(path.join(root, 'mvnw'), ['-B', '-Pci', 'help:effective-pom', `-Doutput=${output}`], { cwd: root, maxBuffer: 16 * 1024 * 1024, timeout: 180000 });
        } catch (error) {
            throw new Error(`无法获取 Java 有效构建模型：${error.message}\n${error.stdout || ''}\n${error.stderr || ''}`);
        }
        return await generateExpectedFromModel(root, runId, await readFile(output, 'utf8'));
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
}

async function readExpected(expectedPath) {
    let expected;
    try {
        expected = JSON.parse(await readFile(expectedPath, 'utf8'));
    } catch (error) {
        throw new Error(`无法读取期望报告矩阵：${error.message}`);
    }
    if (expected.schemaVersion !== SCHEMA_VERSION || !expected.runId || !Array.isArray(expected.modules) || !expected.modules.length || !expected.executionRoot) {
        throw new Error('期望报告矩阵缺少 schemaVersion、runId、executionRoot 或 modules');
    }
    return expected;
}

async function writeJson(file, value) {
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function testReportDirectory(module, kind) {
    const type = kind === 'surefire' ? 'unit' : 'integration';
    return module.tests[type].reportsDirectory || path.posix.join(module.path, 'target', `${kind}-reports`);
}

export async function recordArtifacts(root, expectedPath, { buildSucceeded = true } = {}) {
    const expected = await readExpected(expectedPath);
    if (path.resolve(expected.executionRoot) !== root) throw new Error('期望报告矩阵不属于当前执行根目录');
    if (!expected.cleanedAt) throw new Error('期望报告矩阵尚未清理旧构建产物');
    for (const module of expected.modules) {
        for (const artifact of Object.values(module.artifacts)) {
            const artifactPath = resolveArtifact(root, artifact.path);
            artifact.sha256 = await exists(artifactPath) ? await sha256(artifactPath) : null;
        }
        for (const [kind, type] of [['surefire', 'unit'], ['failsafe', 'integration']]) {
            module.tests[type].reports = [];
            for (const file of await reportFiles(root, testReportDirectory(module, kind))) {
                module.tests[type].reports.push({ path: relativePath(root, file), sha256: await sha256(file) });
            }
        }
    }
    expected.buildStatus = buildSucceeded ? 'passed' : 'failed';
    delete expected.recordedAt;
    delete expected.diagnosticsRecordedAt;
    expected[buildSucceeded ? 'recordedAt' : 'diagnosticsRecordedAt'] = new Date().toISOString();
    await writeJson(expectedPath, expected);
}

export async function cleanExpected(root, expectedPath) {
    const expected = await readExpected(expectedPath);
    if (path.resolve(expected.executionRoot) !== root) throw new Error('期望报告矩阵不属于当前执行根目录');
    // 在删除产物前使旧记录失效，后续清理失败也不能复用成功清单。
    delete expected.cleanedAt;
    delete expected.recordedAt;
    delete expected.diagnosticsRecordedAt;
    delete expected.buildStatus;
    await writeJson(expectedPath, expected);
    for (const module of expected.modules) {
        for (const test of Object.values(module.tests)) test.reports = [];
        for (const artifact of Object.values(module.artifacts)) {
            const artifactPath = resolveArtifact(root, artifact.path);
            await rm(artifactPath, { force: true });
            artifact.sha256 = null;
            if (await exists(artifactPath)) throw new Error(`无法清理旧产物：${artifact.path}`);
        }
    }
    for (const module of expected.modules) {
        for (const kind of ['surefire', 'failsafe']) {
            for (const file of await reportFiles(root, testReportDirectory(module, kind))) await rm(file);
        }
    }
    expected.cleanedAt = new Date().toISOString();
    delete expected.recordedAt;
    delete expected.diagnosticsRecordedAt;
    delete expected.buildStatus;
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
    const directory = testReportDirectory(module, kind);
    const reports = await reportFiles(root, directory);
    const expected = module.tests[kind === 'surefire' ? 'unit' : 'integration'];
    const recorded = new Map((expected.reports || []).map((report) => [report.path, report.sha256]));
    const executedClasses = new Set();
    const classpaths = new Set();
    if (expected.required && reports.length === 0) {
        findings.push({ rule: 'test-report-missing', module: module.path, message: `缺少 ${kind} 测试报告` });
    }
    for (const report of reports) {
        const relative = relativePath(root, report);
        if (!recorded.get(relative) || recorded.get(relative) !== await sha256(report)) {
            findings.push({ rule: 'test-report-stale', module: module.path, message: `${relative} 与本次构建清单摘要不一致` });
        }
        recorded.delete(relative);
        const source = await readFile(report, 'utf8');
        try {
            const suite = parseXml(source, 'testsuite');
            if (counter(suite, 'tests') < 1) throw new Error('测试数量必须大于零');
            for (const name of ['failures', 'errors', 'skipped']) {
                if (counter(suite, name) !== 0) throw new Error('包含失败、错误或跳过测试');
            }
            for (const name of ['failure', 'error', 'skipped']) {
                if (suite.getElementsByTagName(name).length) throw new Error('包含失败、错误或跳过测试');
            }
            const cases = Array.from(suite.getElementsByTagName('testcase'));
            if (cases.length !== counter(suite, 'tests')) throw new Error('testcase 数量与 tests 计数不一致');
            for (const test of cases) {
                const name = test.getAttribute('classname');
                if (!name) throw new Error('testcase 缺少 classname');
                executedClasses.add(name);
            }
            for (const property of Array.from(suite.getElementsByTagName('property'))) {
                if (property.getAttribute('name') === 'surefire.test.class.path') classpaths.add(property.getAttribute('value'));
            }
        } catch (error) {
            findings.push({ rule: 'test-report-invalid', module: module.path, message: `${relative}：${error.message}` });
        }
    }
    for (const relative of recorded.keys()) {
        findings.push({ rule: 'test-report-missing', module: module.path, message: `本次构建记录的报告已丢失：${relative}` });
    }
    if (expected.required && reports.length) {
        try {
            if (classpaths.size !== 1 || ![...classpaths][0]) throw new Error('缺少唯一的实际测试类路径');
            const classes = await discoverTestClasses(root, expected, [...classpaths][0]);
            if (!classes.length) throw new Error('选择器未发现可运行的 JUnit Jupiter 测试类');
            for (const name of classes) {
                if (![...executedClasses].some((actual) => actual === name || actual.startsWith(`${name}$`))) {
                    findings.push({ rule: 'test-report-missing', module: module.path, message: `${kind} 缺少测试类执行证据：${name}` });
                }
            }
        } catch (error) {
            findings.push({ rule: 'test-report-discovery-error', module: module.path, message: `${kind}：${error.message}` });
        }
    }
}

async function discoverTestClasses(root, expected, classpath) {
    if (!expected.sourceClasses?.length || !expected.testClassesDirectory) throw new Error('缺少源码测试类或编译测试目录期望');
    const classesDirectory = resolveArtifact(root, expected.testClassesDirectory);
    if (!classpath.split(path.delimiter).some((item) => path.resolve(item) === classesDirectory)) {
        throw new Error('实际测试类路径未包含期望编译测试目录');
    }
    const temporary = await mkdtemp(path.join(tmpdir(), 'mimir-test-inventory-'));
    try {
        const request = new DOMImplementation().createDocument(null, 'request', null);
        const append = (tag, value) => {
            const element = request.createElement(tag);
            element.appendChild(request.createTextNode(value));
            request.documentElement.appendChild(element);
        };
        for (const name of expected.sourceClasses) append('candidate', name);
        const input = path.join(temporary, 'request.xml');
        const output = path.join(temporary, 'inventory.xml');
        const helper = fileURLToPath(new URL('./TestInventory.java', import.meta.url));
        await writeFile(input, new XMLSerializer().serializeToString(request), 'utf8');
        try {
            await execFileAsync('java', ['--class-path', classpath, helper, input, output, classesDirectory],
                { cwd: root, timeout: 60000, maxBuffer: 4 * 1024 * 1024 });
        } catch (error) {
            throw new Error(`测试清单发现失败：${error.stderr || error.message}`);
        }
        return Array.from(parseXml(await readFile(output, 'utf8'), 'inventory').getElementsByTagName('class')).map((item) => item.textContent);
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
}

function parseXml(source, rootName) {
    const document = new DOMParser({ onError: (_level, message) => { throw new Error(message); } }).parseFromString(source, 'application/xml');
    const root = document.documentElement;
    if (!root || root.tagName !== rootName) throw new Error(`缺少有效 ${rootName} 根元素`);
    return root;
}

function counter(element, name) {
    const value = element.getAttribute(name);
    if (!/^\d+$/u.test(value || '') || !Number.isSafeInteger(Number(value))) throw new Error(`无效计数 ${name}`);
    return Number(value);
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
    if (!expected.cleanedAt) throw new Error('期望报告矩阵尚未清理旧构建产物');
    if (!expected.recordedAt && !expected.diagnosticsRecordedAt) throw new Error('期望报告矩阵尚未记录本次构建产物摘要');
    const findings = [];
    if (!expected.recordedAt || expected.buildStatus === 'failed') {
        findings.push({ rule: 'build-not-completed', message: '当前清单仅保留失败构建的诊断产物，不能作为完整构建成功证据' });
    }
    for (const module of expected.modules) {
        await validateTestReports(root, module, 'surefire', findings);
        await validateTestReports(root, module, 'failsafe', findings);
        if (module.coverage?.required === false) continue;
        const xml = await validateArtifact(root, module, 'jacocoXml', findings);
        await validateArtifact(root, module, 'jacocoExec', findings);
        if (xml) {
            const source = await readFile(xml, 'utf8');
            try {
                const report = parseXml(source, 'report');
                const counters = Array.from(report.childNodes).filter((node) => node.nodeType === 1 && node.tagName === 'counter');
                for (const type of ['INSTRUCTION', 'BRANCH']) {
                    const matches = counters.filter((node) => node.getAttribute('type') === type);
                    if (matches.length !== 1) throw new Error(`缺少唯一的根级 ${type} counter`);
                    const missed = counter(matches[0], 'missed');
                    const covered = counter(matches[0], 'covered');
                    const minimum = module.coverage?.[type === 'INSTRUCTION' ? 'instructionMinimum' : 'branchMinimum'];
                    if (minimum !== undefined && minimum !== null) {
                        if (!Number.isFinite(minimum) || minimum < 0 || minimum > 1) throw new Error(`无效 ${type} 最低阈值`);
                        if (missed + covered > 0 && covered / (missed + covered) < minimum) {
                            findings.push({ rule: 'coverage-threshold-not-met', module: module.path,
                                message: `${type} 覆盖率 ${covered}/${missed + covered} 低于登记阈值 ${minimum}` });
                        }
                    }
                }
            } catch (error) {
                findings.push({ rule: 'coverage-report-invalid', module: module.path, message: `${relativePath(root, xml)}：${error.message}` });
            }
        }
    }
    const testsStatus = findings.some((f) => f.rule.startsWith('test-report')) ? 'failed' : 'passed';
    const coverageStatus = findings.some((f) => f.rule.startsWith('coverage-')) ? 'failed' : 'passed';
    return { testsStatus, coverageStatus, sonarStatus: 'not_applicable', schemaVersion: SCHEMA_VERSION, runId: expected.runId, executionRoot: root, expectedPath, checks: expected.modules.map((module) => ({ id: `module:${module.path}`, status: findings.some((finding) => finding.module === module.path) ? 'failed' : module.exemption && !module.tests.unit.required && !module.tests.integration.required ? 'not_applicable' : 'passed', reason: module.exemption || null })), findings, overall: findings.length === 0 ? 'passed' : 'failed', exitCode: findings.length === 0 ? 0 : 1 };
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

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().then((exitCode) => { process.exitCode = exitCode ?? 0; }).catch((error) => { process.stderr.write(`报告核验失败：${error.message}\n`); process.exitCode = 2; });
}
