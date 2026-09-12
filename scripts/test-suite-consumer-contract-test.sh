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
if ! grep -Eq '(^|[[:space:]])if[[:space:]]+\[\[[[:space:]]*-f[[:space:]]+scripts/test-suite-consumer-contract-test[.]sh[[:space:]]*\]\][[:space:]]*;[[:space:]]*then([[:space:]]|$)' "$workflow" \
  || ! grep -Eq '(^|[[:space:]])bash[[:space:]]+scripts/test-suite-consumer-contract-test[.]sh([[:space:]]|$)' "$workflow"; then
  echo "release workflow 必须在脚本存在时执行发布制品目录契约测试。" >&2
  exit 1
fi

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
