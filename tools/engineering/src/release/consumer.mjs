import { DOMParser } from '@xmldom/xmldom';
import path from 'node:path';
import { copyThirdPartyMavenCache, defaultMavenBootstrapRepository, defaultMavenSeedRepository, preserveMetadata } from './maven-cache.mjs';

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
const MAVEN_REPOSITORY_ID = 'central';
const RETRYABLE_MAVEN_STAGES = new Set([
    'root-online-clean-deploy',
    'consumer-online-resolve',
    'consumer-online-tree',
    'bom-online-resolve',
    'bom-online-tree',
    'failure-online-resolve',
]);

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

async function assertParentQuality(parentPom, revision) {
    const root = parsePom(await runtime.readFile(parentPom, 'utf8'), parentPom);
    const properties = directElements(root, 'properties')[0];
    const version = text(directElements(properties, 'mimir-boot-code-quality.version')[0]);
    if (version !== revision || directElements(properties, 'revision').length) {
        fail('发布 Parent 的质量制品版本必须为本轮字面版本，且不得保留 revision 属性');
    }
    for (const property of Array.from(properties?.childNodes || []).filter((node) => node.nodeType === 1)) {
        if (text(property)?.includes(projectRoot) || path.isAbsolute(text(property) || '')) {
            fail(`发布 Parent 属性包含生产者路径：${property.localName}`);
        }
    }
    const plugins = Array.from(root.getElementsByTagNameNS(MAVEN_NAMESPACE, 'plugin')).filter((node) =>
        text(directElements(node, 'groupId')[0]) === 'io.github.yggdrasil-labs'
        && text(directElements(node, 'artifactId')[0]) === 'mimir-boot-code-quality');
    const implementationVersion = text(directElements(properties, 'maven.pmd.plugin.version')[0]);
    if (!plugins.length || !implementationVersion || implementationVersion.includes('${')) fail('发布 Parent 缺少固定质量实现版本');
    let implementationDeclared = false;
    for (const plugin of plugins) {
        const configuredVersion = text(directElements(plugin, 'version')[0]);
        if (configuredVersion && ![revision, '${mimir-boot-code-quality.version}'].includes(configuredVersion)) fail('发布 Parent 质量插件版本与制品不一致');
        const dependencies = directElements(directElements(plugin, 'dependencies')[0], 'dependency');
        if (!dependencies.length) continue; // build/plugins 的空声明从 pluginManagement 继承。
        implementationDeclared = true;
        if (dependencies.length !== 1 || text(directElements(dependencies[0], 'groupId')[0]) !== 'org.apache.maven.plugins'
            || text(directElements(dependencies[0], 'artifactId')[0]) !== 'maven-pmd-plugin'
            || text(directElements(dependencies[0], 'version')[0]) !== implementationVersion) {
            fail('发布 Parent 的官方 PMD 实现必须固定且与来源配对');
        }
    }
    if (!implementationDeclared) fail('发布 Parent 缺少官方 PMD 实现依赖');
    if (implementationVersion !== await readPomProperty(path.join(projectRoot, 'mimir-boot-code-quality/pom.xml'), 'pmd.upstream.version')) {
        fail('发布 Parent 官方 PMD 实现版本与描述符来源版本不一致');
    }
    return implementationVersion;
}

