import { createHash } from 'node:crypto';
import { lstat, readFile, readdir, realpath } from 'node:fs/promises';
import path from 'node:path';
import { DOMParser } from '@xmldom/xmldom';
import { matchesAny } from '../docs/policy.mjs';
import { inventoryJavaSources } from './java-source-inventory.mjs';

const NAMESPACE = 'http://maven.apache.org/POM/4.0.0';
const PHASES = ['validate', 'initialize', 'generate-sources', 'process-sources', 'generate-resources',
    'process-resources', 'compile', 'process-classes', 'generate-test-sources', 'process-test-sources',
    'generate-test-resources', 'process-test-resources', 'test-compile', 'process-test-classes', 'test',
    'prepare-package', 'package', 'pre-integration-test', 'integration-test', 'post-integration-test', 'verify'];
const DEFAULT_TESTS = {
    unit: ['**/Test*.java', '**/*Test.java', '**/*Tests.java', '**/*TestCase.java'],
    integration: ['**/IT*.java', '**/*IT.java', '**/*ITCase.java'],
};
const PLUGINS = {
    unit: ['org.apache.maven.plugins', 'maven-surefire-plugin'],
    integration: ['org.apache.maven.plugins', 'maven-failsafe-plugin'],
    coverage: ['org.jacoco', 'jacoco-maven-plugin'],
    format: ['com.diffplug.spotless', 'spotless-maven-plugin'],
};
const SOURCE_GENERATORS = new Map([
    ['org.codehaus.mojo:build-helper-maven-plugin', [['add-source', 'generate-sources'], ['add-test-source', 'generate-test-sources']]],
    ['org.codehaus.mojo:jaxb2-maven-plugin', [['xjc', 'generate-sources'], ['testXjc', 'generate-test-sources']]],
    ['org.jvnet.jaxb:jaxb-maven-plugin', [['generate', 'generate-sources']]],
    ['org.jvnet.jaxb2.maven2:maven-jaxb2-plugin', [['generate', 'generate-sources']]],
    ['org.apache.avro:avro-maven-plugin', [['schema', 'generate-sources'], ['protocol', 'generate-sources'], ['idl-protocol', 'generate-sources']]],
    ['org.antlr:antlr4-maven-plugin', [['antlr4', 'generate-sources']]],
    ['org.codehaus.mojo:antlr-maven-plugin', [['generate', 'generate-sources']]],
    ['org.xolstice.maven.plugins:protobuf-maven-plugin', [['compile', 'generate-sources'], ['compile-custom', 'generate-sources'], ['test-compile', 'generate-test-sources'], ['test-compile-custom', 'generate-test-sources']]],
    ['org.openapitools:openapi-generator-maven-plugin', [['generate', 'generate-sources']]],
    ['io.swagger:swagger-codegen-maven-plugin', [['generate', 'generate-sources']]],
    ['io.swagger.codegen.v3:swagger-codegen-maven-plugin', [['generate', 'generate-sources']]],
    ['org.bsc.maven:maven-processor-plugin', [['process', 'generate-sources'], ['process-test', 'generate-test-sources']]],
]);
const JAVA_PROCESSORS = new Set([
    'org.mapstruct:mapstruct-processor', 'com.querydsl:querydsl-apt',
    'org.hibernate:hibernate-jpamodelgen', 'org.hibernate.orm:hibernate-jpamodelgen',
    'com.google.auto.value:auto-value', 'org.immutables:value', 'com.google.dagger:dagger-compiler',
    'io.github.yggdrasil-labs:mimir-boot-starter-mybatis-processor',
]);
const JAVA_PROCESSOR_CLASSES = /^(?:org\.mapstruct\.ap\.|com\.querydsl\.apt\.|org\.hibernate\.jpamodelgen\.|org\.hibernate\.processor\.|com\.google\.auto\.value\.processor\.|org\.immutables\.processor\.|dagger\.internal\.codegen\.|com\.yggdrasil\.labs\.mybatis\.processor\.)/u;

function elements(node, ...names) {
    return names.reduce((nodes, name) => nodes.flatMap((parent) =>
        Array.from(parent?.childNodes || []).filter((child) => child.nodeType === 1
            && child.namespaceURI === NAMESPACE && child.localName === name)), [node]);
}

function value(node) {
    return node?.textContent.trim() || null;
}

function one(node, ...names) {
    const found = elements(node, ...names);
    if (found.length > 1) throw new Error(`构建模型包含重复元素：${names.join('/')}`);
    return found[0] || null;
}

function text(node, ...names) {
    return value(one(node, ...names));
}

