#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
mode=""
source_kind=""
commit=""
comparison_base="${QUALITY_BASE_COMMIT:-}"
report=""
run_directory=""
manifest="${QUALITY_SNAPSHOT_MANIFEST:-}"

usage() {
  cat >&2 <<'EOF'
用法：bash scripts/quality-check.sh --mode <quick|full> --source <worktree|index|commit> [--commit <sha>] [--base <sha>] [--report <绝对 JSON 路径>]
EOF
}

fail_parameter() {
  echo "质量检查参数错误：$*" >&2
  usage
  exit 2
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --mode|--source|--commit|--base|--report)
      [[ "$#" -ge 2 ]] || fail_parameter "$1 缺少值"
      case "$1" in
        --mode) mode="$2" ;;
        --source) source_kind="$2" ;;
        --commit) commit="$2" ;;
        --base) comparison_base="$2" ;;
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
  mkdir -p "$(dirname "$report")" || exit 2
  run_directory="${MIMIR_QUALITY_RUN_DIRECTORY:-$(mktemp -d "$(dirname "$report")/quality-artifacts.XXXXXX")}" || exit 2
fi
if ! mkdir -p "$run_directory/logs"; then
  echo "无法创建质量检查报告目录：$run_directory" >&2
  exit 2
fi

# 相同显式报告路径不可并发写入，也不可把上次成功报告当成本次证据。
if [[ -z "${MIMIR_QUALITY_RUN_DIRECTORY:-}" ]]; then
  exec 8>"$report.lock" || exit 2
  flock -n 8 || { echo '报告路径正在被另一检查使用' >&2; exit 2; }
fi
rm -f -- "$report" || exit 2

json_string() {
  local value="$1" char escaped code
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  for ((code=1; code<32; code++)); do
    printf -v char '%b' "\\$(printf '%03o' "$code")"
    printf -v escaped '\\u%04x' "$code"
    value="${value//"$char"/"$escaped"}"
  done
  printf '"%s"' "$value"
}

shell_report() {
  local stage="$1" source="$2" result="${3:-2}" name code status=error reason='准备失败，请检查本次日志'
  [[ "$result" == 0 ]] && { status=passed; reason=''; }
  [[ "$result" == 1 ]] && { status=failed; reason='格式检查失败，请检查本次日志'; }
  (( result <= 2 )) || result=2
  local command='[]' log=null
  [[ "$stage" == java-format ]] && command='["./mvnw","-Pci","spotless:check"]'
  [[ -f "$run_directory/logs/$stage.log" ]] && log="$(json_string "$run_directory/logs/$stage.log")"
  {
    printf '{"schemaVersion":1,"source":%s,"reportDirectory":%s,"overall":"%s","exitCode":%s,' "$(json_string "$source")" "$(json_string "$run_directory")" "$status" "$result"
    printf '"commit":%s,"tree":%s,"checks":[' "$(if [[ -n "${effective_commit:-}" ]]; then json_string "$effective_commit"; else printf null; fi)" "$(if [[ -n "${effective_tree:-}" ]]; then json_string "$effective_tree"; else printf null; fi)"
    printf '{"id":%s,"status":"%s","required":true,"exitCode":%s,"reason":%s,"command":%s,"dependsOn":[],"logPath":%s,"durationMs":0}' "$(json_string "$stage")" "$status" "$result" "$(if [[ -z "$reason" ]]; then printf null; else json_string "$reason"; fi)" "$command" "$log"
    if [[ -f "$run_directory/independent.tsv" ]]; then
      while IFS=$'\t' read -r name code; do
        command='["bash",'"$(json_string "scripts/$name")"
        [[ "$name" != ci-preflight.sh ]] || command+=',"--maven-only"'
        command+=']'
        status=error; [[ "$code" == 0 ]] && status=passed; [[ "$code" == 1 ]] && status=failed
        printf ',{"id":%s,"status":"%s","required":true,"exitCode":%s,"command":%s,"dependsOn":[],"logPath":%s,"durationMs":0,"reason":%s}' "$(json_string "$name")" "$status" "$code" "$command" "$(json_string "$run_directory/logs/$name.log")" "$(if [[ "$code" == 0 ]]; then printf null; else json_string '独立检查未通过'; fi)"
      done < "$run_directory/independent.tsv"
    fi
    printf ']}\n'
  } > "$report"
}

