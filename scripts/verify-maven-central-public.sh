#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "用法：$0 <发布版本号>" >&2
  exit 2
fi

version="$1"
if [[ ! "$version" =~ ^[0-9]+[.][0-9]+[.][0-9]+$ ]]; then
  echo "发布版本号必须是 x.y.z 格式：$version" >&2
  exit 2
fi

base_url="${MIMIR_MAVEN_CENTRAL_BASE_URL:-https://repo.maven.apache.org/maven2}"
base_url="${base_url%/}"
attempts="${MIMIR_PUBLIC_VERIFY_ATTEMPTS:-8}"
interval_seconds="${MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS:-30}"

if [[ ! "$attempts" =~ ^[1-9][0-9]*$ ]]; then
  echo "MIMIR_PUBLIC_VERIFY_ATTEMPTS 必须是正整数：$attempts" >&2
  exit 2
fi
if [[ ! "$interval_seconds" =~ ^[0-9]+$ ]]; then
  echo "MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS 必须是非负整数：$interval_seconds" >&2
  exit 2
fi

work_dir="$(mktemp -d -t mimir-public-central.XXXXXX)"
trap 'rm -rf "$work_dir"' EXIT

artifact_url() {
  local artifact="$1"
  local extension="$2"
  printf '%s/io/github/yggdrasil-labs/%s/%s/%s-%s.%s\n' \
    "$base_url" "$artifact" "$version" "$artifact" "$version" "$extension"
}

download_artifact() {
  local artifact="$1"
  local extension="$2"
  local destination="$3"
  local url
  url="$(artifact_url "$artifact" "$extension")"

  echo "下载公开 Maven Central 制品：$url"
  curl --fail --location --silent --show-error \
    --connect-timeout 5 --max-time 15 \
    --output "$destination" "$url"
}

validate_pom() {
  local pom_file="$1"
  local expected_artifact="$2"
  python3 - "$pom_file" "$expected_artifact" "$version" <<'PY'
import sys
import xml.etree.ElementTree as element_tree

pom_file, expected_artifact, expected_version = sys.argv[1:]
namespace = "{http://maven.apache.org/POM/4.0.0}"
root = element_tree.parse(pom_file).getroot()
parent = root.find(f"{namespace}parent")


def value(name: str) -> str | None:
    direct = root.findtext(f"{namespace}{name}")
    if direct:
        return direct.strip()
    if parent is not None:
        inherited = parent.findtext(f"{namespace}{name}")
        if inherited:
            return inherited.strip()
    return None


actual_group = value("groupId")
actual_artifact = root.findtext(f"{namespace}artifactId")
actual_version = value("version")
if (
    actual_group != "io.github.yggdrasil-labs"
    or actual_artifact != expected_artifact
    or actual_version != expected_version
):
    raise SystemExit(
        "POM 坐标不匹配："
        f"期望 io.github.yggdrasil-labs:{expected_artifact}:{expected_version}，"
        f"实际 {actual_group}:{actual_artifact}:{actual_version}"
    )
PY
}

verify_once() {
  local root_pom="$work_dir/mimir-boot.pom"
  local starter_pom="$work_dir/mimir-boot-starter-web.pom"
  local starter_jar="$work_dir/mimir-boot-starter-web.jar"

  rm -f "$root_pom" "$starter_pom" "$starter_jar"

  download_artifact mimir-boot pom "$root_pom" || return 1
  validate_pom "$root_pom" mimir-boot || return 1
  download_artifact mimir-boot-starter-web pom "$starter_pom" || return 1
  validate_pom "$starter_pom" mimir-boot-starter-web || return 1
  download_artifact mimir-boot-starter-web jar "$starter_jar" || return 1
  jar tf "$starter_jar" >/dev/null || return 1
}

for attempt in $(seq 1 "$attempts"); do
  echo "公开制品验证，第 $attempt/$attempts 次尝试。"
  if verify_once; then
    echo "Maven Central 公开制品验证通过：$version"
    exit 0
  fi

  if [[ "$attempt" -lt "$attempts" ]]; then
    echo "公开制品尚不可用；$interval_seconds 秒后重试。" >&2
    sleep "$interval_seconds"
  fi
done

echo "Maven Central 公开制品在 $attempts 次尝试后仍不可用：$version" >&2
exit 1
