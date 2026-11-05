# Developing mops

## Build and test

```sh
./gradlew check
./gradlew installMops
./gradlew :cli:run --args="--mps-home /path/to/mps --java-home /path/to/jbr --project-root /path/to/project daemon ping"
./gradlew :cli:run --args=--help
./gradlew :daemon:run --args=--help
```

The repository is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`. The ping
command requires an MPS project root containing `.mps/`. `--java-home` can be omitted when the MPS distribution bundles
Java.

### Build version

`mops --version`, `mops-daemon --version`, the first line of `mops --help` and of `mops wrapper` output report the
`version` from `gradle.properties` together with the git commit of the build, for example `0.4.0-SNAPSHOT (abc1234)`.
The build runs `git describe --always --dirty` in the repository root and stores the result in the protocol jar, so an
installed distribution needs no git checkout. A build with uncommitted changes to tracked files reports `abc1234-dirty`.
A source tree without its own `.git`, or a machine without git, builds normally and reports `(unknown revision)`. Test
tasks ignore the commit on their classpath, so a new commit alone does not rerun them.

## Command names

Commands that operate on MPS data use a verb-first shape: `mops <verb> <object>`, such as `mops find instances`.
Operational commands use a concrete subsystem or artifact name, such as `daemon`, `code`, and `wrapper`.

## Commits and pull requests

Pull request titles follow [Conventional Commits](https://www.conventionalcommits.org/). Pull requests are
squash-merged, so the title becomes the commit message on `main`. The **Pull request title** workflow checks the title
with the `conventional-pre-commit` hook from `.pre-commit-config.yaml`, which runs only in the `manual` stage, and lists
the allowed types when it rejects one. GitHub release notes list the merged pull requests by title.

Commit messages on pull request branches are free-form.

Before you open a pull request:

- Cherry-picks and rebases don't run the `pre-commit` hook, so prek may not have checked commits made that way.
- Run `./gradlew detektMain` (see [Code structure checks](#code-structure-checks)).
- Run the focused tests for what you changed, for example one test class on one MPS version.
- After changing anything under `examples/`, run `./gradlew :cli:updateExamples` and commit the regenerated
  `skills/mops/references/` pages.
- Keep `.kt` and `.md` files under the `file-size` hook's limit. Split a file instead of growing it.

## Release

Releases are published from explicit release commits on `main`:

1. Change `version` in `gradle.properties` from the development snapshot to the exact release version and merge the
   release commit.
2. Run the **Release mops** workflow from `main`, entering the version without the `v` prefix.
3. After publication, change `gradle.properties` to the next development `-SNAPSHOT` version.

The workflow verifies the version, runs all checks, smoke-tests the ZIP distribution, generates its SHA-256 checksum,
and publishes both files in a GitHub Release with notes generated from the merged pull requests. It creates the
`v<version>` tag at the tested release commit; release tags must not be created manually.

## Code structure checks

`check` runs two kinds of structure checks on the Kotlin modules:

- **detekt** (`./gradlew detektMain`) runs the complexity and exceptions rule sets from `config/detekt/detekt.yml` on
  the main source sets, with type resolution. The complexity rules check method length, cyclomatic and cognitive
  complexity, nesting depth, parameter count, and class and file size. The exceptions rules check how exceptions are
  thrown and caught. Name a caught exception that is deliberately ignored `_`. Test sources are not checked. Reports are
  written to `<module>/build/reports/detekt/`. detekt runs in a separate worker process (`detekt.use.worker.api` in
  `gradle.properties`).
- **Architecture tests** in `architecture-tests/` (`./gradlew :architecture-tests:test`) use
  [Konsist](https://docs.konsist.lemonappdev.com/) to check dependencies that Gradle cannot see. They check that CLI
  command packages (`cli.find`, `cli.get`, ...) do not depend on each other. Gradle module dependencies already decide
  which modules can use MPS or each other.

Each module's `detekt-baseline-main.xml` lists the detekt findings that existed when the rules were introduced. These
findings do not fail the build; new findings do. Fix a new finding instead of adding it to the baseline. After fixing a
finding in the baseline, run `./gradlew :<module>:detektBaselineMain` to remove it.

Facades that expose one function per operation, such as `MpsRead`, `MpsWrite`, `MpsExtra`, and `DaemonClient`, are
marked `@Facade`, and detekt does not limit their number of functions. Classes that implement such a facade are not
flagged for the functions they override.

The `file-size` pre-commit hook rejects `.kt` and `.md` files larger than 20000 bytes and explains how to split them.
Files that were already larger are listed in the hook's `exclude` pattern in `.pre-commit-config.yaml`. Remove a file
from that list after splitting it.

The custom markdownlint rule `paragraph-length` in `tools/markdownlint/` rejects Markdown paragraphs with more words
than `max_words` in `.markdownlint-cli2.yaml`. When the rule, `.markdownlint-cli2.yaml`, or `.pre-commit-config.yaml`
changes, the `markdownlint-cli2-all` hook lints every Markdown file that Git does not ignore.

## Daemon tests on each MPS version

`./gradlew :daemon:test` runs the daemon tests against the MPS version pinned as `mps` in `gradle/libs.versions.toml`.
`./gradlew :daemon:testMps<version>` (for example `testMps2024.1.6`) runs the same test classes against each version in
`supportedMpsVersions`, with that version's distribution on the classpath and its matching JBR, both resolved through
mps-platform-cache. `./gradlew :daemon:testAllMpsVersions` runs all of them. Each task keeps its MPS state under
`daemon/build/mps-test-<version>/` and its reports under `daemon/build/reports/tests/<task-name>/`, so the tasks can run
side by side. `check` runs `:daemon:test` and, through `testOtherMpsVersions`, the tasks for the other supported
versions. CI runs these tasks in `daemon-versions` matrix jobs instead.

## Integration tests

`./gradlew :cli:integrationTest` runs the full CLI integration suite against every version listed in
`supportedMpsVersions` in `gradle.properties`. `./gradlew check` includes this matrix. Each version has its own task:

```sh
./gradlew :cli:integrationTestMps2024.1.6
./gradlew :cli:integrationTestMps2025.1.4
./gradlew :cli:integrationTestMps2026.1.1
./gradlew :cli:integrationTestMpsPrerelease
```

The `Prerelease` entry stands for the `mps-prerelease` version in `gradle/libs.versions.toml`, which selects both
`com.jetbrains.mps:mps-prerelease` and the matching `com.jetbrains.mps:mps-jbr` marker. Renovate updates it with the
other dependencies. Prerelease builds are deleted from the repository a few weeks after publishing, so a pinned
prerelease that is not updated stops resolving.

Each task resolves its MPS distribution and matching host-specific JBR through mps-platform-cache. Distributions are
cached and reused. Reports are written separately under `cli/build/reports/tests/<task-name>/`; JUnit XML results are
under `cli/build/test-results/<task-name>/`. Each task runs test classes in two parallel test JVMs. CI runs each version
in a separate Ubuntu matrix job. The CI matrices read their versions from `supportedMpsVersions`. Each job uploads its
test reports even on success, and test start/completion events appear in the job log to help identify slow or stalled
tests. Apply `--tests` to a version-specific task to select tests:

```sh
./gradlew :cli:integrationTestMps2024.1.6 --tests '*CodeModeIntegrationTest'
```

Starting an MPS daemon takes most of an integration test's time, so the tests of one class may share daemons. A daemon
serves one project root, so tests share a daemon by sharing a project copy. A class registers `SharedDaemons` with
`@RegisterExtension`, and `project(name)` returns one copy of a test project for the whole class. The copies share one
daemon home, and their daemons stop after the class. Tests that share a copy must pass whatever changes other tests make
to it. A test that must not see such changes passes its own `copy` key. Tests that stop, kill, or fail to start a
daemon, run a separate builder daemon or a modified MPS distribution, or run MPS tests in worker processes, create a
private daemon home in their own `@TempDir` and stop it with `stopDaemons`.

CI has a pull request tier and a full tier, which runs in the merge queue and on `main`. [ci.md](ci.md) describes the
tiers, the test shards of pull request runs, docs-only runs, and how runs of an already tested tree are skipped.

To test an already extracted distribution, use the separate local task:

```sh
./gradlew :cli:integrationTestLocal -PtestMpsHome=/path/to/mps -PtestJbrHome=/path/to/java/home
```

The local task is excluded from `check` and the supported-version matrix. These tests use the generic MPS archive
through the daemon launcher, without IDE-plugin host-platform validation.

### Cross-platform smoke tests

```sh
./gradlew :cli:smokeTest
./gradlew :cli:smokeUnitTest
./gradlew :cli:smokeIntegrationTest
```

`smokeTest` combines unit and integration tests tagged `@Tag("smoke")`. The unit task uses the normal unit-test
classpath and starts no MPS daemon. The integration task shares runtime configuration with the full integration suite
and uses the last release listed in `supportedMpsVersions` (not `Prerelease`) and its matching JBR. Both tasks have
separate reports. Tag a test class or method to add focused cross-platform smoke coverage; no workflow filter needs
updating. Tagged tests also run in their normal unit/integration suites. The smoke tasks are not added to `check`, which
already runs those suites.

### Code-mode runtime tests

The installed CLI integration test exercises model lookup, an edit, saving and reopening the project, extension
dispatch, and Groovy class identity. It runs both with the selected MPS distribution and with a test copy that omits its
Groovy JAR. CI includes code-mode tests for every supported MPS version.

Bundled agent skills live under `skills/` and ship alongside the CLI in its `skills/` directory. `CodeSkillExamplesTest`
extracts every fenced `groovy` block from `skills/mops/SKILL.md` and executes it verbatim against a fresh fixture copy.
When adding an example, add behavioral assertions for its heading to that test. Run the examples with:

```sh
./gradlew :daemon:test --tests '*CodeSkillExamplesTest'
```

### Bundled task recipe checks

The source catalog lives in [`examples/`](../examples/README.md). Groovy maps hold the snippets and their assertions;
Markdown templates supply prose and table-group slots. Catalogs contain named groups of ID-less examples and named
standalone snippets; group members supply their row titles. `generateExamples` produces the CLI pages and a test
manifest from that catalog. `updateExamples` refreshes the checked-in skill references; `checkExamples` rejects stale
references and runs in both `check` and `smokeTest`.

```sh
./gradlew :cli:updateExamples :cli:checkExamples
./gradlew :cli:test --tests '*ExamplesCommandTest'
```

`ExamplesCommandTest` parses CLI recipes directly from the catalog with the current command tree, including commands in
shell pipelines, without executing them. It validates JSON edit batches with the protocol serializer and generated
schema, and checks bundled reference links. These checks catch syntax and protocol drift without starting MPS. Markdown
lint checks the checked-in references, not the generated build output. Line-length checks apply to prose, excluding code
blocks and tables globally.

`BundledExamplesIntegrationTest` creates a named JUnit dynamic test for each executable Groovy spec. It starts one
daemon and gives every example fresh models in a disposable BaseLanguage project. Each spec declares its placeholder
bindings, optional preparation, and assertions on CLI output or saved model data. The runner executes the same snippet
used to generate the Markdown. Examples requiring other fixtures declare an explicit `untested` reason. The PR smoke
suite uses the last supported MPS version, and the full integration matrix runs the specs on each version.

```sh
./gradlew :cli:smokeIntegrationTest --tests '*BundledExamplesIntegrationTest'
```

### Command reference

`docs/reference/` is generated output: one page per top-level command with the `--help` output of the command and its
subcommands, one page per `mops explain` topic, and an index that also links the bundled skills. `updateReference`
renders it in-process from the command tree, without a daemon or MPS. `checkReference` rejects a stale reference and
runs in `check`. Change the command's help text or the explain page, never the generated page.

```sh
./gradlew :cli:updateReference :cli:checkReference
```

### Test-running integration tests

[Test-running integration tests](test-running-integration-tests.md) covers the test-running fixtures, the timing output
and test report timings, how to run the tests on each MPS version or an MPS development distribution, and the macOS
sandbox limitation.
