import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertFixturePublishedArtifact } from './artifact-contract.mjs';
import { copyThirdPartyMavenCache } from './maven-cache.mjs';
import { verifyPublic } from './public.mjs';
import { parsePortalState } from './portal-state.mjs';
import { runMaven as runSigningMaven } from './signing.mjs';
import { backfillMavenCache, finalizeConsumer } from './consumer.mjs';
import { errorExitCode, isTransientMavenTransferFailure, runMavenStage, toolEnvironmentError } from './runtime.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const normalize = (text) => text.split('\n').map((line) => line.trim()).filter(Boolean).join('\n');

function block(source, heading) {
    const lines = source.split('\n');
    const start = lines.indexOf(heading);
    assert.notEqual(start, -1, `缺少工作流块：${heading.trim()}`);
    const indent = heading.search(/\S/u);
    let end = start + 1;
    while (end < lines.length && (!lines[end].trim() || lines[end].search(/\S/u) > indent)) end++;
    return lines.slice(start + 1, end).join('\n');
}

function command(executable, args, env = process.env) {
    return new Promise((resolve, reject) => {
        const child = spawn(executable, args, { cwd: root, env, stdio: 'inherit' });
        child.on('error', (error) => reject(toolEnvironmentError(executable, error)));
        child.on('exit', (code, signal) => code === 0 ? resolve() : reject(new Error(`${executable} exit=${code}, signal=${signal}`)));
    });
}

