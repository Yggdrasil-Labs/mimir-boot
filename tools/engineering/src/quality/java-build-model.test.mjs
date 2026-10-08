import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { generateExpectedFromModel } from './java-build-model.mjs';
import { cleanExpected, recordArtifacts, verifyReports } from './verify-java-reports.mjs';

// 验证跨模块复用的模型与报告契约；所有项目夹具及产物均写入临时目录。
async function fixture(run) {
    const root = await mkdtemp(path.join(tmpdir(), 'mimir-java-model-test-'));
    try {
        const directory = path.join(root, 'src/test/java/example');
        await mkdir(directory, { recursive: true });
        await writeFile(path.join(directory, 'ATest.java'), 'package example; class ATest {}');
        await writeFile(path.join(directory, 'Outer.java'), `package example;
            class Outer {
                static class InnerTest { static class DeepTest {} }
                class NestedTest {}
                void helper() { class LocalTest {} }
            }
            interface Contract { class MemberTest {} }
        `);
        const model = async ({ includes = ['**/*Test.java'], excludes, kind = 'unit', skip = false } = {}) => {
            const plugin = kind === 'unit' ? 'surefire' : 'failsafe';
            const execution = kind === 'unit' ? '<goal>test</goal>' : '<goal>integration-test</goal><goal>verify</goal>';
            const list = (name, items) => items === undefined ? '' : `<${name}>${items.map((item) =>
                `<${name === 'includes' ? 'include' : 'exclude'}>${item}</${name === 'includes' ? 'include' : 'exclude'}>`).join('')}</${name}>`;
            const pom = `<project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>fixture</artifactId><version>1</version>
                <build><sourceDirectory>${root}/src/main/java</sourceDirectory>
                    <testSourceDirectory>${root}/src/test/java</testSourceDirectory>
                    <directory>${root}/target</directory><testOutputDirectory>${root}/target/test-classes</testOutputDirectory>
                    <plugins><plugin><artifactId>maven-${plugin}-plugin</artifactId>
                        <configuration>${list('includes', includes)}${list('excludes', excludes)}<skipTests>${skip}</skipTests></configuration>
                        <executions><execution><goals>${execution}</goals></execution></executions>
                    </plugin><plugin><artifactId>maven-${kind === 'unit' ? 'failsafe' : 'surefire'}-plugin</artifactId>
                        <configuration><includes><include>**/__NO_SOURCES__</include></includes></configuration>
                        <executions><execution><goals>${kind === 'unit'
                            ? '<goal>integration-test</goal><goal>verify</goal>' : '<goal>test</goal>'}</goals></execution></executions>
                    </plugin><plugin><groupId>com.diffplug.spotless</groupId><artifactId>spotless-maven-plugin</artifactId>
                        <configuration><java><includes><include>src/test/java/**/*.java</include></includes></java></configuration>
                        <executions><execution><goals><goal>check</goal></goals></execution></executions>
                    </plugin></plugins>
                </build></project>`;
            await writeFile(path.join(root, 'pom.xml'), pom);
            return generateExpectedFromModel(root, 'fixture', pom);
        };
        await run({ root, model });
    } finally {
        await rm(root, { recursive: true, force: true });
    }
}

test('合法类名与路径选择器必须纳入测试期望，并拒绝跳过测试', async () => {
    await fixture(async ({ model }) => {
        for (const kind of ['unit', 'integration']) {
            for (const selector of ['ATest.java', 'ATest.class', 'ATest', 'example.ATest',
                'example.ATest.java', 'example.ATest.class', '**/*Test', 'example/ATest', '**/A?est.java']) {
                const options = { kind, includes: [selector] };
                const expected = (await model(options)).modules[0].tests[kind];
                assert.equal(expected.required, true, `${kind}: ${selector}`);
                assert.deepEqual(expected.sourceClasses, ['example.ATest'], `${kind}: ${selector}`);
                await assert.rejects(model({ ...options, skip: true }), /配置了 skip/u);
            }
        }
    });
});

