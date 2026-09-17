import { createWriteStream } from 'node:fs';
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
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
import {
    cleanExpected,
    generateExpected,
    recordArtifacts,
    verifyReports,
} from './verify-java-reports.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
let cancellationSignal = null;

function usage() {
    return [
        '用法：node java.mjs [--root <绝对路径>] [--report <绝对路径>] [--report-directory <绝对路径>]',
        '      [--run-id <id>] [--source <worktree|index|commit>] [--commit <sha|null>] [--tree <id>]',
    ].join('\n');
}

function absolute(value, label) {
    if (!value || !path.isAbsolute(value)) throw new Error(`${label} 必须是绝对路径`);
    return path.resolve(value);
}

export function parseArgs(argv) {
    const options = {
        root: ROOT,
        report: null,
        reportDirectory: process.env.QUALITY_REPORT_DIR || null,
        runId: null,
        source: 'worktree',
        commit: null,
        tree: 'java-worktree',
    };
    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        if (!['--root', '--report', '--report-directory', '--run-id', '--source', '--commit', '--tree'].includes(argument)) {
            throw new Error(`未知参数：${argument}\n${usage()}`);
        }
        const value = argv[index + 1];
        if (!value || value.startsWith('--')) throw new Error(`参数 ${argument} 缺少值\n${usage()}`);
        index += 1;
        if (argument === '--root') options.root = value;
        if (argument === '--report') options.report = value;
        if (argument === '--report-directory') options.reportDirectory = value;
        if (argument === '--run-id') options.runId = value;
        if (argument === '--source') options.source = value;
        if (argument === '--commit') options.commit = value === 'null' ? null : value;
        if (argument === '--tree') options.tree = value;
    }
    options.root = absolute(options.root, '--root');
    if (options.reportDirectory) options.reportDirectory = absolute(options.reportDirectory, '--report-directory');
    if (options.report) options.report = absolute(options.report, '--report');
    if (!['worktree', 'index', 'commit'].includes(options.source)) throw new Error(`不支持的 source：${options.source}`);
    if (options.source === 'commit' && !options.commit) throw new Error('commit 模式必须提供 --commit');
    if (options.source !== 'commit' && options.commit) throw new Error('非 commit 模式不能提供 --commit');
    return options;
}

