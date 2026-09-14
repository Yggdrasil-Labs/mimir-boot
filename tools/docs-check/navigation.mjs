import { checkLinks } from './links.mjs';
import { classifyPath, isNavigationRoot } from './policy.mjs';
import { createCheck } from './results.mjs';

export async function checkNavigation({ root, files, policy }) {
    const startedAt = Date.now();
    const linkResult = await checkLinks({ root, files, policy });
    const documents = linkResult.documents;
    const candidates = new Set(
        documents
            .filter((document) => classifyPath(document.path, policy) === 'effective')
            .map((document) => document.path),
    );
    if (candidates.size === 0) {
        return {
            check: createCheck({
                id: 'navigation',
                status: 'not_applicable',
                required: false,
                reason: null,
                durationMs: Date.now() - startedAt,
            }),
            findings: [],
        };
    }

    const incoming = new Map([...candidates].map((candidate) => [candidate, 0]));
    const adjacency = new Map([...candidates].map((candidate) => [candidate, new Set()]));
    const linksBySource = new Map();
    for (const document of documents) {
        linksBySource.set(document.path, document.links);
    }
    for (const document of documents) {
        for (const link of document.links) {
            if (link.href.startsWith('#') || /^(?:[a-z][a-z\d+.-]*:|\/\/)/iu.test(link.href.trim())) {
                continue;
            }
            const rawTarget = link.href.split('#', 1)[0];
            const sourceParts = document.path.split('/');
            sourceParts.pop();
            const target = rawTarget ? [...sourceParts, ...rawTarget.split('/')].join('/') : document.path;
            const normalized = normalizeNavigationTarget(target);
            const targetCandidates = [normalized];
            if (!normalized.endsWith('.md') && !normalized.endsWith('.markdown') && !normalized.endsWith('.mdx')) {
                targetCandidates.push(`${normalized}.md`, `${normalized}.markdown`, `${normalized}.mdx`);
            }
            const resolved = targetCandidates.find((candidate) => incoming.has(candidate));
            if (resolved) {
                incoming.set(resolved, incoming.get(resolved) + 1);
                if (adjacency.has(document.path)) {
                    adjacency.get(document.path).add(resolved);
                }
            }
        }
    }

    const reachable = new Set();
    const queue = [...candidates].filter((candidate) => isNavigationRoot(candidate, policy));
    while (queue.length > 0) {
        const current = queue.shift();
        if (reachable.has(current)) {
            continue;
        }
        reachable.add(current);
        for (const next of adjacency.get(current) || []) {
            if (!reachable.has(next)) {
                queue.push(next);
            }
        }
    }

    const findings = [];
    for (const candidate of candidates) {
        if (isNavigationRoot(candidate, policy)) {
            continue;
        }
        if (!reachable.has(candidate)) {
            findings.push({
                severity: 'error',
                rule: 'navigation-unreachable',
                path: candidate,
                line: 1,
                message: `有效文档未从导航根可达：${candidate}`,
            });
        }
    }
    return {
        check: createCheck({
            id: 'navigation',
            status: findings.length > 0 ? 'failed' : 'passed',
            exitCode: findings.length > 0 ? 1 : 0,
            reason: findings.length > 0 ? '存在未从导航根可达的有效文档' : null,
            durationMs: Date.now() - startedAt,
        }),
        findings,
        linksBySource,
    };
}

function normalizeNavigationTarget(target) {
    const parts = [];
    for (const part of target.split('/')) {
        if (!part || part === '.') {
            continue;
        }
        if (part === '..') {
            parts.pop();
        } else {
            parts.push(part);
        }
    }
    return parts.join('/');
}
