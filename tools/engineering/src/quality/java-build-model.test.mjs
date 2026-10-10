import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';
import { generateExpectedFromModel } from './java-build-model.mjs';
import { cleanExpected, recordArtifacts, validateBuildInputs, verifyReports } from './verify-java-reports.mjs';

const execFileAsync = promisify(execFile);
// 长期验证跨模块的构建与报告门禁；真实编译类和合成报告均位于临时目录。
async function fixture(run) {
    const root = await mkdtemp(path.join(tmpdir(), 'mimir-java-model-test-'));
    try {
        const source = path.join(root, 'src/test/java/example');
        const classes = path.join(root, 'target/test-classes');
        await mkdir(source, { recursive: true });
        await mkdir(classes, { recursive: true });
        const files = ['ATest', 'BTests', 'ServiceIT'];
        for (const name of files) await writeFile(path.join(source, `${name}.java`), `package example; class ${name} { static class Scenario {} }`);
        const model = async ({ compiler = '', unit = '', integration = '', format = '', formatConfiguration = '', coverage = '',
            unitIncludes = ['**/*Test.java', '**/*Tests.java'],
            unitExcludes = ['**/*IT.java', '**/*IntegrationTest.java'], externalProfile = false, properties = '', formatExecution = '', unitExecution = '', integrationExecution = '',
            testRoot = `${root}/src/test/java` } = {}) => {
            const pom = `<project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>fixture</artifactId><version>1</version><properties>${properties}</properties>
                <build><sourceDirectory>${root}/src/main/java</sourceDirectory><testSourceDirectory>${testRoot}</testSourceDirectory>
                    <directory>${root}/target</directory><outputDirectory>${root}/target/classes</outputDirectory><testOutputDirectory>${classes}</testOutputDirectory>
                    <plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration>${compiler}</configuration></plugin>
                    <plugin><artifactId>maven-surefire-plugin</artifactId><configuration>
                        <includes>${unitIncludes.map((item) => `<include>${item}</include>`).join('')}</includes>
                        <excludes>${unitExcludes.map((item) => `<exclude>${item}</exclude>`).join('')}</excludes>${unit}
                    </configuration><executions><execution><goals><goal>test</goal></goals>${unitExecution}</execution></executions></plugin>
                    <plugin><artifactId>maven-failsafe-plugin</artifactId><configuration>
                        <includes><include>**/*IT.java</include><include>**/*IntegrationTest.java</include></includes>${integration}
                    </configuration><executions><execution><goals><goal>integration-test</goal><goal>verify</goal></goals>${integrationExecution}</execution></executions></plugin>
                    <plugin><groupId>com.diffplug.spotless</groupId><artifactId>spotless-maven-plugin</artifactId>
                        <configuration>${formatConfiguration}<java><includes><include>src/main/java/**/*.java</include><include>src/test/java/**/*.java</include></includes>${format}</java></configuration>
                        <executions><execution><phase>validate</phase><goals><goal>check</goal></goals></execution>${formatExecution}</executions>
                    </plugin><plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><configuration>
                        <rules><rule><element>BUNDLE</element><limits>
                            <limit><counter>INSTRUCTION</counter><value>COVEREDRATIO</value><minimum>0.60</minimum></limit>
                            <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.50</minimum></limit>
                        </limits></rule></rules>${coverage}</configuration><executions>
                        <execution><goals><goal>prepare-agent</goal></goals></execution>
                        <execution><phase>verify</phase><goals><goal>report</goal></goals></execution>
                        <execution><phase>verify</phase><goals><goal>check</goal></goals></execution>
                    </executions></plugin></plugins></build>${externalProfile ? `<profiles><profile><id>external-tests</id><build><plugins>
                        <plugin><artifactId>maven-failsafe-plugin</artifactId><configuration><excludes combine.self="override"/>${externalProfile === true ? '' : externalProfile}</configuration></plugin>
                    </plugins></build></profile></profiles>` : ''}</project>`;
            await writeFile(path.join(root, 'pom.xml'), pom);
            return generateExpectedFromModel(root, 'fixture', pom);
        };
        const compile = async (names = files) => execFileAsync('javac', ['-d', classes, ...names.map((name) => path.join(source, `${name}.java`))]);
        const report = async (name, kind = 'surefire', classname = `example.${name}`) => {
            const directory = path.join(root, 'target', `${kind}-reports`);
            await mkdir(directory, { recursive: true });
            await writeFile(path.join(directory, `TEST-example.${name}.xml`), `<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="${classname}" name="case"/></testsuite>`);
        };
        const manifest = path.join(root, 'manifest.json');
        const prepare = async (options) => {
            await writeFile(manifest, JSON.stringify(await model(options)));
            await cleanExpected(root, manifest);
            await compile();
        };
        const verify = async (options) => {
            await recordArtifacts(root, manifest, options);
            return verifyReports(root, manifest);
        };
        await run({ root, source, classes, model, compile, report, manifest, prepare, verify });
    } finally {
        await rm(root, { recursive: true, force: true });
    }
}

