import path from 'node:path';
import { projectRoot, writeText } from './runtime.mjs';

export const starterArtifacts = [
    'mimir-boot-starter-exception',
    'mimir-boot-starter-log',
    'mimir-boot-starter-web',
    'mimir-boot-starter-rpc-core',
    'mimir-boot-starter-dubbo',
    'mimir-boot-starter-feign',
    'mimir-boot-starter-nacos',
    'mimir-boot-starter-mybatis',
    'mimir-boot-starter-mybatis-processor',
    'mimir-boot-starter-test',
];

function escapeXml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&apos;');
}

function repositoryXml(repositoryDir) {
    return `<repository><id>fixture</id><url>file://${escapeXml(repositoryDir)}</url></repository>`;
}

export function createPom(artifact, version = '2.2.2') {
    return `<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.yggdrasil-labs</groupId>
  <artifactId>${escapeXml(artifact)}</artifactId>
  <version>${escapeXml(version)}</version>
</project>
`;
}

const consumerTest = `package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IsolatedConsumerTest {

    @Test
    void startsContextWithPublishedTestDependencies() {
        new ApplicationContextRunner()
                .run(context -> assertTrue(context.isRunning()));
    }
}
`;

const lifecycleIntegrationTest = `package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParentLifecycleIT {

    @Test
    void isExecutedByThePublishedParentFailsafeConfiguration() {
        assertTrue(true);
    }
}
`;

const failingIntegrationTest = `package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class AlwaysFailIT {

    @Test
    void alwaysFailsToProveFailsafeIsAnEffectiveGate() {
        fail("intentional consumer gate failure");
    }
}
`;

function consumerPom(revision, repositoryDir) {
    const dependencies = [
        ...starterArtifacts.slice(0, -1).map((artifact) => `        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>${artifact}</artifactId></dependency>`),
        '        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-test</artifactId><scope>test</scope></dependency>',
        '        <dependency><groupId>org.apache.rocketmq</groupId><artifactId>rocketmq-spring-boot-starter</artifactId></dependency>',
        '        <dependency><groupId>co.elastic.clients</groupId><artifactId>elasticsearch-java</artifactId></dependency>',
    ].join('\n');
    return `<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.yggdrasil-labs</groupId>
        <artifactId>mimir-boot-parent</artifactId>
        <version>${escapeXml(revision)}</version>
        <relativePath/>
    </parent>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-suite-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <repositories>
        ${repositoryXml(repositoryDir)}
    </repositories>
    <dependencies>
${dependencies}
    </dependencies>
</project>
`;
}

function bomConsumerPom(revision, repositoryDir) {
    return `<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-bom-only-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <repositories>
        ${repositoryXml(repositoryDir)}
    </repositories>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.yggdrasil-labs</groupId>
                <artifactId>mimir-boot-bom</artifactId>
                <version>${escapeXml(revision)}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-starter-log</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
        </dependency>
    </dependencies>
</project>
`;
}

function failureConsumerPom(revision, repositoryDir) {
    return `<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.yggdrasil-labs</groupId>
        <artifactId>mimir-boot-parent</artifactId>
        <version>${escapeXml(revision)}</version>
        <relativePath/>
    </parent>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-failure-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <repositories>
        ${repositoryXml(repositoryDir)}
    </repositories>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
`;
}

export async function writeConsumerFixture(directory, revision, repositoryDir) {
    await writeText(path.join(directory, 'pom.xml'), consumerPom(revision, repositoryDir));
    const testDirectory = path.join(directory, 'src/test/java/io/github/yggdrasil/labs/fixture');
    await writeText(path.join(testDirectory, 'IsolatedConsumerTest.java'), consumerTest);
    await writeText(path.join(testDirectory, 'ParentLifecycleIT.java'), lifecycleIntegrationTest);
}

export async function writeBomConsumerFixture(directory, revision, repositoryDir) {
    await writeText(path.join(directory, 'pom.xml'), bomConsumerPom(revision, repositoryDir));
}

export async function writeFailureConsumerFixture(directory, revision, repositoryDir) {
    await writeText(path.join(directory, 'pom.xml'), failureConsumerPom(revision, repositoryDir));
    await writeText(path.join(directory, 'src/test/java/io/github/yggdrasil/labs/fixture/AlwaysFailIT.java'), failingIntegrationTest);
}

export { projectRoot };
