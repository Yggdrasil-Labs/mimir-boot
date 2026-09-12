#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$project_dir/scripts/lib/published-artifact-contract.sh"
work_dir="$(mktemp -d -t mimir-suite-consumer.XXXXXX)"
consumer_dir="$work_dir/consumer"
bom_consumer_dir="$work_dir/bom-only-consumer"
failure_consumer_dir="$work_dir/failure-consumer"
repository_dir="$work_dir/repository"
blocked_repository_dir="$work_dir/blocked-remote"
producer_cache_dir="$work_dir/producer-m2"
consumer_cache_dir="$work_dir/consumer-m2"
bom_consumer_cache_dir="$work_dir/bom-consumer-m2"
failure_consumer_cache_dir="$work_dir/failure-consumer-m2"
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

find_published_pom() {
  local artifact="$1"
  local artifact_dir="$repository_dir/io/github/yggdrasil-labs/$artifact/$revision"
  local -a poms=()

  mapfile -d '' poms < <(find "$artifact_dir" -maxdepth 1 -type f -name '*.pom' -size +0c -print0)
  if [[ "${#poms[@]}" -ne 1 ]]; then
    echo "$artifact 的已发布 POM 必须恰好存在一份：$artifact_dir" >&2
    return 1
  fi
  printf '%s\n' "${poms[0]}"
}

