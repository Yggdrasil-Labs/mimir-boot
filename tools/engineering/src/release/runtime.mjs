import { createWriteStream } from 'node:fs';
import {
    access,
    cp,
    mkdir,
    mkdtemp,
    readdir,
    readFile,
    rm,
    stat,
    writeFile,
} from 'node:fs/promises';
import { spawn } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';

const releaseDirectory = path.dirname(fileURLToPath(import.meta.url));
export const projectRoot = path.resolve(releaseDirectory, '../../../..');

export const runtime = {
    access,
    cp,
    mkdir,
    mkdtemp,
    readdir,
    readFile,
    rm,
    stat,
    writeFile,
};

export class EngineeringError extends Error {
    constructor(message, exitCode = 1) {
        super(message);
        this.name = 'EngineeringError';
        this.exitCode = exitCode;
    }
}

const TOOL_ENVIRONMENT_ERROR_CODES = new Set([
    'EACCES',
    'EADDRINUSE',
    'EADDRNOTAVAIL',
    'EAFNOSUPPORT',
    'EAGAIN',
    'EBUSY',
    'EEXIST',
    'EIO',
    'EISDIR',
    'ELOOP',
    'EMFILE',
    'EMLINK',
    'ENFILE',
    'ENODEV',
    'ENOENT',
    'ENAMETOOLONG',
    'ENOMEM',
    'ENOTDIR',
    'ENOSPC',
    'ENOTEMPTY',
    'ENOTSUP',
    'EOPNOTSUPP',
    'EOVERFLOW',
    'EPERM',
    'EROFS',
    'ETXTBSY',
    'EXDEV',
]);

export function isToolEnvironmentError(error) {
    if (!error || typeof error !== 'object' || !TOOL_ENVIRONMENT_ERROR_CODES.has(error.code)) return false;
    return typeof error.syscall === 'string'
        || Number.isInteger(error.errno)
        || typeof error.path === 'string'
        || typeof error.address === 'string';
}

export function toolEnvironmentError(label, error) {
    if (error instanceof EngineeringError) return error;
    return new EngineeringError(`无法启动 ${label}：${error.message}`, 2);
}

export function errorExitCode(error, fallback = 1) {
    if (Number.isInteger(error?.exitCode)) return error.exitCode;
    return isToolEnvironmentError(error) ? 2 : fallback;
}

export function fail(message, exitCode = 1) {
    throw new EngineeringError(message, exitCode);
}

export async function makeTempDirectory(prefix) {
    return mkdtemp(path.join(os.tmpdir(), prefix));
}

export async function removeDirectory(directory) {
    await rm(directory, { recursive: true, force: true });
}

export function createProxySettings() {
    const proxyUrl = process.env.MIMIR_MAVEN_PROXY || process.env.HTTPS_PROXY || '';
    if (!proxyUrl) return { proxyUrl: '', proxyXml: '' };

    const match = proxyUrl.match(/^http:\/\/([A-Za-z0-9.-]+):([0-9]{1,5})\/?$/u);
    if (!match) {
        fail('MIMIR_MAVEN_PROXY/HTTPS_PROXY 必须是无凭据的 http://host:port 代理地址。', 2);
    }
    const port = Number(match[2]);
    if (port < 1 || port > 65535) {
        fail('MIMIR_MAVEN_PROXY/HTTPS_PROXY 的端口必须在 1-65535。', 2);
    }
    const proxyXml = `
  <proxies>
    <proxy>
      <id>isolated-http-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>${match[1]}</host>
      <port>${port}</port>
    </proxy>
  </proxies>`;
    return { proxyUrl, proxyXml };
}

export function settingsXml({ proxyXml = '', mirrorId = '', mirrorUrl = '' } = {}) {
    const mirror = mirrorId && mirrorUrl
        ? `
  <mirrors>
    <mirror>
      <id>${mirrorId}</id>
      <mirrorOf>central</mirrorOf>
      <url>${mirrorUrl}</url>
    </mirror>
  </mirrors>`
        : '';
    return `<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">${proxyXml}${mirror}
</settings>
`;
}

function writeChunk(stream, chunk) {
    if (stream) stream.write(chunk);
    return chunk;
}

const CANCELLATION_GRACE_MS = 250;
const PROCESS_GROUP_POLL_MS = 25;
const PROCESS_GROUP_WAIT_MS = 5000;

function processGroupAlive(pid) {
    if (process.platform === 'win32' || !pid) return false;
    try {
        process.kill(-pid, 0);
        return true;
    } catch (error) {
        if (error.code === 'ESRCH') return false;
        if (error.code === 'EPERM') return true;
        throw error;
    }
}

async function waitForProcessGroupExit(pid, timeoutMs = PROCESS_GROUP_WAIT_MS) {
    if (process.platform === 'win32' || !pid) return true;
    const deadline = Date.now() + timeoutMs;
    while (processGroupAlive(pid)) {
        if (Date.now() >= deadline) return false;
        await delay(PROCESS_GROUP_POLL_MS);
    }
    return true;
}

