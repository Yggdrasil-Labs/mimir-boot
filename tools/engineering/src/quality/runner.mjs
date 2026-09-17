import { createWriteStream } from 'node:fs';
import { mkdtemp, mkdir, readFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { tmpdir } from 'node:os';
import { performance } from 'node:perf_hooks';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import {
    addFinding,
    appendResult,
    createCheck,
    createRunReport,
    finalizeReport,
    writeReport,
} from './results.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
let cancellationSignal = null;

// 完整门禁的唯一事实源；执行和 --list 都从这里生成，不允许各自维护一套命令枚举。
export const FULL_PLAN = Object.freeze([
    Object.freeze({ id: 'docs-full', entry: 'src/docs/check.mjs', kind: 'docs', args: ['--mode', 'full', '--self-test'] }),
    Object.freeze({ id: 'verify-build-model', entry: 'src/quality/verify-build-model.mjs', kind: 'tool', args: [] }),
    Object.freeze({ id: 'release-contracts', entry: 'src/release/verify-contracts.mjs', kind: 'tool', args: [] }),
    Object.freeze({ id: 'release-consumer', entry: 'src/release/consumer.mjs', kind: 'tool', args: [] }),
    Object.freeze({ id: 'release-signing', entry: 'src/release/signing.mjs', kind: 'tool', args: ['--preheat'] }),
    Object.freeze({ id: 'java-quality', entry: 'src/quality/java.mjs', kind: 'java', args: [] }),
]);

function usage() {
    return [
        '用法：node runner.mjs --mode <quick|full> --source <worktree|index|commit>',
        '      [--root <绝对路径>] [--commit <sha|null>] [--tree <id>] [--changed-files <绝对 NUL 文件>]',
        '      [--report <绝对路径>] [--report-directory <绝对路径>] [--list]',
    ].join('\n');
}

function absolute(value, label) {
    if (!value || !path.isAbsolute(value)) throw new Error(`${label} 必须是绝对路径`);
    return path.resolve(value);
}

export function parseArgs(argv) {
    const options = {
        mode: null,
        source: null,
        root: ROOT,
        commit: null,
        tree: null,
        changedFiles: null,
        report: null,
        reportDirectory: null,
        bootstrapLog: null,
        bootstrapDuration: 0,
        runId: null,
        list: false,
    };
    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        if (argument === '--list') {
            options.list = true;
            continue;
        }
        if (!['--mode', '--source', '--root', '--commit', '--tree', '--changed-files', '--report', '--report-directory', '--run-id', '--bootstrap-log', '--bootstrap-duration'].includes(argument)) {
            throw new Error(`未知参数：${argument}\n${usage()}`);
        }
        const value = argv[index + 1];
        if (!value || value.startsWith('--')) throw new Error(`参数 ${argument} 缺少值\n${usage()}`);
        index += 1;
        if (argument === '--mode') options.mode = value;
        if (argument === '--source') options.source = value;
        if (argument === '--root') options.root = value;
        if (argument === '--commit') options.commit = value === 'null' ? null : value;
        if (argument === '--tree') options.tree = value;
        if (argument === '--changed-files') options.changedFiles = value;
        if (argument === '--report') options.report = value;
        if (argument === '--report-directory') options.reportDirectory = value;
        if (argument === '--run-id') options.runId = value;
        if (argument === '--bootstrap-log') options.bootstrapLog = value;
        if (argument === '--bootstrap-duration') options.bootstrapDuration = Number(value);
    }
    if (!options.mode || !['quick', 'full'].includes(options.mode)) throw new Error(`--mode 只允许 quick 或 full\n${usage()}`);
    if (!options.source || !['worktree', 'index', 'commit'].includes(options.source)) throw new Error(`--source 只允许 worktree、index 或 commit\n${usage()}`);
    options.root = absolute(options.root, '--root');
    if (options.commit && options.source !== 'commit') throw new Error('只有 commit 模式可以提供 --commit');
    if (options.source === 'commit' && !options.commit) throw new Error('commit 模式必须提供 --commit');
    if (options.report) options.report = absolute(options.report, '--report');
    if (options.reportDirectory) options.reportDirectory = absolute(options.reportDirectory, '--report-directory');
    if (options.changedFiles) options.changedFiles = absolute(options.changedFiles, '--changed-files');
    if (options.bootstrapLog) options.bootstrapLog = absolute(options.bootstrapLog, '--bootstrap-log');
    if (!Number.isInteger(options.bootstrapDuration) || options.bootstrapDuration < 0) throw new Error('--bootstrap-duration 必须是非负整数');
    if (!options.tree) options.tree = options.source === 'worktree' ? 'worktree' : `${options.source}-tree`;
    if (!options.runId) options.runId = `quality-${Date.now()}-${process.pid}`;
    return options;
}