async function assertQualityArtifact(repositoryDirectory, revision, implementationVersion, workDirectory, logsDirectory) {
    const pom = await findPublishedPom(repositoryDirectory, revision, 'mimir-boot-code-quality');
    const model = parsePom(await runtime.readFile(pom, 'utf8'), pom);
    if (directElements(model, 'dependencies').length) fail('规则资源制品 POM 不得声明普通依赖');
    const directory = path.dirname(pom);
    const jars = (await runtime.readdir(directory)).filter((name) => name.endsWith('.jar') && !/-(?:sources|javadoc)\.jar$/u.test(name));
    if (jars.length !== 1) fail('规则资源制品必须恰好发布一个主 jar');
    const jar = path.join(directory, jars[0]);
    const listing = path.join(logsDirectory, 'quality-jar-list.log');
    const listed = await runCommand('jar', ['tf', jar], { logPath: listing });
    if (listed.exitCode !== 0) fail('规则资源 jar 无法读取');
    if ((await runtime.readFile(listing, 'utf8')).split(/\r?\n/u).some((name) => /\.(?:class|jar)$/u.test(name))) fail('规则资源 jar 不得包含 Java 类或第三方 jar');
    const extracted = path.join(workDirectory, 'quality-jar');
    await runtime.mkdir(extracted, { recursive: true });
    const entries = ['META-INF/maven/plugin.xml', 'META-INF/mimir-boot/pmd-source.properties',
        'META-INF/LICENSE', 'META-INF/NOTICE',
        'mimir-boot/pmd/main-ruleset.xml', 'mimir-boot/pmd/test-ruleset.xml'];
    const unpacked = await runCommand('jar', ['xf', jar, ...entries], { cwd: extracted });
    if (unpacked.exitCode !== 0) fail('规则资源 jar 无法解包');
    for (const entry of entries) if (!await nonEmptyFile(path.join(extracted, entry))) fail(`规则资源 jar 缺少 ${entry}`);
    const descriptor = parser.parseFromString(await runtime.readFile(path.join(extracted, entries[0]), 'utf8'), 'application/xml');
    const descriptorChild = (parent, name) => Array.from(parent?.childNodes || []).find((node) => node.nodeType === 1 && node.localName === name);
    const descriptorValue = (name) => text(descriptorChild(descriptor.documentElement, name));
    if (descriptor.doctype || descriptor.documentElement?.localName !== 'plugin'
        || descriptorValue('groupId') !== 'io.github.yggdrasil-labs' || descriptorValue('artifactId') !== 'mimir-boot-code-quality'
        || descriptorValue('version') !== revision) fail('规则资源 jar 的插件描述符坐标错误');
    const mojos = Array.from(descriptor.getElementsByTagName('mojo'));
    const goals = mojos.map((mojo) => text(descriptorChild(mojo, 'goal'))).sort();
    if (JSON.stringify(goals) !== JSON.stringify(['check', 'cpd', 'cpd-check', 'pmd', 'test-check', 'test-cpd', 'test-cpd-check', 'test-pmd'])) {
        fail('规则资源 jar 的插件描述符必须只包含主源码/测试 PMD 与 CPD 的八个目标');
    }
    for (const [reportGoal, checkGoal] of [['test-pmd', 'test-check'], ['test-cpd', 'test-cpd-check']]) {
        const testReport = mojos.find((mojo) => text(descriptorChild(mojo, 'goal')) === reportGoal);
        const testConfiguration = descriptorChild(testReport, 'configuration');
        const testCheck = mojos.find((mojo) => text(descriptorChild(mojo, 'goal')) === checkGoal);
        if (['compileSourceRoots', 'testSourceRoots'].some((name) =>
            descriptorChild(testConfiguration, name)?.getAttribute('default-value') !== '${project.testCompileSourceRoots}')
            || descriptorChild(testConfiguration, 'includeTests')?.getAttribute('default-value') !== 'true'
            || text(descriptorChild(testCheck, 'executeGoal')) !== reportGoal) {
            fail(`${reportGoal} 必须显式绑定 Maven 测试源码根并分叉测试报告目标`);
        }
    }
    const provenance = new Map((await runtime.readFile(path.join(extracted, entries[1]), 'utf8')).split(/\r?\n/u)
        .filter((line) => line.includes('=')).map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
    if (provenance.get('groupId') !== 'org.apache.maven.plugins' || provenance.get('artifactId') !== 'maven-pmd-plugin'
        || provenance.get('version') !== implementationVersion
        || provenance.get('descriptorSha256') !== await readPomProperty(path.join(projectRoot, 'mimir-boot-code-quality/pom.xml'), 'pmd.descriptor.sha256')) {
        fail('规则资源 jar 的描述符来源与 Parent 官方实现不一致');
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
        'mimir-boot-code-quality',
        'mimir-boot-common',
        'mimir-boot-starters',
        ...starterArtifacts,
    ];
    for (const artifact of required) await assertFixturePublishedArtifact(repositoryDir, revision, artifact);
}

async function runConsumerMaven(stage, command, logsDirectory) {
    return runMavenStage(stage, command, {
        logsDirectory,
        cwd: projectRoot,
        maxAttempts: RETRYABLE_MAVEN_STAGES.has(stage) ? 3 : 1,
        retryArgs: RETRYABLE_MAVEN_STAGES.has(stage) ? ['-U'] : [],
    });
}

async function seedMavenCaches(seedDirectory, cacheDirectories, { announce = true } = {}) {
    let files = 0;
    let sourceExists = false;
    for (const directory of cacheDirectories) {
        const result = await copyThirdPartyMavenCache(seedDirectory, directory, { repositoryId: MAVEN_REPOSITORY_ID });
        files += result.files;
        sourceExists ||= result.sourceExists;
    }
    if (announce && sourceExists) process.stderr.write(`发布消费者已从第三方 Maven 缓存种入 ${files} 个文件；Mimir 制品保持隔离。\n`);
    else if (announce) process.stderr.write(`发布消费者未找到第三方 Maven 缓存，将执行冷缓存准备：${seedDirectory}\n`);
    return { files, sourceExists };
}

async function backfillMavenCache({ targetDirectory, sourceDirectories, announce = true }) {
    let files = 0;
    for (const directory of sourceDirectories) {
        const result = await copyThirdPartyMavenCache(directory, targetDirectory, { repositoryId: MAVEN_REPOSITORY_ID, overwrite: false });
        files += result.files;
    }
    if (announce && files > 0) process.stderr.write(`发布消费者已回填 ${files} 个新增第三方 Maven 缓存文件。\n`);
    return { files };
}

async function finalizeConsumer({ completed, seedCacheDirectory, cacheDirectories, externalLogsDirectory, logsDirectory, workDirectory }) {
    if (!completed) {
        try {
            const recovered = await backfillMavenCache({ targetDirectory: seedCacheDirectory, sourceDirectories: cacheDirectories, announce: false });
            if (recovered.files > 0) process.stderr.write(`发布消费者已保留 ${recovered.files} 个已完成的第三方 Maven 下载，供下次重试复用。\n`);
        } catch (error) {
            process.stderr.write(`发布消费者缓存恢复失败，保留原始验收错误：${error.message}\n`);
        }
    }
    if (!completed && !externalLogsDirectory) process.stderr.write(`发布消费者失败日志：${logsDirectory}\n`);
    try {
        try {
            if (completed && !externalLogsDirectory) await removeDirectory(logsDirectory);
        } finally {
            if (process.env.MIMIR_KEEP_WORKDIR === '1') process.stderr.write(`保留 consumer 临时目录：${workDirectory}\n`);
            else await removeDirectory(workDirectory);
        }
    } catch (error) {
        if (completed) throw error;
        process.stderr.write(`发布消费者临时目录清理失败，保留原始验收错误：${error.message}\n`);
    }
}

async function main() {
    const workDirectory = await makeTempDirectory('mimir-suite-consumer-');
    const externalLogsDirectory = process.env.MIMIR_RELEASE_LOG_DIRECTORY || '';
    const logsDirectory = externalLogsDirectory
        ? path.join(path.resolve(externalLogsDirectory), 'consumer')
        : await makeTempDirectory('mimir-release-consumer-logs-');
    const consumerDirectory = path.join(workDirectory, 'consumer');
    const bomConsumerDirectory = path.join(workDirectory, 'bom-only-consumer');
    const failureConsumerDirectory = path.join(workDirectory, 'failure-consumer');
    const repositoryDirectory = path.join(workDirectory, 'repository');
    const blockedRepositoryDirectory = path.join(workDirectory, 'blocked-remote');
    const producerCacheDirectory = path.join(workDirectory, 'producer-m2');
    const consumerCacheDirectory = path.join(workDirectory, 'consumer-m2');
    const bomConsumerCacheDirectory = path.join(workDirectory, 'bom-consumer-m2');
    const failureConsumerCacheDirectory = path.join(workDirectory, 'failure-consumer-m2');
    const sharedCacheDirectory = path.join(workDirectory, 'third-party-m2');
    const settingsFile = path.join(workDirectory, 'settings.xml');
    const blockedSettingsFile = path.join(workDirectory, 'blocked-settings.xml');
    const mvnw = path.join(projectRoot, 'mvnw');
    const seedCacheDirectory = defaultMavenSeedRepository();
    const bootstrapCacheDirectory = defaultMavenBootstrapRepository();
    let completed = false;

    try {
        await runtime.mkdir(logsDirectory, { recursive: true });
        const { proxyXml } = proxySettings();
        await writeSettings(settingsFile, { proxyXml });
        let seededFromBootstrap = false;
        let seed = await seedMavenCaches(seedCacheDirectory, [sharedCacheDirectory], { announce: false });
        if (!seed.sourceExists && !process.env.MIMIR_MAVEN_SEED_REPOSITORY) {
            seed = await seedMavenCaches(bootstrapCacheDirectory, [sharedCacheDirectory], { announce: false });
            seededFromBootstrap = seed.sourceExists;
        }
        if (seed.sourceExists) process.stderr.write(`发布消费者已从${seededFromBootstrap ? '只读 Maven 引导' : '专用 Maven'}缓存种入 ${seed.files} 个文件；Mimir 制品保持隔离。\n`);
        if (!seed.sourceExists) process.stderr.write(`发布消费者未找到第三方 Maven 缓存，将执行冷缓存准备：${seedCacheDirectory}\n`);
        await seedMavenCaches(sharedCacheDirectory, [producerCacheDirectory], { announce: false });
        const revision = await readPomProperty(path.join(projectRoot, 'pom.xml'), 'revision');
        const bomRocketMqVersion = await readPomProperty(path.join(projectRoot, 'mimir-boot-bom/pom.xml'), 'rocketmq.version');
        const springBootVersion = await readPomProperty(path.join(projectRoot, 'pom.xml'), 'spring.boot.version');

        const producerSelector = [':mimir-boot-bom', ':mimir-boot-code-quality', ':mimir-boot-starters', ...starterArtifacts.map((artifact) => `:${artifact}`)].join(',');
        const producerProjects = ['-pl', producerSelector, '-am'];
        const onlineMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(projectRoot, 'pom.xml'), `-Dmaven.repo.local=${producerCacheDirectory}`, ...producerProjects];
        await runConsumerMaven('root-online-clean-deploy', [...onlineMaven, 'clean', 'deploy', '-Dmaven.test.skip=true', '-Dmaven.source.skip=true', '-Dmaven.javadoc.skip=true', '-Dmaven.deploy.skip=true', '-Dgpg.skip=true'], logsDirectory);
        const bootElasticsearchVersion = await readPomProperty(
            path.join(producerCacheDirectory, 'org/springframework/boot/spring-boot-dependencies', springBootVersion, `spring-boot-dependencies-${springBootVersion}.pom`),
            'elasticsearch-client.version',
        );
        await backfillMavenCache({ targetDirectory: sharedCacheDirectory, sourceDirectories: [producerCacheDirectory], announce: false });

        await runtime.mkdir(blockedRepositoryDirectory, { recursive: true });
        await preserveMetadata(producerCacheDirectory, blockedRepositoryDirectory, MAVEN_REPOSITORY_ID);
        await writeSettings(blockedSettingsFile, { mirrorId: MAVEN_REPOSITORY_ID, mirrorUrl: `file://${blockedRepositoryDirectory}` });
        const isolatedMaven = [mvnw, '-B', '-o', '-s', blockedSettingsFile, '-f', path.join(projectRoot, 'pom.xml'), `-Dmaven.repo.local=${producerCacheDirectory}`, ...producerProjects];
        await runConsumerMaven('root-isolated-clean', [...isolatedMaven, 'clean'], logsDirectory);
        const deployMaven = [mvnw, '-B', '-s', blockedSettingsFile, '-f', path.join(projectRoot, 'pom.xml'), `-Dmaven.repo.local=${producerCacheDirectory}`, ...producerProjects];
        await runConsumerMaven('root-fixture-deploy', [...deployMaven, 'deploy', '-Dmaven.test.skip=true', '-Dmaven.source.skip=true', '-Dmaven.javadoc.skip=true', '-Dgpg.skip=true', '-Dmaven.deploy.skip=false', `-DaltDeploymentRepository=fixture::default::file://${repositoryDirectory}`], logsDirectory);

        await assertPublishedArtifacts(repositoryDirectory, revision);
        const parentPom = await findPublishedPom(repositoryDirectory, revision, 'mimir-boot-parent');
        await assertParentFailsafe(parentPom);
        const implementationVersion = await assertParentQuality(parentPom, revision);
        await assertQualityArtifact(repositoryDirectory, revision, implementationVersion, workDirectory, logsDirectory);
        const compactArtifacts = ['mimir-boot', 'mimir-boot-bom', 'mimir-boot-code-quality', 'mimir-boot-common', 'mimir-boot-starters', ...starterArtifacts];
        for (const artifact of compactArtifacts) await assertCompactPom(await findPublishedPom(repositoryDirectory, revision, artifact), artifact);

        await writeConsumerFixture(consumerDirectory, revision, repositoryDirectory);
        await seedMavenCaches(sharedCacheDirectory, [consumerCacheDirectory], { announce: false });
        if (await fileExists(path.join(consumerCacheDirectory, 'io/github/yggdrasil-labs'))) fail('consumer cache 在首次解析前不应已有 Mimir 制品');
        const onlineConsumerMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(consumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${consumerCacheDirectory}`];
        await runConsumerMaven('consumer-online-resolve', [...onlineConsumerMaven, 'dependency:resolve', `-DoutputFile=${path.join(consumerDirectory, 'target/online-dependency-resolve.txt')}`], logsDirectory);
        await runConsumerMaven('consumer-online-tree', [...onlineConsumerMaven, 'dependency:tree', `-DoutputFile=${path.join(consumerDirectory, 'target/online-dependency-tree.txt')}`], logsDirectory);
        await runConsumerMaven('consumer-online-clean-verify', [...onlineConsumerMaven, 'clean', 'verify'], logsDirectory);
        if (!(await nonEmptyFile(path.join(consumerDirectory, 'target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.ParentLifecycleIT.xml')))) fail('consumer 缺少 ParentLifecycleIT Failsafe 报告');
        // 报告渲染会解析运行时皮肤等构件；预热实际 precheck 路径后才进入离线验收。
        await runConsumerMaven('consumer-online-quality-precheck', [...onlineConsumerMaven, '-Pprecheck', 'clean', 'verify'], logsDirectory);
        await assertFixtureMimirRepositoryMarkers(consumerCacheDirectory, ['mimir-boot', 'mimir-boot-parent', 'mimir-boot-code-quality', 'mimir-boot-common', ...starterArtifacts], repositoryDirectory);
        await backfillMavenCache({ targetDirectory: sharedCacheDirectory, sourceDirectories: [consumerCacheDirectory], announce: false });

        const consumerMaven = [mvnw, '-B', '-o', '-s', blockedSettingsFile, '-f', path.join(consumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${consumerCacheDirectory}`];
        await runConsumerMaven('consumer-isolated-resolve', [...consumerMaven, 'dependency:resolve', `-DoutputFile=${path.join(consumerDirectory, 'target/dependency-resolve.txt')}`], logsDirectory);
        await runConsumerMaven('consumer-isolated-tree', [...consumerMaven, 'dependency:tree', `-DoutputFile=${path.join(consumerDirectory, 'target/dependency-tree.txt')}`], logsDirectory);
        const consumerTree = await runtime.readFile(path.join(consumerDirectory, 'target/dependency-tree.txt'), 'utf8');
        if (!consumerTree.includes(`org.apache.rocketmq:rocketmq-spring-boot-starter:jar:${bomRocketMqVersion}`)) fail('consumer 隔离依赖树缺少 BOM 声明的 RocketMQ 版本');
        if (!consumerTree.includes(`co.elastic.clients:elasticsearch-java:jar:${bootElasticsearchVersion}`)) fail('consumer 隔离依赖树未使用 Spring Boot 管理的 Elasticsearch 版本');
        await runConsumerMaven('consumer-isolated-clean-verify', [...consumerMaven, 'clean', 'verify'], logsDirectory);
        if (!(await nonEmptyFile(path.join(consumerDirectory, 'target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.ParentLifecycleIT.xml')))) fail('consumer 隔离执行缺少 ParentLifecycleIT Failsafe 报告');
        await runConsumerMaven('consumer-quality-precheck', [...consumerMaven, '-Pprecheck', 'clean', 'verify'], logsDirectory);
        for (const [kind, relativeReport, expectedRule, expectedFile, expectedDuplications = 0] of [
            ['pmd', 'pmd.xml', 'CommentRequired', '/code/main/io/github/yggdrasil/labs/fixture/ParentQualityContract.java'],
            ['pmd', 'tests/pmd.xml', 'UnnecessaryBooleanAssertion', '/code/tests/io/github/yggdrasil/labs/fixture/ParentLifecycleIT.java'],
            ['cpd', 'cpd.xml', null, null],
            ['cpd', 'tests/cpd.xml', null, '/code/tests/io/github/yggdrasil/labs/fixture/IsolatedConsumerTest.java', 1],
        ]) {
            const report = path.join(consumerDirectory, 'target/mimir-quality', relativeReport);
            if (!await nonEmptyFile(report)) fail(`发布 Parent 消费者缺少 ${relativeReport} 质量报告`);
            const document = parser.parseFromString(await runtime.readFile(report, 'utf8'), 'application/xml');
            const expected = kind === 'pmd' ? 'pmd' : 'pmd-cpd';
            const entries = Array.from(document.getElementsByTagName('*'));
            if (document.doctype || document.documentElement?.localName !== expected
                || entries.some((node) => ['error', 'configerror', 'processingerror', 'configurationerror'].includes(node.localName.toLowerCase()))) {
                fail(`发布 Parent 消费者的 ${relativeReport} 报告包含处理错误或无效 XML`);
            }
            const duplications = entries.filter((node) => node.localName === 'duplication');
            if (duplications.length !== expectedDuplications) fail(`发布 Parent 消费者的 ${relativeReport} 必须恰好包含 ${expectedDuplications} 组预设重复`);
            if (expectedDuplications) {
                const duplication = duplications[0];
                const tokens = Number(duplication.getAttribute('tokens'));
                const files = Array.from(duplication.childNodes).filter((node) => node.nodeType === 1 && node.localName === 'file');
                const locations = new Set(files.map((file) => `${file.getAttribute('path')}:${file.getAttribute('line')}:${file.getAttribute('column')}`));
                if (!Number.isSafeInteger(tokens) || tokens < 100 || files.length < 2 || locations.size < 2
                    || files.some((file) => !file.getAttribute('path')?.replaceAll('\\', '/').endsWith(expectedFile)
                        || !Number.isSafeInteger(Number(file.getAttribute('line'))) || Number(file.getAttribute('line')) < 1)) {
                    fail(`发布 Parent 消费者的 ${relativeReport} 必须报告 ${expectedFile} 至少两个位置的 100 tokens 预设重复`);
                }
            }
            const violations = entries.filter((node) => node.localName === 'violation');
            if (kind === 'pmd' && (violations.length !== 1 || violations[0].getAttribute('rule') !== expectedRule
                || !violations[0].parentNode.getAttribute('name')?.replaceAll('\\', '/').endsWith(expectedFile))) {
                fail(`发布 Parent 消费者的 ${relativeReport} 必须仅报告 ${expectedFile} 的 ${expectedRule} 违规`);
            }
        }

        await writeBomConsumerFixture(bomConsumerDirectory, revision, repositoryDirectory);
        await seedMavenCaches(sharedCacheDirectory, [bomConsumerCacheDirectory], { announce: false });
        if (await fileExists(path.join(bomConsumerCacheDirectory, 'io/github/yggdrasil-labs'))) fail('BOM-only consumer cache 在首次解析前不应已有 Mimir 制品');
        const bomOnlineMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(bomConsumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${bomConsumerCacheDirectory}`];
        await runConsumerMaven('bom-online-resolve', [...bomOnlineMaven, 'dependency:resolve', `-DoutputFile=${path.join(bomConsumerDirectory, 'target/online-dependency-resolve.txt')}`], logsDirectory);
        await runConsumerMaven('bom-online-tree', [...bomOnlineMaven, 'dependency:tree', `-DoutputFile=${path.join(bomConsumerDirectory, 'target/online-dependency-tree.txt')}`], logsDirectory);
        await runConsumerMaven('bom-online-verify', [...bomOnlineMaven, 'clean', 'verify'], logsDirectory);
        await assertFixtureMimirRepositoryMarkers(bomConsumerCacheDirectory, ['mimir-boot-bom', 'mimir-boot-parent', 'mimir-boot', 'mimir-boot-common', 'mimir-boot-starter-log'], repositoryDirectory);
        const bomConsumerMaven = [mvnw, '-B', '-o', '-s', blockedSettingsFile, '-f', path.join(bomConsumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${bomConsumerCacheDirectory}`];
        await runConsumerMaven('bom-isolated-tree', [...bomConsumerMaven, 'dependency:tree', `-DoutputFile=${path.join(bomConsumerDirectory, 'target/dependency-tree.txt')}`], logsDirectory);
        const bomTree = await runtime.readFile(path.join(bomConsumerDirectory, 'target/dependency-tree.txt'), 'utf8');
        for (const coordinate of [
            `io.github.yggdrasil-labs:mimir-boot-starter-log:jar:${revision}`,
            `org.apache.rocketmq:rocketmq-spring-boot-starter:jar:${bomRocketMqVersion}`,
            `co.elastic.clients:elasticsearch-java:jar:${bootElasticsearchVersion}`,
        ]) if (!bomTree.includes(coordinate)) fail(`BOM-only 隔离依赖树缺少 ${coordinate}`);
        await runConsumerMaven('bom-isolated-verify', [...bomConsumerMaven, 'clean', 'verify'], logsDirectory);
        await backfillMavenCache({ targetDirectory: sharedCacheDirectory, sourceDirectories: [bomConsumerCacheDirectory], announce: false });

        await writeFailureConsumerFixture(failureConsumerDirectory, revision, repositoryDirectory);
        await seedMavenCaches(sharedCacheDirectory, [failureConsumerCacheDirectory], { announce: false });
        if (await fileExists(path.join(failureConsumerCacheDirectory, 'io/github/yggdrasil-labs'))) fail('failure consumer cache 在首次解析前不应已有 Mimir 制品');
        const failureMaven = [mvnw, '-B', '-s', settingsFile, '-f', path.join(failureConsumerDirectory, 'pom.xml'), `-Dmaven.repo.local=${failureConsumerCacheDirectory}`];
        await runConsumerMaven('failure-online-resolve', [...failureMaven, 'dependency:resolve', `-DoutputFile=${path.join(failureConsumerDirectory, 'target/online-dependency-resolve.txt')}`], logsDirectory);
        await assertFixtureMimirRepositoryMarkers(failureConsumerCacheDirectory, ['mimir-boot-parent', 'mimir-boot'], repositoryDirectory);
        const failureResult = await runCommand(failureMaven[0], [...failureMaven.slice(1), 'clean', 'verify'], { cwd: projectRoot, logPath: path.join(logsDirectory, 'failure-consumer-clean-verify.log'), label: 'failure-consumer-clean-verify' });
        if (failureResult.exitCode === 0) fail('故意失败的 AlwaysFailIT 未使 failure consumer 的 verify 失败');
        const failureReport = path.join(failureConsumerDirectory, 'target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.AlwaysFailIT.xml');
        if (!(await nonEmptyFile(failureReport))) fail('failure consumer 缺少 AlwaysFailIT Failsafe 报告');
        const failureReportText = await runtime.readFile(failureReport, 'utf8');
        if (!failureReportText.includes('AlwaysFailIT') || !failureReportText.includes('<failure')) fail('AlwaysFailIT Failsafe 报告未记录失败');

        await backfillMavenCache({ targetDirectory: sharedCacheDirectory, sourceDirectories: [failureConsumerCacheDirectory], announce: false });
        await backfillMavenCache({ targetDirectory: seedCacheDirectory, sourceDirectories: [producerCacheDirectory, consumerCacheDirectory, bomConsumerCacheDirectory, failureConsumerCacheDirectory] });
        completed = true;
        process.stdout.write(`隔离发布消费者验证通过：Parent、独立 BOM-only consumer、starter flatten 抽查和故意失败 Failsafe 门禁均符合版本 ${revision}。\n`);
    } finally {
        await finalizeConsumer({ completed, seedCacheDirectory, cacheDirectories: [producerCacheDirectory, consumerCacheDirectory, bomConsumerCacheDirectory, failureConsumerCacheDirectory], externalLogsDirectory, logsDirectory, workDirectory });
    }
}

if (isMainModule(import.meta.url)) finishMain(main());

export {
    backfillMavenCache,
    finalizeConsumer,
    assertCompactPom,
    assertParentFailsafe,
    parsePom,
    readPomProperty,
};
