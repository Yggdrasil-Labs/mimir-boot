#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
mode=""
source_kind=""
commit=""
report=""
run_directory=""
manifest="${QUALITY_SNAPSHOT_MANIFEST:-}"

usage() {
  cat >&2 <<'EOF'
用法：bash scripts/quality-check.sh --mode <quick|full> --source <worktree|index|commit> [--commit <sha>] [--report <绝对 JSON 路径>]
EOF
}

fail_parameter() {
  echo "质量检查参数错误：$*" >&2
  usage
  exit 2
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --mode|--source|--commit|--report)
      [[ "$#" -ge 2 ]] || fail_parameter "$1 缺少值"
      case "$1" in
        --mode) mode="$2" ;;
        --source) source_kind="$2" ;;
        --commit) commit="$2" ;;
        --report) report="$2" ;;
      esac
      shift 2
      ;;
    *)
      fail_parameter "未知参数：$1"
      ;;
  esac
done

[[ "$mode" == quick || "$mode" == full ]] || fail_parameter '--mode 只允许 quick 或 full'
[[ "$source_kind" == worktree || "$source_kind" == index || "$source_kind" == commit ]] || fail_parameter '--source 只允许 worktree、index 或 commit'
if [[ "$source_kind" == commit && -z "$commit" ]]; then
  fail_parameter 'commit 模式必须提供 --commit'
fi
if [[ "$source_kind" != commit && -n "$commit" ]]; then
  fail_parameter '只有 commit 模式可以提供 --commit'
fi
if [[ -n "$manifest" && "$source_kind" != worktree ]]; then
  fail_parameter '已处于快照执行时只能使用 source=worktree，禁止递归导出'
