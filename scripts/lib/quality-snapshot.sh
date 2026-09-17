#!/usr/bin/env bash

# quality-check.sh 只负责参数、准备和调度；Git 目标树导出及清单协议集中在这里。

quality_snapshot_fail() {
  echo "质量快照错误：$*" >&2
  return 2
}

quality_snapshot_empty_tree() {
  git -C "$1" hash-object -t tree /dev/null
}

quality_snapshot_changed_files() {
  local root="$1" source="$2" output="$3" comparison_base="$4" commit="$5" base
  if [[ "$source" == commit ]]; then
    if [[ -n "$comparison_base" ]] && git -C "$root" rev-parse --verify "${comparison_base}^{commit}" >/dev/null 2>&1; then
      git -C "$root" diff --name-only --no-renames -z "$comparison_base" "$commit" >"$output"
    else
      # 新引用或基线不可用时检查完整目标树，避免漏掉较早提交。
      git -C "$root" ls-tree -r -z --name-only "$commit" >"$output"
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
  local root="$1" mode="$2" source="$3" commit="$4" comparison_base="$5" report="$6" run_directory="$7"
  local tree snapshot_commit temporary snapshot_root files_nul changed_nul manifest target_script exit_code variable file

  git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || { quality_snapshot_fail "source=$source 需要 Git 工作树"; return 2; }
  if [[ "$source" == index ]]; then
    tree="$(git -C "$root" write-tree)" \
      || { quality_snapshot_fail '无法导出索引，请解决未合并文件'; return 2; }
    snapshot_commit=null
  else
    snapshot_commit="$(git -C "$root" rev-parse --verify "${commit}^{commit}")" \
      || { quality_snapshot_fail '无法解析提交'; return 2; }
    tree="$(git -C "$root" rev-parse "${snapshot_commit}^{tree}")" || return 2
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
  quality_snapshot_changed_files "$root" "$source" "$changed_nul" "$comparison_base" "$snapshot_commit" || return 2
  git -C "$root" archive --format=tar "$tree" | tar -x -C "$snapshot_root" \
    || { quality_snapshot_fail '导出目标树失败'; return 2; }

  while IFS= read -r -d '' file; do
    [[ -f "$snapshot_root/$file" && "$(realpath -m "$snapshot_root/$file")" == "$snapshot_root/"* ]] \
      || { quality_snapshot_fail "导出文件缺失或越界：$file"; return 2; }
  done <"$files_nul"

  printf '%s\0' "$source" "$snapshot_commit" "$tree" "$snapshot_root" >"$manifest"
  cat "$changed_nul" >>"$manifest"
  target_script="$snapshot_root/scripts/quality-check.sh"
  [[ -f "$target_script" ]] || { quality_snapshot_fail '目标树缺少 scripts/quality-check.sh'; return 2; }

  # 目标树的脚本必须自洽；清除 Git 通过环境变量注入的仓库定位信息。
  while IFS= read -r variable; do unset "$variable"; done < <(git -C "$root" rev-parse --local-env-vars)
  cd "$snapshot_root"
  set +e
  QUALITY_ORIGIN_ROOT="$root" \
  QUALITY_SNAPSHOT_MANIFEST="$manifest" \
  MIMIR_QUALITY_RUN_DIRECTORY="$run_directory" \
    bash "$target_script" --mode "$mode" --source worktree --report "$report"
  exit_code=$?
  [[ "$exit_code" -le 2 ]] || exit_code=2
  return "$exit_code"
)