async function completeReports(report) {
    await report('ATest');
    await report('BTests', 'surefire', 'example.BTests$Scenario');
    await report('ServiceIT', 'failsafe');
}

test('标准源码、真实编译类与报告逐类对齐，嵌套报告归于外层类', async () => {
    await fixture(async ({ model, prepare, report, verify }) => {
        const expected = await model();
        assert.deepEqual(expected.modules[0].tests.unit.sourceClasses, ['example.ATest', 'example.BTests']);
        assert.deepEqual(expected.modules[0].tests.integration.sourceClasses, ['example.ServiceIT']);
        await prepare();
        await completeReports(report);
        assert.equal((await verify()).overall, 'passed');
    });
});

test('当前仓库同范围的 Spotless 重复执行允许，不同范围仍拒绝', async () => {
    await fixture(async ({ model }) => {
        const execution = '<execution><id>inherited-check</id><phase>validate</phase><goals><goal>check</goal></goals>';
        assert.equal((await model({ formatExecution: `${execution}</execution>` })).modules[0].format.required, true);
        await assert.rejects(model({ formatExecution: `${execution}<configuration><java><includes><include>__NO_SOURCES__</include></includes></java></configuration></execution>` }), /Spotless/u);
    });
});

test('Spotless 插件级和执行级扫描根必须指向模块目录', async () => {
    await fixture(async ({ root, model }) => {
        await mkdir(path.join(root, 'empty'));
        for (const scope of ['plugin', 'execution']) {
            const options = (directory) => {
                const configuration = `<baseDir>${directory}</baseDir>`;
                return scope === 'plugin' ? { formatConfiguration: configuration }
                    : { formatExecution: `<execution><id>extra-check</id><phase>validate</phase><goals><goal>check</goal></goals><configuration>${configuration}</configuration></execution>` };
            };
            await assert.rejects(model(options(path.join(root, 'empty'))), /Spotless.*baseDir/u);
            for (const directory of [root, '.']) {
                assert.equal((await model(options(directory))).modules[0].format.required, true);
            }
        }
    });
});

test('compiler 过滤、额外源码根和跳过主编译必须拒绝', async () => {
    await fixture(async ({ model }) => {
        for (const name of ['includes', 'excludes', 'testIncludes', 'testExcludes', 'compileSourceRoots', 'testCompileSourceRoots']) {
            await assert.rejects(model({ compiler: `<${name}><item>**/Omitted.java</item></${name}>` }), new RegExp(`compiler ${name}`));
        }
        for (const compiler of ['<skipMain>true</skipMain>', '<proc>only</proc>', '<compilerArgs><arg>-proc:only</arg></compilerArgs>']) {
            await assert.rejects(model({ compiler }), /跳过编译/u);
        }
        await assert.rejects(model({ properties: '<maven.compiler.proc>only</maven.compiler.proc>' }), /跳过编译/u);
        for (const name of ['compilerArgument', 'compilerArguments', 'testCompilerArgument', 'testCompilerArguments']) {
            await assert.rejects(model({ compiler: `<${name}>@options</${name}>` }), /自定义编译参数/u);
        }
        await assert.rejects(model({ compiler: '<compilerArgs><arg>@options</arg></compilerArgs>' }), /compilerArgs/u);
    });
});

test('skip、关闭报告和发现过滤不能变成测试豁免', async () => {
    await fixture(async ({ model }) => {
        for (const field of ['skip', 'skipTests', 'skipITs', 'disableXmlReport']) {
            for (const kind of ['unit', 'integration']) await assert.rejects(model({ [kind]: `<${field}>true</${field}>` }));
        }
        for (const unit of ['<test>ATest#case</test>', '<groups>fast</groups>', '<excludeJUnit5Engines>junit-jupiter</excludeJUnit5Engines>',
            '<properties><configurationParameters>junit.jupiter.conditions.deactivate=*</configurationParameters></properties>']) {
            await assert.rejects(model({ unit }), /不支持/u);
        }
        for (const name of ['test', 'it.test', 'groups', 'excludedGroups', 'surefire.includeJUnit5Engines', 'failsafe.excludesFile']) {
            await assert.rejects(model({ properties: `<${name}>filter</${name}>` }), /不支持测试属性/u);
        }
        await assert.rejects(model({ format: '<excludes><exclude>**/ATest.java</exclude></excludes>' }), /Spotless/u);
    });
});

