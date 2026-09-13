#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
script="$script_dir/test-suite-consumer.sh"
work_dir="$(mktemp -d -t mimir-suite-consumer-contract.XXXXXX)"
trap 'rm -rf "$work_dir"' EXIT

source "$script_dir/lib/published-artifact-contract.sh"

if ! grep -Eq '^[[:space:]]*(source|\.)[[:space:]]+.*scripts/lib/published-artifact-contract[.]sh' "$script"; then
  echo "test-suite-consumer.sh 必须 source 发布制品目录契约库。" >&2
  exit 1
fi

if ! grep -Eq 'assert_fixture_published_artifact[[:space:]]+"\$repository_dir"[[:space:]]+"\$revision"[[:space:]]+"\$artifact"' "$script"; then
  echo "发布制品循环必须调用发布制品目录契约。" >&2
  exit 1
fi

workflow="$project_dir/.github/workflows/release.yml"
if ! grep -Eq '<waitUntil>uploaded</waitUntil>' "$project_dir/pom.xml"; then
  echo "Maven Central 发布必须只等待 bundle 上传完成。" >&2
  exit 1
fi

if ! grep -Eq '^  verify-maven-central-public:$' "$workflow" \
  || ! grep -Eq 'bash[[:space:]]+scripts/verify-maven-central-public[.]sh' "$workflow"; then
  echo "release workflow 必须执行 Maven Central 公开制品验证。" >&2
  exit 1
fi

if ! grep -Eq 'id: observe' "$workflow" \
  || ! grep -Eq 'publish_status=\$\{PIPESTATUS\[0\]\}' "$workflow" \
  || ! grep -Eq 'PORTAL_OBSERVE_OUTCOME:' "$workflow"; then
  echo "Maven Central 发布必须在失败时保留 deployment ID，并记录 Portal 观察结果。" >&2
  exit 1
fi

# 按当前 YAML 缩进提取指定块，不跨 job 搜索，也不解释 Actions 表达式。
workflow_block() {
  awk -v heading="$1" '
    $0 == heading { active = 1; match($0, /[^ ]/); indent = RSTART; next }
    active && NF {
      match($0, /[^ ]/)
      if (RSTART <= indent) exit
      print
    }
  '
}

assert_snapshot() {
  local label="$1" actual="$2" expected
  expected="$(sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  if [[ "$(printf '%s' "$actual" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')" != "$expected" ]]; then
    echo "$label 与已确认的发布约束不一致，请复核对应 workflow 条件。" >&2
    exit 1
  fi
}

# 固定已确认的完整门禁；改变策略时显式复核并同步预期，不模拟调度器。
assert_snapshot '公开制品验证门禁' "$(workflow_block '  verify-maven-central-public:' <"$workflow" | workflow_block '    if: >-')" <<'EXPECTED'
${{ always()
    && needs.release-verify.result == 'success'
    && needs['release-consumer-verify'].result == 'success'
    && (needs['publish-maven-central'].result == 'success'
        || (github.event_name == 'workflow_dispatch'
            && needs['publish-maven-central'].result == 'skipped'
            && (github.event.inputs.finalize_github_release == 'true'
                || github.event.inputs.update_dev_version == 'true'))) }}
EXPECTED

assert_snapshot 'GitHub Release 门禁' "$(workflow_block '  create-github-release:' <"$workflow" | workflow_block '    if: >-')" <<'EXPECTED'
${{ always()
    && needs.release-verify.result == 'success'
    && needs['release-consumer-verify'].result == 'success'
    && (needs['publish-gpr'].result == 'success' || needs['publish-gpr'].result == 'skipped')
    && (needs['publish-maven-central'].result == 'success' || needs['publish-maven-central'].result == 'skipped')
    && needs['verify-maven-central-public'].result == 'success'
    && (github.event_name != 'workflow_dispatch' || github.event.inputs.finalize_github_release == 'true') }}
EXPECTED

assert_snapshot '开发版本回写门禁' "$(workflow_block '  update-dev-version:' <"$workflow" | workflow_block '    if: >-')" <<'EXPECTED'
${{ always()
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
                || needs['create-github-release'].result == 'skipped'))) }}
EXPECTED

for job in create-github-release update-dev-version; do
  if ! workflow_block "  $job:" <"$workflow" | grep -Eq '^    needs:.*[ ,]verify-maven-central-public[ ,]'; then
    echo "$job 必须依赖公开制品验证 job。" >&2
    exit 1
  fi
done

