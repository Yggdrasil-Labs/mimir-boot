import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { DOMParser } from '@xmldom/xmldom';
import { EngineeringError, errorExitCode, isToolEnvironmentError, toolEnvironmentError } from './runtime.mjs';

const NAMESPACE = 'http://maven.apache.org/POM/4.0.0';
const TRANSIENT_CURL_CODES = new Set([5, 6, 7, 16, 18, 28, 35, 52, 55, 56, 92]);

class PendingArtifactError extends Error {}

export function validatePom(source, expectedArtifact, expectedVersion) {
    const document = new DOMParser({ onError: (_, message) => { throw new Error(message); } })
        .parseFromString(source, 'application/xml');
    const root = document.documentElement;
    if (document.doctype || root.namespaceURI !== NAMESPACE || root.localName !== 'project') throw new Error('无效的 Maven POM');
    const value = (element, name) => Array.from(element?.childNodes || [])
        .find((node) => node.nodeType === 1 && node.namespaceURI === NAMESPACE && node.localName === name);
    const parent = value(root, 'parent');
    const coordinate = (name) => (value(root, name)?.textContent || value(parent, name)?.textContent || '').trim();
    const group = coordinate('groupId');
    const artifact = value(root, 'artifactId')?.textContent.trim();
    const version = coordinate('version');
    if (group !== 'io.github.yggdrasil-labs' || artifact !== expectedArtifact || version !== expectedVersion) {
        throw new Error(`POM 坐标不匹配：期望 io.github.yggdrasil-labs:${expectedArtifact}:${expectedVersion}，实际 ${group}:${artifact}:${version}`);
    }
}

function execute(command, args, timeoutMs) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, {
            stdio: ['ignore', command === 'curl' ? 'pipe' : 'ignore', 'pipe'],
            detached: process.platform !== 'win32',
        });
        let stdout = '';
        let stderr = '';
        let timedOut = false;
        let cancelledSignal = null;
        const signalHandlers = new Map();
        const cleanup = () => {
            clearTimeout(timer);
            for (const [signal, handler] of signalHandlers) process.removeListener(signal, handler);
        };
        const terminate = () => {
            try {
                // 临时下载与目录检查无需保留运行状态；取消或超时均立即终止整个进程组。
                if (process.platform === 'win32') child.kill('SIGKILL');
                else if (child.pid) process.kill(-child.pid, 'SIGKILL');
            } catch (error) {
                if (error.code !== 'ESRCH') {
                    cleanup();
                    reject(toolEnvironmentError(`终止 ${command}`, error));
                }
            }
        };
        const timer = setTimeout(() => {
            timedOut = true;
            terminate();
        }, Math.max(1, Math.ceil(timeoutMs)));
        for (const signal of ['SIGINT', 'SIGTERM']) {
            const handler = () => {
                cancelledSignal ||= signal;
                terminate();
            };
            signalHandlers.set(signal, handler);
            process.on(signal, handler);
        }
        child.stdout?.on('data', (chunk) => { stdout += chunk.toString(); });
        child.stderr.on('data', (chunk) => { stderr = (stderr + chunk.toString()).slice(-4096); });
        child.on('error', (error) => {
            cleanup();
            reject(toolEnvironmentError(command, error));
        });
        child.on('close', (code, signal) => {
            cleanup();
            if (cancelledSignal) reject(new EngineeringError(`公开制品验证已被 ${cancelledSignal} 取消`, 2));
            else resolve({ code, signal, timedOut, stdout, stderr: stderr.trim() });
        });
    });
}

