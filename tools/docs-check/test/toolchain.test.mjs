import { test } from 'node:test';
import assert from 'node:assert/strict';
import { access, readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { runDocsCheck } from '../check.mjs';

const repositoryRoot = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const toolsRoot = path.join(repositoryRoot, 'tools', 'docs-check');

async function exists(filePath) {
    try {
        await access(filePath);
        return true;
    } catch {
        return false;
    }
}

test('文档工具链具有受管入口和锁文件', async () => {
    assert.equal(await exists(path.join(toolsRoot, 'check.mjs')), true);
    assert.equal(await exists(path.join(toolsRoot, 'bootstrap.mjs')), true);
    assert.equal(await exists(path.join(toolsRoot, 'package-lock.json')), true);

    const packageJson = JSON.parse(await readFile(path.join(toolsRoot, 'package.json'), 'utf8'));
    assert.equal(packageJson.devDependencies['markdownlint-cli2'], '0.23.2');
    assert.equal(packageJson.scripts.bootstrap, 'node bootstrap.mjs');

    const lockJson = JSON.parse(await readFile(path.join(toolsRoot, 'package-lock.json'), 'utf8'));
    assert.equal(lockJson.lockfileVersion, 3);
    assert.equal(lockJson.packages[''].devDependencies['markdownlint-cli2'], '0.23.2');
});

test('根 POM 的 docs-check 插件不继承到子模块', async () => {
    const pom = await readFile(path.join(repositoryRoot, 'pom.xml'), 'utf8');
    assert.match(pom, /<id>docs-check<\/id>/);
    assert.match(pom, /<artifactId>frontend-maven-plugin<\/artifactId>[\s\S]*?<inherited>false<\/inherited>/);
    assert.match(pom, /<id>docs-bootstrap<\/id>[\s\S]*?<goal>npm<\/goal>/);
    assert.match(pom, /<id>docs-format-check<\/id>[\s\S]*?<goal>npm<\/goal>/);
    assert.doesNotMatch(pom, /<goal>node<\/goal>/);
});

test('自检报告记录受管运行时和依赖缓存位置', async () => {
    const reportPath = path.join(toolsRoot, 'target', 'toolchain-test-report.json');
    const report = await runDocsCheck({
        root: repositoryRoot,
        mode: 'full',
        reportPath,
    });

    assert.equal(report.toolPaths.managedNode, path.join(toolsRoot, '.maven-node', 'node', 'node'));
    assert.equal(report.toolPaths.dependencyCache, path.join(toolsRoot, 'node_modules'));
});

test('文档工具入口拒绝越界路径和缺失的受管运行时', () => {
    const validEntry = spawnSync('bash', ['scripts/docs-tool.sh', 'bootstrap.mjs'], {
        cwd: repositoryRoot,
        encoding: 'utf8',
    });
    assert.equal(validEntry.status, 0);
    assert.match(validEntry.stdout, /文档工具就绪/);

    const invalidEntry = spawnSync('bash', ['scripts/docs-tool.sh', '../pom.xml'], {
        cwd: repositoryRoot,
        encoding: 'utf8',
    });
    assert.equal(invalidEntry.status, 2);
    assert.match(invalidEntry.stderr, /tools\/docs-check 内/);

    const missingRuntime = spawnSync('bash', ['scripts/docs-tool.sh', 'tools/docs-check/check.mjs'], {
        cwd: repositoryRoot,
        encoding: 'utf8',
        env: { ...process.env, MIMIR_DOCS_NODE: '/tmp/mimir-docs-node-missing' },
    });
    assert.equal(missingRuntime.status, 2);
    assert.match(missingRuntime.stderr, /未找到 Maven 管理的 Node 运行时/);
});
