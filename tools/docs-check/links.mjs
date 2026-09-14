import MarkdownIt from 'markdown-it';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { addFinding, createCheck } from './results.mjs';
import { classifyPath, isFormatExempt, normalizePath } from './policy.mjs';

const parser = new MarkdownIt({ html: true, linkify: false, typographer: false });

function attr(token, name) {
    return token.attrGet(name);
}

function lineOf(token) {
    return token.map?.[0] ? token.map[0] + 1 : 1;
}

function slugifyHeading(value) {
    return value
        .normalize('NFKC')
        .trim()
        .toLocaleLowerCase('en-US')
        .replace(/[`*_~]/gu, '')
        .replace(/[^\p{L}\p{N}\s_-]/gu, '')
        .replace(/[\s_-]+/gu, '-');
}

function inlineText(token) {
    if (!token.children) {
        return token.content || '';
    }
    return token.children.map((child) => {
        if (child.type === 'softbreak' || child.type === 'hardbreak') {
            return ' ';
        }
        if (child.type === 'image') {
            return child.content || '';
        }
        if (child.type === 'html_inline') {
            return '';
        }
        return child.content || '';
    }).join('');
}

function tokensForInline(tokens) {
    return tokens.flatMap((token) => token.type === 'inline' ? [token, ...(token.children || [])] : [token]);
}

function extractExplicitAnchors(tokens) {
    const anchors = new Map();
    for (const token of tokensForInline(tokens)) {
        if (!['html_inline', 'html_block'].includes(token.type)) {
            continue;
        }
        const html = token.content || '';
        const matcher = /<(?:a|span)\b[^>]*?(?:id|name)\s*=\s*(["'])(.*?)\1[^>]*>/giu;
        let match;
        while ((match = matcher.exec(html)) !== null) {
            anchors.set(match[2], lineOf(token));
        }
    }
    return anchors;
}

function extractHeadings(tokens) {
    const headings = [];
    for (let index = 0; index < tokens.length; index += 1) {
        if (tokens[index].type !== 'heading_open') {
            continue;
        }
        const inline = tokens[index + 1];
        if (!inline || inline.type !== 'inline') {
            continue;
        }
        const title = inlineText(inline).trim();
        headings.push({ title, slug: slugifyHeading(title), line: lineOf(tokens[index]) });
    }
    return headings;
}

function extractLinks(tokens) {
    const links = [];
    function visit(token, line) {
        if (token.type === 'code_inline' || token.type === 'code_block' || token.type === 'fence') {
            return;
        }
        if (token.type === 'link_open') {
            const href = attr(token, 'href');
            if (href) {
                links.push({ href, kind: 'link', line });
            }
        } else if (token.type === 'image') {
            const src = attr(token, 'src');
            if (src) {
                links.push({ href: src, kind: 'image', line });
            }
        }
        for (const child of token.children || []) {
            visit(child, line);
        }
    }
    let contextualLine = 1;
    for (const token of tokens) {
        if (token.type === 'tr_open' && token.map?.[0] !== undefined) {
            contextualLine = token.map[0] + 1;
        }
        visit(token, token.map?.[0] !== undefined ? lineOf(token) : contextualLine);
    }
    return links;
}

export function parseMarkdown(source) {
    const tokens = parser.parse(source, {});
    const explicitAnchors = extractExplicitAnchors(tokens);
    const headings = extractHeadings(tokens);
    const anchors = new Map(explicitAnchors);
    for (const heading of headings) {
        if (heading.slug) {
            anchors.set(heading.slug, heading.line);
        }
    }
    return {
        source,
        tokens,
        headings,
        anchors,
        links: extractLinks(tokens),
    };
}

export async function readDocument(root, relativePath, cache = new Map()) {
    const normalized = normalizePath(relativePath);
    if (cache.has(normalized)) {
        return cache.get(normalized);
    }
    const absolute = path.resolve(root, normalized);
    const rootRelative = path.relative(root, absolute);
    if (rootRelative.startsWith('..') || path.isAbsolute(rootRelative)) {
        throw new Error(`文档路径超出检查根目录：${relativePath}`);
    }
    const source = await readFile(absolute, 'utf8');
    const document = { path: normalized, ...parseMarkdown(source) };
    cache.set(normalized, document);
    return document;
}

async function existingTarget(root, sourcePath, href) {
    let decoded;
    try {
        decoded = decodeURIComponent(href.replace(/\\/gu, '/'));
    } catch {
        decoded = href;
    }
    const hashIndex = decoded.indexOf('#');
    const pathname = hashIndex >= 0 ? decoded.slice(0, hashIndex) : decoded;
    const fragment = hashIndex >= 0 ? decoded.slice(hashIndex + 1) : null;
    if (!pathname) {
        return { path: sourcePath, fragment };
    }
    const absoluteBase = path.resolve(root, path.dirname(sourcePath), pathname);
    const baseRelative = path.relative(root, absoluteBase);
    if (baseRelative.startsWith('..') || path.isAbsolute(baseRelative)) {
        return { outside: true, pathname, fragment };
    }
    const candidates = [absoluteBase];
    if (!path.extname(absoluteBase)) {
        candidates.push(`${absoluteBase}.md`, `${absoluteBase}.markdown`, `${absoluteBase}.mdx`);
    }
    for (const candidate of candidates) {
        try {
            const details = await stat(candidate);
            if (details.isFile()) {
                return {
                    path: normalizePath(path.relative(root, candidate)),
                    fragment,
                };
            }
            if (details.isDirectory()) {
                for (const indexFile of ['index.md', 'README.md']) {
                    const indexPath = path.join(candidate, indexFile);
                    try {
                        const indexDetails = await stat(indexPath);
                        if (indexDetails.isFile()) {
                            return {
                                path: normalizePath(path.relative(root, indexPath)),
                                fragment,
                            };
                        }
                    } catch {
                        // Continue to the next conventional index file.
                    }
                }
            }
        } catch {
            // Try the next extension candidate.
        }
    }
    return { missing: true, pathname, fragment };
}

function isExternal(href) {
    return /^(?:[a-z][a-z\d+.-]*:|\/\/)/iu.test(href.trim());
}

export async function collectDocuments(root, files) {
    const cache = new Map();
    const documents = [];
    const errors = [];
    for (const file of files) {
        if (!/\.(?:md|markdown|mdx)$/iu.test(file)) {
            continue;
        }
        try {
            documents.push(await readDocument(root, file, cache));
        } catch (error) {
            errors.push({ path: normalizePath(file), line: 1, message: `无法读取 Markdown：${error.message}` });
        }
    }
    return { documents, cache, errors };
}

export async function checkLinks({ root, files, policy = null }) {
    const startedAt = Date.now();
    const { documents, cache, errors } = await collectDocuments(root, files);
    const findings = [];
    for (const error of errors) {
        findings.push({ severity: 'error', rule: 'markdown-read', path: error.path, line: error.line, message: error.message });
    }
    const seenHeadings = new Map();
    for (const document of documents) {
        const local = new Map();
        const checkHeadingUniqueness = !policy
            || classifyPath(document.path, policy) === 'effective' && !isFormatExempt(document.path, policy);
        for (const heading of document.headings) {
            if (checkHeadingUniqueness && local.has(heading.slug)) {
                findings.push({
                    severity: 'error',
                    rule: 'duplicate-heading',
                    path: document.path,
                    line: heading.line,
                    message: `标题“${heading.title}”生成重复锚点 #${heading.slug}`,
                });
            }
            local.set(heading.slug, heading.line);
            seenHeadings.set(`${document.path}#${heading.slug}`, heading.line);
        }
        for (const link of document.links) {
            const href = link.href.trim();
            if (isExternal(href) || href.startsWith('#') && href.length === 1) {
                continue;
            }
            const target = await existingTarget(root, document.path, href);
            if (target.outside) {
                findings.push({ severity: 'error', rule: 'internal-link', path: document.path, line: link.line, message: `链接越出检查根目录：${href}` });
                continue;
            }
            if (target.missing) {
                findings.push({ severity: 'error', rule: 'internal-link', path: document.path, line: link.line, message: `内部链接目标不存在：${href}` });
                continue;
            }
            if (target.fragment) {
                let targetDocument;
                try {
                    targetDocument = await readDocument(root, target.path, cache);
                } catch (error) {
                    findings.push({ severity: 'error', rule: 'anchor', path: document.path, line: link.line, message: `无法读取锚点目标 ${target.path}：${error.message}` });
                    continue;
                }
                const fragment = target.fragment.replace(/^#/u, '');
                if (!targetDocument.anchors.has(fragment)) {
                    findings.push({ severity: 'error', rule: 'anchor', path: document.path, line: link.line, message: `锚点不存在：${target.path}#${fragment}` });
                }
            }
        }
    }
    const hasReadError = errors.length > 0;
    const hasRuleFailure = findings.some((finding) => finding.severity === 'error');
    return {
        check: createCheck({
            id: 'internal-links',
            status: hasReadError ? 'error' : hasRuleFailure ? 'failed' : 'passed',
            exitCode: hasReadError ? 2 : hasRuleFailure ? 1 : 0,
            reason: hasReadError ? 'Markdown 文件读取失败' : hasRuleFailure ? '内部链接、锚点或标题检查发现问题' : null,
            durationMs: Date.now() - startedAt,
        }),
        findings,
        documents,
        cache,
        seenHeadings,
    };
}

export { isExternal, slugifyHeading };