async function verifyWorkflow(directory) {
    const workflow = await readFile(path.join(root, '.github/workflows/release.yml'), 'utf8');
    const ci = await readFile(path.join(root, '.github/workflows/ci.yml'), 'utf8');
    const shared = 'run: bash scripts/engineering.sh quality --mode full --source worktree --report "${RUNNER_TEMP}/mimir-quality/quality-report.json"';
    assert.ok(block(ci, '  build:').includes(shared), 'CI 必须执行共享完整门禁');
    assert.ok(block(workflow, '  release-consumer-verify:').includes(shared), '发布前必须执行同一完整门禁');
    assert.match(await readFile(path.join(root, 'pom.xml'), 'utf8'), /<waitUntil>uploaded<\/waitUntil>/u);
    const conditions = {
        'verify-maven-central-public': `\${{ always()
          && needs.release-verify.result == 'success'
          && needs['release-consumer-verify'].result == 'success'
          && (needs['publish-maven-central'].result == 'success'
              || (github.event_name == 'workflow_dispatch'
                  && needs['publish-maven-central'].result == 'skipped'
                  && (github.event.inputs.finalize_github_release == 'true'
                      || github.event.inputs.update_dev_version == 'true'))) }}`,
        'create-github-release': `\${{ always()
          && needs.release-verify.result == 'success'
          && needs['release-consumer-verify'].result == 'success'
          && (needs['publish-gpr'].result == 'success' || needs['publish-gpr'].result == 'skipped')
          && (needs['publish-maven-central'].result == 'success' || needs['publish-maven-central'].result == 'skipped')
          && needs['verify-maven-central-public'].result == 'success'
          && (github.event_name != 'workflow_dispatch' || github.event.inputs.finalize_github_release == 'true') }}`,
        'update-dev-version': `\${{ always()
          && needs['release-consumer-verify'].result == 'success'
          && needs['verify-maven-central-public'].result == 'success'
          && ((github.event_name != 'workflow_dispatch'
               && needs['create-github-release'].result == 'success')
              || (github.event_name == 'workflow_dispatch'
                  && github.event.inputs.update_dev_version == 'true'
                  && (github.event.inputs.publish_gpr != 'true'
                      || needs['publish-gpr'].result == 'success')
                  && (github.event.inputs.publish_maven_central != 'true'
                      || needs['publish-maven-central'].result == 'success')
                  && (github.event.inputs.finalize_github_release != 'true'
                      || needs['create-github-release'].result == 'success')
                  && (needs['create-github-release'].result == 'success'
                      || needs['create-github-release'].result == 'skipped'))) }}`,
    };
    for (const [job, expected] of Object.entries(conditions)) {
        const content = block(workflow, `  ${job}:`);
        assert.equal(normalize(block(content, '    if: >-')), normalize(expected), `${job} 发布约束发生变化`);
        if (job !== 'verify-maven-central-public') assert.match(content, /^    needs:.*[ ,]verify-maven-central-public[ ,]/mu);
    }
    const publicJob = block(workflow, '  verify-maven-central-public:');
    assert.doesNotMatch(publicJob, /^\s*continue-on-error:/mu);
    assert.equal(normalize(block(block(publicJob, '      - name: Verify public Maven Central artifacts'), '        run: |')),
        'set -euo pipefail\nbash scripts/engineering.sh public "${VERSION#v}"');
    const publish = block(workflow, '  publish-maven-central:');
    assert.ok(publish.includes('publish_status=${PIPESTATUS[0]}'), '上传失败必须保留 Maven 退出码');
    assert.ok(publish.includes('PORTAL_OBSERVE_OUTCOME:'), '必须保留 Portal 观察结果');
    const observe = block(publish, '      - name: Observe Maven Central deployment');
    assert.match(observe, /^        continue-on-error: true$/mu, 'Portal 必须保持辅助信号');
    const observeCommand = block(observe, '        run: |').split('\n').map((line) => line.replace(/^          /u, '')).join('\n');
    // 执行真实工作流命令；仅替换网络请求，防止契约验证接触发布账户。
    const fixtureCommand = `curl() {
  [[ "$PORTAL_FIXTURE" != timeout ]] || return 28
  while [[ "$#" -gt 1 ]]; do
    if [[ "$1" == --output ]]; then
      printf '{"deploymentState":"PUBLISHING"}\\n' >"$2"
      return 0
    fi
    shift
  done
  return 2
}
export -f curl
${observeCommand}`;
    const env = { ...process.env, RUNNER_TEMP: directory, GITHUB_OUTPUT: path.join(directory, 'portal.output'),
        GITHUB_STEP_SUMMARY: path.join(directory, 'portal.summary'), DEPLOYMENT_ID: '00000000-0000-0000-0000-000000000001',
        MAVEN_CENTRAL_USERNAME: 'fixture', MAVEN_CENTRAL_PASSWORD: 'fixture', PORTAL_FIXTURE: 'PUBLISHING' };
    await command('bash', ['-c', fixtureCommand], env);
    assert.match(await readFile(env.GITHUB_OUTPUT, 'utf8'), /^portal_state=PUBLISHING$/mu);
    await assert.rejects(command('bash', ['-c', fixtureCommand], { ...env, PORTAL_FIXTURE: 'timeout' }));
    assert.equal(parsePortalState('{"deploymentState":"PUBLISHING"}'), 'PUBLISHING');
    for (const value of ['{}', '{"deploymentState":null}', '{"deploymentState":"x\\ninjected=y"}', 'invalid']) assert.throws(() => parsePortalState(value));
}

async function verifyArtifactLayout(directory) {
    const repository = path.join(directory, 'repository');
    const artifactDir = (version) => path.join(repository, 'io/github/yggdrasil-labs/mimir-boot', version);
    for (const version of ['2.2.1', '2.2.1-SNAPSHOT']) {
        await mkdir(artifactDir(version), { recursive: true });
        await writeFile(path.join(artifactDir(version), `mimir-boot-${version}.pom`), '<project/>');
    }
    await assertFixturePublishedArtifact(repository, '2.2.1', 'mimir-boot');
    await assert.rejects(assertFixturePublishedArtifact(repository, '2.2.1', 'missing-pom'));
    await assert.rejects(assertFixturePublishedArtifact(repository, '2.2.1-SNAPSHOT', 'mimir-boot'));
    await writeFile(path.join(artifactDir('2.2.1-SNAPSHOT'), 'maven-metadata.xml'), '<metadata/>');
    await assertFixturePublishedArtifact(repository, '2.2.1-SNAPSHOT', 'mimir-boot');
}