async function readNulFile(file) {
    if (!file) return [];
    const content = await readFile(file);
    return content.toString('utf8').split('\0').filter(Boolean);
}

function isDocumentationControl(file) {
    return file === 'pom.xml'
        || file === 'mvnw'
        || file.startsWith('tools/')
        || file.startsWith('scripts/')
        || file.startsWith('.githooks/')
        || file.startsWith('.mvn/')
        || file.startsWith('.markdownlint')
        || file.includes('spotless');
}

function isJavaFile(file) {
    return file === 'pom.xml' || file.endsWith('.java') || file.endsWith('/pom.xml') || isDocumentationControl(file);
}

export function classifyQuickChanges(files) {
    const docs = files.some((file) => isDocumentationControl(file) || /\.(?:md|markdown|mdx)$/iu.test(file));
    const java = files.some(isJavaFile);
    return { docs, java };
}

function processResult(exitCode, signal = null, error = null) {
    if (error || signal) return { exitCode: 2, signal, error };
    return { exitCode: Number.isInteger(exitCode) ? exitCode : 2, signal: null, error: null };
}

function rememberCancellation(signal) {
    cancellationSignal ||= signal;
}

function terminateProcessGroup(child, signal) {
    if (!child) return;
    if (process.platform !== 'win32' && child.pid) {
        try {
            process.kill(-child.pid, signal);
            return;
        } catch (error) {
            if (error.code !== 'ESRCH') {
                // 进程组不可用时退回到单进程终止；不能因为清理失败而继续门禁。
            }
        }
    }
    try {
        if (!child.killed) child.kill(signal);
    } catch {
        // close 事件会把无法终止的子进程归类为 error。
    }
}

function installCancellationHandler() {
    const handlers = new Map();
    for (const signal of ['SIGINT', 'SIGTERM']) {
        const handler = () => rememberCancellation(signal);
        handlers.set(signal, handler);
        process.on(signal, handler);
    }
    return () => {
        for (const [signal, handler] of handlers) process.removeListener(signal, handler);
    };
}

/** 执行长命令并保证返回前日志流已经 flush，避免读取报告时与写入竞态。 */
export async function runProcess({ command, args, cwd, logPath }) {
    const started = performance.now();
    let log;
    try {
        await mkdir(path.dirname(logPath), { recursive: true });
        log = createWriteStream(logPath, { flags: 'w' });
    } catch (error) {
        return { ...processResult(2, null, error), durationMs: performance.now() - started };
    }
    return new Promise((resolve) => {
        let child;
        let settled = false;
        let resolved = false;
        let logError = null;
        const signalHandlers = new Map();
        const complete = (result) => {
            if (resolved) return;
            resolved = true;
            resolve({ ...result, durationMs: performance.now() - started });
        };
        const onLogError = (error) => {
            logError ||= error;
            terminateProcessGroup(child, 'SIGTERM');
        };
        const finish = (result) => {
            if (settled) return;
            settled = true;
            for (const [signal, handler] of signalHandlers) process.removeListener(signal, handler);
            const finalResult = () => complete(logError ? processResult(2, null, logError) : result);
            if (log.destroyed) {
                finalResult();
                return;
            }
            log.end(finalResult);
        };
        log.on('error', onLogError);
        try {
            child = spawn(command, args, {
                cwd,
                stdio: ['ignore', 'pipe', 'pipe'],
                detached: process.platform !== 'win32',
            });
        } catch (error) {
            finish(processResult(2, null, error));
            return;
        }
        for (const signal of ['SIGINT', 'SIGTERM']) {
            const handler = () => {
                rememberCancellation(signal);
                terminateProcessGroup(child, signal);
            };
            signalHandlers.set(signal, handler);
            process.once(signal, handler);
        }
        if (cancellationSignal) terminateProcessGroup(child, cancellationSignal);
        for (const stream of [child.stdout, child.stderr]) {
            stream.on('data', (chunk) => {
                if (!log.destroyed) log.write(chunk);
                process.stdout.write(chunk);
            });
            stream.on('error', onLogError);
        }
        child.on('error', (error) => finish(processResult(2, null, error)));
        child.on('close', (exitCode, signal) => finish(processResult(exitCode, signal)));
    });
}

