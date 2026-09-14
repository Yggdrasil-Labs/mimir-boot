#!/usr/bin/env bash

set -euo pipefail

quality_snapshot_fail() {
  echo "质量快照错误：$*" >&2
  return 2
}

quality_snapshot_empty_tree() {
  git -C "$1" hash-object -t tree /dev/null
}

quality_snapshot_changed_files() {
  local root="$1" source="$2" output="$3" base
  if [[ "$source" == "commit" ]]; then
    : >"$output"
    return
  fi
  if git -C "$root" rev-parse --verify --quiet HEAD >/dev/null; then
    base=HEAD
  else
    base="$(quality_snapshot_empty_tree "$root")"
  fi
  git -C "$root" diff --cached --name-only -z "$base" >"$output"
}

quality_snapshot_write_manifest() {
  local manifest="$1" origin_root="$2" snapshot_root="$3" source="$4" commit="$5" tree="$6" files_nul="$7" changed_nul="$8"
  python3 - "$manifest" "$origin_root" "$snapshot_root" "$source" "$commit" "$tree" "$files_nul" "$changed_nul" <<'PY'
import json
import pathlib
import sys

manifest, origin_root, snapshot_root, source, commit, tree, files_path, changed_path = sys.argv[1:]

def read_nul(path):
    values = pathlib.Path(path).read_bytes().split(b'\0')
    return [value.decode('utf-8') for value in values if value]

payload = {
    'schemaVersion': 1,
    'originRoot': origin_root,
    'executionRoot': snapshot_root,
    'source': source,
    'commit': None if commit == 'null' else commit,
    'tree': tree,
    'files': read_nul(files_path),
    'changedFiles': read_nul(changed_path),
}
pathlib.Path(manifest).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
PY
}

quality_snapshot_run() {
  local root="$1" mode="$2" source="$3" commit="$4" report="$5"
  local tree snapshot_root files_nul changed_nul manifest target_script exit_code

  git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1 || quality_snapshot_fail "source=$source 需要 Git 工作树"
  if [[ "$source" == "index" ]]; then
    tree="$(git -C "$root" write-tree)"
    commit=null
  else
    commit="$(git -C "$root" rev-parse --verify "${commit}^{commit}")" || quality_snapshot_fail "无法解析提交：$commit"
    tree="$(git -C "$root" rev-parse "${commit}^{tree}")"
  fi

  snapshot_root="$(mktemp -d "${TMPDIR:-/tmp}/mimir-quality-snapshot.XXXXXX")"
  files_nul="$snapshot_root/files.nul"
  changed_nul="$snapshot_root/changed.nul"
  manifest="$snapshot_root/quality-snapshot.json"
  trap 'rm -rf "$snapshot_root"' RETURN

  git -C "$root" ls-tree -r -z --name-only "$tree" >"$files_nul"
  quality_snapshot_changed_files "$root" "$source" "$changed_nul"
  git -C "$root" archive --format=tar "$tree" | tar -x -C "$snapshot_root"
  quality_snapshot_write_manifest "$manifest" "$root" "$snapshot_root" "$source" "$commit" "$tree" "$files_nul" "$changed_nul"
  target_script="$snapshot_root/scripts/quality-check.sh"
  [[ -f "$target_script" ]] || quality_snapshot_fail "快照缺少 scripts/quality-check.sh"

  set +e
  env \
    -u GIT_DIR \
    -u GIT_WORK_TREE \
    -u GIT_INDEX_FILE \
    -u GIT_OBJECT_DIRECTORY \
    -u GIT_ALTERNATE_OBJECT_DIRECTORIES \
    QUALITY_SNAPSHOT_MANIFEST="$manifest" \
    bash "$target_script" --mode "$mode" --source worktree --report "$report"
  exit_code=$?
  set -e
  return "$exit_code"
}