export async function verifyPublic(version, env = process.env) {
    if (!/^\d+\.\d+\.\d+$/u.test(version || '')) throw new TypeError('发布版本号必须是 x.y.z 格式');
    // 次数仅作为显式兼容上限；正常发布由总时限约束，避免请求时间挤占同步等待窗口。
    const attemptsText = env.MIMIR_PUBLIC_VERIFY_ATTEMPTS || '';
    const intervalText = env.MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS || '30';
    const timeoutText = env.MIMIR_PUBLIC_VERIFY_TIMEOUT_SECONDS || '1200';
    if (attemptsText && (!/^[1-9]\d*$/u.test(attemptsText) || !Number.isSafeInteger(Number(attemptsText)))) throw new TypeError('MIMIR_PUBLIC_VERIFY_ATTEMPTS 必须是正整数');
    if (!/^\d+$/u.test(intervalText) || !Number.isSafeInteger(Number(intervalText) * 1000)) throw new TypeError('MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS 必须是非负整数');
    if (!/^[1-9]\d*$/u.test(timeoutText) || !Number.isSafeInteger(Number(timeoutText) * 1000) || Number(timeoutText) * 1000 > 2147483647) throw new TypeError('MIMIR_PUBLIC_VERIFY_TIMEOUT_SECONDS 必须是正整数且不超过 2147483 秒');
    const base = new URL((env.MIMIR_MAVEN_CENTRAL_BASE_URL || 'https://repo.maven.apache.org/maven2').replace(/\/+$/u, '') + '/');
    if (!['https:', 'http:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new TypeError('Maven Central 地址必须是无凭据、无查询参数的 HTTP(S) 地址');
    const started = performance.now();
    const deadline = started + Number(timeoutText) * 1000;
    const elapsed = () => `${((performance.now() - started) / 1000).toFixed(1)}s`;
    let lastDiagnostic = '尚未请求公开制品';
    let attempt = 0;
    const timeoutError = () => new Error(`Maven Central 公开制品验证超时：${version}，上限 ${timeoutText}s，已用 ${elapsed()}，尝试 ${attempt} 次；${lastDiagnostic}`);
    const remaining = () => {
        const milliseconds = deadline - performance.now();
        if (milliseconds <= 0) throw timeoutError();
        return milliseconds;
    };
    const directory = await mkdtemp(path.join(os.tmpdir(), 'mimir-public-central-'));
    try {
        while (true) {
            remaining();
            attempt++;
            process.stdout.write(`公开制品验证：${version}，第 ${attempt}${attemptsText ? `/${attemptsText}` : ''} 次尝试，已用 ${elapsed()}，总时限 ${timeoutText}s。\n`);
            try {
                for (const [artifact, extension] of [['mimir-boot', 'pom'], ['mimir-boot-starter-web', 'pom'], ['mimir-boot-starter-web', 'jar']]) {
                    const destination = path.join(directory, `${artifact}.${extension}`);
                    await rm(destination, { force: true });
                    const url = new URL(`io/github/yggdrasil-labs/${artifact}/${version}/${artifact}-${version}.${extension}`, base).href;
                    lastDiagnostic = url;
                    // 保留 curl 的代理、TLS 与超时行为；所有解析和重试逻辑统一在 Node 中。
                    const requestBudget = remaining();
                    const seconds = Math.max(0.001, Math.min(15, requestBudget / 1000)).toFixed(3);
                    const result = await execute('curl', ['--fail', '--location', '--silent', '--show-error', '--connect-timeout', String(Math.min(5, Number(seconds))), '--max-time', seconds, '--write-out', '%{http_code}', '--output', destination, url], requestBudget);
                    const status = Number(result.stdout.trim());
                    const diagnostic = `${url}：HTTP ${status || '000'}，curl exit=${result.code}${result.signal ? `，signal=${result.signal}` : ''}`;
                    lastDiagnostic = diagnostic;
                    process.stdout.write(`${diagnostic}，已用 ${elapsed()}\n`);
                    if (result.timedOut) throw timeoutError();
                    remaining();
                    if (result.code !== 0 || status !== 200) {
                        const retryable = result.code === 22 || result.code === 0
                            ? status === 404 || status === 408 || status === 429 || (status >= 500 && status <= 599)
                            : TRANSIENT_CURL_CODES.has(result.code);
                        const ErrorType = retryable ? PendingArtifactError : Error;
                        throw new ErrorType(`${diagnostic}${result.stderr ? `；${result.stderr}` : ''}`);
                    }
                    if (extension === 'pom') {
                        const source = await readFile(destination, 'utf8');
                        try { validatePom(source, artifact, version); }
                        catch (error) { throw new Error(`POM 校验失败：${url}；${error.message}`, { cause: error }); }
                    }
                    else {
                        const result = await execute('jar', ['tf', destination], remaining());
                        if (result.timedOut) throw timeoutError();
                        if (result.code !== 0) throw new Error(`JAR 校验失败：${url}（exit=${result.code}, signal=${result.signal}）；${result.stderr}`);
                    }
                    remaining();
                }
                process.stdout.write(`Maven Central 公开制品验证通过：${version}，尝试 ${attempt} 次，已用 ${elapsed()}\n`);
                return;
            } catch (error) {
                if (error instanceof EngineeringError && error.exitCode >= 2) throw error;
                if (isToolEnvironmentError(error)) throw new EngineeringError(`公开制品验证工具异常：${error.message}`, 2);
                if (!(error instanceof PendingArtifactError)) throw error;
                process.stderr.write(`公开制品尚不可用：${error.message}\n`);
                remaining();
                if (attemptsText && attempt >= Number(attemptsText)) throw new Error(`Maven Central 公开制品在 ${attemptsText} 次尝试后仍不可用：${version}，已用 ${elapsed()}；${lastDiagnostic}`, { cause: error });
                await delay(Math.min(Number(intervalText) * 1000, remaining()));
            }
        }
    } finally {
        await rm(directory, { recursive: true, force: true });
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        if (process.argv.length !== 3) throw new TypeError('用法：bash scripts/engineering.sh public <发布版本号>');
        await verifyPublic(process.argv[2]);
    } catch (error) {
        process.stderr.write(`${error.message}\n`);
        process.exitCode = errorExitCode(error, error instanceof TypeError ? 2 : 1);
    }
}
