#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tool="$project_root/scripts/lib/engineering-tool.sh"

case "${1:---help}" in
  quality)
    shift
    exec bash "$project_root/scripts/quality-check.sh" "$@"
    ;;
  prepare)
    shift
    [[ "$#" == 0 ]] || { echo 'prepare 不接受额外参数' >&2; exit 2; }
    exec bash "$tool" --prepare
    ;;
  --help|-h)
    echo '用法：bash scripts/engineering.sh <命令> [参数]'
    echo '命令：prepare、quality、docs、java、build-model、consumer、signing、public、contracts、portal-state'
    ;;
  docs|java|build-model|consumer|signing|public|contracts|portal-state)
    bash "$tool" --ensure >&2
    exec bash "$tool" src/cli.mjs "$@"
    ;;
  *)
    echo "未知工程命令：$1" >&2
    exit 2
    ;;
esac
