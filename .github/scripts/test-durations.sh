#!/usr/bin/env bash
# Prints the mean duration of each test class, or of each test method for the classes named with --split, in the format
# that shardTests in build-logic/src/main/kotlin/testShards.kt reads. Reads the Gradle JUnit XML reports (TEST-*.xml)
# under the given directories, for example the test report artifacts of several CI runs. Split only classes whose test
# methods do not depend on each other, because their methods can run in different shards.
#
# Usage: test-durations.sh [--split <fully qualified class name>]... <directory>...
set -euo pipefail

splits=''
while [ "${1:-}" = --split ]; do
  splits+="$2 "
  shift 2
done
if [ $# -eq 0 ]; then
  echo "usage: $0 [--split <fully qualified class name>]... <directory>..." >&2
  exit 2
fi

echo "# Mean seconds per test class, or per test method of a split class, from JUnit XML reports."
echo "# Written by .github/scripts/test-durations.sh$(for class in $splits; do printf ' --split %s' "$class"; done)."

# shellcheck disable=SC2016 # The awk program's $0 is not a shell expansion.
find "$@" -name 'TEST-*.xml' -print0 | xargs -0 awk -v splits="$splits" '
  function attr(line, key) {
    if (!match(line, " " key "=\"[^\"]*\"")) return ""
    return substr(line, RSTART + length(key) + 3, RLENGTH - length(key) - 4)
  }
  function unescape(text) {
    gsub(/&quot;/, "\"", text); gsub(/&apos;/, "\047", text); gsub(/&lt;/, "<", text); gsub(/&gt;/, ">", text)
    gsub(/&amp;/, "\\&", text)
    return text
  }
  function add(test, seconds) { total[test] += seconds; count[test]++ }
  BEGIN { n = split(splits, list, " "); for (i = 1; i <= n; i++) split_class[list[i]] = 1 }
  /<testsuite / {
    suite = attr($0, "name")
    if (!(suite in split_class)) add(suite, attr($0, "time"))
  }
  /<testcase / && suite in split_class {
    method = unescape(attr($0, "name"))
    sub(/\(\)$/, "", method)
    add(suite "." method, attr($0, "time"))
  }
  END { for (test in total) printf "%d %s\n", total[test] / count[test] + 0.5, test }
' | sort -k1,1nr -k2