function processCheck(id, command, result, logPath, required = true, reasonPrefix = '检查返回非零退出码') {
    const status = result.exitCode === 0 ? 'passed' : result.exitCode === 1 ? 'failed' : 'error';
    return createCheck({
        id,
        command,
        logPath,
        status,
        required,
        exitCode: result.exitCode,
        reason: status === 'passed' ? null : `${reasonPrefix}：${result.error?.message || result.exitCode}`,
        durationMs: result.durationMs ?? 0,
    });
}

async function readChildReport(file) {
    try {
        return JSON.parse(await readFile(file, 'utf8'));
    } catch {
        return null;
    }
}

function addChildFindings(report, childReport, prefix = '') {
    for (const finding of childReport?.findings || []) {
        addFinding(report, {
            severity: finding.severity || 'error',
            rule: prefix ? `${prefix}:${finding.rule || 'finding'}` : finding.rule || 'quality-child',
            path: finding.path || null,
            line: finding.line || null,
            message: finding.module ? `[${finding.module}] ${finding.message}` : finding.message,
        });
    }
}

function validateChildReport(childReport, requiredIds) {
    if (!childReport || childReport.schemaVersion !== 1 || !Array.isArray(childReport.checks)) {
        return '子检查未生成有效报告';
    }
    const ids = new Set();
    const checksById = new Map();
    for (const check of childReport.checks) {
        if (!check || typeof check.id !== 'string' || ids.has(check.id)) return '子检查报告包含无效或重复的 check id';
        if (!['passed', 'failed', 'error', 'not_run', 'not_applicable'].includes(check.status)) return `子检查 ${check.id} 包含无效状态`;
        if (check.status !== 'passed' && (typeof check.reason !== 'string' || !check.reason.trim())) return `子检查 ${check.id} 缺少非通过状态的原因`;
        if (check.status === 'passed' && check.exitCode !== 0) return `子检查 ${check.id} 标记 passed 但退出码不是 0`;
        if (check.status === 'failed' && check.exitCode !== 1) return `子检查 ${check.id} 标记 failed 但退出码不是 1`;
        if (check.status === 'error' && !(Number.isInteger(check.exitCode) && check.exitCode >= 2)) return `子检查 ${check.id} 标记 error 但退出码无效`;
        if (['not_run', 'not_applicable'].includes(check.status) && check.exitCode !== null) return `子检查 ${check.id} 未执行或不适用但退出码不是 null`;
        ids.add(check.id);
        checksById.set(check.id, check);
    }
    const missing = requiredIds.filter((id) => !ids.has(id));
    if (missing.length > 0) return `子检查报告缺少必要检查：${missing.join('、')}`;
    const sonarMode = process.env.RUN_SONAR || 'false';
    for (const id of requiredIds) {
        const check = checksById.get(id);
        const sonarOptional = id === 'java-sonar' && sonarMode === 'false';
        if (!sonarOptional && check.required !== true) return `必要检查 ${id} 必须标记 required=true`;
        if (sonarOptional && (check.required !== false || check.status !== 'not_applicable')) return 'RUN_SONAR=false 时 java-sonar 必须显式 not_applicable 且 required=false';
        if (!sonarOptional && ['not_run', 'not_applicable'].includes(check.status)) return `必要检查 ${id} 不允许为 ${check.status}`;
    }
    if (!Number.isInteger(childReport.exitCode) || !['passed', 'failed', 'error'].includes(childReport.overall)) {
        return '子检查报告缺少有效 overall/exitCode';
    }
    if (childReport.overall === 'passed' && childReport.exitCode === 0
        && childReport.checks.some((check) => check.required !== false && ['failed', 'error', 'not_run'].includes(check.status))) {
        return '子检查报告声称通过但仍有必需检查未通过';
    }
    return null;
}

