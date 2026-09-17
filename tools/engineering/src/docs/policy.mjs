import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

const defaultPolicy = {
    schemaVersion: 1,
    // 默认校验所有发现到的 Markdown；这里只声明明确不要求纳入当前导航的资料。
    navigationExemptions: [
        'docs/archive/**',
        'docs/design-docs/_template.md',
        'CHANGELOG.md',
        '.cursor/**',
    ],
    formatExemptions: ['CHANGELOG.md', '.cursor/**'],
    navigationRoots: ['AGENTS.md', 'README.md', 'docs/index.md'],
};

function globToRegExp(pattern) {
    let source = '^';
    for (let index = 0; index < pattern.length; index += 1) {
        const character = pattern[index];
        if (character === '*') {
            if (pattern[index + 1] === '*') {
                index += 1;
                if (pattern[index + 1] === '/') {
                    index += 1;
                    source += '(?:.*/)?';
                } else {
                    source += '.*';
                }
            } else {
                source += '[^/]*';
            }
        } else if (character === '?') {
            source += '[^/]';
        } else {
            source += character.replace(/[.+^${}()|[\]\\]/gu, '\\$&');
        }
    }
    return new RegExp(`${source}$`, 'u');
}

function normalizePath(file) {
    return file.split(path.sep).join('/').replace(/^\.\//u, '');
}

export function matchesAny(file, patterns = []) {
    const normalized = normalizePath(file);
    return patterns.some((pattern) => globToRegExp(normalizePath(pattern)).test(normalized));
}

export function isFormatExempt(file, policy) {
    return matchesAny(file, policy.formatExemptions);
}

export function isNavigationExempt(file, policy) {
    return matchesAny(file, policy.navigationExemptions);
}

export function isNavigationRoot(file, policy) {
    return matchesAny(file, policy.navigationRoots);
}

export async function loadPolicy(root) {
    const candidates = [
        path.join(root, 'tools', 'engineering', 'config', 'policy.json'),
        path.join(toolRoot, 'config', 'policy.json'),
    ];
    for (const candidate of candidates) {
        try {
            const parsed = JSON.parse(await readFile(candidate, 'utf8'));
            if (parsed.schemaVersion !== 1) {
                throw new Error(`policy.json schemaVersion 不受支持：${parsed.schemaVersion}`);
            }
            return { ...defaultPolicy, ...parsed };
        } catch (error) {
            if (error.code !== 'ENOENT') {
                throw new Error(`无法读取文档策略 ${candidate}：${error.message}`);
            }
        }
    }
    throw new Error('缺少 policy.json：当前快照和检查器均未提供文档策略');
}

export { defaultPolicy, normalizePath };
