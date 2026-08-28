#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d -t mimir-suite-consumer.XXXXXX)"
consumer_dir="$work_dir/consumer"
repository_dir="$work_dir/repository"
blocked_repository_dir="$work_dir/blocked-remote"
cache_dir="$work_dir/m2"
settings_file="$work_dir/settings.xml"
blocked_settings_file="$work_dir/blocked-settings.xml"
logs_dir="$work_dir/logs"
mkdir -p "$logs_dir"

cleanup() {
  if [[ "${MIMIR_KEEP_WORKDIR:-}" == "1" ]]; then
    echo "保留 consumer 临时目录：$work_dir" >&2
  else
    rm -rf "$work_dir"
  fi
}
trap cleanup EXIT

run_maven_stage() {
  local stage="$1"
  shift
  "$@" 2>&1 | tee "$logs_dir/$stage.log"
}

proxy_url="${MIMIR_MAVEN_PROXY:-${HTTPS_PROXY:-}}"
proxy_xml=""
if [[ -n "$proxy_url" ]]; then
  if [[ "$proxy_url" =~ ^http://([A-Za-z0-9.-]+):([0-9]{1,5})/?$ ]]; then
    proxy_host="${BASH_REMATCH[1]}"
    proxy_port="${BASH_REMATCH[2]}"
    if ((proxy_port < 1 || proxy_port > 65535)); then
      echo "MIMIR_MAVEN_PROXY/HTTPS_PROXY 的端口必须在 1-65535。" >&2
      exit 2
    fi
    proxy_xml="
  <proxies>
    <proxy>
      <id>isolated-http-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>$proxy_host</host>
      <port>$proxy_port</port>
    </proxy>
  </proxies>"
  else
    echo "MIMIR_MAVEN_PROXY/HTTPS_PROXY 必须是无凭据的 http://host:port 代理地址。" >&2
    exit 2
  fi
fi

cat >"$settings_file" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">$proxy_xml
</settings>
EOF

revision="$("$project_dir/mvnw" -q -s "$settings_file" -f "$project_dir/pom.xml" help:evaluate -Dexpression=revision -DforceStdout -Dmaven.repo.local="$cache_dir")"
test -n "$revision"

online_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$cache_dir")
run_maven_stage root-go-offline "${online_maven[@]}" dependency:go-offline
run_maven_stage root-online-deploy "${online_maven[@]}" deploy \
  -Dmaven.test.skip=true \
  -Dmaven.deploy.skip=true \
  -Dgpg.skip=true

mkdir -p "$blocked_repository_dir"
cat >"$blocked_settings_file" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>central</id>
      <mirrorOf>central</mirrorOf>
      <url>file://$blocked_repository_dir</url>
    </mirror>
  </mirrors>
</settings>
EOF

isolated_maven=("$project_dir/mvnw" -B -o -s "$blocked_settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$cache_dir")
run_maven_stage root-isolated-clean "${isolated_maven[@]}" clean
deploy_maven=("$project_dir/mvnw" -B -s "$blocked_settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$cache_dir")
run_maven_stage root-fixture-deploy "${deploy_maven[@]}" deploy \
  -Dmaven.test.skip=true \
  -Dgpg.skip=true \
  -Dmaven.deploy.skip=false \
  "-DaltDeploymentRepository=fixture::default::file://$repository_dir"

mkdir -p "$consumer_dir/src/test/java/io/github/yggdrasil/labs/fixture"
cat >"$consumer_dir/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-suite-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <properties><maven.compiler.release>17</maven.compiler.release></properties>
    <repositories>
        <repository><id>fixture</id><url>file://$repository_dir</url></repository>
    </repositories>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.yggdrasil-labs</groupId>
                <artifactId>mimir-boot-bom</artifactId>
                <version>$revision</version>
                <type>pom</type><scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-exception</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-log</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-rpc-core</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-dubbo</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-feign</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-nacos</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-mybatis</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.apache.rocketmq</groupId><artifactId>rocketmq-spring-boot-starter</artifactId></dependency>
        <dependency><groupId>co.elastic.clients</groupId><artifactId>elasticsearch-java</artifactId></dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.15.0</version>
                <configuration><release>17</release></configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.6</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF

cat >"$consumer_dir/src/test/java/io/github/yggdrasil/labs/fixture/IsolatedConsumerTest.java" <<'EOF'
package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yggdrasil.labs.log.config.LogMaskAutoConfiguration;
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;
import com.yggdrasil.labs.mybatis.util.SqlLogMaskUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IsolatedConsumerTest {

    @Test
    void resolvesAutoConfigurationAndMasksSqlBeforeLogging() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogMaskAutoConfiguration.class))
                .run(context -> assertTrue(context.isRunning()));
        String secret = "fixed-secret-for-isolated-consumer";
        String sql = SqlLogMaskUtils.maskSql("select * from user where password='" + secret + "'");
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");
        String logged = new SensitiveDataConverter().maskSensitiveData(sql);
        assertFalse(sql.contains(secret));
        assertFalse(logged.contains(secret));
    }
}
EOF

online_consumer_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$consumer_dir/pom.xml" "-Dmaven.repo.local=$cache_dir")
run_maven_stage consumer-online-go-offline "${online_consumer_maven[@]}" dependency:go-offline
run_maven_stage consumer-online-resolve "${online_consumer_maven[@]}" dependency:resolve -DoutputFile="$consumer_dir/target/online-dependency-resolve.txt"
run_maven_stage consumer-online-clean-test "${online_consumer_maven[@]}" clean test

consumer_maven=("$project_dir/mvnw" -B -o -s "$blocked_settings_file" -f "$consumer_dir/pom.xml" "-Dmaven.repo.local=$cache_dir")
run_maven_stage consumer-isolated-resolve "${consumer_maven[@]}" dependency:resolve -DoutputFile="$consumer_dir/target/dependency-resolve.txt"
run_maven_stage consumer-isolated-tree "${consumer_maven[@]}" dependency:tree -DoutputFile="$consumer_dir/target/dependency-tree.txt"
rg -q 'org\.apache\.rocketmq:rocketmq-spring-boot-starter:jar:2\.3\.6' "$consumer_dir/target/dependency-tree.txt"
rg -q 'co\.elastic\.clients:elasticsearch-java:jar:8\.11\.0' "$consumer_dir/target/dependency-tree.txt"
run_maven_stage consumer-isolated-clean-test "${consumer_maven[@]}" clean test
echo "隔离 BOM consumer 验证通过：版本 $revision，八个 Starter 与受管依赖均只从 file repository 消费。"