quality_snapshot_fail() {
  echo "质量快照错误：$*" >&2
  return 2
}

quality_snapshot_empty_tree() {
  git -C "$1" hash-object -t tree /dev/null
}

quality_snapshot_changed_files() {
  local root="$1" source="$2" output="$3" base
  if [[ "$source" == commit ]]; then
    if [[ -n "$comparison_base" ]] && git -C "$root" rev-parse --verify "${comparison_base}^{commit}" >/dev/null 2>&1; then
      git -C "$root" diff --name-only --no-renames -z "$comparison_base" "$commit" > "$output"
    else
      # 新引用或基线不可用时检查完整文件清单，避免漏掉较早提交。
      git -C "$root" ls-tree -r -z --name-only "$commit" > "$output"
    fi
    return
  fi
  if git -C "$root" rev-parse --verify --quiet HEAD >/dev/null; then
    base=HEAD
  else
    base="$(quality_snapshot_empty_tree "$root")"
  fi
  git -C "$root" diff --cached --name-only --no-renames -z "$base" >"$output"
}

quality_snapshot_run() (
  set -euo pipefail
  local root="$1" mode="$2" source="$3" commit="$4" report="$5"
  local tree temporary snapshot_root files_nul changed_nul manifest target_script exit_code variable file
  git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1 || { quality_snapshot_fail "source=$source 需要 Git 工作树"; return 2; }
  if [[ "$source" == index ]]; then
    tree="$(git -C "$root" write-tree)" || { quality_snapshot_fail '无法导出索引，请解决未合并文件'; return 2; }
    commit=null
  else
    commit="$(git -C "$root" rev-parse --verify "${commit}^{commit}")" || { quality_snapshot_fail '无法解析提交'; return 2; }
    tree="$(git -C "$root" rev-parse "${commit}^{tree}")" || return 2
  fi
  temporary="$(mktemp -d "${TMPDIR:-/tmp}/mimir-quality-snapshot.XXXXXX")" || return 2
  trap 'rm -rf "$temporary"' EXIT
  trap 'exit 2' INT TERM
  snapshot_root="$temporary/root"
  mkdir "$snapshot_root"
  files_nul="$temporary/files.nul"
  changed_nul="$temporary/changed.nul"
  manifest="$temporary/quality-snapshot.json"
  git -C "$root" ls-tree -r -z --name-only "$tree" >"$files_nul" || return 2
  quality_snapshot_changed_files "$root" "$source" "$changed_nul" || return 2
  git -C "$root" archive --format=tar "$tree" | tar -x -C "$snapshot_root" || { quality_snapshot_fail '导出目标树失败'; return 2; }
  while IFS= read -r -d '' file; do
    [[ -f "$snapshot_root/$file" && "$(realpath -m "$snapshot_root/$file")" == "$snapshot_root/"* ]] || { quality_snapshot_fail "导出文件缺失或越界：$file"; return 2; }
  done < "$files_nul"
  printf '%s\0' "$source" "$commit" "$tree" "$snapshot_root" > "$manifest"
  cat "$changed_nul" >> "$manifest"
  target_script="$snapshot_root/scripts/quality-check.sh"
  [[ -f "$target_script" ]] || { quality_snapshot_fail '目标树缺少 scripts/quality-check.sh'; return 2; }
  # 保留原仓库的工具缓存位置，执行时清除 Git 注入的仓库变量。
  while IFS= read -r variable; do unset "$variable"; done < <(git -C "$root" rev-parse --local-env-vars)
  cd "$snapshot_root"
  set +e
  QUALITY_ORIGIN_ROOT="$root" QUALITY_SNAPSHOT_MANIFEST="$manifest" bash "$target_script" --mode "$mode" --source worktree --report "$report"
  exit_code=$?
  [[ "$exit_code" -le 2 ]] || exit_code=2
  return "$exit_code"
)

if [[ "$source_kind" != worktree ]]; then
  set +e
  MIMIR_QUALITY_RUN_DIRECTORY="$run_directory" quality_snapshot_run "$project_root" "$mode" "$source_kind" "$commit" "$report"
  snapshot_exit=$?
  set -e
  if [[ "$snapshot_exit" -ne 0 && ! -f "$report" ]]; then
    shell_report snapshot "$source_kind"
  fi
  exit "$snapshot_exit"
fi

