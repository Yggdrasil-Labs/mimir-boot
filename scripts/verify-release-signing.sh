#!/usr/bin/env bash
set -euo pipefail

preheat=false
case "$#" in
  0) ;;
  1)
    if [[ "$1" == "--preheat" ]]; then
      preheat=true
    else
      echo "用法: $0 [--preheat]" >&2
      exit 2
    fi
    ;;
  *)
    echo "用法: $0 [--preheat]" >&2
    exit 2
    ;;
esac

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d -t mimir-release-signing.XXXXXX)"
gnupg_home="$work_dir/gnupg"
verify_home="$work_dir/verify-gnupg"
repository_dir="$work_dir/repository"
failed_repository_dir="$work_dir/failed-repository"
blocked_repository_dir="$work_dir/blocked-remote"
cache_dir="$work_dir/m2"
seed_cache_dir="${MIMIR_RELEASE_SIGNING_SEED_M2:-$HOME/.m2/repository}"
fixture="$work_dir/gpg-failure-fixture.sh"
settings_file="$work_dir/settings.xml"
preheat_settings_file="$work_dir/preheat-settings.xml"
preheat_pom="$work_dir/preheat-pom.xml"

cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

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

if [[ "$preheat" == true && -z "$proxy_xml" ]]; then
  echo "--preheat 需要 MIMIR_MAVEN_PROXY 或 HTTPS_PROXY 中的无凭据 HTTP 代理。" >&2
  exit 2
fi

umask 077
test -d "$seed_cache_dir"
mkdir -p "$gnupg_home" "$verify_home" "$repository_dir" "$failed_repository_dir" "$blocked_repository_dir" "$cache_dir"
cp -a "$seed_cache_dir/." "$cache_dir/"
chmod 700 "$gnupg_home" "$verify_home"

if [[ "$preheat" == true ]]; then
  cat >"$preheat_settings_file" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">$proxy_xml
  <mirrors>
    <mirror>
      <id>maven-central</id>
      <mirrorOf>central</mirrorOf>
      <url>https://repo.maven.apache.org/maven2</url>
    </mirror>
  </mirrors>
</settings>
EOF
  cat >"$preheat_pom" <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.yggdrasil-labs.fixture</groupId>
  <artifactId>release-signing-preheat</artifactId>
  <version>1.0.0</version>
</project>
EOF
  "$project_dir/mvnw" -B -s "$preheat_settings_file" -f "$preheat_pom" \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get \
    -Dartifact=org.apache.maven.plugins:maven-gpg-plugin:3.2.8 \
    -Dtransitive=true \
    -Dmaven.repo.local="$cache_dir"
fi

cat >"$settings_file" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>maven-central</id>
      <mirrorOf>central</mirrorOf>
      <url>file://$blocked_repository_dir</url>
    </mirror>
  </mirrors>
</settings>
EOF

export GNUPGHOME="$gnupg_home"
gpg --batch --pinentry-mode loopback --passphrase '' --quick-generate-key \
  'Mimir Boot Release Fixture <fixture@example.invalid>' future-default default never
gpg --batch --armor --export fixture@example.invalid >"$work_dir/public.asc"

deploy_command=(
  "$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" deploy
  -Dmaven.test.skip=true
  -Dmaven.repo.local="$cache_dir"
  -Dmaven.deploy.skip=false
  -Dgpg.skip=false
  -Dgpg.executable=gpg
  "-DaltDeploymentRepository=fixture::default::file://$repository_dir"
)
clean_command=(
  "$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" clean
  -Dmaven.repo.local="$cache_dir"
)
"${clean_command[@]}"
"${deploy_command[@]}"

mapfile -d '' artifacts < <(find "$repository_dir" -type f \( -name '*.pom' -o -name '*.jar' \) -print0)
test "${#artifacts[@]}" -gt 0

export GNUPGHOME="$verify_home"
gpg --batch --import "$work_dir/public.asc"
for artifact in "${artifacts[@]}"; do
  test -s "${artifact}.asc"
  gpg --batch --verify "${artifact}.asc" "$artifact"
done

cat >"$fixture" <<'EOF'
#!/usr/bin/env bash
exit 7
EOF
chmod 700 "$fixture"

failed_clean_command=(
  "$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" clean
  -Dmaven.repo.local="$cache_dir"
)
"${failed_clean_command[@]}"

set +e
GNUPGHOME="$gnupg_home" "$project_dir/mvnw" -B -s "$settings_file" -f "$project_dir/pom.xml" deploy \
  -Dmaven.test.skip=true \
  -Dmaven.repo.local="$cache_dir" \
  -Dmaven.deploy.skip=false \
  -Dgpg.skip=false \
  -Dgpg.executable="$fixture" \
  "-DaltDeploymentRepository=fixture::default::file://$failed_repository_dir"
failed_status=$?
set -e
test "$failed_status" -ne 0
! find "$failed_repository_dir" -type f -print -quit | grep .
echo "发布签名验证通过：${#artifacts[@]} 个制品及附属制品均由临时密钥签名，失败 fixture 已阻断部署。"
