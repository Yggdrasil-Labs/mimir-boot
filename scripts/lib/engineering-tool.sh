#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tool_root="$project_root/tools/engineering"

# Maven 在线准备一次；快照只读匹配当前 POM/锁文件的缓存。
prepare_tool() (
    set -euo pipefail
    local operation="$1" origin common cache_root key cache temporary
    origin="${QUALITY_ORIGIN_ROOT:-$project_root}"
    common="$(git -C "$origin" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" || common="$project_root/target"
    cache_root="$common/mimir-quality/cache"
    key="$(cd "$project_root" && { uname -sm && sha256sum pom.xml tools/engineering/package.json tools/engineering/package-lock.json; } | sha256sum | cut -d' ' -f1)" || return 2
    cache="$cache_root/$key"
    if [[ "$operation" == --prepare ]]; then
        mkdir -p "$cache_root" || return 2
        exec 9>"$cache_root/prepare.lock" || return 2
        flock 9 || return 2
        (cd "$project_root" && ./mvnw -N -Pdocs-check process-resources) || {
            echo 'Maven 工程工具准备失败。' >&2
            return 2
        }
        "$tool_root/.maven-node/node/node" "$tool_root/src/bootstrap.mjs" || {
            echo '工程工具 bootstrap 失败。' >&2
            return 2
        }
        temporary="$(mktemp -d "$cache_root/.prepare.XXXXXX")" || return 2
        trap 'rm -rf "$temporary"' EXIT
        cp -a "$tool_root/.maven-node" "$tool_root/node_modules" "$temporary/" || return 2
        (cd "$temporary" && find . -type f ! -name checksums -print0 | sort -z | xargs -0 sha256sum > "$temporary/checksums") || return 2
        # 缓存只在显式准备时替换；flock 保护并发发布和读取。
        rm -rf "$cache" || return 2
        mv "$temporary" "$cache" || return 2
        echo "工程工具缓存已准备：$key"
    else
        if [[ ! -f "$cache/checksums" ]]; then
            if [[ -n "${QUALITY_ORIGIN_ROOT:-}" ]]; then
                echo '目标树的工程工具缓存缺失或锁文件已变化；请先运行 bash scripts/engineering.sh prepare。' >&2
                return 2
            fi
            prepare_tool --prepare || return 2
        fi
        exec 9<"$cache_root/prepare.lock" || return 2
        flock -s 9 || return 2
        (cd "$cache" && sha256sum --quiet -c checksums) || { echo '工程工具缓存损坏，请重新 --prepare。' >&2; return 2; }
        # 每个执行目录持有独立可变安装，避免 worktree/并发构建相互污染。
        mkdir -p "$tool_root" || return 2
        # 嵌套验收可能正在使用此 Node；相同安装无需覆盖正在执行的二进制。
        if ! (cd "$tool_root" && sha256sum --quiet -c "$cache/checksums" >/dev/null 2>&1); then
            cp -a "$cache/.maven-node" "$cache/node_modules" "$tool_root/" || return 2
        fi
        "$tool_root/.maven-node/node/node" "$tool_root/src/bootstrap.mjs" || return 2
        echo "工程工具缓存命中：$key"
    fi
)

if [[ "${1:-}" == --prepare || "${1:-}" == --ensure ]]; then
    prepare_tool "$1" || exit 2
    exit 0
fi

if [[ "$#" -lt 1 ]]; then
    echo "用法：bash scripts/lib/engineering-tool.sh <tools/engineering 内的入口> [参数...]" >&2
    exit 2
fi

entry="$1"
shift
if [[ "$entry" = /* || "$entry" == *$'\n'* || "$entry" == *$'\r'* ]]; then
    echo "工程工具入口必须是相对路径：$entry" >&2
    exit 2
fi

tool_root_real="$(realpath -m -- "$tool_root")"
case "$entry" in
    tools/engineering/*)
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
        echo "工程工具入口必须位于 tools/engineering 内：$entry" >&2
        exit 2
        ;;
esac
if [[ ! -f "$entry_path" ]]; then
    echo "未找到工程工具入口：$entry" >&2
    exit 2
fi

# 所有入口只使用 Maven 固定的运行时，不能由环境变量替换成其他程序。
managed_node="$tool_root/.maven-node/node/node"
if [[ ! -x "$managed_node" ]]; then
    echo "未找到 Maven 管理的 Node 运行时：$managed_node；请先执行 ./mvnw -N -Pdocs-check verify" >&2
    exit 2
fi

exec "$managed_node" "$entry_path" "$@"
