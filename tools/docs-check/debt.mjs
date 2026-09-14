import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseMarkdown } from './links.mjs';
import { normalizePath } from './policy.mjs';
import { createCheck } from './results.mjs';

const moduleRoot = path.dirname(fileURLToPath(import.meta.url));

function lineNumber(lines, predicate) {
    const index = lines.findIndex(predicate);
    return index < 0 ? 1 : index + 1;
}

function cleanMarkdown(value) {
    return value
        .replace(/\[([^\]]+)\]\([^)]*\)/gu, '$1')
        .replace(/[`*_~]/gu, '')
        .replace(/\s+/gu, ' ')
        .trim();
}

function tokenText(token) {
    if (token.type === 'inline' && token.children) {
        return token.children
            .filter((child) => child.type !== 'code_inline' && child.type !== 'html_inline')
            .map((child) => child.content || '')
            .join('');
    }
    return token.content || '';
}

function parseDebtTable(source) {
    const lines = source.split(/\r?\n/u);
    const parsed = parseMarkdown(source);
    const detailsHeading = parsed.tokens.find((token) => token.type === 'heading_open' && token.tag === 'h2' && parsed.tokens[parsed.tokens.indexOf(token) + 1]?.content === '债务明细');
    const detailsStart = detailsHeading?.map?.[0] ?? Number.POSITIVE_INFINITY;
    let activeTable = null;
    const tables = [];
    for (let index = 0; index < parsed.tokens.length; index += 1) {
        const token = parsed.tokens[index];
        if (token.type === 'table_open') {
            activeTable = { rows: [], start: token.map?.[0] ?? 0 };
            tables.push(activeTable);
            continue;
        }
        if (token.type === 'table_close') {
            activeTable = null;
            continue;
        }
        if (activeTable && token.type === 'tr_open') {
            const row = { cells: [], line: (token.map?.[0] ?? activeTable.start) + 1 };
            for (index += 1; index < parsed.tokens.length && parsed.tokens[index].type !== 'tr_close'; index += 1) {
                if (parsed.tokens[index].type === 'inline') {
                    row.cells.push(tokenText(parsed.tokens[index]));
                }
            }
            activeTable.rows.push(row);
        }
    }
    const table = tables.find((candidate) => candidate.start < detailsStart && candidate.rows[0]?.cells[0] === '编号');
    const rows = [];
    if (table) {
        for (const row of table.rows.slice(1)) {
            const idMatch = row.cells[0]?.match(/\b(TD-\d+)\b/u);
            if (idMatch) {
                rows.push({ id: idMatch[1], summary: cleanMarkdown(row.cells[1] || ''), line: row.line });
            }
        }
    }
    return { lines, rows, header: table ? table.start : -1, parsed };
}

function parseDetails(parsedOrLines) {
    const parsed = Array.isArray(parsedOrLines) ? parseMarkdown(parsedOrLines.join('\n')) : parsedOrLines;
    const details = new Map();
    let inDetails = false;
    for (let index = 0; index < parsed.tokens.length; index += 1) {
        const token = parsed.tokens[index];
        if (token.type === 'heading_open' && token.tag === 'h2') {
            const inline = parsed.tokens[index + 1];
            inDetails = tokenText(inline) === '债务明细';
            continue;
        }
        if (!inDetails || token.type !== 'heading_open' || token.tag !== 'h3') {
            continue;
        }
        const inline = parsed.tokens[index + 1];
        const value = tokenText(inline);
        const match = value.match(/^(TD-\d+)\s*[:：]\s*(.+?)\s*$/u);
        if (match && !details.has(match[1])) {
            details.set(match[1], { title: cleanMarkdown(match[2]), line: (token.map?.[0] ?? 0) + 1 });
        }
    }
    return details;
}

async function loadRegistry(root) {
    const candidates = [
        path.join(root, 'tools', 'docs-check', 'debt-id-registry.json'),
        path.join(moduleRoot, 'debt-id-registry.json'),
    ];
    for (const candidate of candidates) {
        try {
            const registry = JSON.parse(await readFile(candidate, 'utf8'));
            if (registry.schemaVersion !== 1 || !Number.isInteger(registry.highWatermark) || !Array.isArray(registry.entries)) {
                throw new Error('必须包含 schemaVersion=1、整数 highWatermark 和 entries 数组');
            }
            for (const entry of registry.entries) {
                if (!/^TD-\d{3}$/u.test(entry.id) || !['active', 'retired'].includes(entry.state)) {
                    throw new Error(`注册表条目格式错误：${JSON.stringify(entry)}`);
                }
                if (Number(entry.id.slice(3)) > registry.highWatermark) {
                    throw new Error(`注册表编号 ${entry.id} 超出 highWatermark ${registry.highWatermark}`);
                }
            }
            return registry;
        } catch (error) {
            if (error.code !== 'ENOENT') {
                throw new Error(`无法读取技术债编号注册表 ${candidate}：${error.message}`);
            }
        }
    }
    throw new Error('缺少 debt-id-registry.json：当前快照和检查器均未提供持久编号注册表');
}

async function checkRootReadme(root, policy, findings) {
    const readmePath = normalizePath(policy.rootReadmePath || 'README.md');
    try {
        const source = await readFile(path.join(root, readmePath), 'utf8');
        const lines = source.split(/\r?\n/u);
        const parsed = parseMarkdown(source);
        const visible = [];
        for (const token of parsed.tokens) {
            if (['fence', 'code_block'].includes(token.type)) {
                continue;
            }
            if (token.type === 'inline') {
                for (const child of token.children || []) {
                    if (child.type !== 'code_inline') {
                        visible.push(child.content || '');
                    }
                }
            } else if (!['html_block', 'html_inline'].includes(token.type)) {
                visible.push(token.content || '');
            }
        }
        const text = visible.join('\n');
        const linkReference = parsed.links.some((link) => /(?:technical[- ]debt|tech-debt|\bTD-\d{3}\b)/iu.test(link.href));
        const reference = text.match(/(?:技术债|technical[- ]debt|\bTD-\d{3}\b)/iu) || linkReference;
        if (reference) {
            const line = lineNumber(lines, (candidate) => /(?:技术债|technical[- ]debt|tech-debt|\bTD-\d{3}\b)/iu.test(candidate) && !/^\s*(```|~~~)/u.test(candidate));
            findings.push({
                severity: 'error',
                rule: 'root-readme-debt-reference',
                path: readmePath,
                line,
                message: '根 README 不得引用技术债台账或 TD 编号',
            });
        }
        return true;
    } catch (error) {
        if (error.code === 'ENOENT') {
            return false;
        }
        throw error;
    }
}

