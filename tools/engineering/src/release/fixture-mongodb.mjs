import path from 'node:path';
import { writeText } from './runtime.mjs';

/** @typedef {'bom' | 'parent' | 'spring-data'} MongoConsumerMode */

/** @param {string} source @param {readonly string[]} expectedArtifacts @param {string} expectedVersion */
export function assertMongoDependencyList(source, expectedArtifacts, expectedVersion) {
    const reject = (coordinate, actual, expected) => {
        throw new Error(`MONGO_DEPENDENCY_CONTRACT coordinate=${coordinate} actual=${actual} expected=${expected}`);
    };
    if (typeof source !== 'string' || !source.trim()) reject('input', 'missing', 'non-empty dependency list');
    if (!Array.isArray(expectedArtifacts) || expectedArtifacts.length === 0) reject('input', 'invalid', 'non-empty expectedArtifacts');
    if (expectedArtifacts.some((artifact) => typeof artifact !== 'string' || !artifact.trim())
        || new Set(expectedArtifacts).size !== expectedArtifacts.length) reject('input', 'invalid', 'unique non-empty expectedArtifacts');
    if (typeof expectedVersion !== 'string' || !expectedVersion.trim()) reject('input', 'invalid', 'non-empty expectedVersion');

    const selected = new Map();
    for (const rawLine of source.split(/\r?\n/u)) {
        let line = rawLine.trim().replace(/^\[INFO\]\s*/u, '').trim();
        if (!line || /\(omitted(?:\s|$)/iu.test(line)) continue;
        line = line.replace(/\s+--\s+module\s+\S+(?:\s+(?:\[[^\]]+\]|\([^)]*\)))?\s*$/u, '').trim();
        if (!line.includes('org.mongodb')) continue;
        const fields = line.split(':');
        const coordinateHint = fields.length >= 2 ? `${fields[0]}:${fields[1]}` : 'org.mongodb:input';
        if (fields[0] !== 'org.mongodb' || ![5, 6].includes(fields.length)
            || fields.some((field) => !field.trim()) || fields[2] !== 'jar'
            || !['compile', 'runtime', 'test', 'provided', 'system'].includes(fields.at(-1))) {
            reject(coordinateHint, 'malformed', 'org.mongodb:artifact:jar[:classifier]:version:scope');
        }
        const artifact = fields[1];
        const coordinate = `org.mongodb:${artifact}`;
        const version = fields.at(-2);
        if (selected.has(coordinate)) reject(coordinate, `duplicate(${selected.get(coordinate)},${version})`, `exactly one selected dependency at ${expectedVersion}`);
        selected.set(coordinate, version);
    }
    if (selected.size === 0) reject('org.mongodb', 'missing', `one or more dependencies at ${expectedVersion}`);
    for (const artifact of expectedArtifacts) {
        const coordinate = `org.mongodb:${artifact}`;
        if (!selected.has(coordinate)) reject(coordinate, 'missing', expectedVersion);
    }
    for (const [coordinate, actual] of selected) {
        if (actual !== expectedVersion) reject(coordinate, actual, expectedVersion);
    }
}

/** @param {string} directory @param {string} revision @param {string} repositoryDir @param {MongoConsumerMode} mode @returns {Promise<void>} */
export async function writeMongoConsumerFixture(directory, revision, repositoryDir, mode) {
    for (const [name, value] of [['directory', directory], ['revision', revision], ['repositoryDir', repositoryDir]]) {
        if (typeof value !== 'string' || !value.trim()) throw new Error(`Invalid Mongo fixture parameter: ${name}`);
    }
    if (!['bom', 'parent', 'spring-data'].includes(mode)) throw new Error(`Unknown Mongo fixture mode: ${mode}`);
    await writeText(path.join(directory, 'pom.xml'), mongoPom(revision, repositoryDir, mode));
    const suite = mode === 'spring-data' ? springDataTest : syncTest;
    const file = mode === 'spring-data' ? 'MongoSpringDataCompatibilityTest.java' : 'MongoClientCompatibilityTest.java';
    await writeText(path.join(directory, 'src/test/java/io/github/yggdrasil/labs/fixture', file), suite);
}

const mongoArtifacts = ['bson', 'bson-kotlin', 'bson-record-codec', 'mongodb-driver-core', 'mongodb-driver-kotlin-coroutine', 'mongodb-driver-legacy', 'mongodb-driver-reactivestreams', 'mongodb-driver-sync'];

function escapeXml(value) {
    return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&apos;');
}

function dependency(groupId, artifactId, { scope = '', version = '' } = {}) {
    return `<dependency><groupId>${groupId}</groupId><artifactId>${artifactId}</artifactId>${version ? `<version>${version}</version>` : ''}${scope ? `<scope>${scope}</scope>` : ''}</dependency>`;
}

