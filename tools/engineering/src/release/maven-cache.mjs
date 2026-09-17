import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

// Maven 3.9.x 的版本范围仍需仓库元数据；file 镜像不能仅是一个空目录。
// 仅保留本轮预热得到的元数据，不向隔离镜像加入制品，也不开放外网。
// 来源：https://maven.apache.org/repositories/metadata.html
export async function preserveMetadata(cacheDirectory, mirrorDirectory, repositoryId) {
    if (!/^[A-Za-z0-9_.-]+$/u.test(repositoryId)) throw new Error('无效的 Maven 仓库 ID');
    const filename = `maven-metadata-${repositoryId}.xml`;
    let count = 0;
    async function visit(relative) {
        const directory = path.join(cacheDirectory, relative);
        for (const entry of await readdir(directory, { withFileTypes: true })) {
            if (entry.isDirectory()) await visit(path.join(relative, entry.name));
            else if (entry.isFile() && entry.name === filename) {
                const content = await readFile(path.join(directory, entry.name));
                const target = path.join(mirrorDirectory, relative, 'maven-metadata.xml');
                await mkdir(path.dirname(target), { recursive: true });
                await writeFile(target, content);
                for (const algorithm of ['sha1', 'sha256']) {
                    await writeFile(`${target}.${algorithm}`, createHash(algorithm).update(content).digest('hex'));
                }
                count++;
            }
        }
    }
    await visit('');
    return count;
}