assert_fixture_mimir_repository_markers() {
  local cache_dir="$1"
  shift
  local namespace_dir="$cache_dir/io/github/yggdrasil-labs"
  local artifact
  local artifact_dir
  local marker
  local marker_status
  local -a marker_files=()
  local -a artifact_marker_files=()

  if [[ "$#" -eq 0 ]]; then
    echo "Maven cache marker 检查必须至少指定一个预期的 Mimir 制品。" >&2
    return 2
  fi
  if [[ ! -d "$namespace_dir" ]]; then
    echo "Maven cache 缺少 Mimir 制品目录：$namespace_dir" >&2
    return 1
  fi

  mapfile -d '' marker_files < <(find "$namespace_dir" -type f -name _remote.repositories -print0)
  if [[ "${#marker_files[@]}" -eq 0 ]]; then
    echo "Maven cache 未生成任何 Mimir 制品的 _remote.repositories：$namespace_dir" >&2
    return 1
  fi
  for marker in "${marker_files[@]}"; do
    if grep -hEv '^(#|$)|>fixture=$' "$marker" >/dev/null; then
      echo "Maven cache 的 Mimir 制品只能标记为 fixture，发现异常 marker：$marker" >&2
      return 1
    else
      marker_status="$?"
      if [[ "$marker_status" -ne 1 ]]; then
        echo "无法读取 Maven cache marker：$marker" >&2
        return 1
      fi
    fi
  done

  for artifact in "$@"; do
    artifact_dir="$namespace_dir/$artifact"
    if [[ ! -d "$artifact_dir" ]]; then
      echo "Maven cache 缺少预期的 Mimir 制品目录：$artifact_dir" >&2
      return 1
    fi
    artifact_marker_files=()
    mapfile -d '' artifact_marker_files < <(find "$artifact_dir" -type f -name _remote.repositories -print0)
    if [[ "${#artifact_marker_files[@]}" -eq 0 ]]; then
      echo "Maven cache 缺少 $artifact 的 _remote.repositories：$artifact_dir" >&2
      return 1
    fi
  done
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
contract_rocketmq_version="2.3.6"
contract_elasticsearch_version="8.11.0"
bom_rocketmq_version="$(read_pom_property "$project_dir/mimir-boot-bom/pom.xml" rocketmq.version)"
bom_elasticsearch_version="$(read_pom_property "$project_dir/mimir-boot-bom/pom.xml" elasticsearch.version)"
if [[ "$bom_rocketmq_version" != "$contract_rocketmq_version" ]]; then
  echo "BOM 的 rocketmq.version=$bom_rocketmq_version 不符合发布契约 $contract_rocketmq_version。" >&2
  exit 1
fi
if [[ "$bom_elasticsearch_version" != "$contract_elasticsearch_version" ]]; then
  echo "BOM 的 elasticsearch.version=$bom_elasticsearch_version 不符合发布契约 $contract_elasticsearch_version。" >&2
  exit 1
fi

starter_artifacts=(
  mimir-boot-starter-exception
  mimir-boot-starter-log
  mimir-boot-starter-web
  mimir-boot-starter-rpc-core
  mimir-boot-starter-dubbo
  mimir-boot-starter-feign
  mimir-boot-starter-nacos
  mimir-boot-starter-mybatis
  mimir-boot-starter-mybatis-processor
  mimir-boot-starter-test
)
producer_selector=":mimir-boot-bom,:mimir-boot-starters"
for artifact in "${starter_artifacts[@]}"; do
  producer_selector+=",:$artifact"
done
producer_projects=(
  -pl
  "$producer_selector"
  -am
)
online_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" "-Dmaven.repo.local=$producer_cache_dir" "${producer_projects[@]}")
# 该阶段只在线预取公共依赖并生成待发布构件；deploy.skip=true，禁止访问任何远程发布端点。
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

fixture_required_artifacts=(
  mimir-boot
  mimir-boot-parent
  mimir-boot-bom
  mimir-boot-common
  mimir-boot-starters
  "${starter_artifacts[@]}"
)
for artifact in "${fixture_required_artifacts[@]}"; do
  if ! assert_fixture_published_artifact "$repository_dir" "$revision" "$artifact"; then
    exit 1
  fi
done

# file:// snapshot 仓库使用时间戳文件名，不能假设固定的 -SNAPSHOT.pom 名称。
parent_pom="$(find_published_pom mimir-boot-parent)"

parent_failsafe_plugin="$(awk '
  /<artifactId>maven-failsafe-plugin<\/artifactId>/ {capture=1}
  capture {print}
  capture && /<\/plugin>/ {exit}
' "$parent_pom")"
grep -Fq '<goal>integration-test</goal>' <<<"$parent_failsafe_plugin"
grep -Fq '<goal>verify</goal>' <<<"$parent_failsafe_plugin"

# Parent 需要保留 Failsafe 以供继承；普通制品则必须部署精简 POM。
compact_pom_artifacts=(
  mimir-boot
  mimir-boot-bom
  mimir-boot-common
  mimir-boot-starters
  "${starter_artifacts[@]}"
)
for artifact in "${compact_pom_artifacts[@]}"; do
  artifact_pom="$(find_published_pom "$artifact")"
  if grep -Fq '<version>${revision}</version>' "$artifact_pom"; then
    echo "普通制品发布 POM 不得包含未解析的 revision Parent 版本：$artifact_pom" >&2
    exit 1
  fi
  if grep -Fq '<artifactId>maven-failsafe-plugin</artifactId>' "$artifact_pom"; then
    echo "普通制品发布 POM 不得保留 Parent 的 Failsafe 构建插件：$artifact_pom" >&2
    exit 1
  fi
done

mkdir -p "$consumer_dir/src/test/java/io/github/yggdrasil/labs/fixture"
cat >"$consumer_dir/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.yggdrasil-labs</groupId>
        <artifactId>mimir-boot-parent</artifactId>
        <version>$revision</version>
        <relativePath/>
    </parent>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-suite-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <repositories>
        <repository><id>fixture</id><url>file://$repository_dir</url></repository>
    </repositories>
    <dependencies>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-exception</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-log</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-web</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-rpc-core</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-dubbo</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-feign</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-nacos</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-mybatis</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-mybatis-processor</artifactId></dependency>
        <dependency><groupId>io.github.yggdrasil-labs</groupId><artifactId>mimir-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.apache.rocketmq</groupId><artifactId>rocketmq-spring-boot-starter</artifactId></dependency>
        <dependency><groupId>co.elastic.clients</groupId><artifactId>elasticsearch-java</artifactId></dependency>
    </dependencies>
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

cat >"$consumer_dir/src/test/java/io/github/yggdrasil/labs/fixture/ParentLifecycleIT.java" <<'EOF'
package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParentLifecycleIT {

    @Test
    void isExecutedByThePublishedParentFailsafeConfiguration() {
        assertTrue(true);
    }
}
EOF

test ! -e "$consumer_cache_dir/io/github/yggdrasil-labs"
online_consumer_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$consumer_dir/pom.xml" "-Dmaven.repo.local=$consumer_cache_dir")
run_maven_stage consumer-online-resolve "${online_consumer_maven[@]}" dependency:resolve -DoutputFile="$consumer_dir/target/online-dependency-resolve.txt"
run_maven_stage consumer-online-tree "${online_consumer_maven[@]}" dependency:tree -DoutputFile="$consumer_dir/target/online-dependency-tree.txt"
run_maven_stage consumer-online-clean-verify "${online_consumer_maven[@]}" clean verify
test -s "$consumer_dir/target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.ParentLifecycleIT.xml"

assert_fixture_mimir_repository_markers \
  "$consumer_cache_dir" \
  mimir-boot \
  mimir-boot-parent \
  mimir-boot-common \
  "${starter_artifacts[@]}"

consumer_maven=("$project_dir/mvnw" -B -o -s "$blocked_settings_file" -f "$consumer_dir/pom.xml" "-Dmaven.repo.local=$consumer_cache_dir")
run_maven_stage consumer-isolated-resolve "${consumer_maven[@]}" dependency:resolve -DoutputFile="$consumer_dir/target/dependency-resolve.txt"
run_maven_stage consumer-isolated-tree "${consumer_maven[@]}" dependency:tree -DoutputFile="$consumer_dir/target/dependency-tree.txt"
grep -Fq "org.apache.rocketmq:rocketmq-spring-boot-starter:jar:$contract_rocketmq_version" "$consumer_dir/target/dependency-tree.txt"
grep -Fq "co.elastic.clients:elasticsearch-java:jar:$contract_elasticsearch_version" "$consumer_dir/target/dependency-tree.txt"
run_maven_stage consumer-isolated-clean-verify "${consumer_maven[@]}" clean verify
test -s "$consumer_dir/target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.ParentLifecycleIT.xml"

# BOM-only consumer 不继承 Parent，也使用独立 Maven cache，确保 BOM 坐标可以单独被下游解析。
mkdir -p "$bom_consumer_dir"
cat >"$bom_consumer_dir/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-bom-only-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <repositories>
        <repository><id>fixture</id><url>file://$repository_dir</url></repository>
    </repositories>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.yggdrasil-labs</groupId>
                <artifactId>mimir-boot-bom</artifactId>
                <version>$revision</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.yggdrasil-labs</groupId>
            <artifactId>mimir-boot-starter-log</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
        </dependency>
    </dependencies>
</project>
EOF
! grep -Fq '<parent>' "$bom_consumer_dir/pom.xml"
test ! -e "$bom_consumer_cache_dir/io/github/yggdrasil-labs"
bom_online_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$bom_consumer_dir/pom.xml" "-Dmaven.repo.local=$bom_consumer_cache_dir")
run_maven_stage bom-online-resolve "${bom_online_maven[@]}" dependency:resolve -DoutputFile="$bom_consumer_dir/target/online-dependency-resolve.txt"
run_maven_stage bom-online-tree "${bom_online_maven[@]}" dependency:tree -DoutputFile="$bom_consumer_dir/target/online-dependency-tree.txt"
run_maven_stage bom-online-verify "${bom_online_maven[@]}" clean verify
assert_fixture_mimir_repository_markers \
  "$bom_consumer_cache_dir" \
  mimir-boot-bom \
  mimir-boot-parent \
  mimir-boot \
  mimir-boot-common \
  mimir-boot-starter-log

bom_consumer_maven=("$project_dir/mvnw" -B -o -s "$blocked_settings_file" -f "$bom_consumer_dir/pom.xml" "-Dmaven.repo.local=$bom_consumer_cache_dir")
run_maven_stage bom-isolated-tree "${bom_consumer_maven[@]}" dependency:tree -DoutputFile="$bom_consumer_dir/target/dependency-tree.txt"
grep -Fq "io.github.yggdrasil-labs:mimir-boot-starter-log:jar:$revision" "$bom_consumer_dir/target/dependency-tree.txt"
grep -Fq "org.apache.rocketmq:rocketmq-spring-boot-starter:jar:$contract_rocketmq_version" "$bom_consumer_dir/target/dependency-tree.txt"
grep -Fq "co.elastic.clients:elasticsearch-java:jar:$contract_elasticsearch_version" "$bom_consumer_dir/target/dependency-tree.txt"
run_maven_stage bom-isolated-verify "${bom_consumer_maven[@]}" clean verify

# 故意失败的 IT 必须由发布 Parent 的 Failsafe execution 捕获；单独目录避免正向 consumer 的绿色结果掩盖门禁失效。
mkdir -p "$failure_consumer_dir/src/test/java/io/github/yggdrasil/labs/fixture"
cat >"$failure_consumer_dir/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.yggdrasil-labs</groupId>
        <artifactId>mimir-boot-parent</artifactId>
        <version>$revision</version>
        <relativePath/>
    </parent>
    <groupId>io.github.yggdrasil-labs.fixture</groupId>
    <artifactId>mimir-failure-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <repositories>
        <repository><id>fixture</id><url>file://$repository_dir</url></repository>
    </repositories>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
EOF
cat >"$failure_consumer_dir/src/test/java/io/github/yggdrasil/labs/fixture/AlwaysFailIT.java" <<'EOF'
package io.github.yggdrasil.labs.fixture;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class AlwaysFailIT {

    @Test
    void alwaysFailsToProveFailsafeIsAnEffectiveGate() {
        fail("intentional consumer gate failure");
    }
}
EOF
test ! -e "$failure_consumer_cache_dir/io/github/yggdrasil-labs"
failure_maven=("$project_dir/mvnw" -B -s "$settings_file" -f "$failure_consumer_dir/pom.xml" "-Dmaven.repo.local=$failure_consumer_cache_dir")
run_maven_stage failure-online-resolve "${failure_maven[@]}" dependency:resolve -DoutputFile="$failure_consumer_dir/target/online-dependency-resolve.txt"
assert_fixture_mimir_repository_markers \
  "$failure_consumer_cache_dir" \
  mimir-boot-parent \
  mimir-boot
set +e
"${failure_maven[@]}" clean verify 2>&1 | tee "$logs_dir/failure-consumer-clean-verify.log"
failure_status="${PIPESTATUS[0]}"
set -e
if [[ "$failure_status" -eq 0 ]]; then
  echo "故意失败的 AlwaysFailIT 未使 failure consumer 的 verify 失败。" >&2
  exit 1
fi
failure_report="$failure_consumer_dir/target/failsafe-reports/TEST-io.github.yggdrasil.labs.fixture.AlwaysFailIT.xml"
test -s "$failure_report"
grep -Fq 'AlwaysFailIT' "$failure_report"
grep -Fq '<failure' "$failure_report"

echo "隔离发布消费者验证通过：Parent、独立 BOM-only consumer、starter flatten 抽查和故意失败 Failsafe 门禁均符合版本 $revision。"