function mongoPom(revision, repositoryDir, mode) {
    const parent = mode === 'parent' ? `<parent><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-parent</artifactId><version>${escapeXml(revision)}</version><relativePath/></parent>` : '';
    const artifact = `mimir-mongo-${mode}-consumer`;
    const management = mode === 'parent' ? '' : `<dependencyManagement><dependencies>${dependency('io.github.yggdrasil-labs', 'mimir-boot-bom', { version: escapeXml(revision) }).replace('</dependency>', '<type>pom</type><scope>import</scope></dependency>')}</dependencies></dependencyManagement>`;
    const dependencies = mode === 'spring-data'
        ? `${dependency('org.springframework.boot', 'spring-boot-starter-data-mongodb')}${dependency('io.github.yggdrasil-labs', 'mimir-boot-starter-test', { scope: 'test' })}`
        : `${dependency('org.mongodb', 'mongodb-driver-sync')}${mode === 'bom' ? dependency('org.junit.jupiter', 'junit-jupiter', { scope: 'test' }) : dependency('io.github.yggdrasil-labs', 'mimir-boot-starter-test', { scope: 'test' })}`;
    const build = mode === 'bom' ? `<build><plugins>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.16.0</version><configuration><release>17</release><parameters>true</parameters></configuration></plugin>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.6.0</version><configuration><failIfNoTests>true</failIfNoTests></configuration></plugin>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-dependency-plugin</artifactId><version>3.11.0</version></plugin>
    </plugins></build>` : '';
    const profile = mode === 'bom' ? `<profiles><profile><id>mongo-family</id><dependencies>${mongoArtifacts.filter((item) => item !== 'mongodb-driver-sync').map((item) => dependency('org.mongodb', item)).join('')}</dependencies></profile></profiles>` : '';
    return `<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    ${parent}
    <groupId>io.github.yggdrasil-labs.fixture</groupId><artifactId>${artifact}</artifactId><version>1.0.0-SNAPSHOT</version>
    ${management}
    <dependencies>${dependencies}</dependencies>
    <repositories><repository><id>fixture</id><url>file://${escapeXml(repositoryDir)}</url></repository></repositories>
    ${build}
    ${profile}
</project>
`;
}

const syncTest = `package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class MongoClientCompatibilityTest {
    private ServerSocket occupiedPort() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        return socket;
    }

    private String uri(ServerSocket socket) {
        return "mongodb://127.0.0.1:" + socket.getLocalPort() + "/td040?connectTimeoutMS=200&socketTimeoutMS=200&serverSelectionTimeoutMS=200";
    }

    @Test
    void selectsTheRequestedDatabaseWithoutContactingAServer() throws IOException {
        ServerSocket socket = occupiedPort();
        MongoClient client = null;
        try {
            client = MongoClients.create(uri(socket));
            assertEquals("td040", client.getDatabase("td040").getName());
        } finally {
            if (client != null) client.close();
            socket.close();
        }
    }

    @Test
    void createsAndClosesAClientWhileTheLoopbackPortHasNoMongoServer() throws IOException {
        ServerSocket socket = occupiedPort();
        MongoClient client = null;
        try {
            client = MongoClients.create(uri(socket));
            assertNotNull(client);
        } finally {
            if (client != null) client.close();
            socket.close();
        }
    }

    @Test
    void rejectsAnInvalidUri() throws IOException {
        ServerSocket socket = occupiedPort();
        try {
            assertThrows(IllegalArgumentException.class, () -> MongoClients.create("not-a-mongodb-uri"));
        } finally {
            socket.close();
        }
    }
}
`;

const springDataTest = `package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

class MongoSpringDataCompatibilityTest {
    private ServerSocket occupiedPort() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        return socket;
    }

    private ApplicationContextRunner runner(String uri) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class, MongoDataAutoConfiguration.class))
                .withPropertyValues("spring.data.mongodb.uri=" + uri, "spring.data.mongodb.auto-index-creation=false");
    }

    private String uri(ServerSocket socket) {
        return "mongodb://127.0.0.1:" + socket.getLocalPort() + "/td040?connectTimeoutMS=200&socketTimeoutMS=200&serverSelectionTimeoutMS=200";
    }

    @Test
    void configuresMongoBeansAndRoundTripsAStringIdThroughTheConverter() throws IOException {
        ServerSocket socket = occupiedPort();
        try {
            runner(uri(socket)).run(context -> {
                assertTrue(context.isRunning());
                assertEquals(1, context.getBeansOfType(MongoClient.class).size());
                assertEquals(1, context.getBeansOfType(MongoDatabaseFactory.class).size());
                assertEquals(1, context.getBeansOfType(MongoTemplate.class).size());
                MongoConverter converter = context.getBean(MongoConverter.class);
                Sample source = new Sample("sample-1", "alice");
                Document document = new Document();
                converter.write(source, document);
                assertEquals("sample-1", document.getString("_id"));
                assertEquals("alice", document.getString("name"));
                Sample restored = converter.read(Sample.class, document);
                assertEquals("sample-1", restored.id);
                assertEquals("alice", restored.name);
            });
        } finally {
            socket.close();
        }
    }

    @Test
    void disablesAutomaticIndexCreationAndDoesNotOpenAMongoConnection() throws IOException {
        ServerSocket socket = occupiedPort();
        try {
            runner(uri(socket)).run(context -> {
                assertTrue(context.isRunning());
                assertFalse(context.getEnvironment().getProperty("spring.data.mongodb.auto-index-creation", Boolean.class, true));
            });
        } finally {
            socket.close();
        }
    }

    @Test
    void reportsInvalidUriAsAnIllegalArgumentExceptionWithoutLinkageErrors() throws IOException {
        ServerSocket socket = occupiedPort();
        try {
            runner("not-a-mongodb-uri").run(context -> {
                Throwable failure = context.getStartupFailure();
                assertNotNull(failure);
                assertTrue(hasCause(failure, IllegalArgumentException.class));
                assertFalse(hasCause(failure, LinkageError.class));
            });
        } finally {
            socket.close();
        }
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) if (type.isInstance(current)) return true;
        return false;
    }

    static class Sample {
        @Id String id;
        String name;

        Sample() {}

        Sample(String id, String name) { this.id = id; this.name = name; }
    }
}
`;
