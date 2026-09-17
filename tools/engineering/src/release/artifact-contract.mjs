import path from 'node:path';
import { listFiles, runtime, fail } from './runtime.mjs';

const namespace = path.join('io', 'github', 'yggdrasil-labs');

async function nonEmptyFile(file) {
    try {
        return (await runtime.stat(file)).isFile() && (await runtime.stat(file)).size > 0;
    } catch {
        return false;
    }
}

async function artifactDirectory(repositoryDir, revision, artifact) {
    return path.join(repositoryDir, namespace, artifact, revision);
}

export async function assertFixturePublishedArtifact(repositoryDir, revision, artifact) {
    const artifactDir = await artifactDirectory(repositoryDir, revision, artifact);
    try {
        if (!(await runtime.stat(artifactDir)).isDirectory()) {
            fail(`fixture repository 缺少 ${artifact} 的版本目录：${artifactDir}`);
        }
    } catch (error) {
        if (error instanceof Error && error.exitCode) throw error;
        fail(`fixture repository 缺少 ${artifact} 的版本目录：${artifactDir}`);
    }
    if (revision.endsWith('-SNAPSHOT') && !(await nonEmptyFile(path.join(artifactDir, 'maven-metadata.xml')))) {
        fail(`fixture repository 缺少 ${artifact} 的 Snapshot 元数据：${path.join(artifactDir, 'maven-metadata.xml')}`);
    }
    const files = await runtime.readdir(artifactDir, { withFileTypes: true });
    const poms = [];
    for (const entry of files) {
        if (!entry.isFile() || !entry.name.endsWith('.pom')) continue;
        const file = path.join(artifactDir, entry.name);
        if (await nonEmptyFile(file)) poms.push(file);
    }
    if (poms.length === 0) fail(`fixture repository 缺少 ${artifact} 的已发布 POM：${artifactDir}`);
}

export async function findPublishedPom(repositoryDir, revision, artifact) {
    const artifactDir = await artifactDirectory(repositoryDir, revision, artifact);
    const entries = await runtime.readdir(artifactDir, { withFileTypes: true });
    const poms = [];
    for (const entry of entries) {
        if (!entry.isFile() || !entry.name.endsWith('.pom')) continue;
        const file = path.join(artifactDir, entry.name);
        if (await nonEmptyFile(file)) poms.push(file);
    }
    if (poms.length !== 1) fail(`${artifact} 的已发布 POM 必须恰好存在一份：${artifactDir}`);
    return poms[0];
}

export async function assertFixtureMimirRepositoryMarkers(cacheDir, artifacts) {
    if (!Array.isArray(artifacts) || artifacts.length === 0) {
        fail('Maven cache marker 检查必须至少指定一个预期的 Mimir 制品。', 2);
    }
    const namespaceDir = path.join(cacheDir, namespace);
    try {
        if (!(await runtime.stat(namespaceDir)).isDirectory()) {
            fail(`Maven cache 缺少 Mimir 制品目录：${namespaceDir}`);
        }
    } catch (error) {
        if (error instanceof Error && error.exitCode) throw error;
        fail(`Maven cache 缺少 Mimir 制品目录：${namespaceDir}`);
    }
    const markerFiles = (await listFiles(namespaceDir)).filter((file) => path.basename(file) === '_remote.repositories');
    if (markerFiles.length === 0) fail(`Maven cache 未生成任何 Mimir 制品的 _remote.repositories：${namespaceDir}`);
    for (const marker of markerFiles) {
        const lines = (await runtime.readFile(marker, 'utf8')).split(/\r?\n/u);
        const invalid = lines.some((line) => line !== '' && !line.startsWith('#') && !line.endsWith('>fixture='));
        if (invalid) fail(`Maven cache 的 Mimir 制品只能标记为 fixture，发现异常 marker：${marker}`);
    }
    for (const artifact of artifacts) {
        const artifactDir = path.join(namespaceDir, artifact);
        try {
            if (!(await runtime.stat(artifactDir)).isDirectory()) fail(`Maven cache 缺少预期的 Mimir 制品目录：${artifactDir}`);
        } catch (error) {
            if (error instanceof Error && error.exitCode) throw error;
            fail(`Maven cache 缺少预期的 Mimir 制品目录：${artifactDir}`);
        }
        const artifactMarkers = (await listFiles(artifactDir)).filter((file) => path.basename(file) === '_remote.repositories');
        if (artifactMarkers.length === 0) fail(`Maven cache 缺少 ${artifact} 的 _remote.repositories：${artifactDir}`);
    }
}
