#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
consumer_dir="$(mktemp -d -t mimir-suite-consumer.XXXXXX)"

cleanup() {
  rm -rf "$consumer_dir"
}
trap cleanup EXIT

cat >"$consumer_dir/pom.xml" <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-suite-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-starter-test</artifactId>
            <version>2.1.2-SNAPSHOT</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.6</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF

mkdir -p "$consumer_dir/src/test/java"
cat >"$consumer_dir/src/test/java/DownstreamSuiteTest.java" <<'EOF'
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses(HiddenSuiteMember.class)
class DownstreamSuiteTest {}
EOF

cat >"$consumer_dir/src/test/java/HiddenSuiteMember.java" <<'EOF'
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HiddenSuiteMember {

    @Test
    void shouldRunOnlyThroughSuite() throws IOException {
        Files.writeString(Path.of("target", "suite-member-ran"), "ran");
    }
}
EOF

"$project_dir/mvnw" -B -f "$consumer_dir/pom.xml" clean test

test -f "$consumer_dir/target/suite-member-ran"
rg -q 'tests="1"' "$consumer_dir/target/surefire-reports/TEST-DownstreamSuiteTest.xml"
test ! -e "$consumer_dir/target/surefire-reports/TEST-HiddenSuiteMember.xml"

"$project_dir/mvnw" -B -f "$consumer_dir/pom.xml" dependency:tree -DoutputFile="$consumer_dir/target/dependency-tree.txt"
! rg -q 'org\.testcontainers' "$consumer_dir/target/dependency-tree.txt"
