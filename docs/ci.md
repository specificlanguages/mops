# Continuous integration

CI has two tiers. The full tier runs in the merge queue, on pushes to `main`, and on manual workflow dispatch. It runs
the daemon tests and the CLI integration suite on every version in `supportedMpsVersions`, the CLI smoke suite on macOS
and Windows, and an Ubuntu verification job. Linux has no separate smoke job: the integration job for the last supported
version runs every smoke test on Ubuntu. The pull request tier runs the verification job, the daemon tests on the oldest
and the newest release in `supportedMpsVersions` (not `Prerelease`), and the CLI integration suite on the newest release
only. To run the full tier on a branch before it enters the merge queue, dispatch the workflow on it:

```sh
gh workflow run ci.yml --ref <branch>
```

The pull request tier splits the integration suite and the daemon tests on the newest release into shards, with the
counts set in `plan.sh`. Each shard is its own job, so the shards run side by side. The full tier runs each version's
tests in one job. The `testShard` Gradle property picks one shard of a `:cli:integrationTestMps<version>` or
`:daemon:testMps<version>` task, for example `-PtestShard=3/7`. A shard runs its tests in a fresh Gradle build, so no
test class may depend on another class running before it in the same JVM.

`shardTests` in `build-logic/src/main/kotlin/testShards.kt` puts each test class in a shard by a hash of its fully
qualified name, so each test runs in exactly one shard and new tests need no setup. The classes in the `splitClasses`
list of the `shardTests` call in `cli/build.gradle.kts` are split by method instead: each method goes to a shard by a
hash of `<class>.<method>`, and `TestShardMethodFilter` in the integration test sources drops the methods of other
shards. Add a class to the list when it takes much longer than the others and its methods share no state, such as a
shared daemon, `@BeforeAll` setup, or a per-class lifecycle. When one shard takes much longer than the others, split a
class or change the salt in `testShardOf`; the filter has a copy of the same hash.

The verification job runs `check -x :cli:integrationTest -x :daemon:test -x :daemon:testOtherMpsVersions`, covering all
subprojects' unit tests, daemon checks, Gradle discovery tests, and packaging checks, and then runs the `prek` hooks on
all files. The separate `daemon-versions` and integration matrices cover the excluded daemon tests and CLI integration
suite. The `plan` job (`.github/scripts/plan.sh`) picks the tier, the versions, and the shards and prints them in its
summary. The `CI passed` job requires every job to succeed or to be skipped by the plan. It is the required check in
both tiers. Release validation runs the complete `check`, including the full integration matrix.

A docs-only run skips the test jobs, and its verification job runs only the `prek` hooks, without Gradle. A run is
docs-only when every changed file is under `docs/` or is a Markdown file in the repository root, and none is under
`docs/reference/`. That folder is left out for the generated command reference pages proposed in PR #175, which a Gradle
check compares with the CLI. A renamed file counts as changed at both paths. No build task or test reads the other
files.

Changes to the configuration that only the `prek` hooks read also count as docs-only: `.markdownlint-cli2.yaml`,
`.pre-commit-config.yaml`, `.prettierrc.yaml`, `.prettierignore`, `.editorconfig`, and the hook scripts under
`tools/markdownlint/` and `tools/pre-commit/`. The verification job runs the hooks on all files, so it checks the whole
repository against the changed configuration. In a docs-only run the build does not generate the pages under
`cli/build/generated/examples/pages/`, so markdownlint does not lint them, but it lints their checked-in copies under
`skills/mops/references/`.

A pull request compares its merge commit with the base branch. A merge queue run compares with the target branch, so the
changes of earlier groups in the queue count too. A push to `main` compares with the previous head. Pushes to `main` do
not cancel each other, so every push is compared with a head that CI also checks. Everything runs on a manual dispatch,
a force push, a push without a previous head, a base that is not in the history, and an empty diff.

When every planned job succeeds and the test jobs ran, `CI passed` saves a cache entry for the tested git tree:
`ci-passed-<tree>` in the full tier, `ci-passed-pr-<tree>` in the pull request tier. Docs-only runs and merge queue runs
save no entry; pull request runs cannot read entries saved on merge queue refs. For a pull request the tested tree is
the tree of the merge commit, and the `plan` job prints it in its summary. A pull request run that finds either entry
for its tree skips the verification and test jobs, and `CI passed` still succeeds, for example after a stacked pull
request is rebased onto `main` without changing its code. Re-running a skipped run skips again.

Entries saved by a pull request run are visible only to later runs of the same pull request; entries saved by runs on
`main` are visible to all pull requests. Merge queue runs, pushes to `main`, and manual dispatches never look up an
entry, so a pull request tier entry never replaces a full tier run. To make a pull request run without skipping, delete
both entries with `gh cache delete ci-passed-<tree>` and `gh cache delete ci-passed-pr-<tree>`, then re-run the
workflow.
