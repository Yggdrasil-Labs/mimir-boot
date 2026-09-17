import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, realpath, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DOMParser } from '@xmldom/xmldom';

const NAMESPACE = 'http://maven.apache.org/POM/4.0.0';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const GPG_GROUP = 'org.apache.maven.plugins';
const GPG_ARTIFACT = 'maven-gpg-plugin';

class BuildModelToolError extends Error {
    constructor(message) {
        super(message);
        this.name = 'BuildModelToolError';
        this.exitCode = 2;
    }
}

function elements(node, ...names) {
    return names.reduce((nodes, name) => nodes.flatMap((parent) =>
        Array.from(parent.childNodes).filter((child) =>
            child.nodeType === 1 && child.namespaceURI === NAMESPACE && child.localName === name)), [node]);
}

function text(node) {
    return node?.textContent.trim() || null;
}

export function parsePom(source) {
    const document = new DOMParser({
        onError: (level, message) => { throw new Error(`XML ${level}: ${message}`); },
    }).parseFromString(source, 'application/xml');
    const root = document.documentElement;
    if (document.doctype || root?.namespaceURI !== NAMESPACE || root.localName !== 'project') {
        throw new Error('必须是无 DTD 的 Maven project XML');
    }
    return root;
}

export function requireGpgConfiguration(model, expectedSkip, label) {
    const values = elements(model, 'properties', 'gpg.skip').map(text);
    if (values.length !== 1 || values[0] !== expectedSkip) {
        throw new Error(`${label}: gpg.skip 必须唯一且为 ${expectedSkip}，实际为 ${JSON.stringify(values)}`);
    }
    // 包括 profile 内的 build/plugins，与原检查范围保持一致；不以 pluginManagement 代替实际插件。
    const builds = Array.from(model.getElementsByTagNameNS(NAMESPACE, 'build'));
    const plugins = builds.flatMap((build) => elements(build, 'plugins', 'plugin')).filter((plugin) =>
        (text(elements(plugin, 'groupId')[0]) || GPG_GROUP) === GPG_GROUP
        && text(elements(plugin, 'artifactId')[0]) === GPG_ARTIFACT);
    if (!plugins.length) throw new Error(`${label}: effective POM 缺少 ${GPG_ARTIFACT}`);
    for (const plugin of plugins) {
        const skip = text(elements(plugin, 'configuration', 'skip')[0]);
        if (skip !== expectedSkip) throw new Error(`${label}: GPG plugin 顶层 skip=${skip}，期望 ${expectedSkip}`);
        const executions = elements(plugin, 'executions', 'execution');
        if (!executions.length) throw new Error(`${label}: GPG plugin 缺少 execution`);
        for (const execution of executions) {
            const executionSkip = text(elements(execution, 'configuration', 'skip')[0]);
            if (executionSkip !== expectedSkip) {
                const id = text(elements(execution, 'id')[0]) || '<unnamed>';
                throw new Error(`${label}: GPG execution ${id} skip=${executionSkip}，期望 ${expectedSkip}`);
            }
        }
    }
}

export async function recursiveReactorPoms(pom, seen = new Set()) {
    const absolute = await realpath(pom);
    if (seen.has(absolute)) throw new Error(`Reactor POM 枚举出现重复：${absolute}`);
    seen.add(absolute);
    const model = parsePom(await readFile(absolute, 'utf8'));
    const result = [absolute];
    for (const module of elements(model, 'modules', 'module')) {
        const name = text(module);
        if (!name) throw new Error(`Reactor module 不能为空：${absolute}`);
        result.push(...await recursiveReactorPoms(path.resolve(path.dirname(absolute), name, 'pom.xml'), seen));
    }
    return result;
}

function command(executable, args, label) {
    let result;
    try {
        result = spawnSync(executable, args, { cwd: ROOT, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
    } catch (error) {
        throw new BuildModelToolError(`${label}：${error.message}`);
    }
    const output = `${result.stdout || ''}\n${result.stderr || ''}`;
    if (result.error || result.status !== 0) {
        throw new BuildModelToolError(`${label}：${result.error?.message || `退出码 ${result.status}，信号 ${result.signal}`}\n${output}`);
    }
    return output;
}

export async function verifyBuildModel() {
    const java = command('java', ['-version'], '无法检查 Java 版本');
    if (!/version "17[."+-]/u.test(java)) throw new BuildModelToolError(`需要 Java 17，实际输出：${java.trim()}`);
    const poms = await recursiveReactorPoms(path.join(ROOT, 'pom.xml'));
    const cacheSetting = process.env.MIMIR_BUILD_MODEL_M2;
    const cache = cacheSetting ? path.resolve(cacheSetting) : null;
    const cacheArgs = cache ? [`-Dmaven.repo.local=${cache}`] : [];
    if (cache) await mkdir(cache, { recursive: true });
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'mimir-effective-poms-'));
    try {
        for (const relative of ['pom.xml', 'mimir-boot-bom/pom.xml', 'mimir-boot-parent/pom.xml']) {
            command(path.join(ROOT, 'mvnw'), ['-B', '-N', '-f', path.join(ROOT, relative),
                'install', '-Dgpg.skip=true', ...cacheArgs], `无法安装 ${relative} 元数据`);
        }
        for (const [index, pom] of poms.entries()) {
            for (const [profile, expected] of [[null, 'true'], ['maven-central', 'false']]) {
                const label = `${path.relative(ROOT, pom)} [${profile || 'default'}]`;
                const output = path.join(temporary, `${index}-${profile || 'default'}.xml`);
                command(path.join(ROOT, 'mvnw'), ['-B', '-N', '-f', pom, ...cacheArgs,
                    ...(profile ? [`-P${profile}`] : []), 'help:effective-pom', `-Doutput=${output}`],
                `无法生成 ${label} 的 effective POM`);
                requireGpgConfiguration(parsePom(await readFile(output, 'utf8')), expected, label);
                process.stdout.write(`已验证 ${label}\n`);
            }
        }
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
    process.stdout.write(`已验证 ${poms.length} 个 Reactor POM 的 default/maven-central GPG 模型\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        if (process.argv.length > 2) {
            if (process.argv.length !== 3 || process.argv[2] !== '--help') throw new BuildModelToolError('只支持 --help 参数');
            process.stdout.write('用法：bash scripts/engineering.sh build-model\n'
                + '要求 Java 17；可用 MIMIR_BUILD_MODEL_M2 指定 Maven 缓存；会安装本地 Reactor 元数据。\n');
        } else {
            await verifyBuildModel();
        }
    } catch (error) {
        process.stderr.write(`构建模型验证失败：${error.message}\n`);
        process.exitCode = Number.isInteger(error?.exitCode) ? error.exitCode : 1;
    }
}