worktree_fingerprint() {
  (
    cd "$project_root"
    git ls-files --cached --others --exclude-standard -z | sort -zu | while IFS= read -r -d '' file; do
      printf '%s\0' "$file"
      if [[ -f "$file" ]]; then sha256sum -- "$file"; else printf '<deleted>'; fi
    done
  ) | sha256sum | cut -d' ' -f1
}

classify_changes() {
  local file docs=false java=false release=false
  while IFS= read -r -d '' file; do
    # 仅包含两项发布契约自测直接读取、执行的文件。
    case "$file" in
      pom.xml|.github/workflows/release.yml|scripts/lib/published-artifact-contract.sh|\
      scripts/test-suite-consumer.sh|scripts/test-suite-consumer-contract-test.sh|\
      scripts/verify-maven-central-public.sh|scripts/verify-maven-central-public-contract-test.sh) release=true ;;
    esac
    case "$file" in
      tools/*|scripts/*.sh|scripts/lib/*|.githooks/*|.mvn/*|mvnw|pom.xml|*/pom.xml|.markdownlint*|*spotless*) docs=true; java=true ;;
      *.md|*.markdown|*.mdx) docs=true ;;
      *.java) java=true ;;
    esac
  done < "$1"
  printf 'docs=%s\njava=%s\nrelease=%s\n' "$docs" "$java" "$release"
}

