export const CHECK_STATUSES = new Set([
    'passed',
    'failed',
    'error',
    'not_run',
    'not_applicable',
]);

export function createCheck({
    id,
    command = [],
    dependsOn = [],
    logPath = null,
    status,
    required = true,
    exitCode = null,
    reason = null,
    durationMs = 0,
}) {
    if (!CHECK_STATUSES.has(status)) {
        throw new Error(`不支持的检查状态：${status}`);
    }
    return {
        id,
        command,
        dependsOn,
        logPath,
        status,
        required,
        exitCode,
        reason: status === 'passed' || status === 'not_applicable' ? null : reason || '检查未通过',
        durationMs,
    };
}

export function addFinding(report, {
    severity = 'error',
    rule,
    path: findingPath = null,
    line = null,
    message,
}) {
    if (!['error', 'warning', 'info'].includes(severity)) {
        throw new Error(`不支持的 finding severity：${severity}`);
    }
    report.findings.push({ severity, rule, path: findingPath, line, message });
}

export function finalizeReport(report, { emptyFull = false } = {}) {
    const requiredChecks = report.checks.filter((check) => check.required);
    const hasError = requiredChecks.some((check) => check.status === 'error');
    const hasFailure = requiredChecks.some((check) => ['failed', 'not_run'].includes(check.status));
    if (hasError) {
        report.overall = 'error';
        report.exitCode = 2;
    } else if (hasFailure || emptyFull) {
        report.overall = 'failed';
        report.exitCode = 1;
    } else {
        report.overall = 'passed';
        report.exitCode = 0;
    }
    return report;
}

import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';

const SCHEMA_VERSION = 1;
const STATUSES = CHECK_STATUSES;

function usage() {
    return [
        '用法：node results.mjs init --report <绝对路径> --report-directory <绝对路径> --root <绝对路径> --run-id <id> --source <worktree|index|commit> --commit <sha|null> --tree <id>',
        '      node results.mjs append --report <绝对路径> --id <id> --status <状态> --required <true|false> --command <JSON 数组> --depends-on <JSON 数组> --log <绝对路径|null> --exit-code <整数|null> --duration-ms <整数> --reason <文本|null>',
        '      node results.mjs finalize --report <绝对路径>',
    ].join('\n');
}

function parseArgs(argv) {
    const [operation, ...rest] = argv;
    if (!['init', 'append', 'finalize'].includes(operation)) throw new Error(usage());
    const options = { operation };
    for (let index = 0; index < rest.length; index += 1) {
        const key = rest[index];
        const value = rest[index + 1];
        if (!key.startsWith('--') || value === undefined) throw new Error(usage());
        options[key.slice(2)] = value;
        index += 1;
    }
    return options;
}

function requireAbsolute(value, label) {
    if (!value || !path.isAbsolute(value)) throw new Error(`${label} 必须是绝对路径`);
    return path.resolve(value);
}

function parseNullable(value, label) {
    if (value === undefined) throw new Error(`${label} 不能为空`);
    return value === 'null' ? null : value;
}

function parseJson(value, label) {
    try {
        return JSON.parse(value);
    } catch {
        throw new Error(`${label} 必须是 JSON`);
    }
}

function within(directory, candidate, label) {
    const relative = path.relative(directory, candidate);
    if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error(`${label} 必须位于报告目录内`);
}

async function contentHash(root) {
    const files = [
        '.markdownlint-cli2.jsonc',
        '.markdownlint.json',
        'scripts/docs-tool.sh',
        'scripts/ci-preflight.sh',
        'tools/docs-check/verify-reports.mjs',
        ...['check', 'links', 'navigation', 'debt', 'policy', 'results'].map((name) => `tools/docs-check/${name}.mjs`),
        'tools/docs-check/debt-id-registry.json',
        'pom.xml',
        'mimir-boot-parent/pom.xml',
        'scripts/quality-check.sh',
        'tools/docs-check/package.json',
        'tools/docs-check/package-lock.json',
        'tools/docs-check/policy.json',
        'tools/docs-check/results.mjs',
    ];
    const hash = createHash('sha256');
    for (const file of files) {
        hash.update(file);
        try {
            hash.update(await readFile(path.join(root, file)));
        } catch (error) {
            if (error.code !== 'ENOENT') throw error;
            hash.update('<missing>');
        }
    }
    return hash.digest('hex');
}

async function readReport(reportPath) {
    try {
        const report = JSON.parse(await readFile(reportPath, 'utf8'));
        if (report.schemaVersion !== SCHEMA_VERSION || !Array.isArray(report.checks)) throw new Error('schema 无效');
        return report;
    } catch (error) {
        throw new Error(`无法读取质量报告：${error.message}`);
    }
}

async function writeReport(reportPath, report) {
    await mkdir(path.dirname(reportPath), { recursive: true });
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

function commandVersion(command, args, root) {
    const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 15000 });
    return result.status === 0 ? (result.stdout || result.stderr).split('\n')[0].replace(/\x1b\[[0-9;]*m/gu, '') : 'unavailable';
}

