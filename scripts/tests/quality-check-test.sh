#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixture_root="$project_root/scripts/tests/fixtures/quality-runner"
work_root="$(mktemp -d "${TMPDIR:-/tmp}/mimir-quality-runner.XXXXXX")"
repository="$work_root/repository"
test_log="$work_root/checks.log"
cleanup() { rm -rf "$work_root"; }
trap cleanup EXIT

fail() { echo "quality-check-test: $*" >&2; exit 1; }
expect_exit() {
  local expected="$1"
  shift
  set +e
  "$@" >/dev/null 2>&1
  local actual=$?
  set -e
  [[ "$actual" == "$expected" ]] || fail "期望退出码 $expected，实际为 $actual：$*"
}
assert_report() {
  local report="$1" expression="$2"
  node -e "const r=require(process.argv[1]); process.exit((${expression}) ? 0 : 1);" "$report" || fail "报告断言失败：$expression"
}

mkdir -p "$repository/scripts/lib" "$repository/scripts/tests" "$repository/tools/docs-check/test"
cp -R "$fixture_root/." "$repository"
install -m 755 "$project_root/scripts/quality-check.sh" "$repository/scripts/quality-check.sh"
install -m 755 "$project_root/scripts/lib/quality-snapshot.sh" "$repository/scripts/lib/quality-snapshot.sh"
install -m 644 "$project_root/tools/docs-check/quality-result.mjs" "$repository/tools/docs-check/quality-result.mjs"
cat > "$repository/mvnw" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'mvn %s\n' "$*" >>"${QUALITY_TEST_LOG:?}"
mkdir -p tools/docs-check/.maven-node/node
ln -sf "$(command -v node)" tools/docs-check/.maven-node/node/node
if [[ " $* " == *' spotless:check '* ]]; then
  exit "${FAKE_JAVA_STATUS:-0}"
fi
exit 0
EOF
cat > "$repository/scripts/docs-tool.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'docs baseline %s\n' "$*" >>"${QUALITY_TEST_LOG:?}"
exit "${FAKE_DOCS_STATUS:-0}"
EOF
cat > "$repository/scripts/ci-preflight.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'java full\n' >>"${QUALITY_TEST_LOG:?}"
exit "${FAKE_JAVA_STATUS:-0}"
EOF
cat > "$repository/scripts/test-suite-consumer-contract-test.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'release suite\n' >>"${QUALITY_TEST_LOG:?}"
EOF
cat > "$repository/scripts/verify-maven-central-public-contract-test.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'release public\n' >>"${QUALITY_TEST_LOG:?}"
EOF
cat > "$repository/tools/docs-check/test/toolchain.test.mjs" <<'EOF'
import test from 'node:test';
test('toolchain fixture', () => {});
EOF
cat > "$repository/tools/docs-check/test/docs-check.test.mjs" <<'EOF'
import test from 'node:test';
test('docs fixture', () => {});
EOF
chmod +x "$repository/mvnw" "$repository/scripts/"*.sh "$repository/scripts/lib/quality-snapshot.sh"

git -C "$repository" init -q
git -C "$repository" config user.email quality@example.test
git -C "$repository" config user.name quality-fixture
git -C "$repository" add .
git -C "$repository" -c commit.gpgsign=false commit -qm baseline

: > "$test_log"
QUALITY_TEST_LOG="$test_log" bash "$repository/scripts/quality-check.sh" --mode full --source commit --commit HEAD --report "$work_root/commit-full.json"
assert_report "$work_root/commit-full.json" 'r.overall === "passed" && r.source === "commit" && r.commit && r.checks.some(c => c.id === "docs-full" && c.status === "passed") && r.checks.some(c => c.id === "java-quality" && c.status === "passed") && r.checks.some(c => c.id === "java-tests-and-coverage" && c.status === "passed")'
grep -Fxq 'java full' "$test_log" || fail 'commit 快照未执行快照内 Java 完整检查入口'

printf '# Staged README\n' > "$repository/README.md"
git -C "$repository" add README.md
: > "$test_log"
QUALITY_TEST_LOG="$test_log" bash "$repository/scripts/quality-check.sh" --mode quick --source index --report "$work_root/index-pass.json"
assert_report "$work_root/index-pass.json" 'r.overall === "passed" && r.source === "index" && r.commit === null && r.checks.some(c => c.id === "docs-format" && c.status === "passed") && r.checks.some(c => c.id === "java-format" && c.status === "not_applicable")'
grep -Fq 'docs baseline' "$test_log" || fail 'index 快照未执行暂存的文档工具'