function parseXml(source, label) {
    const document = new DOMParser({
        onError: (level, message) => { throw new Error(`${label} XML ${level}：${message}`); },
    }).parseFromString(source, 'application/xml');
    if (document.doctype) throw new Error(`${label} 不允许 DTD`);
    return document.documentElement;
}

function projectModels(source) {
    const root = parseXml(source, 'effective POM');
    if (root.localName === 'project' && root.namespaceURI === NAMESPACE) return [root];
    if (root.localName !== 'projects' || root.namespaceURI) throw new Error('effective POM 必须是 Maven projects 或 project XML');
    const projects = Array.from(root.childNodes).filter((child) => child.nodeType === 1);
    if (!projects.length || projects.some((project) => project.localName !== 'project' || project.namespaceURI !== NAMESPACE)) {
        throw new Error('effective POM 的 projects 中包含无效模块');
    }
    return projects;
}

function properties(model) {
    return new Map(elements(model, 'properties').flatMap((node) =>
        Array.from(node.childNodes).filter((child) => child.nodeType === 1).map((child) => [child.localName, value(child)])));
}

function resolved(input, label, props = null, trail = []) {
    if (!input) throw new Error(`${label} 不能为空`);
    const output = input.replace(/\$\{([^}]+)\}/gu, (expression, key) => {
        if (!props?.has(key) || trail.includes(key)) throw new Error(`${label} 包含未解析或循环属性：${expression}`);
        return resolved(props.get(key), label, props, [...trail, key]);
    });
    if (/\$\{|@\{/u.test(output)) throw new Error(`${label} 包含未解析属性：${output}`);
    return output;
}

function coordinate(model, props = properties(model), raw = false) {
    const group = text(model, 'groupId') || (raw ? text(model, 'parent', 'groupId') : null);
    const version = text(model, 'version') || (raw ? text(model, 'parent', 'version') : null);
    return [group, text(model, 'artifactId'), version].map((item) => resolved(item, 'POM 坐标', props)).join(':');
}

function relative(root, absolute) {
    const name = path.relative(root, absolute);
    if (name === '..' || name.startsWith(`..${path.sep}`) || path.isAbsolute(name)) throw new Error(`路径超出执行根目录：${absolute}`);
    return name.split(path.sep).join('/') || '.';
}

async function contained(root, absolute) {
    relative(root, absolute);
    let existing = absolute;
    for (;;) {
        try {
            relative(root, await realpath(existing));
            break;
        } catch (error) {
            if (error.code !== 'ENOENT') throw error;
            const parent = path.dirname(existing);
            if (parent === existing) throw error;
            existing = parent;
        }
    }
    return absolute;
}

async function configuredPath(root, directory, input, label) {
    return contained(root, path.resolve(directory, resolved(input, label)));
}

async function javaFiles(root, directory) {
    await contained(root, directory);
    let entries;
    try {
        entries = await readdir(directory, { withFileTypes: true });
    } catch (error) {
        if (error.code === 'ENOENT') return [];
        throw error;
    }
    const result = [];
    for (const entry of entries) {
        const file = path.join(directory, entry.name);
        if (entry.isSymbolicLink()) throw new Error(`Java 源目录不允许符号链接：${file}`);
        if (entry.isDirectory()) result.push(...await javaFiles(root, file));
        if (entry.isFile() && entry.name.endsWith('.java')) result.push(file);
    }
    return result.sort();
}

async function mapReactor(root, models) {
    const candidates = models.map((model) => ({ model, coordinate: coordinate(model), props: properties(model) }));
    if (new Set(candidates.map((candidate) => candidate.coordinate)).size !== candidates.length) throw new Error('effective POM 包含重复模块坐标');
    const mapped = [];
    const seen = new Set();
    async function visit(pom) {
        await contained(root, pom);
        const canonical = await realpath(pom);
        if (seen.has(canonical)) throw new Error(`Reactor 模块重复或存在循环：${relative(root, pom)}`);
        seen.add(canonical);
        const raw = parseXml(await readFile(pom, 'utf8'), relative(root, pom));
        if (raw.localName !== 'project' || raw.namespaceURI !== NAMESPACE) throw new Error(`不是 Maven POM：${pom}`);
        const matches = candidates.filter((candidate) => {
            try { return coordinate(raw, candidate.props, true) === candidate.coordinate; } catch { return false; }
        });
        if (matches.length !== 1) throw new Error(`源码 POM 无法唯一映射 effective 模块：${relative(root, pom)}`);
        const match = matches[0];
        if (mapped.some((item) => item.model === match.model)) throw new Error(`同一 effective 模块映射多个源码 POM：${match.coordinate}`);
        const module = { ...match, raw, directory: path.dirname(canonical), path: relative(root, path.dirname(canonical)) };
        mapped.push(module);
        for (const entry of elements(match.model, 'modules', 'module')) {
            const modulePath = await configuredPath(root, module.directory, value(entry), `${module.path} module`);
            const info = await lstat(modulePath);
            await visit(info.isDirectory() ? path.join(modulePath, 'pom.xml') : modulePath);
        }
    }
    await visit(path.join(root, 'pom.xml'));
    if (mapped.length !== models.length) throw new Error('effective POM 存在未被当前根项目 active modules 图包含的模块');
    return mapped;
}

function plugin(model, kind) {
    const [group, artifact] = PLUGINS[kind];
    const matches = elements(model, 'build', 'plugins', 'plugin').filter((item) =>
        (text(item, 'groupId') || 'org.apache.maven.plugins') === group && text(item, 'artifactId') === artifact);
    if (matches.length > 1) throw new Error(`实际 build/plugins 重复：${artifact}`);
    return matches[0] || null;
}

function config(pluginNode, execution = null) {
    const base = one(pluginNode, 'configuration');
    const override = one(execution, 'configuration');
    return (...names) => one(override, ...names) || one(base, ...names);
}

function boolean(input, label, fallback = false) {
    if (input === null) return fallback;
    const result = resolved(input, label);
    if (!['true', 'false'].includes(result)) throw new Error(`${label} 必须是 true/false，实际为 ${result}`);
    return result === 'true';
}

function flag(get, name, props, property = name, fallback = false) {
    return boolean(value(get(name)) ?? props.get(property) ?? null, name, fallback);
}

function patterns(node, label, defaults = []) {
    if (!node) return [...defaults];
    const children = Array.from(node.childNodes).filter((child) => child.nodeType === 1);
    const inputs = children.length ? children.map(value) : value(node) ? [value(node)] : [];
    return inputs.flatMap((input) => resolved(input, label).split(',')).map((input) => {
        let pattern = input.trim().replace(/\\/gu, '/').replace(/^\.\//u, '');
        if (!pattern || /[%#![\]{}]/u.test(pattern) || path.posix.isAbsolute(pattern)
            || pattern.split('/').includes('..')) throw new Error(`${label} 包含不支持的 Ant 选择器：${input}`);
        if (pattern.endsWith('/')) pattern += '**';
        return pattern;
    });
}

function executions(pluginNode, goal, defaultPhase) {
    return elements(pluginNode, 'executions', 'execution').flatMap((execution, index) => {
        const goals = elements(execution, 'goals', 'goal').map(value);
        const goalIndex = goals.indexOf(goal);
        if (goalIndex < 0) return [];
        const phase = text(execution, 'phase') || defaultPhase;
        return [{ execution, index, goalIndex, phase, get: config(pluginNode, execution) }];
    });
}

function active(execution) {
    return PHASES.includes(execution.phase);
}

function requireStaticSourceRoots(module) {
    let annotationProcessing = true;
    for (const node of elements(module.model, 'build', 'plugins', 'plugin')) {
        const artifact = text(node, 'artifactId');
        const key = `${text(node, 'groupId') || 'org.apache.maven.plugins'}:${artifact}`;
        const known = SOURCE_GENERATORS.get(key) || [];
        const generated = known.some(([goal, phase]) => executions(node, goal, phase).some(active));
        if (generated) {
            throw new Error(`${module.path} 尚不支持 ${artifact} 生命周期追加源码根或源码生成，无法可靠生成 Java 期望`);
        }
        if (key !== 'org.apache.maven.plugins:maven-compiler-plugin') continue;
        const invocations = ['compile', 'testCompile'].flatMap((goal) =>
            executions(node, goal, goal === 'compile' ? 'compile' : 'test-compile').filter(active));
        annotationProcessing = (invocations.length ? invocations.map((invocation) => invocation.get) : [config(node)])
            .some((get) => value(get('proc')) !== 'none');
        for (const get of [config(node), ...invocations.map((invocation) => invocation.get)]) {
            for (const name of ['compileSourceRoots', 'testCompileSourceRoots']) {
                if (get(name)) throw new Error(`${module.path} 尚不支持 compiler ${name} 额外源码根`);
            }
            if (value(get('proc')) === 'none') continue;
            for (const processor of elements(get('annotationProcessorPaths'), 'path')) {
                const coordinate = `${text(processor, 'groupId')}:${text(processor, 'artifactId')}`;
                if (JAVA_PROCESSORS.has(coordinate)) throw new Error(`${module.path} 尚不支持注解处理器 ${coordinate} 的源码生成`);
            }
            for (const processor of elements(get('annotationProcessors'), 'annotationProcessor')) {
                if (JAVA_PROCESSOR_CLASSES.test(value(processor) || '')) {
                    throw new Error(`${module.path} 尚不支持显式注解处理器 ${value(processor)} 的源码生成`);
                }
            }
            const args = [value(get('compilerArgument')), ...elements(get('compilerArgs'), 'arg').map(value),
                ...Array.from(get('compilerArguments')?.childNodes || []).filter((item) => item.nodeType === 1)
                    .map((item) => `-${item.localName} ${value(item) || ''}`)].filter(Boolean).join(' ');
            if (/(?:^|\s)-(?:s(?:\s|=|$)|proc:only(?:\s|$))/u.test(args)
                || args.split(/[\s,=]+/u).some((item) => JAVA_PROCESSOR_CLASSES.test(item))) {
                throw new Error(`${module.path} 尚不支持 compiler 参数指定的注解处理器或生成源码根`);
            }
        }
    }
    for (const dependency of elements(module.model, 'dependencies', 'dependency')) {
        const coordinate = `${text(dependency, 'groupId')}:${text(dependency, 'artifactId')}`;
        if (annotationProcessing && JAVA_PROCESSORS.has(coordinate)) throw new Error(`${module.path} 尚不支持依赖注解处理器 ${coordinate} 的源码生成`);
    }
}

function requireOneGoal(pluginNode, goal, phase, label) {
    const found = executions(pluginNode, goal, phase).filter(active);
    if (found.length !== 1) throw new Error(`${label} 必须唯一绑定 ${goal}，实际 ${found.length} 次`);
    return found[0];
}

function samePatterns(first, second) {
    return JSON.stringify([...new Set(first)].sort()) === JSON.stringify([...new Set(second)].sort());
}

function sourceMatch(file, includes, excludes) {
    const javaName = file.replace(/\.class$/u, '.java');
    return matchesAny(javaName, includes.map((pattern) => pattern.replace(/\.class$/u, '.java')))
        && !matchesAny(javaName, excludes.map((pattern) => pattern.replace(/\.class$/u, '.java')));
}

async function testExpectation(root, module, kind, declarations, buildDirectory) {
    const node = plugin(module.model, kind);
    const goal = kind === 'unit' ? 'test' : 'integration-test';
    const defaultPhase = kind === 'unit' ? 'test' : 'integration-test';
    const bound = executions(node, goal, defaultPhase).filter(active);
    const all = bound.length ? bound : [{ get: config(node), phase: defaultPhase }];
    const choices = [];
    for (const invocation of all) {
        const get = invocation.get;
        for (const name of ['includesFile', 'excludesFile', 'test', 'suiteXmlFiles', 'dependenciesToScan',
            'groups', 'excludedGroups', 'includeJUnit5Engines', 'excludeJUnit5Engines',
            'junitArtifactName', 'testNGArtifactName', 'provider', 'providers', 'systemPropertiesFile']) {
            if (get(name)) throw new Error(`${module.path} ${kind} 尚不支持 ${name}，无法可靠生成测试期望`);
        }
        if (one(get('properties'), 'configurationParameters')) {
            throw new Error(`${module.path} ${kind} 尚不支持 JUnit configurationParameters，无法可靠生成测试期望`);
        }
        const systemProperties = [
            ...Array.from(get('systemPropertyVariables')?.childNodes || []).filter((item) => item.nodeType === 1)
                .map((item) => item.localName),
            ...elements(get('systemProperties'), 'property').map((item) => text(item, 'name')),
        ];
        if (systemProperties.some((name) => name?.startsWith('junit.'))) {
            throw new Error(`${module.path} ${kind} 尚不支持 junit.* JVM 系统属性，无法可靠生成测试期望`);
        }
        const includes = patterns(get('includes'), `${module.path} ${kind} includes`, DEFAULT_TESTS[kind]);
        const excludes = patterns(get('excludes'), `${module.path} ${kind} excludes`, ['**/*$*']);
        const selected = declarations.filter(({ binaryName }) => sourceMatch(`${binaryName.replaceAll('.', '/')}.java`, includes, excludes));
        const skipped = flag(get, 'skip', module.props, 'maven.test.skip')
            || flag(get, 'skipTests', module.props)
            || kind === 'integration' && flag(get, 'skipITs', module.props);
        if (selected.length && skipped) throw new Error(`${module.path} ${kind} 有匹配测试源码却配置了 skip`);
        if (selected.length && (flag(get, 'disableXmlReport', module.props)
            || boolean(text(get('statelessTestsetReporter'), 'disable'), 'statelessTestsetReporter.disable'))) {
            throw new Error(`${module.path} ${kind} 不允许关闭 XML 报告`);
        }
        const reportsDirectory = relative(root, await configuredPath(root, module.directory,
            value(get('reportsDirectory')) || path.join(buildDirectory, kind === 'unit' ? 'surefire-reports' : 'failsafe-reports'), 'reportsDirectory'));
        const testClassesDirectory = relative(root, await configuredPath(root, module.directory,
            value(get('testClassesDirectory')) || text(module.model, 'build', 'testOutputDirectory'), 'testClassesDirectory'));
        choices.push({ includes, excludes, selected, reportsDirectory, testClassesDirectory });
    }
    const first = choices[0];
    if (choices.some((choice) => choice.reportsDirectory !== first.reportsDirectory || choice.testClassesDirectory !== first.testClassesDirectory
        || !samePatterns(choice.includes, first.includes) || !samePatterns(choice.excludes, first.excludes))) {
        throw new Error(`${module.path} ${kind} 多个执行的选择器、测试类目录或报告目录不同，当前期望模型无法可靠表示`);
    }
    if (first.selected.length && !bound.length) throw new Error(`${module.path} ${kind} 缺少实际生命周期插件执行`);
    if (first.selected.length && bound.some((item) => item.phase !== defaultPhase)) {
        throw new Error(`${module.path} ${kind} 测试必须绑定 ${defaultPhase} 阶段，保证覆盖率生成前完成`);
    }
    if (first.selected.length && kind === 'integration') {
        const verification = executions(node, 'verify', 'verify').filter(active);
        if (!verification.length || verification.some((item) => item.phase !== 'verify')) throw new Error(`${module.path} Failsafe verify 未绑定 verify 阶段`);
        for (const item of verification) {
            if (flag(item.get, 'skip', module.props, 'maven.test.skip') || flag(item.get, 'skipTests', module.props)
                || flag(item.get, 'skipITs', module.props)) throw new Error(`${module.path} Failsafe verify 配置了 skip`);
        }
    }
    return {
        required: first.selected.length > 0,
        reason: first.selected.length ? null : `有效 ${kind === 'unit' ? 'Surefire' : 'Failsafe'} 选择器未匹配 Java 测试源码`,
        includes: first.includes, excludes: first.excludes, reportsDirectory: first.reportsDirectory, testClassesDirectory: first.testClassesDirectory,
        sourceFiles: [...new Set(first.selected.map(({ sourceFile }) => relative(root, sourceFile)))].sort(),
        sourceClasses: [...new Set(first.selected.map(({ binaryName }) => binaryName))].sort(), reports: [],
    };
}

function ratioMinimum(input, label) {
    function decimal(source, percentage = false) {
        const match = /^([+-]?)(\d+(?:\.\d*)?|\.\d+)(?:[eE]([+-]?\d+))?$/u.exec(source);
        if (!match) throw new Error(`${label} minimum 必须是十进制数或百分比`);
        const fraction = match[2].split('.')[1] || '';
        const coefficient = match[2].replace('.', '').replace(/^0+/u, '');
        if (!coefficient) return '0e0';
        const digits = coefficient.replace(/0+$/u, '');
        const exponent = BigInt(match[3] || '0') - BigInt(fraction.length) - (percentage ? 2n : 0n)
            + BigInt(coefficient.length - digits.length);
        return `${match[1] === '-' ? '-' : ''}${digits}e${exponent}`;
    }
    const percentage = input.endsWith('%');
    const normalized = decimal(percentage ? input.slice(0, -1) : input, percentage);
    const minimum = Number(normalized);
    if (!Number.isFinite(minimum) || minimum < 0 || minimum > 1) throw new Error(`${label} minimum 必须在 0 到 1 之间`);
    // 数值模型不能舍入后放行超范围值或降低继承阈值。
    if (decimal(String(minimum)) !== normalized) throw new Error(`${label} minimum 精度无法可靠表示为验收阈值`);
    return minimum;
}

function thresholds(get, label) {
    const limits = new Map();
    const rules = elements(get('rules'), 'rule');
    if (!rules.length) throw new Error(`${label} 缺少覆盖率规则`);
    for (const rule of rules) {
        if ((text(rule, 'element') || 'BUNDLE') !== 'BUNDLE') continue;
        if (one(rule, 'includes') || one(rule, 'excludes')) throw new Error(`${label} BUNDLE 规则不允许缩小检查对象`);
        for (const limit of elements(rule, 'limits', 'limit')) {
            const counter = text(limit, 'counter') || 'INSTRUCTION';
            if (!['INSTRUCTION', 'BRANCH'].includes(counter) || (text(limit, 'value') || 'COVEREDRATIO') !== 'COVEREDRATIO') continue;
            const input = resolved(text(limit, 'minimum'), `${label} ${counter} minimum`).trim();
            const minimum = ratioMinimum(input, `${label} ${counter}`);
            limits.set(counter, Math.max(limits.get(counter) || 0, minimum));
        }
    }
    if (!limits.has('INSTRUCTION') || !limits.has('BRANCH')) throw new Error(`${label} 缺少 INSTRUCTION/BRANCH COVEREDRATIO 最低阈值`);
    return { instructionMinimum: limits.get('INSTRUCTION'), branchMinimum: limits.get('BRANCH') };
}

function coverageBaseline(module, reactor) {
    let current = module;
    let baseline = null;
    const seen = new Set();
    while (current) {
        if (seen.has(current.coordinate)) throw new Error('Reactor parent 继承关系存在循环');
        seen.add(current.coordinate);
        const node = plugin(current.model, 'coverage');
        if (node && config(node)('rules')) baseline = { node, module: current };
        const parent = one(current.model, 'parent');
        const parentCoordinate = parent ? ['groupId', 'artifactId', 'version'].map((name) => resolved(text(parent, name), 'parent 坐标')).join(':') : null;
        current = reactor.find((item) => item.coordinate === parentCoordinate);
    }
    if (!baseline) throw new Error(`${module.path} 未找到 Reactor 继承的覆盖率基线`);
    return baseline;
}

function requireAgentConsumption(module, agent, tests) {
    const propertyName = resolved(value(agent.get('propertyName')) || module.props.get('jacoco.propertyName') || 'argLine', 'JaCoCo propertyName', module.props);
    for (const kind of ['unit', 'integration']) {
        if (!tests[kind].required) continue;
        const goal = kind === 'unit' ? 'test' : 'integration-test';
        for (const invocation of executions(plugin(module.model, kind), goal, kind === 'unit' ? 'test' : 'integration-test').filter(active)) {
            const get = invocation.get;
            const forkCount = value(get('forkCount')) || module.props.get('forkCount') || '1';
            if (!/^\d+(?:\.\d+)?C?$/u.test(forkCount) || Number(forkCount.replace(/C$/u, '')) <= 0
                || value(get('forkMode')) === 'never') throw new Error(`${module.path} ${kind} 必须 fork 测试 JVM 才能采集 JaCoCo`);
            const argLine = value(get('argLine'));
            if (argLine === null && propertyName === 'argLine') continue;
            if (argLine && /['"\\]/u.test(argLine)) {
                throw new Error(`${module.path} ${kind} 尚不支持带引号或转义的 argLine，无法确认 agent 是独立 JVM 参数`);
            }
            if (!argLine || ![`@{${propertyName}}`, `\${${propertyName}}`].some((reference) => argLine.split(/\s+/u).includes(reference))) {
                throw new Error(`${module.path} ${kind} argLine 未引用 JaCoCo agent 属性 ${propertyName}`);
            }
        }
    }
}

async function coverageExpectation(root, module, reactor, files, buildDirectory, tests) {
    if (!files.length) return {
        coverage: { required: false, reason: '没有 Java 主源码，无可度量业务字节码', includes: [], excludes: [], instructionMinimum: null, branchMinimum: null },
        artifacts: {},
    };
    const node = plugin(module.model, 'coverage');
    if (!node) throw new Error(`${module.path} 缺少实际 JaCoCo build/plugins`);
    const agent = requireOneGoal(node, 'prepare-agent', 'initialize', module.path);
    const report = requireOneGoal(node, 'report', 'verify', module.path);
    const check = requireOneGoal(node, 'check', 'verify', module.path);
    if (PHASES.indexOf(agent.phase) >= PHASES.indexOf('test')) throw new Error(`${module.path} JaCoCo agent 必须在测试前准备`);
    if (report.phase !== 'verify' || check.phase !== 'verify'
        || report.index > check.index || report.index === check.index && report.goalIndex >= check.goalIndex) {
        throw new Error(`${module.path} JaCoCo report 必须在 verify 阶段先于 check`);
    }
    for (const invocation of [agent, report, check]) {
        if (flag(invocation.get, 'skip', module.props, 'jacoco.skip')) throw new Error(`${module.path} JaCoCo 配置了 skip`);
    }
    if (!flag(agent.get, 'append', module.props, 'jacoco.append', true)) throw new Error(`${module.path} JaCoCo append 必须为 true，保留单元与集成测试数据`);
    requireAgentConsumption(module, agent, tests);
    const destination = await configuredPath(root, module.directory, value(agent.get('destFile'))
        || path.join(buildDirectory, 'jacoco.exec'), 'JaCoCo destFile');
    for (const invocation of [report, check]) {
        const data = await configuredPath(root, module.directory, value(invocation.get('dataFile'))
            || path.join(buildDirectory, 'jacoco.exec'), 'JaCoCo dataFile');
        if (data !== destination) throw new Error(`${module.path} JaCoCo destFile/dataFile 不一致`);
    }
    const baseline = coverageBaseline(module, reactor);
    const baselineGet = config(baseline.node);
    const minimums = thresholds(check.get, module.path);
    const baselineMinimums = thresholds(baselineGet, baseline.module.path);
    if (minimums.instructionMinimum < Math.max(0.60, baselineMinimums.instructionMinimum)
        || minimums.branchMinimum < Math.max(0.50, baselineMinimums.branchMinimum)) throw new Error(`${module.path} JaCoCo 阈值低于仓库或继承基线`);
    const includes = patterns(report.get('includes'), `${module.path} JaCoCo includes`, ['**/*']);
    const excludes = patterns(report.get('excludes'), `${module.path} JaCoCo excludes`);
    const baselineIncludes = patterns(baselineGet('includes'), 'JaCoCo 基线 includes', ['**/*']);
    const baselineExcludes = patterns(baselineGet('excludes'), 'JaCoCo 基线 excludes');
    if (!samePatterns(includes, baselineIncludes) || excludes.some((exclude) => !baselineExcludes.includes(exclude))) {
        throw new Error(`${module.path} JaCoCo 扫描范围低于继承基线`);
    }
    for (const invocation of [agent, check]) {
        const otherIncludes = patterns(invocation.get('includes'), `${module.path} JaCoCo includes`, ['**/*']);
        const otherExcludes = patterns(invocation.get('excludes'), `${module.path} JaCoCo excludes`);
        if (!samePatterns(includes, otherIncludes) || !samePatterns(excludes, otherExcludes)) throw new Error(`${module.path} JaCoCo agent/report/check 扫描范围不一致`);
    }
    const formats = patterns(report.get('formats'), `${module.path} JaCoCo formats`, ['HTML', 'XML', 'CSV']);
    if (!formats.includes('XML')) throw new Error(`${module.path} JaCoCo report 必须生成 XML`);
    if (!flag(check.get, 'haltOnFailure', module.props, 'jacoco.haltOnFailure', true)) throw new Error(`${module.path} JaCoCo check 必须在失败时阻断`);
    const siteDirectory = text(module.model, 'reporting', 'outputDirectory') || path.join(buildDirectory, 'site');
    const outputDirectory = await configuredPath(root, module.directory,
        value(report.get('outputDirectory')) || path.join(siteDirectory, 'jacoco'), 'JaCoCo outputDirectory');
    return {
        coverage: { required: true, reason: null, includes, excludes, ...minimums },
        artifacts: {
            jacocoXml: { path: relative(root, path.join(outputDirectory, 'jacoco.xml')), sha256: null },
            jacocoExec: { path: relative(root, destination), sha256: null },
        },
    };
}

function formatExpectation(module, files) {
    const node = plugin(module.model, 'format');
    const bound = executions(node, 'check', 'verify').filter(active);
    if (!files.length) {
        const get = bound[0]?.get || config(node);
        return { required: false, reason: '没有 Java 源码，无 Java 格式检查对象',
            includes: patterns(one(get('java'), 'includes'), `${module.path} Spotless includes`),
            excludes: patterns(one(get('java'), 'excludes'), `${module.path} Spotless excludes`),
            skip: flag(get, 'skip', module.props, 'spotless.skip')
                || flag(get, 'checkSkip', module.props, 'spotless.check.skip')
                || boolean(module.props.get('spotless.java.skip') || null, 'spotless.java.skip')
                || boolean(text(get('java'), 'skip'), 'Spotless java.skip') };
    }
    if (!bound.length) throw new Error(`${module.path} 缺少实际 Spotless check 生命周期执行`);
    const choices = bound.map((invocation) => {
        const get = invocation.get;
        if (flag(get, 'skip', module.props, 'spotless.skip') || flag(get, 'checkSkip', module.props, 'spotless.check.skip')
            || boolean(module.props.get('spotless.java.skip') || null, 'spotless.java.skip') || boolean(text(get('java'), 'skip'), 'Spotless java.skip')) {
            throw new Error(`${module.path} Spotless Java 配置了 skip`);
        }
        if (value(get('filePatterns')) || module.props.get('spotlessFiles')
            || value(get('ratchetFrom')) || text(get('java'), 'ratchetFrom')) {
            throw new Error(`${module.path} Spotless 尚不支持 filePatterns/spotlessFiles/ratchetFrom 二次范围限制`);
        }
        const includes = patterns(one(get('java'), 'includes'), `${module.path} Spotless includes`, ['src/main/java/**/*.java', 'src/test/java/**/*.java']);
        const excludes = patterns(one(get('java'), 'excludes'), `${module.path} Spotless excludes`);
        if (!get('java')) throw new Error(`${module.path} Spotless 未配置 Java formatter`);
        const missing = files.filter((file) => !sourceMatch(path.relative(module.directory, file).split(path.sep).join('/'), includes, excludes));
        if (missing.length) throw new Error(`${module.path} Spotless 没有覆盖全部 Java 主源码/测试源码：${path.relative(module.directory, missing[0])}`);
        return { includes, excludes };
    });
    const first = choices[0];
    if (choices.some((choice) => !samePatterns(choice.includes, first.includes) || !samePatterns(choice.excludes, first.excludes))) {
        throw new Error(`${module.path} Spotless 多个 check 的 Java 扫描范围不同`);
    }
    return { required: true, reason: null, ...first, skip: false };
}

/** 基于本轮实际 effective POM 和源码生成验收期望，不从构建产物推导适用范围。 */
export async function generateExpectedFromModel(root, runId, effectivePomSource) {
    if (!root || !path.isAbsolute(root)) throw new Error('executionRoot 必须是绝对路径');
    if (!runId || typeof runId !== 'string') throw new Error('runId 必须是非空字符串');
    if (!effectivePomSource || typeof effectivePomSource !== 'string') throw new Error('必须提供本轮 -Pci effective POM XML 字符串');
    root = await realpath(root);
    const startedAt = new Date().toISOString();
    const reactor = await mapReactor(root, projectModels(effectivePomSource));
    const modules = [];
    const sourceModels = [];
    for (const module of reactor) {
        const packaging = text(module.model, 'packaging') || 'jar';
        if (!['pom', 'jar'].includes(packaging)) throw new Error(`${module.path} 尚不支持 packaging=${packaging} 的 Java 生命周期`);
        requireStaticSourceRoots(module);
        const sourceDirectory = await configuredPath(root, module.directory, text(module.model, 'build', 'sourceDirectory'), 'sourceDirectory');
        const testDirectory = await configuredPath(root, module.directory, text(module.model, 'build', 'testSourceDirectory'), 'testSourceDirectory');
        const buildDirectory = await configuredPath(root, module.directory, text(module.model, 'build', 'directory'), 'build.directory');
        const main = await javaFiles(root, sourceDirectory);
        const tests = await javaFiles(root, testDirectory);
        sourceModels.push({ ...module, packaging, buildDirectory, main, tests });
    }
    const declarations = await inventoryJavaSources(root, sourceModels.flatMap((module) => module.tests));
    for (const module of sourceModels) {
        const { packaging, buildDirectory, main, tests } = module;
        const javaSources = main.length + tests.length > 0;
        if (packaging === 'pom' && javaSources) throw new Error(`${module.path} 纯 POM 模块意外包含 Java 主源码或测试源码`);
        const exemption = packaging === 'pom' ? '纯 POM 模块，不产生 Java 编译和测试产物' : javaSources ? null : '有效源码目录没有 Java 主源码或测试源码';
        const testFiles = new Set(tests);
        const testDeclarations = declarations.filter(({ sourceFile }) => testFiles.has(sourceFile));
        const unit = await testExpectation(root, module, 'unit', testDeclarations, buildDirectory);
        const integration = await testExpectation(root, module, 'integration', testDeclarations, buildDirectory);
        const { coverage, artifacts } = await coverageExpectation(root, module, reactor, main, buildDirectory, { unit, integration });
        modules.push({ path: module.path, packaging, javaSources, exemption, tests: { unit, integration },
            coverage, format: formatExpectation(module, [...main, ...tests]), artifacts });
    }
    const artifactPaths = modules.flatMap((module) => Object.values(module.artifacts).map((artifact) => artifact.path));
    if (new Set(artifactPaths).size !== artifactPaths.length) throw new Error('不同 Reactor 模块的 JaCoCo 产物路径发生重叠');
    const reportDirectories = modules.flatMap((module) => Object.values(module.tests)
        .filter((test) => test.required).map((test) => test.reportsDirectory));
    if (new Set(reportDirectories).size !== reportDirectories.length) throw new Error('必需测试阶段的报告目录发生重叠');
    return { schemaVersion: 1, runId, executionRoot: root, startedAt,
        buildModel: { profile: 'ci', sha256: createHash('sha256').update(effectivePomSource).digest('hex') }, modules };
}