async function init(options) {
    const reportPath = requireAbsolute(options.report, '--report');
    const root = requireAbsolute(options.root, '--root');
    const source = options.source;
    const commit = parseNullable(options.commit, '--commit');
    if (!options['run-id'] || !['worktree', 'index', 'commit'].includes(source) || !options.tree) throw new Error(usage());
    if ((source === 'commit') !== Boolean(commit)) throw new Error('commit 模式必须且只能提供 commit');
    const reportDirectory = requireAbsolute(options['report-directory'], '--report-directory');
    const report = {
        schemaVersion: SCHEMA_VERSION,
        runId: options['run-id'],
        startedAt: new Date().toISOString(),
        finishedAt: null,
        reportDirectory,
        toolVersions: {
            node: process.version,
            java: commandVersion('java', ['-version'], root),
            maven: commandVersion(path.join(root, 'mvnw'), ['--version'], root),
        },
        source,
        commit,
        tree: options.tree,
        configurationHash: await contentHash(root),
        checks: [],
        findings: [],
        overall: 'error',
        exitCode: 2,
    };
    await writeReport(reportPath, report);
}

async function append(options) {
    const reportPath = requireAbsolute(options.report, '--report');
    const report = await readReport(reportPath);
    const status = options.status;
    const required = options.required === 'true' ? true : options.required === 'false' ? false : null;
    const command = parseJson(options.command, '--command');
    const dependsOn = parseJson(options['depends-on'], '--depends-on');
    const logPath = parseNullable(options.log, '--log');
    const exitCodeValue = parseNullable(options['exit-code'], '--exit-code');
    const durationMs = Number(options['duration-ms']);
    const reason = parseNullable(options.reason, '--reason');
    if (!options.id || !STATUSES.has(status) || required === null || !Array.isArray(command) || !Array.isArray(dependsOn) || !Number.isInteger(durationMs) || durationMs < 0) throw new Error(usage());
    if (report.checks.some((check) => check.id === options.id)) throw new Error(`检查 id 重复：${options.id}`);
    const exitCode = exitCodeValue === null ? null : Number(exitCodeValue);
    if (exitCode !== null && !Number.isInteger(exitCode)) throw new Error('--exit-code 必须是整数或 null');
    if (status === 'passed' && reason !== null) throw new Error('passed 状态不能提供 reason');
    validateCheck({ id: options.id, status, exitCode, dependsOn, command }, report.checks);
    if (!['passed', 'not_applicable'].includes(status) && !reason) throw new Error(`${status} 状态必须提供 reason`);
    if (logPath !== null) {
        const resolvedLogPath = requireAbsolute(logPath, '--log');
        within(report.reportDirectory, resolvedLogPath, '--log');
    }
    const check = { id: options.id, command, dependsOn, logPath, status, required, exitCode, reason, durationMs };
    report.checks.push(check);
    if (!['passed', 'not_applicable'].includes(status)) {
        report.findings.push({ severity: status === 'error' ? 'error' : 'warning', rule: `quality:${options.id}`, path: null, line: null, message: reason });
    }
    await writeReport(reportPath, report);
}

function validateCheck(check, previous) {
    if (![...check.command, ...check.dependsOn].every((value) => typeof value === 'string')) throw new Error('命令和依赖必须为字符串数组');
    if (check.dependsOn.some((id) => !previous.some((item) => item.id === id))) throw new Error('依赖必须引用此前已记录的检查，不能缺失或成环');
    if (check.status === 'passed' && check.exitCode !== 0 || ['not_run', 'not_applicable'].includes(check.status) && check.exitCode !== null || check.status === 'failed' && check.exitCode !== 1 || check.status === 'error' && !(check.exitCode >= 2)) throw new Error('状态与退出码矛盾');
}

function resultExitCode(report) {
    report.checks.forEach((check, index) => validateCheck(check, report.checks.slice(0, index)));
    if (report.checks.length === 0) return 2;
    if (report.checks.some((check) => check.required && check.status === 'error')) return 2;
    if (report.checks.some((check) => check.required && ['failed', 'not_run'].includes(check.status))) return 1;
    return 0;
}

async function finalize(options) {
    const reportPath = requireAbsolute(options.report, '--report');
    const report = await readReport(reportPath);
    const exitCode = resultExitCode(report);
    report.finishedAt = new Date().toISOString();
    report.exitCode = exitCode;
    report.overall = exitCode === 0 ? 'passed' : exitCode === 1 ? 'failed' : 'error';
    await writeReport(reportPath, report);
    return exitCode;
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    if (options.operation === 'init') return init(options);
    if (options.operation === 'append') return append(options);
    return finalize(options);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
    main().then((exitCode) => { process.exitCode = exitCode ?? 0; }).catch((error) => { process.stderr.write(`质量结果处理失败：${error.message}\n`); process.exitCode = 2; });
}