cat > "$repository/scripts/docs-tool.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'docs staged %s\n' "$*" >>"${QUALITY_TEST_LOG:?}"
exit 1
EOF
chmod +x "$repository/scripts/docs-tool.sh"
git -C "$repository" add scripts/docs-tool.sh
cat > "$repository/scripts/docs-tool.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'docs worktree %s\n' "$*" >>"${QUALITY_TEST_LOG:?}"
exit 0
EOF
chmod +x "$repository/scripts/docs-tool.sh"
: > "$test_log"
expect_exit 1 env QUALITY_TEST_LOG="$test_log" bash "$repository/scripts/quality-check.sh" --mode quick --source index --report "$work_root/index-staged.json"
assert_report "$work_root/index-staged.json" 'r.overall === "failed" && r.checks.some(c => c.id === "docs-format" && c.status === "failed")'
grep -Fq 'docs staged' "$test_log" || fail 'index 快照错误地执行了工作区脚本'
! grep -Fq 'docs worktree' "$test_log" || fail 'index 快照不应执行未暂存脚本'

expect_exit 2 env QUALITY_SNAPSHOT_MANIFEST=/tmp/not-a-manifest bash "$repository/scripts/quality-check.sh" --mode quick --source index --report "$work_root/recursive.json"

git -C "$repository" restore --source=HEAD --staged --worktree scripts/docs-tool.sh
git -C "$repository" mv 'docs/old.md' 'docs/新 名.md'
git -C "$repository" rm -q 'docs/中文 空格.md'
git -C "$repository" add -A
status_before="$(git -C "$repository" status --porcelain=v1 -z | sha256sum)"
: > "$test_log"
QUALITY_TEST_LOG="$test_log" bash "$repository/scripts/quality-check.sh" --mode quick --source index --report "$work_root/chinese-path.json"
assert_report "$work_root/chinese-path.json" 'r.overall === "passed" && r.source === "index"'
status_after="$(git -C "$repository" status --porcelain=v1 -z | sha256sum)"
[[ "$status_before" == "$status_after" ]] || fail 'index 快照检查不应改写工作区或索引'

: > "$test_log"
expect_exit 1 env QUALITY_TEST_LOG="$test_log" FAKE_JAVA_STATUS=1 bash "$repository/scripts/quality-check.sh" --mode full --source worktree --report "$work_root/full-java-failed.json"
assert_report "$work_root/full-java-failed.json" 'r.overall === "failed" && r.checks.some(c => c.id === "java-quality" && c.status === "failed") && r.checks.some(c => c.id === "java-tests-and-coverage" && c.status === "not_run") && r.checks.some(c => c.id === "release-test-suite-contract" && c.status === "passed") && r.checks.some(c => c.id === "release-public-contract" && c.status === "passed")'
grep -Fxq 'release suite' "$test_log" || fail 'Java 失败后独立发布契约检查仍应执行'
grep -Fxq 'release public' "$test_log" || fail 'Java 失败后独立公开制品检查仍应执行'

printf '# Error\n' >> "$repository/README.md"
expect_exit 2 env QUALITY_TEST_LOG="$test_log" FAKE_DOCS_STATUS=7 bash "$repository/scripts/quality-check.sh" --mode quick --source worktree --report "$work_root/tool-error.json"
assert_report "$work_root/tool-error.json" 'r.overall === "error" && r.checks.some(c => c.id === "docs-format" && c.status === "error")'
expect_exit 2 env QUALITY_TEST_LOG="$test_log" bash "$repository/scripts/quality-check.sh" --mode quick --source worktree --report /proc/mimir-quality-report.json

worktree_two="$work_root/worktree-two"
git -C "$repository" worktree add -q --detach "$worktree_two" HEAD
: > "$work_root/one.log"
: > "$work_root/two.log"
QUALITY_TEST_LOG="$work_root/one.log" bash "$repository/scripts/quality-check.sh" --mode quick --source index --report "$work_root/one.json" &
pid_one=$!
QUALITY_TEST_LOG="$work_root/two.log" bash "$worktree_two/scripts/quality-check.sh" --mode quick --source index --report "$work_root/two.json" &
pid_two=$!
wait "$pid_one"
wait "$pid_two"
assert_report "$work_root/one.json" 'r.overall === "passed" && r.source === "index"'
assert_report "$work_root/two.json" 'r.overall === "passed" && r.source === "index"'
git -C "$repository" worktree remove --force "$worktree_two"

echo 'quality-check-test: passed'