test('测试 provider properties 非空时拒绝，不能通过嵌套配置过滤用例', async () => {
    await fixture(async ({ model }) => {
        for (const kind of ['unit', 'integration']) {
            for (const scope of ['plugin', 'execution']) {
                const options = (properties) => scope === 'plugin' ? { [kind]: properties }
                    : { [`${kind}Execution`]: `<configuration>${properties}</configuration>` };
                await model(options('<properties/>'));
                for (const content of ['<groups>fast</groups>', '<excludegroups>slow</excludegroups>',
                    '<configurationParameters>junit.jupiter.conditions.deactivate=*</configurationParameters>',
                    '<customOption/>', 'custom=value']) {
                    await assert.rejects(model(options(`<properties>${content}</properties>`)), /不支持.*provider properties/u);
                }
            }
        }
    });
});

test('明确排除的外部服务测试不要求报告，其余 IT 仍逐类核验', async () => {
    await fixture(async ({ source, compile, prepare, report, verify }) => {
        await writeFile(path.join(source, 'ExternalIT.java'), 'package example; class ExternalIT {}');
        await prepare({ integration: '<excludes><exclude>**/ExternalIT.java</exclude></excludes>', externalProfile: true });
        await compile(['ExternalIT']);
        await completeReports(report);
        assert.equal((await verify()).overall, 'passed');
    });
});

test('执行级 IT 显式排除不能冒充可由插件级 profile 重新启用的例外', async () => {
    await fixture(async ({ model }) => {
        for (const name of ['exclude', 'item']) {
            const excludes = `<excludes><${name}>**/ServiceIT.java</${name}></excludes>`;
            for (const integration of ['', excludes]) {
                await assert.rejects(model({ integration, integrationExecution: `<configuration>${excludes}</configuration>`,
                    externalProfile: true }), /Failsafe.*执行级/u);
            }
        }
    });
});

test('非 reactor Parent 的继承排除不能获得外部 IT 豁免', async () => {
    await fixture(async ({ root, model }) => {
        const excludes = '<excludes><exclude>**/ServiceIT.java</exclude></excludes>';
        await model({ integration: excludes, externalProfile: true });
        const parent = '<parent><groupId>fixture</groupId><artifactId>external-parent</artifactId><version>1</version><relativePath>parent/pom.xml</relativePath></parent>';
        const raw = (await readFile(path.join(root, 'pom.xml'), 'utf8'))
            .replace('<modelVersion>4.0.0</modelVersion>', `<modelVersion>4.0.0</modelVersion>${parent}`);
        await mkdir(path.join(root, 'parent'));
        await writeFile(path.join(root, 'parent/pom.xml'), `<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
            <groupId>fixture</groupId><artifactId>external-parent</artifactId><version>1</version><packaging>pom</packaging>
            <build><plugins><plugin><artifactId>maven-failsafe-plugin</artifactId><executions><execution>
                <goals><goal>integration-test</goal><goal>verify</goal></goals><configuration>${excludes}</configuration>
            </execution></executions></plugin></plugins></build></project>`);
        await writeFile(path.join(root, 'pom.xml'), raw);
        const effective = raw.replace('<goals><goal>integration-test</goal><goal>verify</goal></goals>',
            `<goals><goal>integration-test</goal><goal>verify</goal></goals><configuration>${excludes}</configuration>`);
        for (const pom of [effective, effective.replace(excludes, '<excludes combine.self="override"/>')]) {
            await assert.rejects(generateExpectedFromModel(root, 'inherited-excludes', pom), /Failsafe.*Parent/u);
        }
        const unfiltered = effective.replaceAll(excludes, '');
        await writeFile(path.join(root, 'pom.xml'), raw.replaceAll(excludes, ''));
        assert.deepEqual((await generateExpectedFromModel(root, 'ordinary-parent', unfiltered))
            .modules[0].tests.integration.sourceClasses, ['example.ServiceIT']);
    });
});

