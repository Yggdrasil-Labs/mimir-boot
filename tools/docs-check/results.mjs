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
