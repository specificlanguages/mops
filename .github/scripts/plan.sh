#!/usr/bin/env bash
# Plans a CI run of the checked-out HEAD and prints the plan as `key=value` lines. Needs the full history of the
# checkout. docs/development.md describes the tiers and the docs-only rule.
#
# Environment:
#   EVENT_NAME  the GitHub event: pull_request, merge_group, push, or workflow_dispatch.
#   BASE_REF    for merge_group: the full ref of the branch the merge group will be merged into.
#   BASE_SHA    for push: the previous head of the branch.
#   FORCED      "true" for a force push.
set -euo pipefail

# Prints "true" when every changed path is documentation that no build task, test, or generated-page check reads:
# files under docs/ and Markdown files in the repository root. docs/reference/ is left out for the generated command
# reference pages proposed in PR #175, which a Gradle check compares with the CLI. Renames count as changes to both
# paths. Prints "false" when the base is missing or not in the history, or when nothing changed.
docs_only() {
  local base="$1"
  if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
    echo false
    return
  fi
  local changed
  changed="$(git diff --no-renames --name-only "$base" HEAD)"
  if [ -n "$changed" ] && ! grep -qvE '^(docs/.+|[^/]+\.md)$' <<< "$changed" && ! grep -qE '^docs/reference/' <<< "$changed"; then
    echo true
  else
    echo false
  fi
}

tree="$(git rev-parse 'HEAD^{tree}')"
versions="$(sed -n 's/^supportedMpsVersions=//p' gradle.properties | jq -Rc 'split(",") | map(gsub("^\\s+|\\s+$"; ""))')"
releases="$(jq -c 'map(select(. != "Prerelease")) | sort_by(split(".") | map(tonumber))' <<< "$versions")"
marker_key=''
case "$EVENT_NAME" in
  pull_request)
    tier='pr'
    marker_key="ci-passed-pr-$tree"
    daemon_versions="$(jq -c '[first, last] | unique' <<< "$releases")"
    integration_versions="$(jq -c '[last]' <<< "$releases")"
    # HEAD is the merge commit of the pull request and HEAD^1 is the base branch.
    docs_only="$(docs_only "$(git rev-parse 'HEAD^1')")"
    ;;
  merge_group)
    tier='full'
    daemon_versions="$versions"
    integration_versions="$versions"
    # Compare with the target branch, not with the group's parent commit: the parent can be the commit of an earlier
    # group in the queue, whose changes are not on the target branch yet. No marker is saved, because pull request runs
    # cannot read caches saved on merge queue refs.
    docs_only="$(docs_only "$(git merge-base "refs/remotes/origin/${BASE_REF#refs/heads/}" HEAD 2>/dev/null || true)")"
    ;;
  *)
    tier='full'
    marker_key="ci-passed-$tree"
    daemon_versions="$versions"
    integration_versions="$versions"
    if [ "$EVENT_NAME" = push ] && [ "${FORCED:-false}" != true ]; then
      docs_only="$(docs_only "${BASE_SHA:-}")"
    else
      docs_only=false
    fi
    ;;
esac

echo "tree=$tree"
echo "tier=$tier"
echo "docs-only=$docs_only"
echo "marker-key=$marker_key"
echo "daemon-versions=$daemon_versions"
echo "integration-versions=$integration_versions"
