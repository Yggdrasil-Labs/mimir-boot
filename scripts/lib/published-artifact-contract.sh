#!/usr/bin/env bash

assert_fixture_published_artifact() {
  local repository_dir="$1"
  local revision="$2"
  local artifact="$3"
  local artifact_dir="$repository_dir/io/github/yggdrasil-labs/$artifact/$revision"

  if [[ ! -d "$artifact_dir" ]]; then
    echo "fixture repository 缺少 $artifact 的版本目录：$artifact_dir" >&2
    return 1
  fi
  if [[ "$revision" == *-SNAPSHOT && ! -s "$artifact_dir/maven-metadata.xml" ]]; then
    echo "fixture repository 缺少 $artifact 的 Snapshot 元数据：$artifact_dir/maven-metadata.xml" >&2
    return 1
  fi
  if ! find "$artifact_dir" -maxdepth 1 -type f -name '*.pom' -size +0c -print -quit | grep -q .; then
    echo "fixture repository 缺少 $artifact 的已发布 POM：$artifact_dir" >&2
    return 1
  fi
}
