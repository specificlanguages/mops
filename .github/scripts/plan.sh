#!/usr/bin/env bash
# Plans a CI run of the checked-out HEAD and prints the plan as `key=value` lines. Needs the full history of the
# checkout. docs/ci.md describes the tiers and the docs-only rule.
#
# Environment:
#   EVENT_NAME  the GitHub event: pull_request, merge_group, push, or workflow_dispatch.
#   BASE_REF    for merge_group: the full ref of the branch the merge group will be merged into.
#   BASE_SHA    for push: the previous head of the branch.
#   FORCED      "true" for a force push.
set -euo pipefail

# Pull request runs split the slowest test jobs into shards that run side by side. shardTests in
# build-logic/src/main/kotlin/testShards.kt assigns the tests to the shards.
pr_daemon_shards=2
pr_integration_shards=7

# Prints "true" when every changed path is documentation that no build task, test, or generated-page check reads, or
# configuration that only the prek hooks read: files under docs/, Markdown files in the repository root, and the lint
# configuration files and hook scripts in lint_config. docs/reference/ is left out for the generated command reference
# pages proposed in PR #175, which a Gradle check compares with the CLI. Renames count as changes to both paths. Prints
# "false" when the base is missing or not in the history, or when nothing changed.
docs_only() {
  local base="$1"
  if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
    echo false
    return
  fi
  local changed
  changed="$(git diff --no-renames --name-only "$base" HEAD)"
  local lint_config='\.markdownlint-cli2\.yaml|\.pre-commit-config\.yaml|\.prettierrc\.yaml|\.prettierignore|\.editorconfig|tools/(markdownlint|pre-commit)/.+'
  if [ -n "$changed" ] && ! grep -qvE "^(docs/.+|[^/]+\.md|$lint_config)$" <<< "$changed" && ! grep -qE '^docs/reference/' <<< "$changed"; then
    echo true
  else
    echo false
  fi
}

# Prints the matrix entries for the MPS versions in the JSON array $1, each split into $2 shards.
shards() {
  jq -c --argjson count "$2" '[.[] as $mps | range(1; $count + 1) | {mps: $mps, shard: ., shards: $count}]' <<< "$1"
}

tree="$(git rev-parse 'HEAD^{tree}')"
versions="$(sed -n 's/^supportedMpsVersions=//p' gradle.properties | jq -Rc 'split(",") | map(gsub("^\\s+|\\s+$"; ""))')"
releases="$(jq -c 'map(select(. != "Prerelease")) | sort_by(split(".") | map(tonumber))' <<< "$versions")"
marker_key=''
case "$EVENT_NAME" in
  pull_request)
    tier='pr'
    marker_key="ci-passed-pr-$tree"
    newest="$(jq -c '[last]' <<< "$releases")"
    # The oldest release runs in one job and the newest in shards. A single release runs in shards only.
    oldest="$(jq -c 'if length > 1 then [first] else [] end' <<< "$releases")"
    daemon_jobs="$(jq -c -s add <<< "$(shards "$oldest" 1) $(shards "$newest" "$pr_daemon_shards")")"
    integration_jobs="$(shards "$newest" "$pr_integration_shards")"
    # HEAD is the merge commit of the pull request and HEAD^1 is the base branch.
    docs_only="$(docs_only "$(git rev-parse 'HEAD^1')")"
    ;;
  merge_group)
    tier='full'
    daemon_jobs="$(shards "$versions" 1)"
    integration_jobs="$(shards "$versions" 1)"
    # Compare with the target branch, not with the group's parent commit: the parent can be the commit of an earlier
    # group in the queue, whose changes are not on the target branch yet. No marker is saved, because pull request runs
    # cannot read caches saved on merge queue refs.
    docs_only="$(docs_only "$(git merge-base "refs/remotes/origin/${BASE_REF#refs/heads/}" HEAD 2>/dev/null || true)")"
    ;;
  *)
    tier='full'
    marker_key="ci-passed-$tree"
    daemon_jobs="$(shards "$versions" 1)"
    integration_jobs="$(shards "$versions" 1)"
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
echo "daemon-jobs=$daemon_jobs"
echo "integration-jobs=$integration_jobs"
