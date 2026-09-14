#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixture_root="$project_root/scripts/tests/fixtures/java-quality/valid"
temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/mimir-java-quality.XXXXXX")"
probe_file=""
integration_probe_source=""
integration_probe_test=""
cleanup() {
    [[ -z "$probe_file" ]] || rm -f "$probe_file"
    [[ -z "$integration_probe_source" ]] || rm -f "$integration_probe_source"
    [[ -z "$integration_probe_test" ]] || rm -f "$integration_probe_test"
    rm -rf "$temporary_root"
}
trap cleanup EXIT
fail() { echo "java-quality-gates-test: $*" >&2; exit 1; }
expect_exit() {
    local expected="$1"
    shift
    set +e
    "$@" >/dev/null 2>&1
    local actual=$?
    set -e
    [[ "$actual" == "$expected" ]] || fail "期望退出码 $expected，实际为 $actual：$*"
}
expect_spotless_rejection() {
    set +e
    local output
    output="$("$@" 2>&1)"
    local actual=$?
    set -e
    [[ "$actual" == "1" ]] || fail "Spotless 反例期望退出码 1，实际为 $actual"
    [[ "$output" == *"FormatProbe.java"* ]] || fail "Spotless 反例未报告 FormatProbe.java：$output"
}
copy_fixture() {
    local name="$1"
    local destination="$temporary_root/$name"
    cp -R "$fixture_root" "$destination"
    printf '%s' "$destination"
}
tool() { bash "$project_root/scripts/docs-tool.sh" verify-reports.mjs "$@"; }
prepare_expected() {
    local root="$1"
    local expected="$root/expected.json"
    tool --root "$root" --generate-expected "$expected" --run-id "test-run"
    tool --root "$root" --record-artifacts "$expected"
    printf '%s' "$expected"
}
valid_root="$(copy_fixture valid)"
valid_expected="$(prepare_expected "$valid_root")"
node -e 'const expected=JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); process.exit(expected.modules[0]?.path === "module-a" ? 0 : 1)' "$valid_expected" || fail "期望矩阵必须使用规范模块相对路径"
tool --root "$valid_root" --expected "$valid_expected" --report "$valid_root/result.json"
grep -q '"overall": "passed"' "$valid_root/result.json" || fail "完整报告矩阵应通过"
failed_root="$(copy_fixture failed-test)"
failed_expected="$(prepare_expected "$failed_root")"
sed -i 's/failures="0"/failures="1"/' "$failed_root/module-a/target/surefire-reports/TEST-ExampleTest.xml"
expect_exit 1 tool --root "$failed_root" --expected "$failed_expected" --report "$failed_root/result.json"
skipped_root="$(copy_fixture skipped-test)"
skipped_expected="$(prepare_expected "$skipped_root")"
sed -i 's/skipped="0"/skipped="1"/' "$skipped_root/module-a/target/failsafe-reports/TEST-ExampleIT.xml"
expect_exit 1 tool --root "$skipped_root" --expected "$skipped_expected" --report "$skipped_root/result.json"
missing_root="$(copy_fixture missing-report)"
missing_expected="$(prepare_expected "$missing_root")"
rm "$missing_root/module-a/target/site/jacoco/jacoco.xml"
expect_exit 1 tool --root "$missing_root" --expected "$missing_expected" --report "$missing_root/result.json"
stale_root="$(copy_fixture stale-report)"
stale_expected="$(prepare_expected "$stale_root")"
printf 'old-report' > "$stale_root/module-a/target/jacoco.exec"
expect_exit 1 tool --root "$stale_root" --expected "$stale_expected" --report "$stale_root/result.json"
threshold_root="$(copy_fixture threshold)"
threshold_expected="$(prepare_expected "$threshold_root")"
sed -i 's/missed="20" covered="80"/missed="80" covered="20"/' "$threshold_root/module-a/target/site/jacoco/jacoco.xml"
tool --root "$threshold_root" --record-artifacts "$threshold_expected"
expect_exit 1 tool --root "$threshold_root" --expected "$threshold_expected" --report "$threshold_root/result.json"
incomplete_root="$(copy_fixture incomplete-manifest)"
incomplete_expected="$incomplete_root/expected.json"
tool --root "$incomplete_root" --generate-expected "$incomplete_expected" --run-id "test-run"
expect_exit 2 tool --root "$incomplete_root" --expected "$incomplete_expected" --report "$incomplete_root/result.json"
clean_root="$(copy_fixture clean)"
clean_expected="$(prepare_expected "$clean_root")"
tool --root "$clean_root" --clean-expected "$clean_expected"
expect_exit 1 tool --root "$clean_root" --expected "$clean_expected" --report "$clean_root/result.json"
probe_file="$project_root/mimir-boot-common/src/test/java/com/yggdrasil/labs/common/T3FormatProbe.java"
cat > "$probe_file" <<'EOF'
package com.yggdrasil.labs.common; public class T3FormatProbe { public String value(){return "bad";} }
EOF
expect_spotless_rejection "$project_root/mvnw" -q -Pci -pl mimir-boot-common spotless:check
rm -f "$probe_file"
probe_file=""
integration_probe_source="$project_root/mimir-boot-common/src/main/java/com/yggdrasil/labs/common/T3IntegrationOnlyProbe.java"
integration_probe_test="$project_root/mimir-boot-common/src/test/java/com/yggdrasil/labs/common/T3IntegrationOnlyProbeIT.java"
cat > "$integration_probe_source" <<'EOF'
package com.yggdrasil.labs.common;

public final class T3IntegrationOnlyProbe {
    public boolean calledFromIntegrationTest() {
        return true;
    }
}
EOF
cat > "$integration_probe_test" <<'EOF'
package com.yggdrasil.labs.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class T3IntegrationOnlyProbeIT {
    @Test
    void shouldCoverTheProbeOnlyFromAnIntegrationTest() {
        assertTrue(new T3IntegrationOnlyProbe().calledFromIntegrationTest());
    }
}
EOF
"$project_root/mvnw" -q -Pci -pl mimir-boot-common verify
node -e '
const fs = require("fs");
const report = fs.readFileSync(process.argv[1], "utf8");
const classStart = report.indexOf("<class name=\"com/yggdrasil/labs/common/T3IntegrationOnlyProbe\"");
const classEnd = report.indexOf("</class>", classStart);
const classContent = classStart === -1 || classEnd === -1 ? "" : report.slice(classStart, classEnd);
const instructionCovered = /<counter type="INSTRUCTION" missed="0" covered="[1-9][0-9]*"\/>/.test(classContent);
process.exit(classStart !== -1 && instructionCovered ? 0 : 1);
' "$project_root/mimir-boot-common/target/site/jacoco/jacoco.xml" || fail "集成测试独占类必须出现在最终 JaCoCo 报告且指令全覆盖"
echo "java-quality-gates-test: passed"
