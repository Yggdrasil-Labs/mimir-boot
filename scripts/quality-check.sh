#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$project_root/scripts/lib/quality-snapshot.sh"
mode='' source_kind='' commit='' report='' list=false
comparison_base="${QUALITY_BASE_COMMIT:-}"
manifest="${QUALITY_SNAPSHOT_MANIFEST:-}"
fail() { echo "质量检查参数错误：$*" >&2; exit 2; }

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --list) list=true; shift ;;
    --mode|--source|--commit|--base|--report)
      [[ "$#" -ge 2 && -n "$2" && "$2" != --* ]] || fail "$1 缺少值"
      case "$1" in
        --mode) mode="$2" ;; --source) source_kind="$2" ;; --commit) commit="$2" ;;
        --base) comparison_base="$2" ;; --report) report="$2" ;;
      esac
      shift 2 ;;
    *) fail "未知参数：$1" ;;
  esac
done
[[ "$mode" == quick || "$mode" == full ]] || fail '--mode 必须为 quick 或 full'
[[ "$source_kind" == worktree || "$source_kind" == index || "$source_kind" == commit ]] || fail '--source 必须为 worktree、index 或 commit'
[[ "$source_kind" != commit || -n "$commit" ]] || fail 'commit 模式必须提供 --commit'
[[ "$source_kind" == commit || -z "$commit" ]] || fail '只有 commit 模式允许 --commit'
[[ -z "$manifest" || "$source_kind" == worktree ]] || fail '快照内禁止递归导出'
[[ -z "$report" || "$report" == /* ]] || fail '--report 必须是绝对路径'
[[ "$list" == false || "$source_kind" == worktree ]] || fail '--list 使用 source=worktree'

# quick 是日常 Hook 的离线入口，只可验证已准备的工程工具缓存；
# full 则允许在 CI 或显式验收时自动准备。
tool_operation=--ensure
if [[ "$mode" == quick ]]; then tool_operation=--verify; fi

if [[ "$list" == true ]]; then
  bash "$project_root/scripts/lib/engineering-tool.sh" "$tool_operation" >&2
  exec bash "$project_root/scripts/lib/engineering-tool.sh" src/quality/runner.mjs --mode "$mode" --source worktree --list
fi

if [[ -z "$report" ]]; then
  run_directory="${MIMIR_QUALITY_RUN_DIRECTORY:-$(mktemp -d "${TMPDIR:-/tmp}/mimir-quality-run.XXXXXX")}"
  report="$run_directory/quality-report.json"
else
  mkdir -p "$(dirname "$report")"
  run_directory="${MIMIR_QUALITY_RUN_DIRECTORY:-$(mktemp -d "$(dirname "$report")/quality-artifacts.XXXXXX")}"
fi
report="$(realpath -m -- "$report")"
run_directory="$(realpath -m -- "$run_directory")"
# 完整验收包含 clean；不能将运行清单和日志放到会被本次构建清空的目录。
case "$report:$run_directory" in *"$project_root/target/"*|*"$project_root/"*"/target/"*) fail '报告必须位于构建 target 目录之外' ;; esac
mkdir -p "$run_directory/logs"
if [[ -z "$manifest" ]]; then
  exec 8>"$report.lock"
  flock -n 8 || fail '报告路径正在被另一检查使用'
fi
rm -f -- "$report"

json_string() {
  local value="$1" char escaped code
  value="${value//\\/\\\\}"; value="${value//\"/\\\"}"
  for ((code=1; code<32; code++)); do
    printf -v char '%b' "\\$(printf '%03o' "$code")"
    printf -v escaped '\\u%04x' "$code"
    value="${value//"$char"/"$escaped"}"
  done
  printf '"%s"' "$value"
}

# 仅在 Node 无法启动时写最小错误证据；正常报告全部由 Node 生成。
bootstrap_error() {
  local stage="$1" reason="${2:-工程工具准备或快照失败，请检查 logs}"
  printf '{"schemaVersion":1,"source":%s,"reportDirectory":%s,"overall":"error","exitCode":2,"checks":[{"id":%s,"status":"error","required":true,"exitCode":2,"reason":%s,"command":[],"dependsOn":[]}]}\n' \
    "$(json_string "${effective_source:-$source_kind}")" "$(json_string "$run_directory")" "$(json_string "$stage")" "$(json_string "$reason")" > "$report"
}

if [[ "$source_kind" != worktree ]]; then
  set +e
  quality_snapshot_run "$project_root" "$mode" "$source_kind" "$commit" "$comparison_base" "$report" "$run_directory"
  result=$?
  set -e
  if [[ "$result" != 0 && ! -f "$report" ]]; then bootstrap_error snapshot; fi
  exit "$result"
fi

changed_file="$run_directory/changed-files.nul"
if [[ -n "$manifest" ]]; then
  [[ -f "$manifest" ]] || fail '缺少快照清单'
  mapfile -d '' -t snapshot < "$manifest"
  [[ "${snapshot[3]:-}" == "$project_root" && "${snapshot[0]:-}" =~ ^(index|commit)$ && "${snapshot[2]:-}" =~ ^[0-9a-f]+$ ]] || fail '快照清单不属于当前执行目录'
  effective_source="${snapshot[0]}"; effective_commit="${snapshot[1]}"; effective_tree="${snapshot[2]}"
  : > "$changed_file"
  if (( ${#snapshot[@]} > 4 )); then printf '%s\0' "${snapshot[@]:4}" > "$changed_file"; fi
else
  # 同一工作树的构建不能并发 clean；不同提交的独立快照不共享此锁。
  lock_key="$(printf '%s' "$project_root" | sha256sum | cut -d' ' -f1)"
  exec 7>"${TMPDIR:-/tmp}/mimir-quality-worktree-$lock_key.lock"
  flock -n 7 || fail '当前工作树正在运行另一质量检查'
  effective_source=worktree; effective_commit=null
  git -C "$project_root" ls-files --cached --others --exclude-standard -z | sort -zu > "$run_directory/files.nul"
  effective_tree="worktree-$(while IFS= read -r -d '' file; do
    printf '%s\0' "$file"
    if [[ -f "$project_root/$file" ]]; then sha256sum -- "$project_root/$file"; else printf '<deleted>'; fi
  done < "$run_directory/files.nul" | sha256sum | cut -d' ' -f1)"
  base="$(git -C "$project_root" rev-parse --verify HEAD 2>/dev/null)" || base="$(quality_snapshot_empty_tree "$project_root")"
  if [[ -n "$comparison_base" ]]; then
    base="$(git -C "$project_root" rev-parse --verify "${comparison_base}^{commit}" 2>/dev/null)" || base="$(quality_snapshot_empty_tree "$project_root")"
  fi
  { git -C "$project_root" diff --name-only --no-renames -z "$base"; git -C "$project_root" ls-files --others --exclude-standard -z; } | sort -zu > "$changed_file"
fi

bootstrap_started=$SECONDS
if ! bash "$project_root/scripts/lib/engineering-tool.sh" "$tool_operation" > "$run_directory/logs/engineering-bootstrap.log" 2>&1; then
  echo "工程工具准备失败：$run_directory/logs/engineering-bootstrap.log" >&2
  bootstrap_reason='工程工具准备或快照失败，请检查 logs'
  if [[ "$mode" == quick ]]; then
    bootstrap_reason='quick 门禁依赖的工程工具缓存不可用；请先运行 bash scripts/setup-dev.sh，然后检查 logs'
  fi
  bootstrap_error engineering-bootstrap "$bootstrap_reason"
  # 保留 Node 不可用时的独立 Maven 诊断能力，但本次门禁始终失败。
  if [[ "$mode" == full ]]; then
    (cd "$project_root" && ./mvnw -B -Pci clean verify) > "$run_directory/logs/java-fallback.log" 2>&1 || true
  fi
  exit 2
fi
echo "质量检查报告：$report"
exec bash "$project_root/scripts/lib/engineering-tool.sh" src/quality/runner.mjs \
  --mode "$mode" --source "$effective_source" --root "$project_root" \
  --commit "$effective_commit" --tree "$effective_tree" --changed-files "$changed_file" \
  --report "$report" --report-directory "$run_directory" \
  --bootstrap-log "$run_directory/logs/engineering-bootstrap.log" --bootstrap-duration "$(( (SECONDS - bootstrap_started) * 1000 ))"