export async function checkDebt({ root, files, policy }) {
    const startedAt = Date.now();
    const findings = [];
    const trackerPath = normalizePath(policy.technicalDebtPath || 'docs/active/tech-debt-tracker.md');
    const trackerInInput = files.includes(trackerPath) || files.length === 0;
    let trackerSource = null;
    if (trackerInInput) {
        try {
            trackerSource = await readFile(path.join(root, trackerPath), 'utf8');
        } catch (error) {
            if (error.code !== 'ENOENT') {
                return {
                    check: createCheck({ id: 'technical-debt', status: 'error', exitCode: 2, reason: error.message, durationMs: Date.now() - startedAt }),
                    findings: [{ severity: 'error', rule: 'debt-read', path: trackerPath, line: 1, message: `无法读取技术债台账：${error.message}` }],
                };
            }
        }
    }

    await checkRootReadme(root, policy, findings);
    if (trackerSource === null) {
        return {
            check: createCheck({
                id: 'technical-debt',
                status: findings.length > 0 ? 'failed' : 'not_applicable',
                required: findings.length > 0,
                exitCode: findings.length > 0 ? 1 : null,
                reason: findings.length > 0 ? '根 README 违反技术债公开入口约束' : null,
                durationMs: Date.now() - startedAt,
            }),
            findings,
        };
    }

    const table = parseDebtTable(trackerSource);
    const details = parseDetails(table.lines);
    const registry = await loadRegistry(root);
    const registryById = new Map();
    for (const entry of registry.entries) {
        if (registryById.has(entry.id)) {
            findings.push({ severity: 'error', rule: 'debt-registry-duplicate', path: 'tools/docs-check/debt-id-registry.json', line: 1, message: `注册表编号重复：${entry.id}` });
        }
        registryById.set(entry.id, entry);
    }
    const currentIds = [];
    const seen = new Set();
    for (const row of table.rows) {
        currentIds.push(row.id);
        if (seen.has(row.id)) {
            findings.push({ severity: 'error', rule: 'debt-id-duplicate', path: trackerPath, line: row.line, message: `活跃技术债编号重复：${row.id}` });
        }
        seen.add(row.id);
        const number = Number(row.id.slice(3));
        if (!/^TD-\d{3}$/u.test(row.id)) {
            findings.push({ severity: 'error', rule: 'debt-id-format', path: trackerPath, line: row.line, message: `技术债编号必须使用三位数字：${row.id}` });
        }
        if (number > registry.highWatermark) {
            findings.push({ severity: 'error', rule: 'debt-id-high-watermark', path: trackerPath, line: row.line, message: `${row.id} 超出注册表高水位 ${registry.highWatermark}` });
        }
        const registered = registryById.get(row.id);
        if (!registered) {
            findings.push({ severity: 'error', rule: 'debt-id-unregistered', path: trackerPath, line: row.line, message: `${row.id} 未登记在持久编号注册表中` });
        } else if (registered.state === 'retired') {
            findings.push({ severity: 'error', rule: 'debt-id-retired-reuse', path: trackerPath, line: row.line, message: `退役技术债编号不得复用：${row.id}` });
        }
    }
    for (let index = 1; index < currentIds.length; index += 1) {
        if (Number(currentIds[index].slice(3)) <= Number(currentIds[index - 1].slice(3))) {
            const row = table.rows[index];
            findings.push({ severity: 'error', rule: 'debt-id-order', path: trackerPath, line: row.line, message: '活跃技术债编号必须严格按数字升序排列' });
        }
    }
    for (const row of table.rows) {
        const detail = details.get(row.id);
        if (!detail || detail.title !== row.summary) {
            findings.push({ severity: 'error', rule: 'debt-summary-mismatch', path: trackerPath, line: row.line, message: `${row.id} 的清单摘要与明细标题不一致` });
        }
    }
    for (const [id, entry] of registryById) {
        if (entry.state === 'active' && !seen.has(id)) {
            findings.push({ severity: 'error', rule: 'debt-active-registry-missing', path: trackerPath, line: table.header >= 0 ? table.header + 1 : 1, message: `注册表中的 active 编号未出现在活跃清单：${id}` });
        }
    }
    return {
        check: createCheck({
            id: 'technical-debt',
            status: findings.length > 0 ? 'failed' : 'passed',
            exitCode: findings.length > 0 ? 1 : 0,
            reason: findings.length > 0 ? '技术债编号、清单或摘要一致性检查发现问题' : null,
            durationMs: Date.now() - startedAt,
        }),
        findings,
        currentIds,
        registry,
    };
}

export { loadRegistry, parseDebtTable, parseDetails };
