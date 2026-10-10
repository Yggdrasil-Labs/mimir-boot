import { createHash } from 'node:crypto';
import { lstat, readFile, readdir, realpath } from 'node:fs/promises';
import path from 'node:path';
import { DOMParser } from '@xmldom/xmldom';
import { matchesAny } from '../docs/policy.mjs';

const NAMESPACE = 'http://maven.apache.org/POM/4.0.0';
const PHASES = ['validate', 'initialize', 'generate-sources', 'process-sources', 'generate-resources',
    'process-resources', 'compile', 'process-classes', 'generate-test-sources', 'process-test-sources',
    'generate-test-resources', 'process-test-resources', 'test-compile', 'process-test-classes', 'test',
    'prepare-package', 'package', 'pre-integration-test', 'integration-test', 'post-integration-test', 'verify'];
const DEFAULT_TESTS = {
    unit: ['**/*Test.java', '**/*Tests.java'],
    integration: ['**/*IT.java', '**/*IntegrationTest.java'],
};
const PLUGINS = {
    unit: ['org.apache.maven.plugins', 'maven-surefire-plugin'],
    integration: ['org.apache.maven.plugins', 'maven-failsafe-plugin'],
    coverage: ['org.jacoco', 'jacoco-maven-plugin'],
    format: ['com.diffplug.spotless', 'spotless-maven-plugin'],
};
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
        const module = { ...match, raw, pom: canonical, directory: path.dirname(canonical), path: relative(root, path.dirname(canonical)) };
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
    const inputs = Array.from(node?.childNodes || []).filter((child) => child.nodeType === 1).map(value);
    if (!inputs.length) return [...defaults];
    return inputs.map((input) => resolved(input, label));
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

