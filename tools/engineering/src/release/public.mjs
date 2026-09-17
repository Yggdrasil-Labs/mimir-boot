import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { DOMParser } from '@xmldom/xmldom';
import { EngineeringError, errorExitCode, isToolEnvironmentError, toolEnvironmentError } from './runtime.mjs';

const NAMESPACE = 'http://maven.apache.org/POM/4.0.0';

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

function execute(command, args) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { stdio: ['ignore', 'ignore', 'inherit'] });
        child.on('error', (error) => reject(toolEnvironmentError(command, error)));
        child.on('exit', (code, signal) => code === 0 ? resolve() : reject(new Error(`${command} 失败（exit=${code}, signal=${signal}）`)));
    });
}

export async function verifyPublic(version, env = process.env) {
    if (!/^\d+\.\d+\.\d+$/u.test(version || '')) throw new TypeError('发布版本号必须是 x.y.z 格式');
    const attemptsText = env.MIMIR_PUBLIC_VERIFY_ATTEMPTS || '8';
    const intervalText = env.MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS || '30';
    if (!/^[1-9]\d*$/u.test(attemptsText) || !Number.isSafeInteger(Number(attemptsText))) throw new TypeError('MIMIR_PUBLIC_VERIFY_ATTEMPTS 必须是正整数');
    if (!/^\d+$/u.test(intervalText) || !Number.isSafeInteger(Number(intervalText) * 1000)) throw new TypeError('MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS 必须是非负整数');
    const base = new URL((env.MIMIR_MAVEN_CENTRAL_BASE_URL || 'https://repo.maven.apache.org/maven2').replace(/\/+$/u, '') + '/');
    if (!['https:', 'http:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new TypeError('Maven Central 地址必须是无凭据、无查询参数的 HTTP(S) 地址');
    const directory = await mkdtemp(path.join(os.tmpdir(), 'mimir-public-central-'));
    try {
        for (let attempt = 1; attempt <= Number(attemptsText); attempt++) {
            process.stdout.write(`公开制品验证，第 ${attempt}/${attemptsText} 次尝试。\n`);
            try {
                for (const [artifact, extension] of [['mimir-boot', 'pom'], ['mimir-boot-starter-web', 'pom'], ['mimir-boot-starter-web', 'jar']]) {
                    const destination = path.join(directory, `${artifact}.${extension}`);
                    await rm(destination, { force: true });
                    const url = new URL(`io/github/yggdrasil-labs/${artifact}/${version}/${artifact}-${version}.${extension}`, base).href;
                    // 保留 curl 的代理、TLS 与超时行为；所有解析和重试逻辑统一在 Node 中。
                    await execute('curl', ['--fail', '--location', '--silent', '--show-error', '--connect-timeout', '5', '--max-time', '15', '--output', destination, url]);
                    if (extension === 'pom') validatePom(await readFile(destination, 'utf8'), artifact, version);
                    else await execute('jar', ['tf', destination]);
                }
                process.stdout.write(`Maven Central 公开制品验证通过：${version}\n`);
                return;
            } catch (error) {
                if (error instanceof EngineeringError && error.exitCode >= 2) throw error;
                if (isToolEnvironmentError(error)) throw new EngineeringError(`公开制品验证工具异常：${error.message}`, 2);
                process.stderr.write(`公开制品尚不可用：${error.message}\n`);
                if (attempt === Number(attemptsText)) throw new Error(`Maven Central 公开制品在 ${attemptsText} 次尝试后仍不可用：${version}`, { cause: error });
                await delay(Number(intervalText) * 1000);
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
