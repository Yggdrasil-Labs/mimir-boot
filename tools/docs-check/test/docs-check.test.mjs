import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cp, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { runDocsCheck } from '../check.mjs';

const repositoryRoot = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const fixturesRoot = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures');

async function withFixture(name, callback) {
    const root = await mkdtemp(path.join(tmpdir(), 'mimir-docs-check-test-'));
    await cp(path.join(fixturesRoot, name), root, { recursive: true });
    try {
        return await callback(root);
    } finally {
        await rm(root, { recursive: true, force: true });
    }
}

function check(report, id) {
    const result = report.checks.find((item) => item.id === id);
    assert.ok(result, `缺少检查结果：${id}`);
    return result;
}

function findings(report, rule) {
    return report.findings.filter((finding) => finding.rule === rule);
}

test('full 模式同时报告格式错误和断链，并继续执行独立检查', async () => {
    const report = await withFixture('format-and-links', (root) => runDocsCheck({
        root,
        mode: 'full',
        files: ['docs/broken.md'],
        reportPath: path.join(root, 'report.json'),
    }));

    assert.equal(report.schemaVersion, 1);
    assert.equal(report.exitCode, 1);
    assert.equal(report.overall, 'failed');
    assert.equal(check(report, 'markdown-format').status, 'failed');
    assert.equal(check(report, 'internal-links').status, 'failed');
    assert.ok(findings(report, 'markdown-format').length > 0);
    assert.ok(findings(report, 'internal-link').length > 0);
    assert.ok(check(report, 'navigation').status);
    assert.ok(check(report, 'technical-debt').status);
    assert.ok(check(report, 'maintenance-policy').status);
});

test('Markdown AST 正确处理中文标题、显式 anchor、引用式链接、图片和代码块', async () => {
    const report = await withFixture('valid-links', (root) => runDocsCheck({
        root,
        mode: 'full',
        files: ['README.md', 'docs/index.md', 'docs/guide.md', 'docs/reference.md'],
        reportPath: path.join(root, 'report.json'),
    }));

    assert.equal(report.exitCode, 0);
    assert.equal(report.overall, 'passed');
    assert.equal(check(report, 'internal-links').status, 'passed');
    assert.equal(findings(report, 'internal-link').length, 0);
    assert.equal(findings(report, 'anchor').length, 0);
    assert.equal(findings(report, 'duplicate-heading').length, 0);
});

test('中文重复标题、失效 anchor 和失效相对路径分别定位到规则与行号', async () => {
    const report = await withFixture('invalid-links', (root) => runDocsCheck({
        root,
        mode: 'full',
        files: ['docs/invalid.md', 'docs/target.md'],
        reportPath: path.join(root, 'report.json'),
    }));

    assert.equal(report.exitCode, 1);
    assert.equal(check(report, 'internal-links').status, 'failed');
    assert.ok(findings(report, 'duplicate-heading').some((finding) => finding.path === 'docs/invalid.md'));
    assert.ok(findings(report, 'anchor').some((finding) => finding.path === 'docs/invalid.md'));
    assert.ok(findings(report, 'internal-link').some((finding) => finding.path === 'docs/invalid.md'));
    for (const finding of report.findings) {
        assert.equal(typeof finding.line, 'number');
        assert.ok(finding.line > 0);
    }
});

test('技术债检查拒绝重复、错序、摘要不一致和退役编号复用', async () => {
    const report = await withFixture('debt-invalid', (root) => runDocsCheck({
        root,
        mode: 'full',
        files: ['README.md', 'docs/active/tech-debt-tracker.md'],
        reportPath: path.join(root, 'report.json'),
    }));

    assert.equal(report.exitCode, 1);
    assert.equal(check(report, 'technical-debt').status, 'failed');
    assert.ok(findings(report, 'debt-id-duplicate').length > 0);
    assert.ok(findings(report, 'debt-id-order').length > 0);
    assert.ok(findings(report, 'debt-id-retired-reuse').length > 0);
    assert.ok(findings(report, 'debt-summary-mismatch').length > 0);
    assert.ok(findings(report, 'root-readme-debt-reference').length > 0);
});

test('只有归档提示时整体通过，且浅层 checkout 读取同一注册表', async () => {
    const report = await withFixture('archive-warning', (root) => runDocsCheck({
        root,
        mode: 'full',
        files: ['README.md', 'docs/archive/v1/release.md'],
        reportPath: path.join(root, 'report.json'),
    }));

    assert.equal(report.exitCode, 0);
    assert.equal(report.overall, 'passed');
    assert.equal(check(report, 'maintenance-policy').status, 'passed');
    assert.ok(report.findings.some((finding) => finding.severity === 'warning'));
    assert.equal(check(report, 'technical-debt').status, 'not_applicable');
});

test('缺少受管工具返回 2，空 full 集合不能伪装成通过', async () => {
    const emptyRoot = await mkdtemp(path.join(tmpdir(), 'mimir-docs-check-empty-'));
    const isolatedTool = await mkdtemp(path.join(tmpdir(), 'mimir-docs-check-tool-'));
    try {
        await writeFile(path.join(emptyRoot, 'docs.md'), '# 文档\n\n内容\n', 'utf8');
        const result = spawnSync(process.execPath, [path.join(repositoryRoot, 'tools/docs-check/check.mjs'), '--root', emptyRoot, '--files', 'docs.md', '--mode', 'full', '--report', path.join(emptyRoot, 'report.json')], {
            cwd: isolatedTool,
            encoding: 'utf8',
            env: { ...process.env, MIMIR_DOCS_TOOL_ROOT: isolatedTool },
        });
        assert.equal(result.status, 2);

        await rm(path.join(emptyRoot, 'docs.md'), { force: true });
        const emptyReport = await runDocsCheck({
            root: emptyRoot,
            mode: 'full',
            files: [],
            reportPath: path.join(emptyRoot, 'empty-report.json'),
        });
        assert.notEqual(emptyReport.overall, 'passed');
        assert.equal(emptyReport.exitCode, 1);
    } finally {
        await rm(emptyRoot, { recursive: true, force: true });
        await rm(isolatedTool, { recursive: true, force: true });
    }
});

test('报告落盘失败返回 error/2，而不是返回空成功结果', async () => {
    await withFixture('valid-links', async (root) => {
        const report = await runDocsCheck({
            root,
            mode: 'full',
            files: ['README.md'],
            reportPath: path.join(root, 'README.md', 'report.json'),
        });
        assert.equal(report.overall, 'error');
        assert.equal(report.exitCode, 2);
        assert.equal(report.reportWriteError, true);
    });
});

test('报告 JSON 保留独立检查状态和可复核的命令字段', async () => {
    await withFixture('valid-links', async (root) => {
        const reportPath = path.join(root, 'report.json');
        const report = await runDocsCheck({
            root,
            mode: 'full',
            files: ['README.md'],
            reportPath,
        });
        const serialized = JSON.parse(await readFile(reportPath, 'utf8'));
        assert.deepEqual(serialized, report);
        for (const item of report.checks) {
            assert.equal(typeof item.id, 'string');
            assert.ok(Array.isArray(item.command));
            assert.ok(['passed', 'failed', 'error', 'not_run', 'not_applicable'].includes(item.status));
        }
    });
});