function childResultError(result, childReport, validationError) {
    if (result.exitCode !== 0) return null;
    if (validationError) return validationError;
    if (childReport.exitCode !== 0 || childReport.overall !== 'passed') return '子检查报告与零退出码不一致';
    return null;
}

function reportChildFailure(report, id, result, validationError) {
    const message = validationError || '子检查报告不可用';
    addFinding(report, { severity: 'error', rule: `quality-child:${id}`, path: null, line: null, message });
    return { ...processResult(2, null, new Error(message)), durationMs: result.durationMs };
}

async function runEngineeringTool({ root, toolPath, entry, args, logPath }) {
    return runProcess({
        command: 'bash',
        args: [toolPath, entry, ...args],
        cwd: root,
        logPath,
    });
}

async function runToolCheck(report, options, id, entry, args = [], required = true) {
    const logPath = path.join(options.reportDirectory, 'logs', `${id}.log`);
    const command = ['bash', 'scripts/lib/engineering-tool.sh', entry, ...args];
    const result = await runEngineeringTool({
        root: options.root,
        toolPath: 'scripts/lib/engineering-tool.sh',
        entry,
        args,
        logPath,
    });
    appendResult(report, { check: processCheck(id, command, result, logPath, required), findings: [] });
    return result;
}

async function runDocsCheck(report, options, mode, plan = null) {
    const id = mode === 'full' ? 'docs-full' : 'docs-format';
    const childReportPath = path.join(options.reportDirectory, 'docs-report.json');
    const args = ['--root', options.root, '--mode', mode, '--report', childReportPath];
    if (plan?.args?.includes('--self-test') || mode === 'full') args.push('--self-test');
    const logPath = path.join(options.reportDirectory, 'logs', `${id}.log`);
    const command = ['bash', 'scripts/lib/engineering-tool.sh', 'src/docs/check.mjs', ...args];
    const result = await runEngineeringTool({ root: options.root, toolPath: 'scripts/lib/engineering-tool.sh', entry: 'src/docs/check.mjs', args, logPath });
    const childReport = await readChildReport(childReportPath);
    addChildFindings(report, childReport, 'docs');
    const requiredIds = mode === 'full'
        ? ['markdown-format', 'internal-links', 'navigation', 'tool-self-test']
        : ['markdown-format'];
    const validationError = childResultError(result, childReport, validateChildReport(childReport, requiredIds));
    const effectiveResult = validationError ? reportChildFailure(report, id, result, validationError) : result;
    appendResult(report, { check: processCheck(id, command, effectiveResult, logPath), findings: [] });
    return effectiveResult;
}

function javaChildArgs(options, javaDirectory, childReportPath) {
    return [
        '--root', options.root,
        '--report', childReportPath,
        '--report-directory', javaDirectory,
        '--run-id', options.runId,
        '--source', options.source,
        '--commit', options.commit || 'null',
        '--tree', options.tree,
    ];
}

function appendMissingJavaChecks(report, childReport, reason) {
    const existing = new Set(report.checks.map((check) => check.id));
    const childIds = new Set(childReport?.checks?.map((check) => check.id) || []);
    const sonarMode = process.env.RUN_SONAR || 'false';
    for (const id of ['java-tests', 'java-coverage', 'java-sonar']) {
        if (existing.has(id) || childIds.has(id)) continue;
        const sonar = id === 'java-sonar';
        appendResult(report, {
            check: createCheck({
                id,
                status: sonar && sonarMode === 'false' ? 'not_applicable' : 'not_run',
                required: !sonar || sonarMode !== 'false',
                exitCode: null,
                reason,
            }),
            findings: [],
        });
    }
}

