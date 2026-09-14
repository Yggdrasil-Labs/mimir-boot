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
      MAVEN_ARGS+=(
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
  ./mvnw -N -Pdocs-check process-resources
}

main() {
  cd "$project_root"
  require_java_17
  build_maven_args
  initialize_managed_docs_tool
  local report
  prepare_quality_matrix
  report="$quality_directory/java-quality-report.json"
  ./mvnw "${MAVEN_ARGS[@]}"
  bash scripts/docs-tool.sh verify-reports.mjs --root "$project_root" --record-artifacts "$quality_expected"
  bash scripts/docs-tool.sh verify-reports.mjs --root "$project_root" --expected "$quality_expected" --report "$report"
  echo "Java quality report: $report"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
