#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
hook_directory="$project_root/.githooks"
hook_names=(pre-commit pre-push)

fail() { echo "开发环境初始化失败：$*" >&2; exit 2; }

git -C "$project_root" rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail '当前目录不是 Git 工作树'
for hook_name in "${hook_names[@]}"; do
  hook_path="$hook_directory/$hook_name"
  [[ -f "$hook_path" && -x "$hook_path" ]] || fail "当前工作树缺少可执行的 $hook_name"
done

git_common_dir="$(git -C "$project_root" rev-parse --git-common-dir)" || fail '无法解析 Git common dir'
if [[ "$git_common_dir" != /* ]]; then
  git_common_dir="$project_root/$git_common_dir"
fi

default_hooks_directory="$git_common_dir/hooks"
for hook_name in "${hook_names[@]}"; do
  default_hook="$default_hooks_directory/$hook_name"
  if [[ -e "$default_hook" || -L "$default_hook" ]]; then
    fail "默认 .git/hooks 中已有 $hook_name，自定义 hook 未被覆盖：$default_hook"
  fi
done

check_hooks_path_values() {
  local worktree="$1" value
  while IFS= read -r -d '' value; do
    [[ "$value" == .githooks ]] || fail "检测到非 .githooks 的 core.hooksPath（工作树：$worktree，值：$value）"
  done < <(git -C "$worktree" config --null --get-all core.hooksPath 2>/dev/null || true)
}

check_hooks_path_values "$project_root"

worktrees=()
while IFS= read -r -d '' worktree_record; do
  if [[ "$worktree_record" == 'worktree '* ]]; then
    worktree_path="${worktree_record#worktree }"
    [[ -d "$worktree_path" ]] || continue
    worktrees+=("$worktree_path")
    check_hooks_path_values "$worktree_path"
  fi
done < <(git -C "$project_root" worktree list --porcelain -z)

for worktree in "${worktrees[@]}"; do
  for hook_name in "${hook_names[@]}"; do
    source_hook="$hook_directory/$hook_name"
    target_hook="$worktree/.githooks/$hook_name"
    [[ -f "$target_hook" && -x "$target_hook" ]] || fail "工作树缺少同版本可执行 hook：$target_hook"
    cmp -s "$source_hook" "$target_hook" || fail "工作树 hook 内容冲突：$target_hook"
    [[ "$(stat -c '%a' "$source_hook")" == "$(stat -c '%a' "$target_hook")" ]] || fail "工作树 hook 权限冲突：$target_hook"
  done
done

if ! (
  cd "$project_root"
  bash scripts/docs-tool.sh --prepare
); then
  fail '文档工具准备失败'
fi

if ! (
  cd "$project_root"
  bash scripts/quality-check.sh --mode quick --source worktree
); then
  fail 'quick worktree 检查失败'
fi

git -C "$project_root" config --local core.hooksPath .githooks || fail '无法写入仓库 local core.hooksPath'
echo '开发环境已初始化：已启用仓库本地 Git hooks。'