async function runJavaCheck(report, options) {
    const javaDirectory = path.join(options.reportDirectory, 'java');
    const childReportPath = path.join(javaDirectory, 'java-quality-report.json');
    const args = javaChildArgs(options, javaDirectory, childReportPath);
    const logPath = path.join(options.reportDirectory, 'logs', 'java-quality.log');
    const command = ['bash', 'scripts/lib/engineering-tool.sh', 'src/quality/java.mjs', ...args];
    const result = await runEngineeringTool({ root: options.root, toolPath: 'scripts/lib/engineering-tool.sh', entry: 'src/quality/java.mjs', args, logPath });
    const childReport = await readChildReport(childReportPath);
    addChildFindings(report, childReport, 'java');
    const requiredIds = ['java-tests', 'java-coverage', 'java-sonar'];
    const validationError = childResultError(result, childReport, validateChildReport(childReport, requiredIds));
    const effectiveResult = validationError ? reportChildFailure(report, 'java-quality', result, validationError) : result;
    appendResult(report, { check: processCheck('java-quality', command, effectiveResult, logPath), findings: [] });
    const copied = new Set();
    for (const childCheck of childReport?.checks || []) {
        if (!requiredIds.includes(childCheck.id) || copied.has(childCheck.id)) continue;
        copied.add(childCheck.id);
        appendResult(report, { check: childCheck, findings: [] });
    }
    if (validationError) appendMissingJavaChecks(report, childReport, validationError);
    return effectiveResult;
}

function appendStageError(report, id, error) {
    if (report.checks.some((check) => check.id === id)) {
        addFinding(report, { severity: 'error', rule: `quality-stage:${id}`, path: null, line: null, message: error.message });
        return;
    }
    appendResult(report, {
        check: createCheck({ id, status: 'error', exitCode: 2, reason: error.message }),
        findings: [{ severity: 'error', rule: `quality-stage:${id}`, path: null, line: null, message: error.message }],
    });
}

function cancellationReason() {
    return `收到 ${cancellationSignal || 'SIGTERM'}，已停止后续质量阶段`;
}

function appendNotRun(report, id, reason, required = true) {
    if (report.checks.some((check) => check.id === id)) return;
    appendResult(report, {
        check: createCheck({ id, status: 'not_run', required, exitCode: null, reason }),
        findings: [],
    });
}

function appendCancellation(report) {
    if (report.checks.some((check) => check.id === 'quality-cancelled')) return;
    const reason = cancellationReason();
    appendResult(report, {
        check: createCheck({ id: 'quality-cancelled', status: 'error', required: true, exitCode: 2, reason }),
        findings: [{ severity: 'error', rule: 'quality-cancelled', path: null, line: null, message: reason }],
    });
}

function appendCancelledPlan(report, startIndex) {
    const reason = cancellationReason();
    for (const step of FULL_PLAN.slice(startIndex)) {
        appendNotRun(report, step.id, reason, true);
        if (step.kind === 'java') appendMissingJavaChecks(report, null, reason);
    }
}

async function runIndependentStage(report, id, action) {
    try {
        return await action();
    } catch (error) {
        const normalized = error instanceof Error ? error : new Error(String(error));
        appendStageError(report, id, normalized);
        return processResult(2, null, normalized);
    }
}

async function runFullStep(report, options, step) {
    if (step.kind === 'docs') return runDocsCheck(report, options, 'full', step);
    if (step.kind === 'java') return runJavaCheck(report, options);
    return runToolCheck(report, options, step.id, step.entry, step.args);
}

function reportDirectoryText(options) {
    return options.reportDirectory || '<report-directory>';
}

function listCommand(options, step) {
    const tool = 'bash scripts/lib/engineering-tool.sh';
    if (step.kind === 'docs') {
        return `${tool} ${step.entry} --root ${options.root} --mode full --report ${path.join(reportDirectoryText(options), 'docs-report.json')} --self-test`;
    }
    if (step.kind === 'java') {
        const javaDirectory = path.join(reportDirectoryText(options), 'java');
        const args = javaChildArgs({ ...options, runId: '<run-id>' }, javaDirectory, path.join(javaDirectory, 'java-quality-report.json'));
        return `${tool} ${step.entry} ${args.map((value) => value.includes(' ') ? JSON.stringify(value) : value).join(' ')}`;
    }
    return `${tool} ${step.entry}${step.args.length > 0 ? ` ${step.args.join(' ')}` : ''}`;
}