test('外部 IT 豁免必须核实 Parent 源码路径，不能只匹配坐标', async () => {
    await fixture(async ({ root, model }) => {
        const excludes = '<excludes><exclude>**/ServiceIT.java</exclude></excludes>';
        await model({ integration: excludes, externalProfile: true });
        const base = await readFile(path.join(root, 'pom.xml'), 'utf8');
        await mkdir(path.join(root, 'child'));
        await rename(path.join(root, 'src'), path.join(root, 'child/src'));
        const aggregator = base.replace('<version>1</version>', '<version>1</version><packaging>pom</packaging><modules><module>child</module></modules>');
        await writeFile(path.join(root, 'pom.xml'), aggregator);
        await mkdir(path.join(root, 'shadow'));
        const execution = '<goals><goal>integration-test</goal><goal>verify</goal></goals>';
        await writeFile(path.join(root, 'shadow/pom.xml'), base.replace(execution, `${execution}<configuration>${excludes}</configuration>`));
        const child = base.replaceAll(root, path.join(root, 'child')).replace('<artifactId>fixture</artifactId>', '<artifactId>child</artifactId>');
        const withParent = (relativePath) => child.replace('<modelVersion>4.0.0</modelVersion>', `<modelVersion>4.0.0</modelVersion>
            <parent><groupId>fixture</groupId><artifactId>fixture</artifactId><version>1</version>${relativePath}</parent>`);
        for (const relativePath of ['<relativePath>../shadow/pom.xml</relativePath>', '<relativePath/>']) {
            const raw = withParent(relativePath);
            await writeFile(path.join(root, 'child/pom.xml'), raw);
            const effective = raw.replace(execution, `${execution}<configuration>${excludes}</configuration>`)
                .replace(excludes, '<excludes combine.self="override"/>');
            await assert.rejects(generateExpectedFromModel(root, 'same-coordinate-parent', `<projects>${aggregator}${effective}</projects>`), /Failsafe.*Parent/u);
        }
        for (const relativePath of ['<relativePath>../pom.xml</relativePath>', '']) {
            const raw = withParent(relativePath);
            await writeFile(path.join(root, 'child/pom.xml'), raw);
            const effective = raw.replace(execution, `${execution}<configuration>${excludes}</configuration>`);
            const expected = await generateExpectedFromModel(root, 'reactor-parent', `<projects>${aggregator}${effective}</projects>`);
            assert.equal(expected.modules.find((module) => module.path === 'child').tests.integration.required, false);
        }
    });
});

test('非标准选择器、目录和缩小的覆盖率范围明确拒绝', async () => {
    await fixture(async ({ root, model }) => {
        await assert.rejects(model({ unitExcludes: ['**/*IT.java', '**/*IntegrationTest.java', '**/ATest.java', '**/BTests.java'] }), /Surefire/u);
        await assert.rejects(model({ integration: '<excludes><exclude>**/ServiceIT.java</exclude></excludes>' }), /显式 profile/u);
        await assert.rejects(model({ integration: '<excludes><exclude>**/ServiceIT.java</exclude></excludes>',
            externalProfile: '<skipITs>true</skipITs>' }), /显式 profile/u);
        await assert.rejects(model({ unitIncludes: ['ATest'] }), /标准测试命名/u);
        await assert.rejects(model({ unitIncludes: [] }), /标准测试命名/u);
        await assert.rejects(model({ testRoot: `${root}/custom-tests` }), /testSourceDirectory/u);
        const main = path.join(root, 'src/main/java/example');
        await mkdir(main, { recursive: true });
        await writeFile(path.join(main, 'Main.java'), 'package example; class Main {}');
        assert.equal((await model()).modules[0].coverage.required, true);
        for (const coverage of ['<excludes><exclude>**/Main.class</exclude></excludes>',
            '<includes><include>**/Other.class</include></includes>', '<skip>true</skip>', '<haltOnFailure>false</haltOnFailure>']) {
            await assert.rejects(model({ coverage }), /JaCoCo/u);
        }
    });
});

