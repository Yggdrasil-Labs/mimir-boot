import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const commands = {
    docs: 'docs/check.mjs',
    java: 'quality/java.mjs',
    'build-model': 'quality/verify-build-model.mjs',
    consumer: 'release/consumer.mjs',
    signing: 'release/signing.mjs',
    public: 'release/public.mjs',
    contracts: 'release/verify-contracts.mjs',
    'portal-state': 'release/portal-state.mjs',
};
const [command, ...args] = process.argv.slice(2);
if (!Object.hasOwn(commands, command)) {
    process.stderr.write(`未知工程命令：${command}\n`);
    process.exitCode = 2;
} else {
    const child = spawn(process.execPath, [path.join(path.dirname(fileURLToPath(import.meta.url)), commands[command]), ...args], { stdio: 'inherit' });
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
    child.on('error', (error) => { process.stderr.write(`${error.message}\n`); process.exitCode = 2; });
    child.on('exit', (code) => { process.exitCode = code ?? 2; });
}
