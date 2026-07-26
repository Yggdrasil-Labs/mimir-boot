#!/usr/bin/env bash

# Shared release-version guard for GitHub Actions shell steps.
# Release tags are intentionally limited to stable semantic versions because the release workflow
# calculates the next patch development version from the same value.

require_release_version() {
  local version="${1:-}"

  if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    echo "Error: invalid release version '$version'. Expected MAJOR.MINOR.PATCH." >&2
    return 1
  fi
}

require_maven_revision() {
  local revision="${1:-}"
  local release_version="${revision%-SNAPSHOT}"

  if [ "$revision" != "$release_version" ] && [ "$revision" != "${release_version}-SNAPSHOT" ]; then
    echo "Error: invalid Maven revision '$revision'." >&2
    return 1
  fi

  require_release_version "$release_version"
}

update_maven_revision() {
  local pom_file="$1"
  local version="$2"

  require_maven_revision "$version" || return 1

  if [[ ! -f "$pom_file" ]]; then
    echo "Error: pom.xml not found: $pom_file" >&2
    return 1
  fi

  sed -i "s|<revision>[^<]*</revision>|<revision>$version</revision>|" "$pom_file"
}