async function verifyMavenCacheFixture(directory) {
    const source = path.join(directory, 'maven-seed');
    const target = path.join(directory, 'maven-target');
    const dependency = path.join(source, 'org/example/dependency/1.0.0');
    const excluded = path.join(source, 'io/github/yggdrasil-labs/mimir-boot/2.2.2');
    await mkdir(dependency, { recursive: true });
    await mkdir(excluded, { recursive: true });
    await writeFile(path.join(dependency, 'dependency-1.0.0.jar'), 'third-party');
    await writeFile(path.join(dependency, 'dependency-1.0.0.jar.sha1'), 'checksum');
    await writeFile(path.join(dependency, '_remote.repositories'), '#NOTE\ndependency-1.0.0.jar>maven-central=\n');
    await writeFile(path.join(dependency, 'dependency-1.0.0.jar.lastUpdated'), 'transient failure');
    await writeFile(path.join(dependency, 'dependency-1.0.0.pom.part'), 'partial');
    await writeFile(path.join(dependency, 'resolver-status.properties'), 'stale failure');
    await writeFile(path.join(source, 'org/example/maven-metadata-maven-central.xml'), '<metadata/>');
    await writeFile(path.join(excluded, 'mimir-boot-2.2.2.jar'), 'must not leak');

    const copied = await copyThirdPartyMavenCache(source, target, { repositoryId: 'central' });
    assert.ok(copied.files > 0, '第三方 Maven 缓存应复制有效制品');
    assert.equal(await readFile(path.join(target, 'org/example/dependency/1.0.0/dependency-1.0.0.jar'), 'utf8'), 'third-party');
    assert.match(await readFile(path.join(target, 'org/example/dependency/1.0.0/_remote.repositories'), 'utf8'), />central=/u);
    await assert.rejects(readFile(path.join(target, 'io/github/yggdrasil-labs/mimir-boot/2.2.2/mimir-boot-2.2.2.jar')));
    await assert.rejects(readFile(path.join(target, 'org/example/dependency/1.0.0/dependency-1.0.0.jar.lastUpdated')));
    await assert.rejects(readFile(path.join(target, 'org/example/dependency/1.0.0/dependency-1.0.0.pom.part')));
    await assert.rejects(readFile(path.join(target, 'org/example/dependency/1.0.0/resolver-status.properties')));
    assert.equal(await readFile(path.join(target, 'org/example/maven-metadata-central.xml'), 'utf8'), '<metadata/>');
    assert.ok(await readFile(path.join(target, 'org/example/maven-metadata-central.xml.sha1'), 'utf8'));

    await writeFile(path.join(dependency, 'dependency-1.0.0.pom'), '<project/>');
    await writeFile(path.join(dependency, '_remote.repositories'), 'dependency-1.0.0.pom>maven-central=\n');
    await copyThirdPartyMavenCache(source, target, { repositoryId: 'central', overwrite: false });
    const mergedMarkers = await readFile(path.join(target, 'org/example/dependency/1.0.0/_remote.repositories'), 'utf8');
    assert.match(mergedMarkers, /dependency-1\.0\.0\.jar>central=/u);
    assert.match(mergedMarkers, /dependency-1\.0\.0\.pom>central=/u, '增量回填必须合并新增制品的来源标记');

    assert.equal(isTransientMavenTransferFailure('Remote host terminated the handshake'), true);
    assert.equal(isTransientMavenTransferFailure('Could not transfer artifact: Connection reset'), true);
    assert.equal(isTransientMavenTransferFailure('Tests run: 1, Failures: 1'), false);
    assert.equal(isTransientMavenTransferFailure('status code: 401'), false);
    assert.equal(isTransientMavenTransferFailure('Could not transfer artifact example:private:jar:1.0.0 from/to central: status code: 401'), false);
    assert.equal(isTransientMavenTransferFailure('Could not find artifact example:missing:jar:1.0.0'), false);
    assert.equal(isTransientMavenTransferFailure('Could not find artifact example:missing:jar:1.0.0 in fixture\nCould not transfer artifact example:missing:jar:1.0.0 from/to central: Remote host terminated the handshake'), true);
}

async function verifyConsumerCacheFlow() {
    const source = await readFile(path.join(root, 'tools/engineering/src/release/consumer.mjs'), 'utf8');
    for (const cache of ['producerCacheDirectory', 'consumerCacheDirectory', 'bomConsumerCacheDirectory', 'failureConsumerCacheDirectory']) {
        assert.ok(source.includes(`backfillMavenCache({ targetDirectory: sharedCacheDirectory, sourceDirectories: [${cache}]`), `${cache} 必须回填到共享缓存`);
    }
}

