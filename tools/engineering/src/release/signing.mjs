import path from 'node:path';
import { copyThirdPartyMavenCache, defaultMavenBootstrapRepository, defaultMavenSeedRepository, preserveMetadata } from './maven-cache.mjs';

import {
    createProxySettings,
    fail,
    finishMain,
    isMainModule,
    listFiles,
    makeTempDirectory,
    projectRoot,
    removeDirectory,
    runCommand,
    runMavenStage,
    settingsXml,
    runtime,
    writeText,
} from './runtime.mjs';

function parseArgs(argv) {
    if (argv.length === 0) return { preheat: false };
    if (argv.length === 1 && argv[0] === '--preheat') return { preheat: true };
    fail('用法: signing.mjs [--preheat]', 2);
}

async function runMaven(command, args, label, { logsDirectory, env = {}, maxAttempts = 1, retryArgs = [] } = {}) {
    return runMavenStage(label, [command, ...args], { logsDirectory, cwd: projectRoot, env, maxAttempts, retryArgs });
}

async function writeMavenSettings(file, { proxyXml, mirrorId = '', mirrorUrl = '' }) {
    await writeText(file, settingsXml({ proxyXml, mirrorId, mirrorUrl }));
}

async function main(argv = process.argv.slice(2)) {
    const { preheat } = parseArgs(argv);
    const projectDirectory = projectRoot;
    const workDirectory = await makeTempDirectory('mimir-release-signing-');
    const gpgHome = path.join(workDirectory, 'gnupg');
    const verifyHome = path.join(workDirectory, 'verify-gnupg');
    const repositoryDirectory = path.join(workDirectory, 'repository');
    const failedRepositoryDirectory = path.join(workDirectory, 'failed-repository');
    const blockedRepositoryDirectory = path.join(workDirectory, 'blocked-remote');
    const cacheDirectory = path.join(workDirectory, 'm2');
    const seedCacheDirectory = process.env.MIMIR_RELEASE_SIGNING_SEED_M2 || defaultMavenSeedRepository();
    const bootstrapCacheDirectory = defaultMavenBootstrapRepository();
    const fixture = path.join(workDirectory, 'gpg-failure-fixture.sh');
    const settingsFile = path.join(workDirectory, 'settings.xml');
    const preheatSettingsFile = path.join(workDirectory, 'preheat-settings.xml');
    const externalLogsDirectory = process.env.MIMIR_RELEASE_LOG_DIRECTORY || '';
    const logsDirectory = externalLogsDirectory
        ? path.join(path.resolve(externalLogsDirectory), 'signing')
        : await makeTempDirectory('mimir-release-signing-logs-');
    const previousUmask = process.umask(0o077);
    const mvnw = path.join(projectDirectory, 'mvnw');
    let completed = false;

    try {
        const { proxyXml } = createProxySettings();
        await runtime.mkdir(gpgHome, { recursive: true, mode: 0o700 });
        await runtime.mkdir(verifyHome, { recursive: true, mode: 0o700 });
        await runtime.mkdir(repositoryDirectory, { recursive: true });
        await runtime.mkdir(failedRepositoryDirectory, { recursive: true });
        await runtime.mkdir(blockedRepositoryDirectory, { recursive: true });
        await runtime.mkdir(cacheDirectory, { recursive: true });
        await runtime.mkdir(logsDirectory, { recursive: true });
        await runtime.stat(gpgHome);
        await runtime.stat(verifyHome);

        let seededFromBootstrap = false;
        let seed = await copyThirdPartyMavenCache(seedCacheDirectory, cacheDirectory, { repositoryId: 'maven-central' });
        if (!seed.sourceExists && !process.env.MIMIR_RELEASE_SIGNING_SEED_M2 && !process.env.MIMIR_MAVEN_SEED_REPOSITORY) {
            seed = await copyThirdPartyMavenCache(bootstrapCacheDirectory, cacheDirectory, { repositoryId: 'maven-central' });
            seededFromBootstrap = seed.sourceExists;
        }
        if (seed.sourceExists) process.stderr.write(`发布签名已从${seededFromBootstrap ? '只读 Maven 引导' : '第三方 Maven'}缓存种入 ${seed.files} 个文件；Mimir 制品保持隔离。\n`);
        else if (!preheat) fail(`未找到第三方 Maven 缓存：${seedCacheDirectory}；请使用 --preheat 或设置 MIMIR_RELEASE_SIGNING_SEED_M2。`, 2);

        if (preheat) {
            await writeMavenSettings(preheatSettingsFile, { proxyXml, mirrorId: 'maven-central', mirrorUrl: 'https://repo.maven.apache.org/maven2' });
            await runMaven(mvnw, ['-B', '-s', preheatSettingsFile, '-f', path.join(projectDirectory, 'pom.xml'), 'clean', `-Dmaven.repo.local=${cacheDirectory}`], 'signing-preheat-clean', { logsDirectory, maxAttempts: 3, retryArgs: ['-U'] });
            await runMaven(mvnw, ['-B', '-s', preheatSettingsFile, '-f', path.join(projectDirectory, 'pom.xml'), 'deploy', '-Dmaven.test.skip=true', `-Dmaven.repo.local=${cacheDirectory}`, '-Dmaven.deploy.skip=true', '-Dgpg.skip=true'], 'signing-preheat-deploy', { logsDirectory, maxAttempts: 3, retryArgs: ['-U'] });
        }

        await preserveMetadata(cacheDirectory, blockedRepositoryDirectory, 'maven-central');
        await writeMavenSettings(settingsFile, { proxyXml, mirrorId: 'maven-central', mirrorUrl: `file://${blockedRepositoryDirectory}` });
        const gpgEnv = { GNUPGHOME: gpgHome };
        await runCommand('gpg', ['--batch', '--pinentry-mode', 'loopback', '--passphrase', '', '--quick-generate-key', 'Mimir Boot Release Fixture <fixture@example.invalid>', 'future-default', 'default', 'never'], { cwd: projectDirectory, env: gpgEnv, label: 'gpg-generate-key' }).then((result) => {
            if (result.exitCode !== 0) fail(`临时 GPG 密钥生成失败（exit=${result.exitCode}）`, result.exitCode >= 2 ? 2 : 1);
        });
        const publicKey = path.join(workDirectory, 'public.asc');
        await runCommand('gpg', ['--batch', '--armor', '--output', publicKey, '--export', 'fixture@example.invalid'], { cwd: projectDirectory, env: gpgEnv, label: 'gpg-export-key' }).then((result) => {
            if (result.exitCode !== 0) fail(`临时 GPG 公钥导出失败（exit=${result.exitCode}）`, result.exitCode >= 2 ? 2 : 1);
        });
        const publicKeyInformation = await runtime.stat(publicKey).catch(() => null);
        if (!publicKeyInformation?.isFile() || publicKeyInformation.size === 0) fail(`临时 GPG 公钥导出失败，文件不存在或为空：${publicKey}`);

        const deployCommand = [
            '-B', '-s', settingsFile, '-f', path.join(projectDirectory, 'pom.xml'), 'deploy',
            '-Dmaven.test.skip=true', `-Dmaven.repo.local=${cacheDirectory}`, '-Dmaven.deploy.skip=false',
            '-Dgpg.skip=false', '-Dgpg.executable=gpg', `-DaltDeploymentRepository=fixture::default::file://${repositoryDirectory}`,
        ];
        const cleanCommand = ['-B', '-s', settingsFile, '-f', path.join(projectDirectory, 'pom.xml'), 'clean', `-Dmaven.repo.local=${cacheDirectory}`];
        await runMaven(mvnw, cleanCommand, 'signing-clean', { logsDirectory });
        await runMaven(mvnw, deployCommand, 'signing-deploy', { logsDirectory, env: gpgEnv });

        const artifacts = (await listFiles(repositoryDirectory)).filter((file) => file.endsWith('.pom') || file.endsWith('.jar'));
        if (artifacts.length === 0) fail('签名 fixture 未生成任何 POM/JAR 制品');
        const verifyEnv = { GNUPGHOME: verifyHome };
        await runCommand('gpg', ['--batch', '--import', publicKey], { cwd: projectDirectory, env: verifyEnv, label: 'gpg-import-public-key' }).then((result) => {
            if (result.exitCode !== 0) fail(`临时 GPG 公钥导入失败（exit=${result.exitCode}）`, result.exitCode >= 2 ? 2 : 1);
        });
        for (const artifact of artifacts) {
            const signature = `${artifact}.asc`;
            const signatureInformation = await runtime.stat(signature).catch(() => null);
            if (!signatureInformation?.isFile() || signatureInformation.size === 0) fail(`制品缺少签名附属文件：${signature}`);
            const result = await runCommand('gpg', ['--batch', '--verify', signature, artifact], { cwd: projectDirectory, env: verifyEnv, label: `gpg-verify-${path.basename(artifact)}` });
            if (result.exitCode !== 0) fail(`制品签名验证失败：${artifact}`, result.exitCode >= 2 ? 2 : 1);
        }

        await writeText(fixture, '#!/usr/bin/env bash\nexit 7\n', { mode: 0o700 });
        await runMaven(mvnw, cleanCommand, 'signing-failed-clean', { logsDirectory });
        const failedResult = await runCommand(mvnw, [
            '-B', '-s', settingsFile, '-f', path.join(projectDirectory, 'pom.xml'), 'deploy',
            '-Dmaven.test.skip=true', `-Dmaven.repo.local=${cacheDirectory}`, '-Dmaven.deploy.skip=false',
            '-Dgpg.skip=false', `-Dgpg.executable=${fixture}`, `-DaltDeploymentRepository=fixture::default::file://${failedRepositoryDirectory}`,
        ], { cwd: projectDirectory, env: gpgEnv, label: 'signing-failure-fixture' });
        if (failedResult.exitCode === 0) fail('返回 7 的 GPG fixture 未阻断 deploy');
        if ((await listFiles(failedRepositoryDirectory)).length > 0) fail('失败签名 deploy 不得在隔离仓库留下半成功制品');
        const backfilled = await copyThirdPartyMavenCache(cacheDirectory, seedCacheDirectory, { repositoryId: 'maven-central', overwrite: false });
        if (backfilled.files > 0) process.stderr.write(`发布签名已回填 ${backfilled.files} 个新增第三方 Maven 缓存文件。\n`);
        completed = true;
        process.stdout.write(`发布签名验证通过：${artifacts.length} 个制品及附属制品均由临时密钥签名，失败 fixture 已阻断部署。\n`);
    } finally {
        process.umask(previousUmask);
        if (!completed && !externalLogsDirectory) process.stderr.write(`发布签名失败日志：${logsDirectory}\n`);
        if (completed && !externalLogsDirectory) await removeDirectory(logsDirectory);
        await removeDirectory(workDirectory);
    }
}

if (isMainModule(import.meta.url)) finishMain(main());

export { main, parseArgs, runMaven };