changed_file="$run_directory/changed-files.nul"
if [[ -n "$manifest" ]]; then
  [[ -f "$manifest" ]] || fail_parameter "找不到快照清单：$manifest"
  mapfile -d '' -t snapshot < "$manifest"
  [[ "${snapshot[3]:-}" == "$project_root" && "${snapshot[0]:-}" =~ ^(index|commit)$ && "${snapshot[2]:-}" =~ ^[0-9a-f]+$ ]] || fail_parameter '快照清单不属于当前执行目录'
  effective_source="${snapshot[0]}"
  effective_commit="${snapshot[1]}"
  effective_tree="${snapshot[2]}"
  [[ "$effective_commit" == null ]] && effective_commit=""
  : > "$changed_file"
  if (( ${#snapshot[@]} > 4 )); then printf '%s\0' "${snapshot[@]:4}" > "$changed_file"; fi
else
  effective_source=worktree
  effective_commit=""
  effective_tree="worktree-$(worktree_fingerprint)" || fail_parameter '无法计算工作区状态指纹'
  base="$(git -C "$project_root" rev-parse --verify HEAD 2>/dev/null)" || base="$(git -C "$project_root" hash-object -t tree /dev/null)"
  if [[ -n "$comparison_base" ]]; then
    base="$(git -C "$project_root" rev-parse --verify "${comparison_base}^{commit}" 2>/dev/null)" || base="$(git -C "$project_root" hash-object -t tree /dev/null)"
  elif [[ "${CI:-false}" == true ]]; then
    base="$(git -C "$project_root" hash-object -t tree /dev/null)"
  fi
  { git -C "$project_root" diff --name-only --no-renames -z "$base"; git -C "$project_root" ls-files --others --exclude-standard -z; } | sort -zu > "$changed_file"
fi

categories="$(classify_changes "$changed_file")"
docs_required="$(awk -F= '$1 == "docs" { print $2 }' <<<"$categories")"
java_required="$(awk -F= '$1 == "java" { print $2 }' <<<"$categories")"
release_required="$(awk -F= '$1 == "release" { print $2 }' <<<"$categories")"

# 纯 Java 提交直接使用 Maven；不为格式检查准备 Node 或文档依赖。
if [[ "$mode" == quick && "$docs_required" == false ]]; then
  quick_exit=0
  if [[ "$java_required" == true ]]; then
    set +e
    (cd "$project_root" && ./mvnw -Pci spotless:check) > "$run_directory/logs/java-format.log" 2>&1
    quick_exit=$?
    set -e
    shell_report java-format "$effective_source" "$quick_exit"
  else
    shell_report quick-no-changes "$effective_source" 0
  fi
  echo "质量检查报告：$report"
  (( quick_exit <= 2 )) || quick_exit=2
  exit "$quick_exit"
fi

bootstrap_log="$run_directory/logs/docs-bootstrap.log"
bootstrap_started="$(date +%s%3N)"
set +e
(cd "$project_root" && bash scripts/docs-tool.sh --ensure) >"$bootstrap_log" 2>&1
bootstrap_exit=$?
set -e
if [[ "$bootstrap_exit" -ne 0 ]]; then
  echo "文档工具准备失败，详见 $bootstrap_log" >&2
  # 没有 Node 时仍执行独立 Java/发布检查，用 Bash 写最小失败证据。
  if [[ "$mode" == full ]]; then
    independent_commands=(ci-preflight.sh)
    if [[ "$release_required" == true ]]; then independent_commands+=(test-suite-consumer-contract-test.sh verify-maven-central-public-contract-test.sh); fi
    for command in "${independent_commands[@]}"; do
      command_args=()
      [[ "$command" != ci-preflight.sh ]] || command_args=(--maven-only)
      set +e
      (cd "$project_root" && QUALITY_REPORT_DIR="$run_directory/java" bash "scripts/$command" "${command_args[@]}") >"$run_directory/logs/$command.log" 2>&1
      code=$?
      set -e
      printf '%s\t%s\n' "$command" "$code" >> "$run_directory/independent.tsv"
    done
  fi
  shell_report docs-bootstrap "$effective_source"
  exit 2
fi

managed_node="$project_root/tools/docs-check/.maven-node/node/node"
result_tool="$project_root/tools/docs-check/src/quality/results.mjs"
[[ -x "$managed_node" && -f "$result_tool" ]] || { echo '质量检查缺少 Maven 托管 Node 或结果汇总器。' >&2; exit 2; }
run_id="quality-$(date -u +%Y%m%dT%H%M%SZ)-$$"
"$managed_node" "$result_tool" init --report "$report" --report-directory "$run_directory" --root "$project_root" --run-id "$run_id" --source "$effective_source" --commit "${effective_commit:-null}" --tree "$effective_tree"

json_array() {
  "$managed_node" -e 'console.log(JSON.stringify(process.argv.slice(1)))' "$@"
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

append_check docs-bootstrap passed true "$(json_array bash scripts/docs-tool.sh --ensure)" '[]' "$bootstrap_log" 0 "$(( $(date +%s%3N) - bootstrap_started ))" null

if [[ "$mode" == quick ]]; then
  if [[ "$docs_required" == true ]]; then
    run_check docs-format true '[]' bash scripts/docs-tool.sh src/docs/check.mjs --root "$project_root" --mode format --report "$run_directory/docs-report.json"
  else
    append_not_applicable docs-format
  fi
  if [[ "$java_required" == true ]]; then
    run_check java-format true '[]' ./mvnw -Pci spotless:check
  else
    append_not_applicable java-format
  fi
else
  run_check docs-full true '[]' bash scripts/docs-tool.sh src/docs/check.mjs --root "$project_root" --mode full --report "$run_directory/docs-report.json"
  export QUALITY_REPORT_DIR="$run_directory/java"
  run_check java-quality true '[]' env MIMIR_DOCS_READY_ROOT="$project_root" bash scripts/ci-preflight.sh
  java_exit="$RUN_CHECK_EXIT"
  for stage in tests coverage sonar; do
    stage_status="$("$managed_node" -e 'const fs=require("fs"); const [file,key]=process.argv.slice(1); try { console.log(JSON.parse(fs.readFileSync(file))[key+"Status"] || "not_run"); } catch { console.log("not_run"); }' "$QUALITY_REPORT_DIR/java-quality-report.json" "$stage")"
    case "$stage_status" in
      passed) stage_code=0; stage_reason=null ;;
      failed) stage_code=1; stage_reason='检查失败，详见 Java 报告' ;;
      error) stage_code=2; stage_reason='检查执行错误，详见 Java 报告' ;;
      not_applicable) stage_code=null; stage_reason='当前环境不适用' ;;
      *) stage_status=not_run; stage_code=null; stage_reason='前置阶段未完成，详见 Java 报告' ;;
    esac
    append_check "java-$stage" "$stage_status" true '[]' '["java-quality"]' null "$stage_code" 0 "$stage_reason"
  done
  if [[ "$release_required" == true ]]; then
    run_check release-test-suite-contract true '[]' bash scripts/test-suite-consumer-contract-test.sh
    run_check release-public-contract true '[]' bash scripts/verify-maven-central-public-contract-test.sh
  else
    append_not_applicable release-test-suite-contract
    append_not_applicable release-public-contract
  fi
fi

set +e
"$managed_node" "$result_tool" finalize --report "$report"
final_exit=$?
set -e
echo "质量检查报告：$report"
exit "$final_exit"
