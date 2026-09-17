import { access, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

async function readJson(filePath, label) {
    try {
        return JSON.parse(await readFile(filePath, 'utf8'));
    } catch (error) {
        throw new Error(`无法读取${label}：${filePath}（${error.message}）`);
    }
}

async function assertFile(filePath, label) {
    try {
        await access(filePath);
    } catch {
        throw new Error(`未找到${label}：${filePath}`);
    }
}

const packageJson = await readJson(path.join(toolRoot, 'package.json'), '工具清单');
const lockJson = await readJson(path.join(toolRoot, 'package-lock.json'), '依赖锁文件');
const pom = await readFile(path.join(toolRoot, '../../pom.xml'), 'utf8');
const nodeVersion = pom.match(/<nodeVersion>([^<]+)<\/nodeVersion>/u)?.[1];
const npmVersion = pom.match(/<npmVersion>([^<]+)<\/npmVersion>/u)?.[1];
if (process.version !== nodeVersion) throw new Error(`Node 版本与根 POM 不匹配：${process.version} / ${nodeVersion}`);
const npm = await readJson(path.join(toolRoot, '.maven-node/node/node_modules/npm/package.json'), '受管 npm 清单');
if (npm.version !== npmVersion) throw new Error('npm 版本与根 POM 不匹配');
if (lockJson.lockfileVersion !== 3) throw new Error('依赖锁文件版本必须为 3');
for (const [name, version] of Object.entries(packageJson.devDependencies || {})) {
    const installed = await readJson(path.join(toolRoot, 'node_modules', name, 'package.json'), `已安装 ${name} 清单`);
    if (lockJson.packages?.['']?.devDependencies?.[name] !== version || installed.version !== version) {
        throw new Error(`${name} 的清单、锁文件和安装版本不一致`);
    }
}
await assertFile(path.join(toolRoot, 'node_modules/markdownlint-cli2/markdownlint-cli2-bin.mjs'), 'markdownlint-cli2 入口');
process.stdout.write(`工程工具就绪：node=${process.version} npm=${npm.version} markdownlint-cli2=${packageJson.devDependencies['markdownlint-cli2']}\n`);
