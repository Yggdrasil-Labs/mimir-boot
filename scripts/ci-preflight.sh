#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
quality_directory=""
quality_expected=""

require_java_17() {
  local java_major
  java_major="$(java -version 2>&1 | sed -nE '1{s/.*version "([0-9]+).*/\1/p;}')"
  if [[ "$java_major" != "17" ]]; then
    echo "需要 Java 17，实际为 ${java_major:-未知}" >&2
    return 1
  fi
}

build_maven_args() {
  MAVEN_ARGS=(-B -Pci clean verify)

  case "${RUN_SONAR:-false}" in
    false)
      echo "Sonar analysis: skipped (not eligible)"
      ;;
    true)
      if [[ -z "${SONAR_TOKEN:-}" || -z "${SONAR_ORGANIZATION:-}" || -z "${SONAR_PROJECT_KEY:-}" ]]; then
        echo "RUN_SONAR=true 时必须设置 SONAR_TOKEN、SONAR_ORGANIZATION 和 SONAR_PROJECT_KEY" >&2
        return 1
      fi
      SONAR_ARGS=(
        sonar:sonar
        -Dsonar.host.url=https://sonarcloud.io
        "-Dsonar.organization=${SONAR_ORGANIZATION}"
        "-Dsonar.projectKey=${SONAR_PROJECT_KEY}"
        "-Dsonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml"
        -Dsonar.qualitygate.wait=true
        -Dsonar.qualitygate.timeout=300
      )
      ;;
    *)
      echo "RUN_SONAR 只允许 true 或 false，实际为 ${RUN_SONAR}" >&2
      return 1
      ;;
  esac
}

prepare_quality_matrix() {
  local run_id
  run_id="java-quality-$(date -u +%Y%m%dT%H%M%SZ)-$$"
  if [[ -n "${QUALITY_REPORT_DIR:-}" ]]; then
    quality_directory="$QUALITY_REPORT_DIR"
  else
    quality_directory="$(mktemp -d "${TMPDIR:-/tmp}/mimir-java-quality.XXXXXX")"
  fi
  mkdir -p "$quality_directory"
  quality_expected="$quality_directory/build-manifest.json"
  bash scripts/docs-tool.sh verify-reports.mjs --root "$project_root" --generate-expected "$quality_expected" --run-id "$run_id"
  bash scripts/docs-tool.sh verify-reports.mjs --root "$project_root" --clean-expected "$quality_expected"
}

initialize_managed_docs_tool() {
  bash scripts/docs-tool.sh --ensure
}

main() {
  if [[ "$#" -gt 1 || ( "$#" -eq 1 && "$1" != --maven-only ) ]]; then
    echo '用法：bash scripts/ci-preflight.sh [--maven-only]' >&2
    return 2
  fi
  cd "$project_root"
  require_java_17
  build_maven_args
  # 文档工具不可用时，共享入口仍可独立获取 Maven 检查结果。
  if [[ "${1:-}" == --maven-only ]]; then
    ./mvnw "${MAVEN_ARGS[@]}"
    return
  fi
  if [[ "${MIMIR_DOCS_READY_ROOT:-}" != "$project_root" ]] && ! initialize_managed_docs_tool; then
    echo '文档工具准备失败，继续运行 Maven；报告核验无法执行，本次预检不通过。' >&2
    ./mvnw "${MAVEN_ARGS[@]}"
    return 2
  fi
  local report
  prepare_quality_matrix
  report="$quality_directory/java-quality-report.json"
  local build_exit verify_exit sonar_status=not_applicable sonar_exit=0
  set +e
  ./mvnw "${MAVEN_ARGS[@]}"
  build_exit=$?
  set -e
  bash scripts/docs-tool.sh verify-reports.mjs --root "$project_root" --record-artifacts "$quality_expected"
  set +e
  bash scripts/docs-tool.sh verify-reports.mjs --root "$project_root" --expected "$quality_expected" --report "$report"
  verify_exit=$?
  set -e
  if [[ "${RUN_SONAR:-false}" == true ]]; then
    sonar_status=not_run
    if [[ "$build_exit" == 0 && "$verify_exit" == 0 ]]; then
      set +e
      ./mvnw -B -Pci "${SONAR_ARGS[@]}"
      sonar_exit=$?
      set -e
      sonar_status=passed
      [[ "$sonar_exit" == 0 ]] || sonar_status=failed
    fi
  fi
  "$project_root/tools/docs-check/.maven-node/node/node" --input-type=commonjs - "$report" "$build_exit" "$verify_exit" "$sonar_status" "$sonar_exit" <<'JS'
const fs = require('fs');
const [file, build, verify, sonar, sonarExit] = process.argv.slice(2);
const report = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file)) : { findings: [], testsStatus: 'error', coverageStatus: 'error' };
if (+build !== 0) {
  if (report.findings.some(f => f.rule === 'test-report-missing') && !report.findings.some(f => f.rule === 'test-report-invalid')) report.testsStatus = 'not_run';
  // Maven 未成功完成时，完整产物也不能证明 JaCoCo 阈值门禁已通过。
  if (report.coverageStatus === 'passed' || report.findings.some(f => f.rule === 'coverage-artifact-missing')) report.coverageStatus = 'not_run';
}
report.sonarStatus = sonar;
report.exitCode = +verify > 1 || +build > 1 ? 2 : (+build || +verify || +sonarExit ? 1 : 0);
report.overall = report.exitCode === 0 ? 'passed' : report.exitCode === 1 ? 'failed' : 'error';
fs.writeFileSync(file, JSON.stringify(report, null, 2) + '\n');
process.exitCode = report.exitCode;
JS

}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
