#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
workflow="$project_root/.github/workflows/ci.yml"

fail() { echo "ci-quality-contract-test: $*" >&2; exit 1; }

[[ -f "$workflow" ]] || fail '缺少 CI workflow'
grep -Fq 'bash scripts/quality-check.sh --mode full --source worktree' "$workflow" || fail 'CI 必须调用共享 full quality-check 入口'
grep -Fq 'QUALITY_REPORT_DIR: ${{ runner.temp }}/mimir-quality/java' "$workflow" || fail 'CI 必须为 Java 报告提供独立质量目录'
grep -Fq 'name: quality-report' "$workflow" || fail 'CI 必须上传质量汇总报告'
grep -Fq 'path: ${{ runner.temp }}/mimir-quality' "$workflow" || fail 'CI 质量汇总必须来自同一运行目录'
grep -Fq 'if: always()' "$workflow" || fail '失败时必须上传质量证据'
grep -Fq "JAVA_VERSION: '17'" "$workflow" || fail 'CI 必须保持 Java 17'
grep -Fq "github.event_name == 'push' && secrets.SONAR_TOKEN" "$workflow" || fail 'Sonar 只能在 push 且凭证齐全时运行'
grep -Fq 'SONAR_TOKEN: ${{ github.event_name == ' "$workflow" || fail 'Sonar token 必须只经环境变量传入共享入口'
! grep -Fq 'markdownlint-cli2-action' "$workflow" || fail 'CI 不应保留独立 Markdown action'
! grep -Fq 'bash scripts/ci-preflight.sh' "$workflow" || fail 'CI 不应绕过共享质量入口直接运行 ci-preflight'
! grep -Fq 'bash scripts/test-suite-consumer-contract-test.sh' "$workflow" || fail 'CI 不应重复运行已由共享入口调度的发布契约'
! grep -Fq 'bash scripts/verify-maven-central-public-contract-test.sh' "$workflow" || fail 'CI 不应重复运行已由共享入口调度的公开制品契约'

echo 'ci-quality-contract-test: passed'