function verifyTransferClassification() {
    assert.equal(isTransientMavenTransferFailure('Non-resolvable parent POM: Could not transfer artifact example:parent:pom:1.0: Remote host terminated the handshake'), true);
    for (const reason of ['status code: 400', 'status code: 401', 'status code: 403', 'status code: 404', 'Checksum validation failed', 'PKIX path building failed']) {
        assert.equal(isTransientMavenTransferFailure(`Could not transfer artifact example:artifact:jar:1.0: ${reason}`), false, reason);
    }
    assert.equal(isTransientMavenTransferFailure('Could not transfer artifact example:artifact:jar:401: Connection reset'), true);
    assert.equal(isTransientMavenTransferFailure('Non-resolvable parent POM: Could not find artifact example:parent:pom:1.0'), false);
}

async function verifySigningRetry(directory) {
    const executable = path.join(directory, 'maven-retry-fixture');
    await writeFile(executable, '#!/usr/bin/env bash\nif [[ ! -f "$MIMIR_CONTRACT_RETRY_FILE" ]]; then\n  touch "$MIMIR_CONTRACT_RETRY_FILE"\n  echo "Remote host terminated the handshake" >&2\n  exit 1\nfi\n[[ "${1:-}" == "-U" ]]\n', { mode: 0o700 });
    const result = await runSigningMaven(executable, [], 'signing-retry-contract', {
        logsDirectory: path.join(directory, 'signing-retry'),
        env: { MIMIR_CONTRACT_RETRY_FILE: path.join(directory, 'signing-attempt') },
        maxAttempts: 3,
        retryArgs: ['-U'],
    });
    assert.equal(result.attempts, 2, '签名重试必须透传 -U');
}

async function verifyConsumerRecovery(directory) {
    const workDirectory = path.join(directory, 'failed-consumer');
    const cache = path.join(workDirectory, 'stage-cache');
    await mkdir(path.join(cache, 'org/example'), { recursive: true });
    await writeFile(path.join(cache, 'org/example/download.jar'), 'completed-download');
    const shared = path.join(directory, 'shared-cache');
    await backfillMavenCache({ targetDirectory: shared, sourceDirectories: [cache], announce: false });
    assert.equal(await readFile(path.join(shared, 'org/example/download.jar'), 'utf8'), 'completed-download');
    const invalidSeed = path.join(directory, 'seed-is-file');
    await writeFile(invalidSeed, 'not-a-directory');
    const original = new Error('原始 Maven 失败');
    await assert.rejects(async () => {
        try { throw original; } finally {
            await finalizeConsumer({ completed: false, seedCacheDirectory: invalidSeed, cacheDirectories: [cache], externalLogsDirectory: directory, logsDirectory: directory, workDirectory });
        }
    }, (error) => error === original, '回填异常不得覆盖原始失败');
    if (process.env.MIMIR_KEEP_WORKDIR !== '1') await assert.rejects(readFile(path.join(cache, 'org/example/download.jar')), { code: 'ENOENT' });
}

async function verifyMavenRetryFixture(directory) {
    const logsDirectory = path.join(directory, 'maven-retry-logs');
    const attemptFile = path.join(directory, 'maven-retry-attempt');
    const script = `import { readFile, writeFile } from 'node:fs/promises';
const file = process.env.MIMIR_CONTRACT_RETRY_FILE;
const attempt = Number(await readFile(file, 'utf8').catch(() => '0')) + 1;
await writeFile(file, String(attempt));
if (attempt === 1) {
    await new Promise((resolve) => process.stderr.write('Remote host terminated the handshake\\n', resolve));
    process.exitCode = 1;
} else {
    process.stdout.write('retry recovered\\n');
}`;
    const result = await runMavenStage('transient-maven-fixture', [process.execPath, '--input-type=module', '--eval', script], {
        logsDirectory,
        env: { MIMIR_CONTRACT_RETRY_FILE: attemptFile },
        maxAttempts: 3,
    });
    assert.equal(result.attempts, 2);
    assert.match(await readFile(path.join(logsDirectory, 'transient-maven-fixture.attempt-1.log'), 'utf8'), /handshake/u);
    assert.match(await readFile(path.join(logsDirectory, 'transient-maven-fixture.attempt-2.log'), 'utf8'), /retry recovered/u);

    await assert.rejects(runMavenStage('timeout-maven-fixture', [process.execPath, '--eval', 'setTimeout(() => {}, 2000)'], {
        logsDirectory,
        timeoutMs: 50,
    }), /超时/u);
}

