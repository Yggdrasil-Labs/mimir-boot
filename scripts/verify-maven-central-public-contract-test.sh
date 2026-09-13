#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$script_dir/verify-maven-central-public.sh"
work_dir="$(mktemp -d -t mimir-public-central-contract.XXXXXX)"
repository_dir="$work_dir/repository"
port_file="$work_dir/port"
server_log="$work_dir/http-server.log"
mode_file="$work_dir/mode"
request_log="$work_dir/requests.log"
server_pid=""

cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -rf "$work_dir"
}
trap cleanup EXIT

create_pom() {
  local artifact="$1"
  local destination="$2"
  mkdir -p "$(dirname "$destination")"
  printf '%s\n' \
    '<project xmlns="http://maven.apache.org/POM/4.0.0">' \
    '  <modelVersion>4.0.0</modelVersion>' \
    '  <groupId>io.github.yggdrasil-labs</groupId>' \
    "  <artifactId>$artifact</artifactId>" \
    '  <version>2.2.2</version>' \
    '</project>' >"$destination"
}

root_pom_path="/io/github/yggdrasil-labs/mimir-boot/2.2.2/mimir-boot-2.2.2.pom"
starter_pom_path="/io/github/yggdrasil-labs/mimir-boot-starter-web/2.2.2/mimir-boot-starter-web-2.2.2.pom"
starter_jar_path="/io/github/yggdrasil-labs/mimir-boot-starter-web/2.2.2/mimir-boot-starter-web-2.2.2.jar"
root_pom_file="$repository_dir$root_pom_path"
starter_pom_file="$repository_dir$starter_pom_path"
starter_jar_file="$repository_dir$starter_jar_path"

create_pom mimir-boot "$root_pom_file"
create_pom mimir-boot-starter-web "$starter_pom_file"
jar_content_dir="$work_dir/jar-content"
mkdir -p "$jar_content_dir/META-INF" "$(dirname "$starter_jar_file")"
printf 'Manifest-Version: 1.0\n' >"$jar_content_dir/META-INF/MANIFEST.MF"
create_jar() {
  jar --create \
    --file "$starter_jar_file" \
    -C "$jar_content_dir" .
}

create_jar
printf 'normal\n' >"$mode_file"
: >"$request_log"

python3 - "$repository_dir" "$port_file" "$mode_file" "$request_log" >"$server_log" 2>&1 <<'PY' &
import http.server
import pathlib
import sys
import urllib.parse

repository, port_file, mode_file, request_log = sys.argv[1:]


class FixtureHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=repository, **kwargs)

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        with open(request_log, "a", encoding="utf-8") as file:
            file.write(path + "\n")

        mode = pathlib.Path(mode_file).read_text(encoding="utf-8").strip()
        previous_mode = getattr(self.server, "fixture_mode", None)
        if mode != previous_mode:
            self.server.fixture_mode = mode
            self.server.jar_requests_for_mode = 0

        if path.endswith(".jar"):
            self.server.jar_requests_for_mode += 1
            if mode == "jar-404-always" or (
                mode == "jar-404-once" and self.server.jar_requests_for_mode == 1
            ):
                self.send_error(404, "fixture configured JAR 404")
                return

        super().do_GET()


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), FixtureHandler)
with open(port_file, "w", encoding="utf-8") as file:
    file.write(str(server.server_port))
server.serve_forever()
PY
server_pid="$!"

for _ in $(seq 1 50); do
  if [[ -s "$port_file" ]]; then
    break
  fi
  sleep 0.1
done
if [[ ! -s "$port_file" ]]; then
  echo "本地 HTTP fixture 未在预期时间内启动。" >&2
  cat "$server_log" >&2 || true
  exit 1
fi

base_url="http://127.0.0.1:$(<"$port_file")"

run_verifier() {
  local attempts="$1"
  MIMIR_MAVEN_CENTRAL_BASE_URL="$base_url" \
    MIMIR_PUBLIC_VERIFY_ATTEMPTS="$attempts" \
    MIMIR_PUBLIC_VERIFY_INTERVAL_SECONDS=0 \
    bash "$script" 2.2.2
}

start_case() {
  local mode="$1"
  printf '%s\n' "$mode" >"$mode_file"
  : >"$request_log"
}

assert_request_count() {
  local request_path="$1"
  local expected_count="$2"
  local actual_count
  actual_count="$(awk -v expected_path="$request_path" '$0 == expected_path { count++ } END { print count + 0 }' "$request_log")"
  if [[ "$actual_count" -ne "$expected_count" ]]; then
    echo "fixture 请求次数不符合预期：$request_path，期望 $expected_count，实际 $actual_count。" >&2
    exit 1
  fi
}

start_case normal
run_verifier 1

create_pom unexpected-artifact "$root_pom_file"
start_case normal
if run_verifier 1; then
  echo "坐标不匹配的 POM 不应通过验证。" >&2
  exit 1
fi
create_pom mimir-boot "$root_pom_file"

printf '<project>' >"$root_pom_file"
start_case normal
if run_verifier 1; then
  echo "损坏的 XML POM 不应通过验证。" >&2
  exit 1
fi
create_pom mimir-boot "$root_pom_file"

printf 'not a JAR\n' >"$starter_jar_file"
start_case normal
if run_verifier 1; then
  echo "非 JAR 内容的 Starter 制品不应通过验证。" >&2
  exit 1
fi
create_jar

rm "$starter_jar_file"
start_case normal
if run_verifier 1; then
  echo "缺少 Starter JAR 的公开仓库不应通过验证。" >&2
  exit 1
fi
create_jar

start_case jar-404-once
if ! run_verifier 2; then
  echo "第一次 JAR 请求 404 后，第二次请求成功时应通过验证。" >&2
  exit 1
fi
assert_request_count "$root_pom_path" 2
assert_request_count "$starter_pom_path" 2
assert_request_count "$starter_jar_path" 2

start_case jar-404-always
if run_verifier 3; then
  echo "持续 404 的 Starter JAR 不应通过验证。" >&2
  exit 1
fi
assert_request_count "$root_pom_path" 3
assert_request_count "$starter_pom_path" 3
assert_request_count "$starter_jar_path" 3

echo "Maven Central 公开制品验证契约测试通过。"
