#!/usr/bin/env bash
set -euo pipefail

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

find_report_files() {
  local report_pattern="$1"

  find . \
    \( -path './.git' -o -path './.worktrees' \) -prune -o \
    -path "$report_pattern" -type f -size +0c -print0
}

verify_test_reports() {
  local -a surefire_reports=()
  local -a failsafe_reports=()

  mapfile -d '' surefire_reports < <(find_report_files '*/target/surefire-reports/TEST-*.xml')
  test "${#surefire_reports[@]}" -gt 0
  ! grep -E 'failures="[1-9][0-9]*"|errors="[1-9][0-9]*"' "${surefire_reports[@]}"
  ! grep -E 'skipped="[1-9][0-9]*"|<skipped([[:space:]/>])' "${surefire_reports[@]}"
  mapfile -d '' failsafe_reports < <(find_report_files '*/target/failsafe-reports/TEST-*.xml')
  test "${#failsafe_reports[@]}" -gt 0
  ! grep -E 'failures="[1-9][0-9]*"|errors="[1-9][0-9]*"' "${failsafe_reports[@]}"
  ! grep -E 'skipped="[1-9][0-9]*"|<skipped([[:space:]/>])' "${failsafe_reports[@]}"
}

verify_jacoco_reports() {
  local -a jacoco_reports=()

  mapfile -d '' jacoco_reports < <(find_report_files '*/target/site/jacoco/jacoco.xml')
  test "${#jacoco_reports[@]}" -gt 0
}

main() {
  require_java_17
  build_maven_args
  ./mvnw "${MAVEN_ARGS[@]}"
  verify_test_reports
  verify_jacoco_reports
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
