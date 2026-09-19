import { createHash, randomUUID } from 'node:crypto';
import { copyFile, mkdir, readFile, readdir, rename, stat, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const MIMIR_GROUP_PATH = path.join('io', 'github', 'yggdrasil-labs');
const TRANSIENT_CACHE_FILENAMES = new Set(['resolver-status.properties', '_lastUpdated']);

function validRepositoryId(repositoryId) {
    return typeof repositoryId === 'string' && /^[A-Za-z0-9_.-]+$/u.test(repositoryId);
}

function isMimirArtifact(relative) {
    return relative === MIMIR_GROUP_PATH || relative.startsWith(`${MIMIR_GROUP_PATH}${path.sep}`);
}

function isTransientCacheFile(name) {
    return TRANSIENT_CACHE_FILENAMES.has(name) || name.endsWith('.lastUpdated') || name.endsWith('.part')
        || (name.includes('.mimir-gate-') && name.endsWith('.tmp'));
}

function isRemoteMetadata(name) {
    return /^maven-metadata-(?!local\.xml$)[A-Za-z0-9_.-]+\.xml$/u.test(name);
}

function isRemoteMetadataChecksum(name) {
    return /^maven-metadata-(?!local\.xml$)[A-Za-z0-9_.-]+\.xml\.(?:md5|sha1|sha256)$/u.test(name);
}

function normalizeRemoteRepositories(content, repositoryId) {
    return content.split(/\r?\n/u).map((line) => {
        const match = line.match(/^([^>]+>)[^=]*=(.*)$/u);
        return match ? `${match[1]}${repositoryId}=${match[2]}` : line;
    }).join('\n');
}

function mergeRemoteRepositories(existing, incoming, repositoryId) {
    const lines = [];
    const seen = new Set();
    let trailingNewline = false;
    for (const content of [existing, incoming]) {
        const normalized = normalizeRemoteRepositories(content, repositoryId);
        trailingNewline ||= normalized.endsWith('\n');
        for (const line of normalized.replace(/\n+$/u, '').split('\n')) {
            if (line === '' && normalized === '') continue;
            if (seen.has(line)) continue;
            seen.add(line);
            lines.push(line);
        }
    }
    return `${lines.join('\n')}${trailingNewline ? '\n' : ''}`;
}

async function destinationExists(file) {
    try {
        await stat(file);
        return true;
    } catch (error) {
        if (error.code === 'ENOENT') return false;
        throw error;
    }
}

async function copyMavenCacheFile(source, target, name, repositoryId, overwrite) {
    const targetExists = await destinationExists(target);
    if (name !== '_remote.repositories' && !overwrite && targetExists) return false;
    await mkdir(path.dirname(target), { recursive: true });
    const temporary = `${target}.mimir-gate-${randomUUID()}.tmp`;
    if (name === '_remote.repositories') {
        const existing = targetExists ? await readFile(target, 'utf8') : '';
        await writeFile(temporary, mergeRemoteRepositories(existing, await readFile(source, 'utf8'), repositoryId));
        await rename(temporary, target);
        return true;
    }
    if (isRemoteMetadata(name)) {
        const content = await readFile(source);
        await writeFile(temporary, content);
        await rename(temporary, target);
        for (const algorithm of ['sha1', 'sha256']) {
            const checksum = `${target}.${algorithm}`;
            const temporaryChecksum = `${checksum}.mimir-gate-${randomUUID()}.tmp`;
            await writeFile(temporaryChecksum, createHash(algorithm).update(content).digest('hex'));
            await rename(temporaryChecksum, checksum);
        }
        return true;
    }
    await copyFile(source, temporary);
    await rename(temporary, target);
    return true;
}

/**
 * 从共享 Maven 仓库复制第三方制品到本次验收的独立仓库。
 * 项目制品、失败标记和未完成下载不会进入目标仓库；目标仓库的来源 ID
 * 会按当前 Maven settings 归一，供随后的在线/离线阶段一致使用。
 */
export async function copyThirdPartyMavenCache(sourceDirectory, targetDirectory, { repositoryId, overwrite = true } = {}) {
    if (!validRepositoryId(repositoryId)) throw new Error('无效的 Maven 仓库 ID');
    if (typeof sourceDirectory !== 'string' || !path.isAbsolute(sourceDirectory)) throw new Error('Maven 种子仓库必须是绝对路径');
    if (typeof targetDirectory !== 'string' || !path.isAbsolute(targetDirectory)) throw new Error('Maven 目标仓库必须是绝对路径');
    if (path.resolve(sourceDirectory) === path.resolve(targetDirectory)) throw new Error('Maven 种子仓库和目标仓库不能相同');
    try {
        if (!(await stat(sourceDirectory)).isDirectory()) throw new Error(`Maven 种子仓库不是目录：${sourceDirectory}`);
    } catch (error) {
        if (error.code === 'ENOENT') return { sourceExists: false, files: 0 };
        throw error;
    }

    let files = 0;
    async function visit(relative = '') {
        const source = path.join(sourceDirectory, relative);
        for (const entry of await readdir(source, { withFileTypes: true })) {
            const next = path.join(relative, entry.name);
            if (isMimirArtifact(next) || entry.isSymbolicLink()) continue;
            if (entry.isDirectory()) {
                if (entry.name === '.locks') continue;
                await visit(next);
                continue;
            }
            if (!entry.isFile() || isTransientCacheFile(entry.name) || isRemoteMetadataChecksum(entry.name)) continue;
            const targetName = isRemoteMetadata(entry.name) ? `maven-metadata-${repositoryId}.xml` : entry.name;
            if (await copyMavenCacheFile(path.join(sourceDirectory, next), path.join(targetDirectory, relative, targetName), entry.name, repositoryId, overwrite)) files++;
        }
    }
    await visit();
    return { sourceExists: true, files };
}

export function defaultMavenSeedRepository(environment = process.env) {
    return environment.MIMIR_MAVEN_SEED_REPOSITORY || path.join(os.homedir(), '.cache', 'mimir-boot', 'maven-repository');
}

export function defaultMavenBootstrapRepository(environment = process.env) {
    return environment.MIMIR_MAVEN_BOOTSTRAP_REPOSITORY || path.join(os.homedir(), '.m2', 'repository');
}

// Maven 3.9.x 的版本范围仍需仓库元数据；file 镜像不能仅是一个空目录。
// 仅保留本轮预热得到的元数据，不向隔离镜像加入制品，也不开放外网。
// 来源：https://maven.apache.org/repositories/metadata.html
export async function preserveMetadata(cacheDirectory, mirrorDirectory, repositoryId) {
    if (!validRepositoryId(repositoryId)) throw new Error('无效的 Maven 仓库 ID');
    const filename = `maven-metadata-${repositoryId}.xml`;
    let count = 0;
    async function visit(relative) {
        const directory = path.join(cacheDirectory, relative);
        for (const entry of await readdir(directory, { withFileTypes: true })) {
            if (entry.isDirectory()) await visit(path.join(relative, entry.name));
            else if (entry.isFile() && entry.name === filename) {
                const content = await readFile(path.join(directory, entry.name));
                const target = path.join(mirrorDirectory, relative, 'maven-metadata.xml');
                await mkdir(path.dirname(target), { recursive: true });
                await writeFile(target, content);
                for (const algorithm of ['sha1', 'sha256']) {
                    await writeFile(`${target}.${algorithm}`, createHash(algorithm).update(content).digest('hex'));
                }
                count++;
            }
        }
    }
    await visit('');
    return count;
}
