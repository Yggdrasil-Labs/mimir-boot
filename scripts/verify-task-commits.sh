#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "用法: $0 [--print-t12-files0|--allow-t12-index] <plan.md>" >&2
  exit 2
}

fail() {
  echo "任务提交校验失败: $*" >&2
  exit 1
}

contains() {
  local values="$1"
  local expected="$2"
  local value
  for value in $values; do
    [[ "$value" == "$expected" ]] && return 0
  done
  return 1
}

extract_code_path() {
  local value="$1"
  [[ "$value" == *\`*\`* ]] || return 1
  value="${value#*\`}"
  printf '%s\n' "${value%%\`*}"
}

extract_last_code_path() {
  local value="$1"
  local path=""

  while [[ "$value" == *\`*\`* ]]; do
    path="$(extract_code_path "$value")"
    value="${value#*\`$path\`}"
  done
  [[ -n "$path" ]] || return 1
  printf '%s\n' "$path"
}

json_array_values() {
  local value="$1"
  local key="$2"
  local payload
  local item

  [[ "$value" =~ \"$key\":\[([^]]*)\] ]] || return 0
  payload="${BASH_REMATCH[1]}"
  while [[ "$payload" =~ \"([^\"]+)\" ]]; do
    item="${BASH_REMATCH[1]}"
    printf '%s\n' "$item"
    payload="${payload#*\"$item\"}"
  done
}

mode="final"
case "${1:-}" in
  --print-t12-files0)
    mode="print"
    shift
    ;;
  --allow-t12-index)
    mode="index"
    shift
    ;;
esac
[[ "$#" == "1" ]] || usage

plan_input="$1"
[[ -f "$plan_input" ]] || fail "计划文件不存在: $plan_input"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || fail "当前目录不在 Git 仓库中"
plan_dir="$(cd "$(dirname "$plan_input")" && pwd)"
plan_abs="$plan_dir/$(basename "$plan_input")"
[[ "$plan_abs" == "$repo_root"/* || "$plan_abs" == "$repo_root" ]] || fail "计划文件不在仓库中: $plan_abs"
plan_path="${plan_abs#"$repo_root"/}"
cd "$repo_root"

baseline_sha="$(sed -nE 's/^\*\*Baseline SHA:\*\* ([0-9a-f]{40})$/\1/p' "$plan_path" | head -n1)"
implementation_head_sha="$(sed -nE 's/^\*\*Implementation Head SHA:\*\* ([0-9a-f]{40})$/\1/p' "$plan_path" | head -n1)"
[[ -n "$baseline_sha" ]] || fail "缺少 40 位 Baseline SHA"
[[ -n "$implementation_head_sha" ]] || fail "缺少 40 位 Implementation Head SHA"
git cat-file -e "$baseline_sha^{commit}" || fail "Baseline SHA 不存在: $baseline_sha"
git cat-file -e "$implementation_head_sha^{commit}" || fail "Implementation Head SHA 不存在: $implementation_head_sha"
git merge-base --is-ancestor "$baseline_sha" "$implementation_head_sha" \
  || fail "实施边界不是祖先关系: $baseline_sha..$implementation_head_sha"

declare -A primary_sha=()
declare -A supplemental_shas=()
declare -A allowed_paths=()
declare -A readonly_paths=()
declare -A conditional_modes=()
declare -A red_results=()
declare -a t12_paths=()
declare -A seen_t12_path=()
current_task=""
t12_commit_sha=""

while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^###\ T(1[0-2]|[1-9]): ]]; then
    current_task="${BASH_REMATCH[1]}"
    continue
  fi
  [[ -n "$current_task" ]] || continue

  if [[ "$line" == "- Modify only if authorized "* ]]; then
    path="$(extract_last_code_path "$line")" || fail "Task T$current_task 条件路径无法解析: $line"
    modes="${line#*- Modify only if authorized (}"
    modes="${modes%%):*}"
    modes="${modes//\`/}"
    modes="${modes// or / }"
    conditional_modes["$current_task|$path"]="$modes"
  elif [[ "$line" == "- Create:"* || "$line" == "- Modify:"* ]]; then
    path="$(extract_code_path "$line")" || fail "Task T$current_task 允许路径无法解析: $line"
    allowed_paths["$current_task"]+=" $path"
    if [[ "$current_task" == "12" && -z "${seen_t12_path[$path]:-}" ]]; then
      t12_paths+=("$path")
      seen_t12_path["$path"]=1
    fi
  elif [[ "$line" == "- Test (read-only baseline):"* ]]; then
    path="$(extract_code_path "$line")" || fail "Task T$current_task 只读路径无法解析: $line"
    readonly_paths["$current_task"]+=" $path"
  elif [[ "$line" == "- **Commit SHA:**"* ]]; then
    value="${line#*- **Commit SHA:** }"
    if [[ "$current_task" == "12" ]]; then
      t12_commit_sha="$value"
      continue
    fi
    [[ "$value" =~ ^[0-9a-f]{40}$ ]] || fail "Task T$current_task 缺少 40 位主提交 SHA: $value"
    primary_sha["$current_task"]="$value"
  elif [[ "$line" == "- **Supplemental Commit SHAs:**"* ]]; then
    value="${line#*- **Supplemental Commit SHAs:** }"
    while [[ "$value" =~ ([0-9a-f]{40}) ]]; do
      supplemental_shas["$current_task"]+=" ${BASH_REMATCH[1]}"
      value="${value#*${BASH_REMATCH[1]}}"
    done
  elif [[ "$line" == "- **Red Result:**"* ]]; then
    red_results["$current_task"]="${line#*- **Red Result:** }"
  fi
done <"$plan_path"

[[ "${#t12_paths[@]}" -gt 0 ]] || fail "T12 没有 Create/Modify 允许文件"
if [[ "$mode" == "print" ]]; then
  printf '%s\0' "${t12_paths[@]}"
  exit 0
fi

declare -A sha_owner=()
declare -A in_implementation_range=()
declare -a implementation_commits=()
mapfile -t implementation_commits < <(git rev-list --reverse "$baseline_sha..$implementation_head_sha")
[[ "${#implementation_commits[@]}" -gt 0 ]] || fail "实施区间没有提交: $baseline_sha..$implementation_head_sha"
for sha in "${implementation_commits[@]}"; do
  in_implementation_range["$sha"]=1
done

for task in $(seq 1 11); do
  sha="${primary_sha[$task]:-}"
  [[ -n "$sha" ]] || fail "Task T$task 缺少主提交 SHA"
  git cat-file -e "$sha^{commit}" || fail "Task T$task 主提交不存在: $sha"
  [[ -n "${in_implementation_range[$sha]:-}" ]] \
    || fail "Task T$task 主提交位于实施区间外: sha=$sha boundary=$baseline_sha..$implementation_head_sha"
  [[ -z "${sha_owner[$sha]:-}" ]] || fail "提交 SHA 被多个 Task 使用: $sha (T${sha_owner[$sha]} 与 T$task)"
  sha_owner["$sha"]="$task"

  previous_sha="$sha"
  for supplemental in ${supplemental_shas[$task]:-}; do
    git cat-file -e "$supplemental^{commit}" || fail "Task T$task 补充提交不存在: $supplemental"
    [[ -n "${in_implementation_range[$supplemental]:-}" ]] \
      || fail "Task T$task 补充提交位于实施区间外: sha=$supplemental boundary=$baseline_sha..$implementation_head_sha"
    [[ -z "${sha_owner[$supplemental]:-}" ]] || fail "补充提交重复归属: $supplemental"
    sha_owner["$supplemental"]="$task"
    previous_sha="$supplemental"
  done
done

for index in "${!implementation_commits[@]}"; do
  sha="${implementation_commits[$index]}"
  task="${sha_owner[$sha]:-}"
  [[ -n "$task" ]] || fail "实施区间存在未归属提交: $sha"
  if [[ "$sha" != "${primary_sha[$task]}" ]]; then
    previous_index=$((index - 1))
    [[ "$previous_index" -ge 0 ]] || fail "Task T$task 补充提交不紧随主提交: $sha"
    previous_sha="${implementation_commits[$previous_index]}"
    if [[ "$previous_sha" != "${primary_sha[$task]}" && ! " ${supplemental_shas[$task]:-} " == *" $previous_sha "* ]]; then
      fail "Task T$task 补充提交不紧随同一 Task 提交: $sha"
    fi
  fi
done

for task in $(seq 1 11); do
  for sha in "${primary_sha[$task]}" ${supplemental_shas[$task]:-}; do
    mapfile -t changed_paths < <(git diff-tree --no-commit-id --name-only -r "$sha")
    for path in "${changed_paths[@]}"; do
      if contains "${readonly_paths[$task]:-}" "$path"; then
        fail "Task T$task 修改只读基线路径: sha=$sha path=$path"
      fi
      if [[ "$path" == "$plan_path" ]]; then
        continue
      fi
      if contains "${allowed_paths[$task]:-}" "$path"; then
        continue
      fi
      modes="${conditional_modes["$task|$path"]:-}"
      if [[ -n "$modes" ]]; then
        [[ "$task" == "10" ]] || fail "非 T10 Task 使用条件授权路径: T$task path=$path"
        red_result="${red_results[10]:-}"
        [[ "$red_result" =~ \"failureKind\":\"contract\" ]] \
          || fail "T10 条件路径缺少 contract Decision Evidence: sha=$sha path=$path"
        [[ "$red_result" =~ \"command\":\"[^\"]+\" ]] \
          || fail "T10 条件路径缺少命令证据: sha=$sha path=$path"
        [[ "$red_result" =~ \"exitCode\":-?[1-9][0-9]* ]] \
          || fail "T10 条件路径 contract 证据必须为非零退出: sha=$sha path=$path"
        [[ "$red_result" =~ \"failsafeTests\":[1-9][0-9]* ]] \
          || fail "T10 条件路径 contract 证据必须有 Failsafe 测试: sha=$sha path=$path"
        mapfile -t authorizations < <(json_array_values "$red_result" runtimeChangeAuthorization)
        mapfile -t failed_contracts < <(json_array_values "$red_result" failedContracts)
        [[ "${#authorizations[@]}" -gt 0 ]] || fail "T10 条件路径没有运行时授权: sha=$sha path=$path"
        for authorization in "${authorizations[@]}"; do
          case "$authorization" in
            refresh-order|rollback|log-safety) ;;
            *) fail "T10 存在未知授权: $authorization" ;;
          esac
          contains "${failed_contracts[*]}" "$authorization" \
            || fail "T10 授权未对应失败契约: $authorization"
        done
        for failed_contract in "${failed_contracts[@]}"; do
          contains "${authorizations[*]}" "$failed_contract" \
            || fail "T10 失败契约未获得授权: $failed_contract"
        done
        authorized=false
        for required_mode in $modes; do
          if contains "${authorizations[*]}" "$required_mode"; then
            authorized=true
          fi
        done
        "$authorized" || fail "T10 路径授权不匹配: sha=$sha path=$path modes=$modes auth=${authorizations[*]}"
        continue
      fi
      fail "Task T$task 提交越出允许范围: sha=$sha path=$path allowed=${allowed_paths[$task]:-}"
    done
  done
done

if [[ "$mode" == "index" ]]; then
  [[ "$t12_commit_sha" == "final-record-exception" ]] \
    || fail "允许 T12 index 时 T12 Commit SHA 必须为 final-record-exception: actual=$t12_commit_sha"
  [[ "$(git rev-parse HEAD)" == "$implementation_head_sha" ]] \
    || fail "允许 T12 index 时 HEAD 必须等于 Implementation Head: expected=$implementation_head_sha actual=$(git rev-parse HEAD)"
  git diff --quiet || fail "允许 T12 index 时存在未暂存已跟踪修改"
  [[ -z "$(git ls-files --others --exclude-standard)" ]] || fail "允许 T12 index 时存在未跟踪文件"
  mapfile -t indexed_paths < <(git diff --cached --name-only)
  for path in "${indexed_paths[@]}"; do
    contains "${t12_paths[*]}" "$path" || fail "index 包含非 T12 文件: $path"
  done
  exit 0
fi

mapfile -t final_commits < <(git rev-list --reverse "$implementation_head_sha..HEAD")
[[ "$t12_commit_sha" == "final-record-exception" ]] \
  || fail "终验后 T12 Commit SHA 必须为 final-record-exception: actual=$t12_commit_sha"
[[ "${#final_commits[@]}" == "1" ]] \
  || fail "Implementation Head 之后必须恰好一个 T12 提交: count=${#final_commits[@]}"
mapfile -t final_paths < <(git diff-tree --no-commit-id --name-only -r "${final_commits[0]}")
for path in "${final_paths[@]}"; do
  contains "${t12_paths[*]}" "$path" \
    || fail "T12 终验提交包含越界文件: sha=${final_commits[0]} path=$path"
done
[[ -z "$(git status --porcelain)" ]] || fail "终验后工作区不干净"