assert_snapshot '契约测试入口' "$(workflow_block '  release-consumer-verify:' <"$workflow" | workflow_block '      - name: Verify published artifact layout contract' | workflow_block '        run: |')" <<'EXPECTED'
if [[ -f scripts/test-suite-consumer-contract-test.sh ]]; then
  bash scripts/test-suite-consumer-contract-test.sh
fi
EXPECTED

public_job="$(workflow_block '  verify-maven-central-public:' <"$workflow")"
if grep -Eq '^[[:space:]]*continue-on-error:' <<<"$public_job"; then
  echo "公开制品验证不能忽略失败。" >&2
  exit 1
fi
assert_snapshot '公开制品验证调用' "$(workflow_block '      - name: Verify public Maven Central artifacts' <<<"$public_job" | workflow_block '        run: |')" <<'EXPECTED'
set -euo pipefail
bash scripts/verify-maven-central-public.sh "${VERSION#v}"
EXPECTED

# 执行真实 Portal 观察代码，仅用 Shell 函数替代 curl，避免访问外部服务。
observe_step="$(workflow_block '  publish-maven-central:' <"$workflow" | workflow_block '      - name: Observe Maven Central deployment')"
if ! grep -Eq '^        continue-on-error: true$' <<<"$observe_step"; then
  echo "Portal 观察失败必须保持为辅助信号。" >&2
  exit 1
fi
observe_command="$(workflow_block '        run: |' <<<"$observe_step" | sed 's/^          //')"
(
  curl() {
    [[ "$PORTAL_FIXTURE" != timeout ]] || return 28
    while [[ "$#" -gt 1 ]]; do
      if [[ "$1" == --output ]]; then
        printf '{"deploymentState":"PUBLISHING"}\n' >"$2"
        return 0
      fi
      shift
    done
    return 2
  }
  export -f curl
  export RUNNER_TEMP="$work_dir" GITHUB_OUTPUT="$work_dir/portal.output" GITHUB_STEP_SUMMARY="$work_dir/portal.summary"
  export DEPLOYMENT_ID=00000000-0000-0000-0000-000000000001 MAVEN_CENTRAL_USERNAME=fixture MAVEN_CENTRAL_PASSWORD=fixture
  PORTAL_FIXTURE=PUBLISHING bash -c "$observe_command"
  grep -Fxq 'portal_state=PUBLISHING' "$GITHUB_OUTPUT"
  if PORTAL_FIXTURE=timeout bash -c "$observe_command"; then
    echo "Portal 查询超时应返回失败，由 continue-on-error 降级。" >&2
    exit 1
  fi
)

repository_dir="$work_dir/repository"
mkdir -p \
  "$repository_dir/io/github/yggdrasil-labs/mimir-boot/2.2.1" \
  "$repository_dir/io/github/yggdrasil-labs/mimir-boot/2.2.1-SNAPSHOT" \
  "$repository_dir/io/github/yggdrasil-labs/missing-pom/2.2.1"
pom_content='<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot</artifactId><version>2.2.1</version></project>'
printf '%s\n' "$pom_content" >"$repository_dir/io/github/yggdrasil-labs/mimir-boot/2.2.1/mimir-boot-2.2.1.pom"
printf '%s\n' "$pom_content" >"$repository_dir/io/github/yggdrasil-labs/mimir-boot/2.2.1-SNAPSHOT/mimir-boot-2.2.1-SNAPSHOT.pom"

if ! assert_fixture_published_artifact "$repository_dir" "2.2.1" mimir-boot; then
  echo "稳定版制品不应要求版本目录级 maven-metadata.xml。" >&2
  exit 1
fi

if assert_fixture_published_artifact "$repository_dir" "2.2.1" missing-pom; then
  echo "缺少 POM 的稳定版制品不应通过检查。" >&2
  exit 1
fi

printf '<metadata/>\n' >"$repository_dir/io/github/yggdrasil-labs/mimir-boot/2.2.1-SNAPSHOT/maven-metadata.xml"
if ! assert_fixture_published_artifact "$repository_dir" "2.2.1-SNAPSHOT" mimir-boot; then
  echo "带版本级元数据的 Snapshot 制品应通过检查。" >&2
  exit 1
fi

rm "$repository_dir/io/github/yggdrasil-labs/mimir-boot/2.2.1-SNAPSHOT/maven-metadata.xml"
if assert_fixture_published_artifact "$repository_dir" "2.2.1-SNAPSHOT" mimir-boot; then
  echo "缺少版本级 maven-metadata.xml 的 Snapshot 制品不应通过检查。" >&2
  exit 1
fi

echo "发布制品目录契约测试通过。"