async function verifyPublicFixture(directory) {
    const version = '2.2.2';
    const artifactPath = (artifact, extension) => `/io/github/yggdrasil-labs/${artifact}/${version}/${artifact}-${version}.${extension}`;
    const rootPath = artifactPath('mimir-boot', 'pom');
    const pomPath = artifactPath('mimir-boot-starter-web', 'pom');
    const jarPath = artifactPath('mimir-boot-starter-web', 'jar');
    const pom = (artifact) => `<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>io.github.yggdrasil-labs</groupId><artifactId>${artifact}</artifactId><version>${version}</version></project>`;
    const content = new Map([[rootPath, pom('mimir-boot')], [pomPath, pom('mimir-boot-starter-web')]]);
    const jarContent = path.join(directory, 'jar-content');
    await mkdir(jarContent);
    await writeFile(path.join(jarContent, 'fixture.txt'), 'fixture');
    const jar = path.join(directory, 'fixture.jar');
    await command('jar', ['--create', '--file', jar, '-C', jarContent, '.']);
    const validJar = await readFile(jar);
    content.set(jarPath, validJar);
    let requests = [];
    let mode = 'normal';
    const server = createServer((request, response) => {
        const requested = new URL(request.url, 'http://127.0.0.1').pathname;
        requests.push(requested);
        const unavailable = requested === jarPath && (mode === 'jar-404-always'
            || mode === 'jar-404-once' && requests.filter((item) => item === jarPath).length === 1);
        if (unavailable || !content.has(requested)) { response.writeHead(404); response.end(); }
        else response.end(content.get(requested));
    });
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    const run = (attempts) => verifyPublic(version, { MIMIR_MAVEN_CENTRAL_BASE_URL: `http://127.0.0.1:${server.address().port}`,
        MIMIR_PUBLIC_VERIFY_ATTEMPTS: String(attempts), MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS: '0' });
    try {
        await run(1);
        for (const invalid of [pom('unexpected-artifact'), '<project>']) {
            content.set(rootPath, invalid);
            await assert.rejects(run(1));
        }
        content.set(rootPath, pom('mimir-boot'));
        content.set(jarPath, 'not a JAR');
        await assert.rejects(run(1));
        content.delete(jarPath);
        await assert.rejects(run(1));
        content.set(jarPath, validJar);
        mode = 'jar-404-once'; requests = [];
        await run(2);
        for (const file of [rootPath, pomPath, jarPath]) assert.equal(requests.filter((item) => item === file).length, 2);
        mode = 'jar-404-always'; requests = [];
        await assert.rejects(run(3));
        for (const file of [rootPath, pomPath, jarPath]) assert.equal(requests.filter((item) => item === file).length, 3);
    } finally {
        server.closeAllConnections();
        await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
    }
}

export async function verifyContracts() {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'mimir-release-contracts-'));
    try {
        await verifyWorkflow(directory);
        await verifyArtifactLayout(directory);
        await verifyConsumerCacheFlow();
        await verifyConsumerRecovery(directory);
        verifyTransferClassification();
        await verifySigningRetry(directory);
        await verifyMavenCacheFixture(directory);
        await verifyMavenRetryFixture(directory);
        await verifyPublicFixture(directory);
        process.stdout.write('发布工作流、制品目录、Portal 观察和公开制品重试契约均通过。\n');
    } finally {
        await rm(directory, { recursive: true, force: true });
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try { await verifyContracts(); }
    catch (error) {
        process.stderr.write(`发布契约验证失败：${error.stack}\n`);
        process.exitCode = errorExitCode(error, 1);
    }
}