test('受控 Maven 输入阻断门禁覆盖，允许普通 JVM 内存和网络参数', async () => {
    await fixture(async ({ root }) => {
        await validateBuildInputs(root, { MAVEN_OPTS: '-Xmx512m -Dhttp.proxyHost=localhost' });
        await validateBuildInputs(root, { MAVEN_BASEDIR: root });
        await assert.rejects(validateBuildInputs(root, { MAVEN_BASEDIR: '/tmp/other-maven-root' }), /配置目录/u);
        await assert.rejects(validateBuildInputs(root, { MAVEN_OPTS: '@options' }), /参数文件/u);
        await assert.rejects(validateBuildInputs(root, { MAVEN_ARGS: '-Dtest=ATest' }), /MAVEN_ARGS/u);
        for (const name of ['MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']) {
            await assert.rejects(validateBuildInputs(root, { [name]: '-Dtest=ATest#case' }), /不得覆盖/u);
        }
        await mkdir(path.join(root, '.mvn'));
        await writeFile(path.join(root, '.mvn/jvm.config'), '-DskipTests=true');
        await assert.rejects(validateBuildInputs(root, {}), /jvm.config/u);
        await writeFile(path.join(root, '.mvn/jvm.config'), '-Xmx512m');
        await writeFile(path.join(root, '.mvn/maven.config'), '-pl module');
        await assert.rejects(validateBuildInputs(root, {}), /maven.config/u);
    });
});

test('普通 JVM 属性值中的参数文本不算门禁覆盖，独立覆盖参数仍拒绝', async () => {
    await fixture(async ({ root }) => {
        const values = ['-Dhttp.proxyPassword=-Dtest=sample', '-Dhttp.proxyPassword="-DskipTests=true"'];
        for (const name of ['MAVEN_OPTS', 'MAVEN_DEBUG_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']) {
            for (const input of values) {
                await validateBuildInputs(root, { [name]: input });
                await assert.rejects(validateBuildInputs(root, { [name]: `${input} -Dtest=ATest#case` }), /不得覆盖/u);
            }
        }
        await mkdir(path.join(root, '.mvn'));
        await writeFile(path.join(root, '.mvn/jvm.config'), values[0]);
        await validateBuildInputs(root, {});
        await writeFile(path.join(root, '.mvn/jvm.config'), `${values[0]}\n-DskipTests=true`);
        await assert.rejects(validateBuildInputs(root, {}), /不得覆盖/u);
    });
});

test('带引号属性值中的空白分隔门禁参数文本仍保守拒绝', async () => {
    await fixture(async ({ root }) => {
        const ordinary = '-Dhttp.proxyPassword="ordinary value"';
        const restricted = ['-Dhttp.proxyPassword="ordinary -Dtest=sample"',
            '-Dhttp.proxyPassword="ordinary @options"', '-Dhttp.proxyPassword="ordinary -XX:VMOptionsFile=options"'];
        for (const name of ['MAVEN_OPTS', 'MAVEN_DEBUG_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']) {
            await validateBuildInputs(root, { [name]: ordinary });
            for (const input of restricted) await assert.rejects(validateBuildInputs(root, { [name]: input }), /不得覆盖/u);
        }
        await mkdir(path.join(root, '.mvn'));
        await writeFile(path.join(root, '.mvn/jvm.config'), ordinary);
        await validateBuildInputs(root, {});
        for (const input of restricted) {
            await writeFile(path.join(root, '.mvn/jvm.config'), input);
            await assert.rejects(validateBuildInputs(root, {}), /jvm.config/u);
        }
    });
});

test('JVM 属性键引号和 Maven 调试参数不能绕过门禁输入检查', async () => {
    await fixture(async ({ root }) => {
        for (const name of ['MAVEN_OPTS', 'MAVEN_DEBUG_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']) {
            await validateBuildInputs(root, { [name]: '-Xmx512m -Dhttp.proxyHost="localhost"' });
            for (const input of ['-D"test"=ATest#case', "-Dte'st'=ATest#case", '-"D"test=ATest#case', '"@options"']) {
                await assert.rejects(validateBuildInputs(root, { [name]: input }), /不得覆盖/u);
            }
        }
        await assert.rejects(validateBuildInputs(root, { MAVEN_DEBUG_OPTS: '-Dtest=ATest#case' }), /不得覆盖/u);
        await mkdir(path.join(root, '.mvn'));
        await writeFile(path.join(root, '.mvn/jvm.config'), '-D"skipTests"=true');
        await assert.rejects(validateBuildInputs(root, {}), /jvm.config/u);
    });
});

test('JVM 参数文件入口不能间接注入门禁覆盖', async () => {
    await fixture(async ({ root }) => {
        for (const name of ['MAVEN_OPTS', 'MAVEN_DEBUG_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']) {
            for (const input of ['-XX:VMOptionsFile=/tmp/options', '-XX:VMOptionsFile="/tmp/options"', '"-XX:VMOptionsFile=/tmp/options"']) {
                await assert.rejects(validateBuildInputs(root, { [name]: input }), /参数文件/u);
            }
        }
        await mkdir(path.join(root, '.mvn'));
        await writeFile(path.join(root, '.mvn/jvm.config'), '-XX:VMOptionsFile=/tmp/options');
        await assert.rejects(validateBuildInputs(root, {}), /jvm.config/u);
    });
});