test('成员类按二进制类名建模，默认排除保留，局部类不进入清单', async () => {
    await fixture(async ({ model }) => {
        const defaults = (await model()).modules[0].tests.unit;
        assert.deepEqual(defaults.sourceClasses, ['example.ATest']);
        const explicit = (await model({ includes: ['**/*Test.class'], excludes: ['Nothing'] })).modules[0].tests.unit;
        assert.deepEqual(explicit.sourceClasses, ['example.ATest', 'example.Contract$MemberTest',
            'example.Outer$InnerTest', 'example.Outer$InnerTest$DeepTest', 'example.Outer$NestedTest']);
        const options = { includes: ['**/Outer$InnerTest.class'], excludes: ['Nothing'] };
        assert.deepEqual((await model(options)).modules[0].tests.unit.sourceClasses, ['example.Outer$InnerTest']);
        await assert.rejects(model({ ...options, skip: true }), /配置了 skip/u);
    });
});

test('排除选择器使用相同归一化规则，确实无匹配时允许明确豁免', async () => {
    await fixture(async ({ model }) => {
        for (const selector of ['ATest.java', 'ATest', 'example.ATest', '**/*Test']) {
            const expected = (await model({ includes: ['ATest'], excludes: [selector], skip: true })).modules[0].tests.unit;
            assert.equal(expected.required, false, selector);
            assert.deepEqual(expected.sourceClasses, [], selector);
        }
    });
});

test('空选择器配置回退到插件默认规则', async () => {
    await fixture(async ({ model }) => {
        const expected = (await model({ includes: [], excludes: [] })).modules[0].tests.unit;
        assert.deepEqual(expected.sourceClasses, ['example.ATest']);
        await assert.rejects(model({ includes: [], excludes: [], skip: true }), /配置了 skip/u);
    });
});

test('不能可靠建模的路径写法明确拒绝，不能静默扩大排除范围', async () => {
    await fixture(async ({ model }) => {
        for (const selector of ['example/', './example/ATest', 'example\\ATest', 'example//ATest',
            '%regex[.*Test]', 'ATest#method', '!ATest']) {
            await assert.rejects(model({ excludes: [selector] }), /不支持的测试选择器/u, selector);
        }
    });
});

test('只有整段双星跨目录，FQN 和路径模式均保留插件的后缀匹配', async () => {
    await fixture(async ({ root, model }) => {
        const directory = path.join(root, 'src/test/java/other/example/deep');
        await mkdir(directory, { recursive: true });
        await writeFile(path.join(directory, 'BTest.java'), 'package other.example.deep; class BTest {}');
        for (const selector of ['example/**/BTest', 'other.example.deep.BTest', 'deep/BTest']) {
            assert.deepEqual((await model({ includes: [selector] })).modules[0].tests.unit.sourceClasses,
                ['other.example.deep.BTest'], selector);
        }
        assert.equal((await model({ includes: ['example/**BTest'] })).modules[0].tests.unit.required, false);
    });
});

test('类名归一化产生的空路径段按 Ant 规则忽略', async () => {
    await fixture(async ({ model }) => {
        for (const selector of ['.ATest', 'example..ATest', 'example...ATest.java',
            'example..ATest.class', 'example/.ATest.class', '**/example..ATest.class', 'example../ATest.class']) {
            const options = { includes: [selector] };
            assert.deepEqual((await model(options)).modules[0].tests.unit.sourceClasses, ['example.ATest'], selector);
            await assert.rejects(model({ ...options, skip: true }), /配置了 skip/u);
        }
    });
});

test('已匹配的测试阶段缺少 XML 必须失败', async () => {
    await fixture(async ({ root, model }) => {
        for (const options of [{ includes: ['ATest'] },
            { kind: 'integration', includes: ['example.ATest'] },
            { includes: ['**/Outer$InnerTest.class'], excludes: ['Nothing'] }]) {
            const manifest = path.join(root, 'manifest.json');
            await writeFile(manifest, JSON.stringify(await model(options)));
            await cleanExpected(root, manifest);
            await recordArtifacts(root, manifest);
            const result = await verifyReports(root, manifest);
            assert.equal(result.overall, 'failed');
            assert.ok(result.findings.some((finding) => finding.rule === 'test-report-missing'));
        }
    });
});
