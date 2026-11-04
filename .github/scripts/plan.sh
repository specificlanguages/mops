#!/usr/bin/env bash
# Plans a CI run of the checked-out HEAD and prints the plan as `key=value` lines. docs/development.md describes the
# tiers and the docs-only rule.
#
# Environment:
#   EVENT_NAME  the GitHub event: pull_request, merge_group, push, or workflow_dispatch.
#   BASE_SHA    the commit to compare HEAD with: the merge group base for merge_group, the previous head for push.
#               Ignored for pull_request, where HEAD is the merge commit and HEAD^1 is the base branch.
#   FORCED      "true" for a force push.
set -euo pipefail

# Prints "true" when every changed path is documentation that no build task, test, or generated-page check reads:
# files under docs/ (except the generated command reference in docs/reference/) and Markdown files in the repository
# root. Prints "false" when the base is unknown, cannot be fetched, or nothing changed.
docs_only() {
  local base="$1"
  if [ -z "$base" ] || [ "$base" = 0000000000000000000000000000000000000000 ]; then
    echo false
    return
  fi
  if ! git cat-file -e "$base^{commit}" 2>/dev/null &&
    ! git fetch --quiet --no-tags --depth=1 origin "$base" 2>/dev/null; then
    echo false
    return
  fi
  local changed
  changed="$(git diff --name-only "$base" HEAD)"
  if [ -n "$changed" ] && ! grep -qvE '^(docs/.+|[^/]+\.md)$' <<< "$changed" && ! grep -qE '^docs/reference/' <<< "$changed"; then
    echo true
  else
    echo false
  fi
}

tree="$(git rev-parse 'HEAD^{tree}')"
versions="$(sed -n 's/^supportedMpsVersions=//p' gradle.properties | jq -Rc 'split(",") | map(gsub("^\\s+|\\s+$"; ""))')"
case "$EVENT_NAME" in
  pull_request)
    tier=pr
    marker_key="ci-passed-pr-$tree"
    daemon_versions="$(jq -c 'map(select(. != "Prerelease")) | [first, last] | unique' <<< "$versions")"
    integration_versions="$(jq -c 'map(select(. != "Prerelease")) | [last]' <<< "$versions")"
    docs_only="$(docs_only "$(git rev-parse 'HEAD^1')")"
    ;;
  *)
    tier=full
    marker_key="ci-passed-$tree"
    daemon_versions="$versions"
    integration_versions="$versions"
    if [ "$EVENT_NAME" = workflow_dispatch ] || [ "${FORCED:-false}" = true ]; then
      docs_only=false
    else
      docs_only="$(docs_only "${BASE_SHA:-}")"
    fi
    ;;
esac

echo "tree=$tree"
echo "tier=$tier"
echo "docs-only=$docs_only"
echo "marker-key=$marker_key"
echo "daemon-versions=$daemon_versions"
echo "integration-versions=$integration_versions"