export function listPlan(options) {
    if (options.mode !== 'full') return ['quick: docs-format（按变更） -> java-format（按变更）'];
    return FULL_PLAN.map((step) => `${step.id}: ${listCommand(options, step)}`);
}

async function runQualityInternal(options) {
    if (!options.reportDirectory) options.reportDirectory = await mkdtemp(path.join(tmpdir(), 'mimir-quality-'));
    if (!options.report) options.report = path.join(options.reportDirectory, 'quality-report.json');
    const report = await createRunReport({
        root: options.root,
        reportDirectory: options.reportDirectory,
        runId: options.runId,
        source: options.source,
        commit: options.commit,
        tree: options.tree,
    });
    report.toolVersions.engineeringTool = path.join(options.root, 'scripts/lib/engineering-tool.sh');
    if (options.bootstrapLog) {
        appendResult(report, {
            check: createCheck({
                id: 'engineering-bootstrap',
                command: ['bash', 'scripts/lib/engineering-tool.sh', '--ensure'],
                logPath: options.bootstrapLog,
                status: 'passed',
                required: true,
                exitCode: 0,
                durationMs: options.bootstrapDuration,
            }),
            findings: [],
        });
    }
    if (options.mode === 'quick') {
        let changes;
        try {
            changes = await readNulFile(options.changedFiles);
        } catch (error) {
            const normalized = error instanceof Error ? error : new Error(String(error));
            appendStageError(report, 'docs-format', normalized);
            appendNotRun(report, 'java-format', normalized.message, true);
            if (cancellationSignal) appendCancellation(report);
            finalizeReport(report);
            report.finishedAt = new Date().toISOString();
            await writeReport(options.report, report);
            return report;
        }
        const categories = classifyQuickChanges(changes);
        if (cancellationSignal && categories.docs) {
            appendNotRun(report, 'docs-format', cancellationReason(), true);
        } else if (categories.docs) {
            await runIndependentStage(report, 'docs-format', () => runDocsCheck(report, options, 'format'));
        } else {
            appendResult(report, { check: createCheck({ id: 'docs-format', status: 'not_applicable', required: false, exitCode: null, reason: '本次变更不涉及文档或文档检查配置' }), findings: [] });
        }
        if (cancellationSignal && categories.java) {
            appendNotRun(report, 'java-format', cancellationReason(), true);
        } else if (categories.java) {
            await runIndependentStage(report, 'java-format', async () => {
                const logPath = path.join(options.reportDirectory, 'logs', 'java-format.log');
                const result = await runProcess({ command: path.join(options.root, 'mvnw'), args: ['-Pci', 'spotless:check'], cwd: options.root, logPath });
                appendResult(report, { check: processCheck('java-format', ['./mvnw', '-Pci', 'spotless:check'], result, logPath), findings: [] });
                return result;
            });
        } else {
            appendResult(report, { check: createCheck({ id: 'java-format', status: 'not_applicable', required: false, exitCode: null, reason: '本次变更不涉及 Java 或格式检查配置' }), findings: [] });
        }
    } else {
        for (let index = 0; index < FULL_PLAN.length; index += 1) {
            if (cancellationSignal) {
                appendCancelledPlan(report, index);
                break;
            }
            const step = FULL_PLAN[index];
            await runIndependentStage(report, step.id, () => runFullStep(report, options, step));
            if (cancellationSignal) {
                appendCancelledPlan(report, index + 1);
                break;
            }
        }
    }
    if (cancellationSignal) appendCancellation(report);
    finalizeReport(report);
    report.finishedAt = new Date().toISOString();
    report.durationMs = Date.now() - Date.parse(report.startedAt);
    await writeReport(options.report, report);
    return report;
}

export async function runQuality(options) {
    cancellationSignal = null;
    const removeCancellationHandlers = installCancellationHandler();
    try {
        return await runQualityInternal(options);
    } finally {
        removeCancellationHandlers();
    }
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    if (options.list) {
        process.stdout.write(`${listPlan(options).join('\n')}\n`);
        return;
    }
    const report = await runQuality(options);
    process.exitCode = report.exitCode;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
    main().catch((error) => {
        process.stderr.write(`质量调度失败：${error.message}\n`);
        process.exitCode = 2;
    });
}
