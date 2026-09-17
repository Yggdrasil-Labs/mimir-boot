import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertFixturePublishedArtifact } from './artifact-contract.mjs';
import { verifyPublic } from './public.mjs';
import { parsePortalState } from './portal-state.mjs';
import { errorExitCode, toolEnvironmentError } from './runtime.mjs';

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
