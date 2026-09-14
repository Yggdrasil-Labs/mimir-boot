import { access, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = path.dirname(fileURLToPath(import.meta.url));

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
const expectedVersion = packageJson.devDependencies?.['markdownlint-cli2'];
const lockedVersion = lockJson.packages?.['']?.devDependencies?.['markdownlint-cli2'];

if (lockJson.lockfileVersion !== 3) {
    throw new Error(`依赖锁文件版本必须为 3，实际为 ${lockJson.lockfileVersion}`);
}
if (!expectedVersion || expectedVersion !== lockedVersion) {
    throw new Error(`markdownlint-cli2 清单版本 ${expectedVersion ?? '缺失'} 与锁文件版本 ${lockedVersion ?? '缺失'} 不一致`);
}

const installedPackagePath = path.join(toolRoot, 'node_modules', 'markdownlint-cli2', 'package.json');
const installedPackage = await readJson(installedPackagePath, '已安装 markdownlint-cli2 清单');
if (installedPackage.version !== expectedVersion) {
    throw new Error(`markdownlint-cli2 已安装版本 ${installedPackage.version} 与锁定版本 ${expectedVersion} 不一致`);
}
await assertFile(
    path.join(toolRoot, 'node_modules', 'markdownlint-cli2', 'markdownlint-cli2-bin.mjs'),
    'markdownlint-cli2 执行入口',
);

process.stdout.write(`文档工具就绪：node=${process.version} markdownlint-cli2=${installedPackage.version}\n`);