test('清理失败先使旧成功记录失效', async () => {
    await fixture(async ({ root, manifest, prepare, report, verify }) => {
        await prepare();
        await completeReports(report);
        assert.equal((await verify()).overall, 'passed');
        const expected = JSON.parse(await readFile(manifest, 'utf8'));
        expected.modules[0].artifacts.blocked = { path: 'target/blocked.xml', sha256: 'old' };
        await mkdir(path.join(root, 'target/blocked.xml'));
        await writeFile(manifest, JSON.stringify(expected));
        await assert.rejects(cleanExpected(root, manifest));
        const invalidated = JSON.parse(await readFile(manifest, 'utf8'));
        assert.equal(invalidated.buildStatus, undefined);
        assert.equal(invalidated.cleanedAt, undefined);
        assert.equal(invalidated.recordedAt, undefined);
    });
});

test('其他报告正常时缺一个类报告仍失败', async () => {
    await fixture(async ({ prepare, report, verify }) => {
        await prepare();
        await report('ATest');
        await report('ServiceIT', 'failsafe');
        const result = await verify();
        assert.ok(result.findings.some((item) => item.rule === 'test-report-missing' && item.message.includes('example.BTests')));
    });
});

test('源码对应编译类缺失不能靠有效 XML 通过', async () => {
    await fixture(async ({ classes, prepare, report, verify }) => {
        await prepare();
        await completeReports(report);
        await rm(path.join(classes, 'example/ATest.class'));
        assert.ok((await verify()).findings.some((item) => item.rule === 'test-report-compiled-missing'));
    });
});

test('异名顶层类及独立成员测试类明确拒绝', async () => {
    await fixture(async ({ source, compile, prepare, report, verify }) => {
        await writeFile(path.join(source, 'Helper.java'), 'package example; class HiddenTest {} class Helper { static class MemberTest {} }');
        await prepare();
        await compile(['Helper']);
        await completeReports(report);
        const unexpected = (await verify()).findings.filter((item) => item.rule === 'test-report-unsupported-class');
        assert.equal(unexpected.length, 2);
    });
});

test('嵌套类另行命中测试候选时，外层的有效 XML 不能掩盖源码清单缺口', async () => {
    await fixture(async ({ source, prepare, report, verify }) => {
        await writeFile(path.join(source, 'BTests.java'), 'package example; class BTests { class ScenarioTest {} }');
        await prepare();
        await report('ATest');
        await report('BTests', 'surefire', 'example.BTests$ScenarioTest');
        await report('ServiceIT', 'failsafe');
        const result = await verify();
        assert.equal(result.overall, 'failed');
        assert.ok(result.findings.some((item) => item.rule === 'test-report-unsupported-class'
            && item.message.includes('example.BTests$ScenarioTest')));
        assert.ok(!result.findings.some((item) => item.rule === 'test-report-missing' && item.message.includes('example.BTests')));
    });
});

test('构建失败和旧成功记录失效均不能通过', async () => {
    await fixture(async ({ root, manifest, prepare, report, verify }) => {
        await prepare();
        await completeReports(report);
        assert.equal((await verify({ buildSucceeded: false })).overall, 'failed');
        assert.equal((await verify()).overall, 'passed');
        await cleanExpected(root, manifest);
        await assert.rejects(verifyReports(root, manifest), /尚未记录/u);
        const expected = JSON.parse(await readFile(manifest, 'utf8'));
        assert.equal(expected.buildStatus, undefined);
        assert.equal(expected.recordedAt, undefined);
    });
});

test('成功登记后的报告替换、新增和丢失必须失败', async () => {
    await fixture(async ({ root, manifest, prepare, report, verify }) => {
        await prepare();
        await completeReports(report);
        assert.equal((await verify()).overall, 'passed');
        await report('ATest', 'surefire', 'example.OtherTest');
        await report('ExtraTest');
        await rm(path.join(root, 'target/failsafe-reports/TEST-example.ServiceIT.xml'));
        const findings = (await verifyReports(root, manifest)).findings;
        assert.ok(findings.some((item) => item.rule === 'test-report-stale'));
        assert.ok(findings.some((item) => item.rule === 'test-report-missing'));
        assert.ok(findings.some((item) => item.rule === 'test-report-unexpected-class'));
    });
});