export function runCommand(command, args = [], {
    cwd = projectRoot,
    env = {},
    logPath = null,
    label = command,
} = {}) {
    return new Promise((resolve, reject) => {
        let logStream = null;
        try {
            if (logPath) logStream = createWriteStream(logPath, { flags: 'w', encoding: 'utf8' });
        } catch (error) {
            reject(error);
            return;
        }
        let child = null;
        let settled = false;
        let finalizing = false;
        let cancelledSignal = null;
        let terminationError = null;
        let escalationTimer = null;
        const signalHandlers = new Map();
        const cleanup = () => {
            for (const [signal, handler] of signalHandlers) process.removeListener(signal, handler);
            signalHandlers.clear();
            if (escalationTimer) clearTimeout(escalationTimer);
            escalationTimer = null;
        };
        const sendSignal = (signal) => {
            if (!child?.pid) return;
            try {
                if (process.platform === 'win32') {
                    if (!child.killed) child.kill(signal);
                } else {
                    // detached=true 为子进程分配独立进程组，取消时连同后代一起终止。
                    process.kill(-child.pid, signal);
                }
            } catch (error) {
                if (error.code !== 'ESRCH') terminationError ||= error;
            }
        };
        const scheduleEscalation = () => {
            if (escalationTimer || !child?.pid) return;
            escalationTimer = setTimeout(() => {
                if (!settled && cancelledSignal) sendSignal('SIGKILL');
            }, CANCELLATION_GRACE_MS);
        };
        const closeLog = () => new Promise((resolveLog, rejectLog) => {
            if (!logStream || logStream.destroyed) {
                resolveLog();
                return;
            }
            const onError = (error) => {
                logStream.removeListener('error', onError);
                rejectLog(error);
            };
            logStream.once('error', onError);
            logStream.end(() => {
                logStream.removeListener('error', onError);
                resolveLog();
            });
        });
        const terminateAndWait = async (signal) => {
            if (!child?.pid) return;
            sendSignal(signal);
            if (process.platform === 'win32') return;
            if (await waitForProcessGroupExit(child.pid, CANCELLATION_GRACE_MS)) return;
            sendSignal('SIGKILL');
            if (!(await waitForProcessGroupExit(child.pid))) {
                throw new EngineeringError(`无法终止 ${label} 的进程组 ${child.pid}`, 2);
            }
        };
        const finish = (result, error = null) => {
            if (settled || finalizing) return;
            finalizing = true;
            (async () => {
                let finalError = error || terminationError;
                try {
                    if (cancelledSignal) {
                        await terminateAndWait(cancelledSignal);
                        finalError = new EngineeringError(`${label} 已被 ${cancelledSignal} 取消`, 2);
                    } else if (error) {
                        await terminateAndWait('SIGTERM');
                    }
                    await closeLog();
                } catch (terminationFailure) {
                    finalError = terminationFailure;
                }
                settled = true;
                cleanup();
                if (finalError) reject(finalError);
                else resolve(result);
            })();
        };
        const onLogError = (error) => finish(null, error);
        if (logStream) logStream.once('error', onLogError);
        for (const signal of ['SIGINT', 'SIGTERM']) {
            const handler = () => {
                if (settled) return;
                cancelledSignal ||= signal;
                sendSignal(signal);
                scheduleEscalation();
            };
            signalHandlers.set(signal, handler);
            process.on(signal, handler);
        }
        try {
            child = spawn(command, args, {
                cwd,
                env: { ...process.env, ...env },
                stdio: ['ignore', 'pipe', 'pipe'],
                shell: false,
                detached: process.platform !== 'win32',
            });
        } catch (error) {
            finish(null, error);
            return;
        }
        if (cancelledSignal) {
            sendSignal(cancelledSignal);
            scheduleEscalation();
        }
        child.stdout.on('data', (chunk) => {
            const text = chunk.toString();
            process.stdout.write(text);
            writeChunk(logStream, text);
        });
        child.stderr.on('data', (chunk) => {
            const text = chunk.toString();
            process.stderr.write(text);
            writeChunk(logStream, text);
        });
        child.once('error', (error) => {
            if (error.code === 'ENOENT') {
                finish(null, new EngineeringError(`无法启动 ${label}：${error.message}`, 2));
            } else {
                finish(null, error);
            }
        });
        child.once('close', (exitCode, signal) => {
            finish({ exitCode: Number.isInteger(exitCode) ? exitCode : 2, signal });
        });
    });
}

export async function runMavenStage(stage, args, { logsDirectory, cwd = projectRoot, env = {} } = {}) {
    if (!logsDirectory) fail('Maven 阶段缺少日志目录', 2);
    await mkdir(logsDirectory, { recursive: true });
    const logPath = path.join(logsDirectory, `${stage}.log`);
    const command = args[0] || path.join(projectRoot, 'mvnw');
    const commandArgs = args[0] ? args.slice(1) : args;
    const result = await runCommand(command, commandArgs, { cwd, env, logPath, label: stage });
    if (result.exitCode !== 0) {
        fail(`Maven 阶段失败：${stage}（exit=${result.exitCode}${result.signal ? `, signal=${result.signal}` : ''}，日志：${logPath}）`, result.exitCode >= 2 ? 2 : 1);
    }
    return { ...result, logPath };
}

export async function writeText(file, text, options = {}) {
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, text, options);
}

export async function fileExists(file) {
    try {
        await access(file);
        return true;
    } catch {
        return false;
    }
}

export async function listFiles(root, relative = '') {
    const absolute = path.join(root, relative);
    let entries;
    try {
        entries = await readdir(absolute, { withFileTypes: true });
    } catch (error) {
        if (error.code === 'ENOENT') return [];
        throw error;
    }
    const files = [];
    for (const entry of entries) {
        const next = path.join(relative, entry.name);
        if (entry.isDirectory()) files.push(...await listFiles(root, next));
        else if (entry.isFile()) files.push(path.join(root, next));
    }
    return files;
}

export function isMainModule(moduleUrl) {
    return process.argv[1] && moduleUrl === pathToFileURL(path.resolve(process.argv[1])).href;
}

export function finishMain(promise) {
    promise.catch((error) => {
        const exitCode = Number.isInteger(error?.exitCode) ? error.exitCode : 2;
        process.stderr.write(`${error?.message || error}\n`);
        process.exitCode = exitCode;
    });
}
