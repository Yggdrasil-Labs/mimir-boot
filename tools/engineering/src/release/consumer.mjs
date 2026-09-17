import { DOMParser } from '@xmldom/xmldom';
import path from 'node:path';
import { preserveMetadata } from './maven-cache.mjs';

import {
    assertFixtureMimirRepositoryMarkers,
    assertFixturePublishedArtifact,
    findPublishedPom,
} from './artifact-contract.mjs';
import {
    createProxySettings,
    fail,
    fileExists,
    finishMain,
    isMainModule,
    makeTempDirectory,
    projectRoot,
    removeDirectory,
    runCommand,
    runMavenStage,
    runtime,
    settingsXml,
    writeText,
} from './runtime.mjs';
import {
    starterArtifacts,
    writeBomConsumerFixture,
    writeConsumerFixture,
    writeFailureConsumerFixture,
} from './fixture-consumer.mjs';

const MAVEN_NAMESPACE = 'http://maven.apache.org/POM/4.0.0';
const contractRocketMqVersion = '2.3.6';
const contractElasticsearchVersion = '8.11.0';

const parser = new DOMParser({
    onError: (level, message) => { throw new Error(`XML ${level}: ${message}`); },
});

function parsePom(source, label) {
    const document = parser.parseFromString(source, 'application/xml');
    const root = document.documentElement;
    if (document.doctype || root?.namespaceURI !== MAVEN_NAMESPACE || root.localName !== 'project') {
        fail(`${label} 必须是无 DTD 的 Maven project XML`);
    }
    return root;
}

function directElements(node, name) {
    return Array.from(node?.childNodes || []).filter((child) =>
        child.nodeType === 1 && child.namespaceURI === MAVEN_NAMESPACE && child.localName === name);
}

function text(node) {
    return node?.textContent.trim() || null;
}

async function readPomProperty(pom, property) {
    const root = parsePom(await runtime.readFile(pom, 'utf8'), pom);
    const values = Array.from(root.getElementsByTagNameNS(MAVEN_NAMESPACE, property)).map(text).filter(Boolean);
    if (values.length !== 1) fail(`${pom} 中的 ${property} 必须恰好定义一次且非空。`);
    return values[0];
}

function proxySettings() {
    return createProxySettings();
}

async function writeSettings(file, options = {}) {
    await writeText(file, settingsXml(options));
}

async function nonEmptyFile(file) {
    try {
        const information = await runtime.stat(file);
        return information.isFile() && information.size > 0;
    } catch {
        return false;
    }
}

async function assertParentFailsafe(parentPom) {
    const root = parsePom(await runtime.readFile(parentPom, 'utf8'), parentPom);
    const plugins = Array.from(root.getElementsByTagNameNS(MAVEN_NAMESPACE, 'plugin'));
    const plugin = plugins.find((candidate) => text(directElements(candidate, 'artifactId')[0]) === 'maven-failsafe-plugin');
    if (!plugin) fail(`已发布 Parent POM 缺少 maven-failsafe-plugin：${parentPom}`);
    const goals = Array.from(plugin.getElementsByTagNameNS(MAVEN_NAMESPACE, 'goal')).map(text);
    for (const goal of ['integration-test', 'verify']) {
        if (!goals.includes(goal)) fail(`已发布 Parent POM 的 Failsafe 缺少 ${goal} execution：${parentPom}`);
    }
}

async function assertCompactPom(pom, artifact) {
    const root = parsePom(await runtime.readFile(pom, 'utf8'), pom);
    const versions = Array.from(root.getElementsByTagNameNS(MAVEN_NAMESPACE, 'version')).map(text);
    if (versions.includes('${revision}')) {
        fail(`普通制品发布 POM 不得包含未解析的 revision Parent 版本：${pom}`);
    }
    const artifactIds = Array.from(root.getElementsByTagNameNS(MAVEN_NAMESPACE, 'artifactId')).map(text);
    if (artifactIds.includes('maven-failsafe-plugin')) {
        fail(`普通制品发布 POM 不得保留 Parent 的 Failsafe 构建插件：${pom}`);
    }
    return artifact;
}

