import { execFile } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { DOMImplementation, DOMParser, XMLSerializer } from '@xmldom/xmldom';

const execFileAsync = promisify(execFile);
const JAVA_IDENTIFIER = /^[\p{ID_Start}_$][\p{ID_Continue}$]*$/u;

/** 返回顶层及命名成员类型的 binaryName 与声明来源；范围选择由有效构建模型负责。 */
export async function inventoryJavaSources(root, files) {
    if (!path.isAbsolute(root) || files.some((file) => !path.isAbsolute(file))) {
        throw new Error('源码清单的执行根目录和源码文件必须是绝对路径');
    }
    if (!files.length) return [];
    const known = new Set(files.map((file) => path.resolve(file)));
    const temporary = await mkdtemp(path.join(tmpdir(), 'mimir-source-inventory-'));
    try {
        const request = new DOMImplementation().createDocument(null, 'request', null);
        for (const file of [...known].sort()) {
            const source = request.createElement('source');
            source.appendChild(request.createTextNode(file));
            request.documentElement.appendChild(source);
        }
        const input = path.join(temporary, 'request.xml');
        const output = path.join(temporary, 'inventory.xml');
        await writeFile(input, new XMLSerializer().serializeToString(request), 'utf8');
        const helper = fileURLToPath(new URL('./JavaSourceInventory.java', import.meta.url));
        try {
            await execFileAsync('java', [helper, input, output],
                { cwd: root, timeout: 60000, maxBuffer: 4 * 1024 * 1024 });
        } catch (error) {
            throw new Error(`Java 源码声明清单失败：${error.stderr || error.message}`);
        }
        const document = new DOMParser({ onError: (_level, message) => { throw new Error(message); } })
            .parseFromString(await readFile(output, 'utf8'), 'application/xml');
        const result = document.documentElement;
        if (document.doctype || !result || result.tagName !== 'source-inventory') throw new Error('无效 Java 源码声明清单 XML');
        return Array.from(result.childNodes).filter((item) => item.nodeType === 1).map((item) => {
            const sourceFile = item.getAttribute('source');
            const binaryName = item.getAttribute('binaryName');
            if (item.tagName !== 'declaration' || !known.has(sourceFile)
                || !binaryName || binaryName.split('.').some((name) => !JAVA_IDENTIFIER.test(name))) {
                throw new Error('Java 源码声明清单包含未知来源或无效类型名');
            }
            return { sourceFile, binaryName };
        });
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
}