fi
if [[ -n "$report" && "$report" != /* ]]; then
  fail_parameter '--report 必须是绝对路径'
fi

if [[ -z "$report" ]]; then
  run_directory="${MIMIR_QUALITY_RUN_DIRECTORY:-$(mktemp -d "${TMPDIR:-/tmp}/mimir-quality-run.XXXXXX")}"
  report="$run_directory/quality-report.json"
else
  run_directory="${MIMIR_QUALITY_RUN_DIRECTORY:-$(dirname "$report")/$(basename "${report%.json}")-artifacts}"
fi
if ! mkdir -p "$run_directory/logs"; then
  echo "无法创建质量检查报告目录：$run_directory" >&2
  exit 2
fi

if [[ "$source_kind" != worktree ]]; then
  source "$project_root/scripts/lib/quality-snapshot.sh"
  MIMIR_QUALITY_RUN_DIRECTORY="$run_directory" quality_snapshot_run "$project_root" "$mode" "$source_kind" "$commit" "$report"
  exit $?
fi

manifest_value() {
  local key="$1"
  python3 - "$manifest" "$project_root" "$key" <<'PY'
import json
import pathlib
import sys

manifest_path, root, key = sys.argv[1:]
payload = json.loads(pathlib.Path(manifest_path).read_text(encoding='utf-8'))
if payload.get('schemaVersion') != 1:
    raise SystemExit('快照清单 schemaVersion 无效')
if pathlib.Path(payload.get('executionRoot', '')).resolve() != pathlib.Path(root).resolve():
    raise SystemExit('快照清单不属于当前执行目录')
if payload.get('source') not in {'index', 'commit'} or not payload.get('tree'):
    raise SystemExit('快照清单缺少 source 或 tree')
if payload['source'] == 'commit' and not payload.get('commit'):
    raise SystemExit('commit 快照缺少 commit')
for relative in payload.get('files', []):
    target = pathlib.Path(root, relative).resolve()
    if pathlib.Path(root).resolve() not in target.parents or not target.is_file():
        raise SystemExit(f'快照清单文件不匹配执行目录：{relative}')
value = payload.get(key)
if value is None:
    print('null')
elif isinstance(value, str):
    print(value)
else:
    print(json.dumps(value, ensure_ascii=False))
PY
}

worktree_fingerprint() {
  python3 - "$project_root" <<'PY'
import hashlib
import pathlib
import subprocess
import sys

root = pathlib.Path(sys.argv[1])
files = subprocess.run(['git', '-C', str(root), 'ls-files', '-z'], check=True, stdout=subprocess.PIPE).stdout.split(b'\0')
digest = hashlib.sha256()
for raw in sorted(path for path in files if path):
    relative = raw.decode('utf-8')
    digest.update(raw + b'\0')
    digest.update((root / relative).read_bytes())
print(f'worktree-{digest.hexdigest()}')
PY
}

worktree_changed_files() {
  local output="$1" base raw
  raw="${output}.raw"
  if git -C "$project_root" rev-parse --verify --quiet HEAD >/dev/null; then
    base=HEAD
  else
    base="$(git -C "$project_root" hash-object -t tree /dev/null)"
  fi
  {
    git -C "$project_root" diff --name-only -z "$base"
    git -C "$project_root" diff --cached --name-only -z "$base"
    git -C "$project_root" ls-files --others --exclude-standard -z
  } >"$raw"
  python3 - "$raw" "$output" <<'PY'
import pathlib
import sys

source, destination = map(pathlib.Path, sys.argv[1:])
seen = set()
ordered = []
for value in source.read_bytes().split(b'\0'):
    if value and value not in seen:
        seen.add(value)
        ordered.append(value)
destination.write_bytes(b'\0'.join(ordered) + (b'\0' if ordered else b''))
source.unlink()
PY
}

classify_changes() {
  local changed_file="$1"
  python3 - "$manifest" "$changed_file" <<'PY'
import json
import pathlib
import sys

manifest, changed_file = sys.argv[1:]
if manifest:
    paths = json.loads(pathlib.Path(manifest).read_text(encoding='utf-8'))['changedFiles']
else:
    paths = [value.decode('utf-8') for value in pathlib.Path(changed_file).read_bytes().split(b'\0') if value]

control_files = {
    'scripts/quality-check.sh',
    'scripts/docs-tool.sh',
    'scripts/setup-dev.sh',
    'scripts/ci-preflight.sh',
    'scripts/test-suite-consumer-contract-test.sh',
    'scripts/verify-maven-central-public-contract-test.sh',
    'mvnw',
}

def is_control(path):
    return (
        path.startswith('tools/docs-check/')
        or path in control_files
        or path.startswith('scripts/lib/')
        or path.startswith('.githooks/')
        or path.startswith('.mvn/')
        or path == 'pom.xml'
        or path.endswith('/pom.xml')
        or path.startswith('.markdownlint')
        or 'spotless' in path.lower()
    )

docs = any(path.endswith('.md') or path.startswith('tools/docs-check/') or path.startswith('.markdownlint') or is_control(path) for path in paths)
java = any(path.endswith('.java') or path == 'pom.xml' or path.endswith('/pom.xml') or is_control(path) for path in paths)
print(f'docs={str(docs).lower()}')
print(f'java={str(java).lower()}')
PY
}

if [[ -n "$manifest" ]]; then
  [[ -f "$manifest" ]] || fail_parameter "找不到快照清单：$manifest"
  effective_source="$(manifest_value source)" || fail_parameter '无法读取快照清单'
  effective_commit="$(manifest_value commit)" || fail_parameter '无法读取快照清单'
  effective_tree="$(manifest_value tree)" || fail_parameter '无法读取快照清单'
  [[ "$effective_commit" == null ]] && effective_commit=""
  changed_file=""
else
  effective_source=worktree
  effective_commit=""
  effective_tree="$(worktree_fingerprint)" || fail_parameter '无法计算工作区状态指纹'
  changed_file="$run_directory/changed-files.nul"
  worktree_changed_files "$changed_file" || fail_parameter '无法读取工作区变更'
fi

bootstrap_log="$run_directory/logs/docs-bootstrap.log"
set +e
(cd "$project_root" && ./mvnw -N -Pdocs-check process-resources) >"$bootstrap_log" 2>&1
bootstrap_exit=$?
set -e
if [[ "$bootstrap_exit" -ne 0 ]]; then
  echo "质量检查无法准备 Maven 托管文档工具，详见 $bootstrap_log" >&2
  exit 2
fi

managed_node="$project_root/tools/docs-check/.maven-node/node/node"
result_tool="$project_root/tools/docs-check/quality-result.mjs"
[[ -x "$managed_node" && -f "$result_tool" ]] || { echo '质量检查缺少 Maven 托管 Node 或结果汇总器。' >&2; exit 2; }
run_id="quality-$(date -u +%Y%m%dT%H%M%SZ)-$$"
"$managed_node" "$result_tool" init --report "$report" --report-directory "$run_directory" --root "$project_root" --run-id "$run_id" --source "$effective_source" --commit "${effective_commit:-null}" --tree "$effective_tree"

json_array() {
  python3 - "$@" <<'PY'
import json
import sys
print(json.dumps(sys.argv[1:], ensure_ascii=False))
PY
}

append_check() {
  local id="$1" status="$2" required="$3" command_json="$4" depends_json="$5" log_path="$6" exit_code="$7" duration_ms="$8" reason="$9"
  "$managed_node" "$result_tool" append \
    --report "$report" \
    --id "$id" \
    --status "$status" \
    --required "$required" \
    --command "$command_json" \
    --depends-on "$depends_json" \
    --log "$log_path" \
    --exit-code "$exit_code" \
    --duration-ms "$duration_ms" \
    --reason "$reason"
}

append_not_applicable() {
  append_check "$1" not_applicable false '[]' '[]' null null 0 null
}

run_check() {
  local id="$1" required="$2" depends_json="$3"
  shift 3
  local log_path="$run_directory/logs/$id.log" started finished duration exit_code status reason command_json
  command_json="$(json_array "$@")"
  started="$(date +%s%3N)"
  set +e
  (cd "$project_root" && "$@") >"$log_path" 2>&1
  exit_code=$?
  set -e
  finished="$(date +%s%3N)"
  duration=$((finished - started))
  if [[ "$exit_code" -eq 0 ]]; then
    status=passed
    reason=null
  elif [[ "$exit_code" -eq 1 ]]; then
    status=failed
    reason="检查返回普通失败，详见 $log_path"
  else
    status=error
    reason="检查返回执行错误（exit=$exit_code），详见 $log_path"
  fi
  append_check "$id" "$status" "$required" "$command_json" "$depends_json" "$log_path" "$exit_code" "$duration" "$reason"
  RUN_CHECK_EXIT="$exit_code"
}

append_check docs-bootstrap passed true "$(json_array ./mvnw -N -Pdocs-check process-resources)" '[]' "$bootstrap_log" 0 0 null

if [[ "$mode" == quick ]]; then
  categories="$(classify_changes "$changed_file")" || fail_parameter '无法判定快照变更类别'
  docs_required="$(awk -F= '$1 == "docs" { print $2 }' <<<"$categories")"
  java_required="$(awk -F= '$1 == "java" { print $2 }' <<<"$categories")"
  if [[ "$docs_required" == true ]]; then
    run_check docs-format true '[]' bash scripts/docs-tool.sh check.mjs --root "$project_root" --mode format --report "$run_directory/docs-report.json"
  else
    append_not_applicable docs-format
  fi
  if [[ "$java_required" == true ]]; then
    run_check java-format true '[]' ./mvnw -Pci spotless:check
  else
    append_not_applicable java-format
  fi
else
  run_check docs-full true '[]' bash scripts/docs-tool.sh check.mjs --root "$project_root" --mode full --report "$run_directory/docs-report.json"
  run_check docs-self-test true '["docs-full"]' "$managed_node" --test --test-reporter spec tools/docs-check/test/toolchain.test.mjs tools/docs-check/test/docs-check.test.mjs
  run_check java-quality true '[]' bash scripts/ci-preflight.sh
  if [[ "$RUN_CHECK_EXIT" -eq 0 ]]; then
    append_check java-tests-and-coverage passed true '[]' '["java-quality"]' null 0 0 null
  else
    append_check java-tests-and-coverage not_run true '[]' '["java-quality"]' null null 0 'Java 完整构建未完成，测试和覆盖率结果不能作为通过证据'
  fi
  run_check release-test-suite-contract true '[]' bash scripts/test-suite-consumer-contract-test.sh
  run_check release-public-contract true '[]' bash scripts/verify-maven-central-public-contract-test.sh
fi

set +e
"$managed_node" "$result_tool" finalize --report "$report"
final_exit=$?
set -e
echo "质量检查报告：$report"
exit "$final_exit"