async function assertPublishedArtifacts(repositoryDir, revision) {
    const required = [
        'mimir-boot',
        'mimir-boot-parent',
        'mimir-boot-bom',
        'mimir-boot-common',
        'mimir-boot-starters',
        ...starterArtifacts,
    ];
    for (const artifact of required) await assertFixturePublishedArtifact(repositoryDir, revision, artifact);
}

async function runConsumerMaven(stage, command, logsDirectory) {
    return runMavenStage(stage, command, { logsDirectory, cwd: projectRoot });
}

async function main() {
    const workDirectory = await makeTempDirectory('mimir-suite-consumer-');
    const logsDirectory = path.join(workDirectory, 'logs');
    const consumerDirectory = path.join(workDirectory, 'consumer');
    const bomConsumerDirectory = path.join(workDirectory, 'bom-only-consumer');
    const failureConsumerDirectory = path.join(workDirectory, 'failure-consumer');
    const repositoryDirectory = path.join(workDirectory, 'repository');
    const blockedRepositoryDirectory = path.join(workDirectory, 'blocked-remote');
    const producerCacheDirectory = path.join(workDirectory, 'producer-m2');
    const consumerCacheDirectory = path.join(workDirectory, 'consumer-m2');
    const bomConsumerCacheDirectory = path.join(workDirectory, 'bom-consumer-m2');
    const failureConsumerCacheDirectory = path.join(workDirectory, 'failure-consumer-m2');
    const settingsFile = path.join(workDirectory, 'settings.xml');
    const blockedSettingsFile = path.join(workDirectory, 'blocked-settings.xml');
    const mvnw = path.join(projectRoot, 'mvnw');

    try {
        await runtime.mkdir(logsDirectory, { recursive: true });
        const { proxyXml } = proxySettings();
        await writeSettings(settingsFile, { proxyXml });
        const revision = await readPomProperty(path.join(projectRoot, 'pom.xml'), 'revision');
        const bomRocketMqVersion = await readPomProperty(path.join(projectRoot, 'mimir-boot-bom/pom.xml'), 'rocketmq.version');
        const bomElasticsearchVersion = await readPomProperty(path.join(projectRoot, 'mimir-boot-bom/pom.xml'), 'elasticsearch.version');
        if (bomRocketMqVersion !== contractRocketMqVersion) {
            fail(`BOM 的 rocketmq.version=${bomRocketMqVersion} 不符合发布契约 ${contractRocketMqVersion}`);
        }
        if (bomElasticsearchVersion !== contractElasticsearchVersion) {
            fail(`BOM 的 elasticsearch.version=${bomElasticsearchVersion} 不符合发布契约 ${contractElasticsearchVersion}`);
        }

        const producerSelector = [':mimir-boot-bom', ':mimir-boot-starters', ...starterArtifacts.map((artifact) => `:${artifact}`)].join(',');
        const producerProjects = ['-pl', producerSelector, '-am'];
        const onlineMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(projectRoot, 'pom.xml'), `-Dmaven.repo.local=${producerCacheDirectory}`, ...producerProjects];
        await runConsumerMaven('root-online-clean-deploy', [...onlineMaven, 'clean', 'deploy', '-Dmaven.test.skip=true', '-Dmaven.source.skip=true', '-Dmaven.javadoc.skip=true', '-Dmaven.deploy.skip=true', '-Dgpg.skip=true'], logsDirectory);

        await runtime.mkdir(blockedRepositoryDirectory, { recursive: true });
        await preserveMetadata(producerCacheDirectory, blockedRepositoryDirectory, 'central');
        await writeSettings(blockedSettingsFile, { mirrorId: 'central', mirrorUrl: `file://${blockedRepositoryDirectory}` });
        const isolatedMaven = [mvnw, '-B', '-o', '-s', blockedSettingsFile, '-f', path.join(projectRoot, 'pom.xml'), `-Dmaven.repo.local=${producerCacheDirectory}`, ...producerProjects];
        await runConsumerMaven('root-isolated-clean', [...isolatedMaven, 'clean'], logsDirectory);
        const deployMaven = [mvnw, '-B', '-s', blockedSettingsFile, '-f', path.join(projectRoot, 'pom.xml'), `-Dmaven.repo.local=${producerCacheDirectory}`, ...producerProjects];
        await runConsumerMaven('root-fixture-deploy', [...deployMaven, 'deploy', '-Dmaven.test.skip=true', '-Dmaven.source.skip=true', '-Dmaven.javadoc.skip=true', '-Dgpg.skip=true', '-Dmaven.deploy.skip=false', `-DaltDeploymentRepository=fixture::default::file://${repositoryDirectory}`], logsDirectory);

        await assertPublishedArtifacts(repositoryDirectory, revision);
        const parentPom = await findPublishedPom(repositoryDirectory, revision, 'mimir-boot-parent');
        await assertParentFailsafe(parentPom);
        const compactArtifacts = ['mimir-boot', 'mimir-boot-bom', 'mimir-boot-common', 'mimir-boot-starters', ...starterArtifacts];
        for (const artifact of compactArtifacts) await assertCompactPom(await findPublishedPom(repositoryDirectory, revision, artifact), artifact);

        await writeConsumerFixture(consumerDirectory, revision, repositoryDirectory);
        if (await fileExists(path.join(consumerCacheDirectory, 'io/github/yggdrasil-labs'))) fail('consumer cache 在首次解析前不应已有 Mimir 制品');
        const onlineConsumerMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(consumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${consumerCacheDirectory}`];
        await runConsumerMaven('consumer-online-resolve', [...onlineConsumerMaven, 'dependency:resolve', `-DoutputFile=${path.join(consumerDirectory, 'target/online-dependency-resolve.txt')}`], logsDirectory);
        await runConsumerMaven('consumer-online-tree', [...onlineConsumerMaven, 'dependency:tree', `-DoutputFile=${path.join(consumerDirectory, 'target/online-dependency-tree.txt')}`], logsDirectory);
        await runConsumerMaven('consumer-online-clean-verify', [...onlineConsumerMaven, 'clean', 'verify'], logsDirectory);
        if (!(await nonEmptyFile(path.join(consumerDirectory, 'target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.ParentLifecycleIT.xml')))) fail('consumer 缺少 ParentLifecycleIT Failsafe 报告');
        await assertFixtureMimirRepositoryMarkers(consumerCacheDirectory, ['mimir-boot', 'mimir-boot-parent', 'mimir-boot-common', ...starterArtifacts]);

        const consumerMaven = [mvnw, '-B', '-o', '-s', blockedSettingsFile, '-f', path.join(consumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${consumerCacheDirectory}`];
        await runConsumerMaven('consumer-isolated-resolve', [...consumerMaven, 'dependency:resolve', `-DoutputFile=${path.join(consumerDirectory, 'target/dependency-resolve.txt')}`], logsDirectory);
        await runConsumerMaven('consumer-isolated-tree', [...consumerMaven, 'dependency:tree', `-DoutputFile=${path.join(consumerDirectory, 'target/dependency-tree.txt')}`], logsDirectory);
        const consumerTree = await runtime.readFile(path.join(consumerDirectory, 'target/dependency-tree.txt'), 'utf8');
        if (!consumerTree.includes(`org.apache.rocketmq:rocketmq-spring-boot-starter:jar:${contractRocketMqVersion}`)) fail('consumer 隔离依赖树缺少 RocketMQ 契约版本');
        if (!consumerTree.includes(`co.elastic.clients:elasticsearch-java:jar:${contractElasticsearchVersion}`)) fail('consumer 隔离依赖树缺少 Elasticsearch 契约版本');
        await runConsumerMaven('consumer-isolated-clean-verify', [...consumerMaven, 'clean', 'verify'], logsDirectory);
        if (!(await nonEmptyFile(path.join(consumerDirectory, 'target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.ParentLifecycleIT.xml')))) fail('consumer 隔离执行缺少 ParentLifecycleIT Failsafe 报告');

        await writeBomConsumerFixture(bomConsumerDirectory, revision, repositoryDirectory);
        if (await fileExists(path.join(bomConsumerCacheDirectory, 'io/github/yggdrasil-labs'))) fail('BOM-only consumer cache 在首次解析前不应已有 Mimir 制品');
        const bomOnlineMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(bomConsumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${bomConsumerCacheDirectory}`];
        await runConsumerMaven('bom-online-resolve', [...bomOnlineMaven, 'dependency:resolve', `-DoutputFile=${path.join(bomConsumerDirectory, 'target/online-dependency-resolve.txt')}`], logsDirectory);
        await runConsumerMaven('bom-online-tree', [...bomOnlineMaven, 'dependency:tree', `-DoutputFile=${path.join(bomConsumerDirectory, 'target/online-dependency-tree.txt')}`], logsDirectory);
        await runConsumerMaven('bom-online-verify', [...bomOnlineMaven, 'clean', 'verify'], logsDirectory);
        await assertFixtureMimirRepositoryMarkers(bomConsumerCacheDirectory, ['mimir-boot-bom', 'mimir-boot-parent', 'mimir-boot', 'mimir-boot-common', 'mimir-boot-starter-log']);
        const bomConsumerMaven = [mvnw, '-B', '-o', '-s', blockedSettingsFile, '-f', path.join(bomConsumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${bomConsumerCacheDirectory}`];
        await runConsumerMaven('bom-isolated-tree', [...bomConsumerMaven, 'dependency:tree', `-DoutputFile=${path.join(bomConsumerDirectory, 'target/dependency-tree.txt')}`], logsDirectory);
        const bomTree = await runtime.readFile(path.join(bomConsumerDirectory, 'target/dependency-tree.txt'), 'utf8');
        for (const coordinate of [
            `io.github.yggdrasil-labs:mimir-boot-starter-log:jar:${revision}`,
            `org.apache.rocketmq:rocketmq-spring-boot-starter:jar:${contractRocketMqVersion}`,
            `co.elastic.clients:elasticsearch-java:jar:${contractElasticsearchVersion}`,
        ]) if (!bomTree.includes(coordinate)) fail(`BOM-only 隔离依赖树缺少 ${coordinate}`);
        await runConsumerMaven('bom-isolated-verify', [...bomConsumerMaven, 'clean', 'verify'], logsDirectory);

        await writeFailureConsumerFixture(failureConsumerDirectory, revision, repositoryDirectory);
        if (await fileExists(path.join(failureConsumerCacheDirectory, 'io/github/yggdrasil-labs'))) fail('failure consumer cache 在首次解析前不应已有 Mimir 制品');
        const failureMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(failureConsumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${failureConsumerCacheDirectory}`];
        await runConsumerMaven('failure-online-resolve', [...failureMaven, 'dependency:resolve', `-DoutputFile=${path.join(failureConsumerDirectory, 'target/online-dependency-resolve.txt')}`], logsDirectory);
        await assertFixtureMimirRepositoryMarkers(failureConsumerCacheDirectory, ['mimir-boot-parent', 'mimir-boot']);
        const failureResult = await runCommand(failureMaven[0], [...failureMaven.slice(1), 'clean', 'verify'], { cwd: projectRoot, logPath: path.join(logsDirectory, 'failure-consumer-clean-verify.log'), label: 'failure-consumer-clean-verify' });
        if (failureResult.exitCode === 0) fail('故意失败的 AlwaysFailIT 未使 failure consumer 的 verify 失败');
        const failureReport = path.join(failureConsumerDirectory, 'target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.AlwaysFailIT.xml');
        if (!(await nonEmptyFile(failureReport))) fail('failure consumer 缺少 AlwaysFailIT Failsafe 报告');
        const failureReportText = await runtime.readFile(failureReport, 'utf8');
        if (!failureReportText.includes('AlwaysFailIT') || !failureReportText.includes('<failure')) fail('AlwaysFailIT Failsafe 报告未记录失败');

        process.stdout.write(`隔离发布消费者验证通过：Parent、独立 BOM-only consumer、starter flatten 抽查和故意失败 Failsafe 门禁均符合版本 ${revision}。\n`);
    } finally {
        if (process.env.MIMIR_KEEP_WORKDIR === '1') process.stderr.write(`保留 consumer 临时目录：${workDirectory}\n`);
        else await removeDirectory(workDirectory);
    }
}

if (isMainModule(import.meta.url)) finishMain(main());

export {
    assertCompactPom,
    assertParentFailsafe,
    parsePom,
    readPomProperty,
};