function commandResult(exitCode, signal = null, error = null) {
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
                // 进程组不可用时退回到单进程终止。
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

export async function runCommand({ command, args, cwd, logPath }) {
    const startedAt = performance.now();
    let log;
    try {
        await mkdir(path.dirname(logPath), { recursive: true });
        log = createWriteStream(logPath, { flags: 'w' });
    } catch (error) {
        return { ...commandResult(2, null, error), durationMs: performance.now() - startedAt };
    }
    return new Promise((resolve) => {
        let settled = false;
        let resolved = false;
        let logError = null;
        let child;
        const signalHandlers = new Map();
        const complete = (result) => {
            if (resolved) return;
            resolved = true;
            resolve({ ...result, durationMs: performance.now() - startedAt });
        };
        const finish = (result) => {
            if (settled) return;
            settled = true;
            for (const [signal, handler] of signalHandlers) process.removeListener(signal, handler);
            const completeAfterFlush = () => complete(logError ? commandResult(2, null, logError) : result);
            if (log.destroyed) {
                completeAfterFlush();
                return;
            }
            log.end(completeAfterFlush);
        };
        const onLogError = (error) => {
            logError ||= error;
            terminateProcessGroup(child, 'SIGTERM');
        };
        log.on('error', onLogError);
        try {
            child = spawn(command, args, {
                cwd,
                stdio: ['ignore', 'pipe', 'pipe'],
                detached: process.platform !== 'win32',
            });
        } catch (error) {
            finish(commandResult(2, null, error));
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
        }
        child.stdout.on('error', onLogError);
        child.stderr.on('error', onLogError);
        child.on('error', (error) => finish(commandResult(2, null, error)));
        child.on('close', (exitCode, signal) => finish(commandResult(exitCode, signal)));
    });
}

function checkFromProcess(id, result, logPath, reasonPrefix = '检查返回非零退出码') {
    const status = result.exitCode === 0 ? 'passed' : result.exitCode === 1 ? 'failed' : 'error';
    return createCheck({
        id,
        command: [],
        logPath,
        status,
        required: true,
        exitCode: result.exitCode,
        reason: status === 'passed' ? null : `${reasonPrefix}${result.error ? `：${result.error.message}` : ''}`,
    });
}

function stageCheck(id, status, reason = null, required = true) {
    const exitCode = status === 'passed' ? 0 : status === 'failed' ? 1 : status === 'error' ? 2 : null;
    return createCheck({ id, status, required, exitCode, reason, durationMs: 0 });
}

async function writeJson(file, value) {
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function appendVerificationFindings(report, findings = []) {
    for (const finding of findings) {
        addFinding(report, {
            severity: 'error',
            rule: finding.rule || 'java-quality',
            path: finding.path || null,
            line: finding.line || null,
            message: finding.module ? `[${finding.module}] ${finding.message}` : finding.message,
        });
    }
}

function normalizeBuildFailure(status, findings, prefix) {
    const scopedFindings = findings.filter((finding) => finding.rule?.startsWith(prefix));
    if (scopedFindings.some((finding) => !finding.rule.endsWith('-missing'))) return 'failed';
    if (status === 'passed' || (status === 'failed' && scopedFindings.length > 0)) return 'not_run';
    return status;
}

function cancellationReason() {
    return `收到 ${cancellationSignal || 'SIGTERM'}，Java 质量阶段已取消`;
}

function appendJavaCancellation(report) {
    if (report.checks.some((check) => check.id === 'java-cancelled')) return;
    const reason = cancellationReason();
    appendResult(report, {
        check: createCheck({ id: 'java-cancelled', status: 'error', required: true, exitCode: 2, reason }),
        findings: [{ severity: 'error', rule: 'java-cancelled', path: null, line: null, message: reason }],
    });
}

async function finishCancelledJava(report, options, runStarted) {
    const reason = cancellationReason();
    if (!report.checks.some((check) => check.id === 'java-tests')) appendResult(report, { check: stageCheck('java-tests', 'not_run', reason), findings: [] });
    if (!report.checks.some((check) => check.id === 'java-coverage')) appendResult(report, { check: stageCheck('java-coverage', 'not_run', reason), findings: [] });
    if (!report.checks.some((check) => check.id === 'java-sonar')) {
        const sonarMode = process.env.RUN_SONAR || 'false';
        const sonarRequired = sonarMode !== 'false';
        appendResult(report, { check: stageCheck('java-sonar', sonarRequired ? 'not_run' : 'not_applicable', sonarRequired ? reason : 'RUN_SONAR=false', sonarRequired), findings: [] });
    }
    appendJavaCancellation(report);
    finalizeReport(report);
    report.finishedAt = new Date().toISOString();
    report.durationMs = Date.now() - runStarted;
    await writeReport(options.report, report);
    return report;
}

async function runJavaQualityInternal(options) {
    if (!options.reportDirectory) {
        options.reportDirectory = await mkdtemp(path.join(tmpdir(), 'mimir-java-quality-'));
    }
    if (!options.report) options.report = path.join(options.reportDirectory, 'java-quality-report.json');
    const runId = options.runId || `java-${Date.now()}-${process.pid}`;
    const report = await createRunReport({
        root: options.root,
        reportDirectory: options.reportDirectory,
        runId,
        source: options.source,
        commit: options.commit,
        tree: options.tree,
    });
    report.toolVersions.java = null;
    report.toolVersions.maven = null;
    const runStarted = Date.now();
    if (cancellationSignal) {
        appendResult(report, {
            check: createCheck({ id: 'java-version', status: 'error', exitCode: 2, reason: cancellationReason() }),
            findings: [],
        });
        return finishCancelledJava(report, options, runStarted);
    }
    const javaLog = path.join(options.reportDirectory, 'logs', 'java-version.log');
    const javaVersion = await runCommand({ command: 'java', args: ['-version'], cwd: options.root, logPath: javaLog });
    const javaVersionSource = await readFile(javaLog, 'utf8').catch(() => '');
    report.toolVersions.java = javaVersionSource.split(/\r?\n/u).find((line) => line.trim()) || null;
    if (cancellationSignal) {
        appendResult(report, {
            check: createCheck({
                id: 'java-version',
                command: ['java', '-version'],
                logPath: javaLog,
                status: 'error',
                exitCode: 2,
                reason: cancellationReason(),
                durationMs: javaVersion.durationMs,
            }),
            findings: [],
        });
        return finishCancelledJava(report, options, runStarted);
    }
    if (javaVersion.exitCode !== 0 || !/version "17(?:[.\-+"])/u.test(javaVersionSource)) {
        appendResult(report, {
            check: createCheck({
                id: 'java-version',
                command: ['java', '-version'],
                logPath: javaLog,
                status: 'error',
                exitCode: 2,
                reason: '需要 Java 17',
                durationMs: javaVersion.durationMs,
            }),
            findings: [{ severity: 'error', rule: 'java-version', path: null, line: null, message: '需要 Java 17' }],
        });
        appendResult(report, { check: stageCheck('java-tests', 'not_run', 'Java 版本不满足要求'), findings: [] });
        appendResult(report, { check: stageCheck('java-coverage', 'not_run', 'Java 版本不满足要求'), findings: [] });
        const sonarMode = process.env.RUN_SONAR || 'false';
        const sonarRequired = sonarMode !== 'false';
        appendResult(report, { check: stageCheck('java-sonar', sonarRequired ? 'not_run' : 'not_applicable', sonarRequired ? 'Java 版本不满足要求' : 'RUN_SONAR=false', sonarRequired), findings: [] });
        finalizeReport(report);
        report.finishedAt = new Date().toISOString();
        await writeReport(options.report, report);
        return report;
    }

    appendResult(report, {
        check: createCheck({
            id: 'java-version',
            command: ['java', '-version'],
            logPath: javaLog,
            status: 'passed',
            exitCode: 0,
            durationMs: javaVersion.durationMs,
        }),
        findings: [],
    });

    const mavenLog = path.join(options.reportDirectory, 'logs', 'maven-version.log');
    const mavenVersion = await runCommand({ command: path.join(options.root, 'mvnw'), args: ['--version'], cwd: options.root, logPath: mavenLog });
    const mavenVersionSource = await readFile(mavenLog, 'utf8').catch(() => '');
    const mavenVersionLine = mavenVersionSource.split(/\r?\n/u).map((line) => line.trim()).find((line) => /^Apache Maven \S+/u.test(line)) || null;
    const mavenVersionValid = !cancellationSignal && mavenVersion.exitCode === 0 && Boolean(mavenVersionLine);
    if (mavenVersionValid) report.toolVersions.maven = mavenVersionLine;
    let mavenVersionReason;
    if (cancellationSignal) {
        mavenVersionReason = cancellationReason();
    } else if (mavenVersion.exitCode === 0) {
        mavenVersionReason = 'Maven 版本探测失败：mvnw --version 未输出有效版本';
    } else {
        mavenVersionReason = `Maven 版本探测失败：mvnw --version 退出码 ${mavenVersion.exitCode}${mavenVersion.error ? `：${mavenVersion.error.message}` : ''}`;
    }
    appendResult(report, {
        check: createCheck({
            id: 'maven-version',
            command: ['./mvnw', '--version'],
            logPath: mavenLog,
            status: mavenVersionValid ? 'passed' : 'error',
            exitCode: mavenVersionValid ? 0 : 2,
            reason: mavenVersionValid ? null : mavenVersionReason,
            durationMs: mavenVersion.durationMs,
        }),
        findings: mavenVersionValid ? [] : [{ severity: 'error', rule: 'maven-version', path: null, line: null, message: mavenVersionReason }],
    });
    if (cancellationSignal) return finishCancelledJava(report, options, runStarted);

    const sonarMode = process.env.RUN_SONAR || 'false';
    let sonarConfigError = null;
    if (!['true', 'false'].includes(sonarMode)) {
        sonarConfigError = 'RUN_SONAR 只允许 true 或 false';
    } else if (sonarMode === 'true' && (!process.env.SONAR_TOKEN || !process.env.SONAR_ORGANIZATION || !process.env.SONAR_PROJECT_KEY)) {
        sonarConfigError = 'RUN_SONAR=true 时必须设置 SONAR_TOKEN、SONAR_ORGANIZATION 和 SONAR_PROJECT_KEY';
    }
    if (sonarConfigError) {
        appendResult(report, {
            check: createCheck({ id: 'java-sonar-config', status: 'error', exitCode: 2, reason: sonarConfigError }),
            findings: [{ severity: 'error', rule: 'sonar-config', path: null, line: null, message: sonarConfigError }],
        });
    }
    // Sonar 是独立的可选阶段；配置错误不能吞掉后续 Maven 测试和覆盖率核验。
    const sonarConfigurationFailed = Boolean(sonarConfigError);
    if (cancellationSignal) return finishCancelledJava(report, options, runStarted);

    const expectedPath = path.join(options.reportDirectory, 'build-manifest.json');
    let expectedReady = false;
    try {
        await writeJson(expectedPath, await generateExpected(options.root, runId));
        await cleanExpected(options.root, expectedPath);
        expectedReady = true;
        appendResult(report, { check: stageCheck('java-reports-clean', 'passed'), findings: [] });
    } catch (error) {
        appendResult(report, {
            check: createCheck({ id: 'java-reports-clean', status: 'error', exitCode: 2, reason: `清理 Java 旧产物失败：${error.message}` }),
            findings: [{ severity: 'error', rule: 'java-reports-clean', path: null, line: null, message: error.message }],
        });
    }
    if (cancellationSignal) return finishCancelledJava(report, options, runStarted);

    let buildResult = commandResult(2);
    if (expectedReady) {
        const buildLog = path.join(options.reportDirectory, 'logs', 'java-build.log');
        buildResult = await runCommand({ command: path.join(options.root, 'mvnw'), args: ['-B', '-Pci', 'clean', 'verify'], cwd: options.root, logPath: buildLog });
        appendResult(report, {
            check: createCheck({
                id: 'java-build',
                command: ['./mvnw', '-B', '-Pci', 'clean', 'verify'],
                logPath: buildLog,
                status: buildResult.exitCode === 0 ? 'passed' : buildResult.exitCode === 1 ? 'failed' : 'error',
                exitCode: buildResult.exitCode,
                reason: buildResult.exitCode === 0 ? null : `Maven 构建退出码 ${buildResult.exitCode}`,
                durationMs: buildResult.durationMs || 0,
            }),
            findings: [],
        });
    }
    if (cancellationSignal) return finishCancelledJava(report, options, runStarted);

    let verification = null;
    if (expectedReady) {
        try {
            await recordArtifacts(options.root, expectedPath);
            appendResult(report, { check: stageCheck('java-reports-record', 'passed'), findings: [] });
        } catch (error) {
            appendResult(report, {
                check: createCheck({ id: 'java-reports-record', status: 'error', exitCode: 2, reason: `记录 Java 产物失败：${error.message}` }),
                findings: [{ severity: 'error', rule: 'java-reports-record', path: null, line: null, message: error.message }],
            });
        }
        try {
            verification = await verifyReports(options.root, expectedPath);
            appendVerificationFindings(report, verification.findings);
            appendResult(report, {
                check: stageCheck('java-reports-verify', verification.exitCode === 0 ? 'passed' : 'failed', verification.exitCode === 0 ? null : 'Java 测试或覆盖率报告核验失败'),
                findings: [],
            });
        } catch (error) {
            appendResult(report, {
                check: createCheck({ id: 'java-reports-verify', status: 'error', exitCode: 2, reason: `核验 Java 报告失败：${error.message}` }),
                findings: [{ severity: 'error', rule: 'java-reports-verify', path: null, line: null, message: error.message }],
            });
        }
    }
    if (cancellationSignal) return finishCancelledJava(report, options, runStarted);

    const verificationFindings = verification?.findings || [];
    let testsStatus = verification?.testsStatus || 'not_run';
    let coverageStatus = verification?.coverageStatus || 'not_run';
    if (buildResult.exitCode !== 0) {
        testsStatus = normalizeBuildFailure(testsStatus, verificationFindings, 'test-report-');
        coverageStatus = normalizeBuildFailure(coverageStatus, verificationFindings, 'coverage-');
    }
    appendResult(report, { check: stageCheck('java-tests', testsStatus, testsStatus === 'passed' ? null : '测试未完成或报告核验失败'), findings: [] });
    appendResult(report, { check: stageCheck('java-coverage', coverageStatus, coverageStatus === 'passed' ? null : '覆盖率未完成或报告核验失败'), findings: [] });

    if (sonarConfigurationFailed) {
        appendResult(report, { check: stageCheck('java-sonar', 'error', 'Sonar 配置无效', true), findings: [] });
    } else if (sonarMode === 'false') {
        appendResult(report, { check: stageCheck('java-sonar', 'not_applicable', 'RUN_SONAR=false', false), findings: [] });
    } else if (buildResult.exitCode === 0 && verification?.exitCode === 0) {
        const sonarLog = path.join(options.reportDirectory, 'logs', 'java-sonar.log');
        const sonarResult = await runCommand({
            command: path.join(options.root, 'mvnw'),
            args: [
                '-B', '-Pci', 'sonar:sonar',
                '-Dsonar.host.url=https://sonarcloud.io',
                `-Dsonar.organization=${process.env.SONAR_ORGANIZATION}`,
                `-Dsonar.projectKey=${process.env.SONAR_PROJECT_KEY}`,
                '-Dsonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml',
                '-Dsonar.qualitygate.wait=true',
                '-Dsonar.qualitygate.timeout=300',
            ],
            cwd: options.root,
            logPath: sonarLog,
        });
        appendResult(report, {
            check: createCheck({
                id: 'java-sonar',
                command: ['./mvnw', '-B', '-Pci', 'sonar:sonar'],
                logPath: sonarLog,
                status: sonarResult.exitCode === 0 ? 'passed' : sonarResult.exitCode === 1 ? 'failed' : 'error',
                exitCode: sonarResult.exitCode,
                reason: sonarResult.exitCode === 0 ? null : `Sonar 执行退出码 ${sonarResult.exitCode}`,
                durationMs: sonarResult.durationMs || 0,
            }),
            findings: [],
        });
    } else {
        appendResult(report, { check: stageCheck('java-sonar', 'not_run', 'Java 构建或报告核验未通过', true), findings: [] });
    }

    if (cancellationSignal) appendJavaCancellation(report);
    finalizeReport(report);
    report.finishedAt = new Date().toISOString();
    report.durationMs = Date.now() - runStarted;
    await writeReport(options.report, report);
    return report;
}

export async function runJavaQuality(options) {
    cancellationSignal = null;
    const removeCancellationHandlers = installCancellationHandler();
    try {
        return await runJavaQualityInternal(options);
    } finally {
        removeCancellationHandlers();
    }
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    const report = await runJavaQuality(options);
    process.exitCode = report.exitCode;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
    main().catch(async (error) => {
        process.stderr.write(`Java 质量检查失败：${error.message}\n`);
        process.exitCode = 2;
    });
}
