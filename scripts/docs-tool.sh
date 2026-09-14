#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tool_root="$project_root/tools/docs-check"

if [[ "$#" -lt 1 ]]; then
    echo "用法：bash scripts/docs-tool.sh <tools/docs-check 内的入口> [参数...]" >&2
    exit 2
fi

entry="$1"
shift
if [[ "$entry" = /* || "$entry" == *$'\n'* || "$entry" == *$'\r'* ]]; then
    echo "文档工具入口必须是相对路径：$entry" >&2
    exit 2
fi

tool_root_real="$(realpath -m -- "$tool_root")"
case "$entry" in
    tools/docs-check/*)
        entry_candidate="$project_root/$entry"
        ;;
    *)
        entry_candidate="$tool_root/$entry"
        ;;
esac
entry_path="$(realpath -m -- "$entry_candidate")"
case "$entry_path" in
    "$tool_root_real"/*) ;;
    *)
        echo "文档工具入口必须位于 tools/docs-check 内：$entry" >&2
        exit 2
        ;;
esac
if [[ ! -f "$entry_path" ]]; then
    echo "未找到文档工具入口：$entry" >&2
    exit 2
fi

managed_node="${MIMIR_DOCS_NODE:-$tool_root/.maven-node/node/node}"
if [[ ! -x "$managed_node" ]]; then
    echo "未找到 Maven 管理的 Node 运行时：$managed_node；请先执行 ./mvnw -N -Pdocs-check verify" >&2
    exit 2
fi

exec "$managed_node" "$entry_path" "$@"
