import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function parsePortalState(source) {
    const state = JSON.parse(source).deploymentState;
    if (typeof state !== 'string' || !/^[A-Z][A-Z_]*$/u.test(state)) throw new Error('Portal 状态响应缺少有效 deploymentState');
    return state;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        if (process.argv.length !== 3) throw new Error('用法：bash scripts/engineering.sh portal-state <响应 JSON 文件>');
        process.stdout.write(`${parsePortalState(await readFile(process.argv[2], 'utf8'))}\n`);
    } catch (error) {
        process.stderr.write(`${error.message}\n`);
        process.exitCode = 1;
    }
}