function requireStandardBuild(module) {
    for (const [name, suffix] of Object.entries({ sourceDirectory: 'src/main/java', testSourceDirectory: 'src/test/java',
        directory: 'target', outputDirectory: 'target/classes', testOutputDirectory: 'target/test-classes' })) {
        if (path.resolve(module.directory, text(module.model, 'build', name) || suffix) !== path.join(module.directory, suffix)) {
            throw new Error(`${module.path} 不支持自定义 build.${name}`);
        }
    }
    // 有效 POM 会把插件配置复制到执行节点；只按源码声明拒绝无法由插件级 profile 清空的排除。
    for (const model of [module.raw, ...elements(module.raw, 'profiles', 'profile')]) {
        const nodes = [...elements(model, 'build', 'plugins', 'plugin'), ...elements(model, 'build', 'pluginManagement', 'plugins', 'plugin')];
        for (const node of nodes) {
            if ((text(node, 'groupId') || 'org.apache.maven.plugins') !== PLUGINS.integration[0]
                || text(node, 'artifactId') !== PLUGINS.integration[1]) continue;
            if (elements(node, 'executions', 'execution', 'configuration', 'excludes')
                .some((excludes) => patterns(excludes, `${module.path} Failsafe 执行级 excludes`)
                    .some((item) => item !== '**/*$*'))) {
                throw new Error(`${module.path} Failsafe 不支持执行级显式 IT 排除，只允许插件级外部测试例外`);
            }
        }
    }
    for (const node of elements(module.model, 'build', 'plugins', 'plugin')) {
        if (text(node, 'artifactId') === 'build-helper-maven-plugin'
            && ['add-source', 'add-test-source'].some((goal) => executions(node, goal, 'generate-sources').some(active))) {
            throw new Error(`${module.path} 不支持动态追加源码根`);
        }
        if (text(node, 'artifactId') !== 'maven-compiler-plugin') continue;
        const gets = [config(node), ...['compile', 'testCompile'].flatMap((goal) =>
            executions(node, goal, goal === 'compile' ? 'compile' : 'test-compile').filter(active).map((item) => item.get))];
        for (const get of gets) {
            for (const name of ['includes', 'excludes', 'testIncludes', 'testExcludes', 'compileSourceRoots', 'testCompileSourceRoots']) {
                if (get(name)) throw new Error(`${module.path} 不支持 compiler ${name} 改变源码范围`);
            }
            if (flag(get, 'skipMain', module.props, 'maven.main.skip') || (value(get('proc')) ?? module.props.get('maven.compiler.proc')) === 'only'
                || /(?:^|\s)-proc:only(?:\s|$)/u.test([value(get('compilerArgument')),
                    ...elements(get('compilerArgs'), 'arg').map(value)].filter(Boolean).join(' '))) {
                throw new Error(`${module.path} 不支持 compiler skipMain/proc:only 跳过编译`);
            }
            for (const name of ['compilerArgument', 'compilerArguments', 'testCompilerArgument', 'testCompilerArguments']) {
                if (get(name)) throw new Error(`${module.path} 不支持 compiler ${name} 自定义编译参数`);
            }
            if (elements(get('compilerArgs'), 'arg').some((item) => value(item) !== '-parameters')) {
                throw new Error(`${module.path} compilerArgs 仅支持现有 -parameters 参数`);
            }
        }
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

/** 仅匹配仓库已约束的文件规则，不解释 Maven 的通用类名/方法选择语法。 */
export function selectTestClasses(names, { includes, excludes }) {
    return names.filter((name) => {
        const file = `${name.replaceAll('.', '/')}.java`;
        return matchesAny(file, includes) && !matchesAny(file, excludes);
    });
}

function hasExternalTestProfile(module) {
    return elements(module.raw, 'profiles', 'profile').some((profile) => {
        const node = plugin(profile, 'integration');
        const configuration = one(node, 'configuration');
        const excludes = one(configuration, 'excludes');
        const only = (parent, names) => Array.from(parent?.childNodes || []).every((child) => child.nodeType !== 1 || names.includes(child.localName));
        return text(profile, 'id') && only(profile, ['id', 'build']) && only(one(profile, 'build'), ['plugins'])
            && elements(profile, 'build', 'plugins', 'plugin').length === 1 && only(node, ['groupId', 'artifactId', 'configuration'])
            && only(configuration, ['excludes']) && excludes?.getAttribute('combine.self') === 'override'
            && !value(excludes) && !Array.from(excludes.childNodes).some((child) => child.nodeType === 1);
    });
}

async function testExpectation(root, module, kind, declarations, buildDirectory, reactor) {
    const node = plugin(module.model, kind);
    const phase = kind === 'unit' ? 'test' : 'integration-test';
    const bound = executions(node, phase, phase).filter(active);
    const gets = bound.length ? bound.map((item) => item.get) : [config(node)];
    for (const name of ['test', 'it.test', 'groups', 'excludedGroups', 'argLine',
        ...['surefire', 'failsafe'].flatMap((prefix) => ['includesFile', 'excludesFile', 'includeJUnit5Engines',
            'excludeJUnit5Engines', 'systemPropertiesFile'].map((name) => `${prefix}.${name}`))]) {
        if (module.props.get(name)) throw new Error(`${module.path} 不支持测试属性 ${name} 改变执行范围或 JVM 参数`);
    }
    const choices = gets.map((get) => {
        const includes = patterns(get('includes'), `${module.path} ${kind} includes`);
        if (declarations.length && !samePatterns(includes, DEFAULT_TESTS[kind])) {
            throw new Error(`${module.path} ${kind} includes 必须使用 Parent 的标准测试命名规则`);
        }
        const excludes = patterns(get('excludes'), `${module.path} ${kind} excludes`, ['**/*$*']);
        if (kind === 'unit' && declarations.length && !samePatterns(excludes, DEFAULT_TESTS.integration)) {
            throw new Error(`${module.path} Surefire excludes 必须仅排除标准集成测试命名规则`);
        }
        if (kind === 'integration' && excludes.some((item) => item !== '**/*$*')) {
            if (excludes.some((item) => !/^\*\*\/[\p{L}_][\p{L}\p{N}_]*(?:IT|IntegrationTest)\.java$/u.test(item)) || !hasExternalTestProfile(module)) {
                throw new Error(`${module.path} Failsafe 具体类排除必须有显式 profile 重新启用，不支持其他范围缩小`);
            }
        }
        for (const name of ['includesFile', 'excludesFile', 'test', 'suiteXmlFiles', 'dependenciesToScan', 'groups',
            'excludedGroups', 'includeJUnit5Engines', 'excludeJUnit5Engines', 'junitArtifactName', 'testNGArtifactName', 'provider', 'providers', 'systemPropertiesFile']) {
            if (get(name)) throw new Error(`${module.path} ${kind} 不支持 ${name} 改变测试范围`);
        }
        const providerProperties = get('properties');
        if (value(providerProperties) || Array.from(providerProperties?.childNodes || []).some((item) => item.nodeType === 1)) {
            throw new Error(`${module.path} ${kind} 不支持非空 provider properties`);
        }
        if (Array.from(get('systemPropertyVariables')?.childNodes || []).some((item) => item.nodeType === 1 && item.localName.startsWith('junit.'))
            || elements(get('systemProperties'), 'property').some((item) => text(item, 'name')?.startsWith('junit.'))) {
            throw new Error(`${module.path} ${kind} 不支持 JUnit 发现参数`);
        }
        if (flag(get, 'skip', module.props, 'maven.test.skip') || flag(get, 'skipTests', module.props)
            || flag(get, 'skipITs', module.props)) throw new Error(`${module.path} ${kind} 配置了 skip`);
        if (flag(get, 'disableXmlReport', module.props) || boolean(text(get('statelessTestsetReporter'), 'disable'), 'XML reporter.disable')) {
            throw new Error(`${module.path} ${kind} 不允许关闭 XML 报告`);
        }
        const reportsDirectory = path.join(buildDirectory, kind === 'unit' ? 'surefire-reports' : 'failsafe-reports');
        const testClassesDirectory = path.join(buildDirectory, 'test-classes');
        for (const [name, expected] of Object.entries({ reportsDirectory, testClassesDirectory })) {
            if (value(get(name)) && path.resolve(module.directory, resolved(value(get(name)), name)) !== expected) {
                throw new Error(`${module.path} ${kind} 不支持自定义 ${name}`);
            }
        }
        return { includes: includes.length ? includes : DEFAULT_TESTS[kind], excludes,
            reportsDirectory: relative(root, reportsDirectory), testClassesDirectory: relative(root, testClassesDirectory) };
    });
    if (choices.some((choice) => JSON.stringify(choice) !== JSON.stringify(choices[0]))) {
        throw new Error(`${module.path} ${kind} 不支持不同配置的重复执行`);
    }
    if (kind === 'integration' && choices[0].excludes.some((item) => item !== '**/*$*')) {
        // 排除例外必须能核实整条继承链；只定位已映射的源码 POM，不读取外部 Parent 或缓存。
        const seen = new Set();
        for (let current = module; current;) {
            if (seen.has(current.coordinate)) throw new Error(`${module.path} Failsafe Parent 继承关系存在循环`);
            seen.add(current.coordinate);
            const parent = one(current.model, 'parent');
            if (!parent) break;
            const parentCoordinate = ['groupId', 'artifactId', 'version']
                .map((name) => resolved(text(parent, name), 'Parent 坐标')).join(':');
            const parentModule = reactor.find((item) => item.coordinate === parentCoordinate);
            if (!parentModule) throw new Error(`${module.path} Failsafe 具体 IT 排除无法核实非 Reactor Parent 的来源：${parentCoordinate}`);
            const relativePath = one(current.raw, 'parent', 'relativePath');
            if (relativePath && !value(relativePath)) throw new Error(`${module.path} Failsafe Parent 的空 relativePath 无法核实源码来源`);
            const source = resolved(value(relativePath) || '../pom.xml', 'Parent relativePath', current.props);
            const actual = await realpath(await configuredPath(root, current.directory, source, 'Parent relativePath'));
            if (actual !== parentModule.pom) throw new Error(`${module.path} Failsafe Parent 来源与 Reactor 源码 POM 不一致：${parentCoordinate}`);
            current = parentModule;
        }
    }
    const selected = new Set(selectTestClasses(declarations.map((item) => item.binaryName), choices[0]));
    if (selected.size && (bound.length !== 1 || bound[0].phase !== phase)) throw new Error(`${module.path} ${kind} 必须唯一绑定 ${phase}`);
    if (selected.size && kind === 'integration') {
        const verification = requireOneGoal(node, 'verify', 'verify', module.path);
        if (verification.phase !== 'verify' || flag(verification.get, 'skip', module.props, 'maven.test.skip')
            || flag(verification.get, 'skipTests', module.props) || flag(verification.get, 'skipITs', module.props)) {
            throw new Error(`${module.path} Failsafe verify 不得跳过或改变阶段`);
        }
    }
    return { required: selected.size > 0, reason: selected.size ? null : '标准源码清单在本轮配置中没有此类测试', ...choices[0],
        sourceFiles: declarations.filter((item) => selected.has(item.binaryName)).map((item) => relative(root, item.sourceFile)).sort(),
        sourceClasses: [...selected].sort(), reports: [] };
}

function ratioMinimum(input, label) {
    if (!/^(?:0(?:\.\d{1,6})?|1(?:\.0{1,6})?)$/u.test(input)) {
        throw new Error(`${label} minimum 仅支持 0 到 1、至多六位小数的比率`);
    }
    return Number(input);
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
    const propertyName = value(agent.get('propertyName')) || module.props.get('jacoco.propertyName') || 'argLine';
    if (propertyName !== 'argLine') throw new Error(`${module.path} JaCoCo 只支持默认 argLine 属性`);
    for (const kind of ['unit', 'integration']) {
        if (!tests[kind].required) continue;
        const phase = kind === 'unit' ? 'test' : 'integration-test';
        for (const invocation of executions(plugin(module.model, kind), phase, phase).filter(active)) {
            const get = invocation.get;
            const forkCount = value(get('forkCount')) || module.props.get('forkCount') || '1';
            if (!/^\d+(?:\.\d+)?C?$/u.test(forkCount) || Number(forkCount.replace(/C$/u, '')) <= 0 || value(get('forkMode')) === 'never') {
                throw new Error(`${module.path} ${kind} 必须 fork 测试 JVM 才能采集 JaCoCo`);
            }
            if (get('argLine')) throw new Error(`${module.path} ${kind} 不支持自定义 argLine，使用默认 JaCoCo agent 属性`);
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
    const allowedExcludes = ['**/entity/**', '**/dto/**', '**/vo/**', '**/Application.class'];
    if (!samePatterns(includes, ['**/*']) || !samePatterns(includes, baselineIncludes)
        || excludes.some((exclude) => !allowedExcludes.includes(exclude) || !baselineExcludes.includes(exclude))) {
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

async function formatExpectation(module, files) {
    if (!files.length) return { required: false, reason: '没有 Java 源码', includes: [], excludes: [], skip: false };
    const bound = executions(plugin(module.model, 'format'), 'check', 'validate').filter(active);
    if (!bound.length) throw new Error(`${module.path} 缺少 Spotless check 执行`);
    const choices = await Promise.all(bound.map(async (invocation) => {
        const get = invocation.get;
        if (invocation.phase !== 'validate' || !get('java')) throw new Error(`${module.path} Spotless Java check 必须绑定 validate`);
        const baseDir = value(get('baseDir'));
        if (baseDir) {
            const directory = resolved(baseDir, 'Spotless baseDir');
            const actual = await realpath(path.isAbsolute(directory) ? directory : `${module.directory}${path.sep}${directory}`);
            if (actual !== module.directory) throw new Error(`${module.path} Spotless baseDir 必须指向模块目录`);
        }
        if (flag(get, 'skip', module.props, 'spotless.skip') || flag(get, 'checkSkip', module.props, 'spotless.check.skip')
            || boolean(module.props.get('spotless.java.skip') || null, 'spotless.java.skip') || boolean(text(get('java'), 'skip'), 'Spotless java.skip')) {
            throw new Error(`${module.path} Spotless Java 配置了 skip`);
        }
        if (value(get('filePatterns')) || module.props.get('spotlessFiles') || value(get('ratchetFrom')) || text(get('java'), 'ratchetFrom')) {
            throw new Error(`${module.path} Spotless 不支持二次范围限制`);
        }
        const includes = patterns(one(get('java'), 'includes'), 'Spotless includes');
        const excludes = patterns(one(get('java'), 'excludes'), 'Spotless excludes');
        const allowed = ['**/target/**/*.java', '**/generated/**/*.java', '**/generated-sources/**/*.java'];
        if (!samePatterns(includes, ['src/main/java/**/*.java', 'src/test/java/**/*.java']) || excludes.some((item) => !allowed.includes(item))) {
            throw new Error(`${module.path} Spotless 必须覆盖标准主源码和测试源码，仅排除生成目录`);
        }
        return { includes, excludes };
    }));
    if (choices.some((choice) => !samePatterns(choice.includes, choices[0].includes) || !samePatterns(choice.excludes, choices[0].excludes))) {
        throw new Error(`${module.path} Spotless 不支持不同范围的重复执行`);
    }
    return { required: true, reason: null, ...choices[0], skip: false };
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
        requireStandardBuild(module);
        const sourceDirectory = await configuredPath(root, module.directory, text(module.model, 'build', 'sourceDirectory'), 'sourceDirectory');
        const testDirectory = await configuredPath(root, module.directory, text(module.model, 'build', 'testSourceDirectory'), 'testSourceDirectory');
        const buildDirectory = await configuredPath(root, module.directory, text(module.model, 'build', 'directory'), 'build.directory');
        const main = await javaFiles(root, sourceDirectory);
        const tests = await javaFiles(root, testDirectory);
        sourceModels.push({ ...module, packaging, buildDirectory, main, tests });
    }
    const declarations = sourceModels.flatMap((module) => module.tests.map((sourceFile) => ({ sourceFile,
        binaryName: path.relative(path.join(module.directory, 'src/test/java'), sourceFile).slice(0, -5).split(path.sep).join('.') })));
    for (const module of sourceModels) {
        const { packaging, buildDirectory, main, tests } = module;
        const javaSources = main.length + tests.length > 0;
        if (packaging === 'pom' && javaSources) throw new Error(`${module.path} 纯 POM 模块意外包含 Java 主源码或测试源码`);
        const exemption = packaging === 'pom' ? '纯 POM 模块，不产生 Java 编译和测试产物' : javaSources ? null : '有效源码目录没有 Java 主源码或测试源码';
        const testFiles = new Set(tests);
        const testDeclarations = declarations.filter(({ sourceFile }) => testFiles.has(sourceFile));
        const unit = await testExpectation(root, module, 'unit', testDeclarations, buildDirectory, reactor);
        const integration = await testExpectation(root, module, 'integration', testDeclarations, buildDirectory, reactor);
        const { coverage, artifacts } = await coverageExpectation(root, module, reactor, main, buildDirectory, { unit, integration });
        modules.push({ path: module.path, packaging, javaSources, exemption, tests: { unit, integration },
            coverage, format: await formatExpectation(module, [...main, ...tests]), artifacts });
    }
    const artifactPaths = modules.flatMap((module) => Object.values(module.artifacts).map((artifact) => artifact.path));
    if (new Set(artifactPaths).size !== artifactPaths.length) throw new Error('不同 Reactor 模块的 JaCoCo 产物路径发生重叠');
    const reportDirectories = modules.flatMap((module) => Object.values(module.tests)
        .filter((test) => test.required).map((test) => test.reportsDirectory));
    if (new Set(reportDirectories).size !== reportDirectories.length) throw new Error('必需测试阶段的报告目录发生重叠');
    return { schemaVersion: 1, runId, executionRoot: root, startedAt,
        buildModel: { profile: 'ci', sha256: createHash('sha256').update(effectivePomSource).digest('hex') }, modules };
}
