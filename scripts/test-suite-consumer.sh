#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d -t mimir-suite-consumer.XXXXXX)"
consumer_dir="$work_dir/consumer"
repository_dir="$work_dir/repository"
blocked_repository_dir="$work_dir/blocked-remote"
producer_cache_dir="$work_dir/producer-m2"
consumer_cache_dir="$work_dir/consumer-m2"
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

read_pom_property() {
  local pom="$1"
  local property="$2"
  local -a values=()

  mapfile -t values < <(
    sed -n "s|^[[:space:]]*<${property}>\\([^<]*\\)</${property}>[[:space:]]*$|\\1|p" "$pom"
  )
  if [[ "${#values[@]}" -ne 1 || -z "${values[0]}" ]]; then
    echo "$pom 中的 $property 必须恰好定义一次且非空。" >&2
    return 1
  fi
  printf '%s\n' "${values[0]}"
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

revision="$(read_pom_property "$project_dir/pom.xml" revision)"
rocketmq_version="$(read_pom_property "$project_dir/mimir-boot-bom/pom.xml" rocketmq.version)"
elasticsearch_version="$(read_pom_property "$project_dir/mimir-boot-bom/pom.xml" elasticsearch.version)"

producer_projects=(
  -pl
  :mimir-boot-bom,:mimir-boot-starter-exception,:mimir-boot-starter-log,:mimir-boot-starter-rpc-core,:mimir-boot-starter-dubbo,:mimir-boot-starter-feign,:mimir-boot-starter-nacos,:mimir-boot-starter-mybatis,:mimir-boot-starter-test
  -am
)
online_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$producer_cache_dir" "${producer_projects[@]}")
run_maven_stage root-online-clean-deploy "${online_maven[@]}" clean deploy \
  -Dmaven.test.skip=true \
  -Dmaven.source.skip=true \
  -Dmaven.javadoc.skip=true \
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

isolated_maven=("$project_dir/mvnw" -B -o -s "$blocked_settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$producer_cache_dir" "${producer_projects[@]}")
run_maven_stage root-isolated-clean "${isolated_maven[@]}" clean
deploy_maven=("$project_dir/mvnw" -B -s "$blocked_settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$producer_cache_dir" "${producer_projects[@]}")
run_maven_stage root-fixture-deploy "${deploy_maven[@]}" deploy \
  -Dmaven.test.skip=true \
  -Dmaven.source.skip=true \
  -Dmaven.javadoc.skip=true \
  -Dgpg.skip=true \
  -Dmaven.deploy.skip=false \
  "-DaltDeploymentRepository=fixture::default::file://$repository_dir"

# 根聚合 POM 将 deploy 的 skip 硬编码为 true，BOM 因而无法随 reactor 部署。
# consumer 解析 BOM 时仍需要根 POM 与 BOM POM；使用无父 POM 的部署器显式投放它们。
fixture_deployer_pom="$work_dir/fixture-deployer-pom.xml"
cat >"$fixture_deployer_pom" <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.yggdrasil-labs.fixture</groupId>
  <artifactId>fixture-pom-deployer</artifactId>
  <version>1.0.0</version>
</project>
EOF

deploy_fixture_pom() {
  local stage="$1"
  local artifact="$2"
  local pom="$3"
  run_maven_stage "$stage" "$project_dir/mvnw" -B -s "$blocked_settings_file" -f "$fixture_deployer_pom" \
    "-Dmaven.repo.local=$producer_cache_dir" \
    org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file \
    "-Dfile=$pom" \
    "-DpomFile=$pom" \
    -DgroupId=io.github.yggdrasil-labs \
    "-DartifactId=$artifact" \
    "-Dversion=$revision" \
    -Dpackaging=pom \
    -DgeneratePom=false \
    "-Durl=file://$repository_dir" \
    -DrepositoryId=fixture
}

fixture_root_pom="$producer_cache_dir/io/github/yggdrasil-labs/mimir-boot/$revision/mimir-boot-$revision.pom"
fixture_bom_pom="$producer_cache_dir/io/github/yggdrasil-labs/mimir-boot-bom/$revision/mimir-boot-bom-$revision.pom"
test -s "$fixture_root_pom"
test -s "$fixture_bom_pom"
fixture_root_deploy_pom="$work_dir/mimir-boot-$revision.pom"
fixture_bom_deploy_pom="$work_dir/mimir-boot-bom-$revision.pom"
cp "$fixture_root_pom" "$fixture_root_deploy_pom"
cp "$fixture_bom_pom" "$fixture_bom_deploy_pom"
deploy_fixture_pom fixture-deploy-root-pom mimir-boot "$fixture_root_deploy_pom"
deploy_fixture_pom fixture-deploy-bom-pom mimir-boot-bom "$fixture_bom_deploy_pom"

fixture_required_artifacts=(
  mimir-boot
  mimir-boot-parent
  mimir-boot-bom
  mimir-boot-common
  mimir-boot-starter-exception
  mimir-boot-starter-log
  mimir-boot-starter-rpc-core
  mimir-boot-starter-dubbo
  mimir-boot-starter-feign
  mimir-boot-starter-nacos
  mimir-boot-starter-mybatis
  mimir-boot-starter-test
)
for artifact in "${fixture_required_artifacts[@]}"; do
  fixture_artifact_dir="$repository_dir/io/github/yggdrasil-labs/$artifact/$revision"
  if [[ ! -s "$fixture_artifact_dir/maven-metadata.xml" ]] \
    || ! find "$fixture_artifact_dir" -maxdepth 1 -type f -name '*.pom' -size +0c -print -quit | grep -q .; then
    echo "fixture repository 缺少 $artifact 的已发布 POM：$fixture_artifact_dir" >&2
    exit 1
  fi
done

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

test ! -e "$consumer_cache_dir/io/github/yggdrasil-labs"
online_consumer_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$consumer_dir/pom.xml" "-Dmaven.repo.local=$consumer_cache_dir")
run_maven_stage consumer-online-resolve "${online_consumer_maven[@]}" dependency:resolve -DoutputFile="$consumer_dir/target/online-dependency-resolve.txt"
run_maven_stage consumer-online-tree "${online_consumer_maven[@]}" dependency:tree -DoutputFile="$consumer_dir/target/online-dependency-tree.txt"
run_maven_stage consumer-online-clean-test "${online_consumer_maven[@]}" clean test

mapfile -d '' mimir_repository_markers < <(find "$consumer_cache_dir/io/github/yggdrasil-labs" -name _remote.repositories -type f -print0)
test "${#mimir_repository_markers[@]}" -gt 0
! grep -hEv '^(#|$)|>fixture=$' "${mimir_repository_markers[@]}"

consumer_maven=("$project_dir/mvnw" -B -o -s "$blocked_settings_file" -f "$consumer_dir/pom.xml" "-Dmaven.repo.local=$consumer_cache_dir")
run_maven_stage consumer-isolated-resolve "${consumer_maven[@]}" dependency:resolve -DoutputFile="$consumer_dir/target/dependency-resolve.txt"
run_maven_stage consumer-isolated-tree "${consumer_maven[@]}" dependency:tree -DoutputFile="$consumer_dir/target/dependency-tree.txt"
grep -Fq "org.apache.rocketmq:rocketmq-spring-boot-starter:jar:$rocketmq_version" "$consumer_dir/target/dependency-tree.txt"
grep -Fq "co.elastic.clients:elasticsearch-java:jar:$elasticsearch_version" "$consumer_dir/target/dependency-tree.txt"
run_maven_stage consumer-isolated-clean-test "${consumer_maven[@]}" clean test
echo "隔离 BOM consumer 验证通过：版本 $revision，八个 Starter 与受管依赖均从独立 file repository 消费。"
