# Developing mops

## Build and test

```sh
./gradlew check
./gradlew installMops
./gradlew :cli:run --args="--mps-home /path/to/mps daemon ping"
./gradlew :cli:run --args=--help
./gradlew :daemon:run --args=--help
```

The repository is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`.

## Integration tests

`./gradlew :cli:integrationTest` runs the full CLI integration suite against every version listed in
`supportedMpsVersions` in `gradle.properties`. `./gradlew check` includes this matrix. Each version has its own task:

```sh
./gradlew :cli:integrationTestMps2024.1.6
./gradlew :cli:integrationTestMps2025.1.4
./gradlew :cli:integrationTestMps2026.1.1
```

Each task resolves its MPS distribution and matching host-specific JBR through mps-platform-cache. Distributions are
cached and reused. Reports are written separately under `cli/build/reports/tests/<task-name>/`; JUnit XML results are
under `cli/build/test-results/<task-name>/`. CI reads the supported versions from `gradle.properties` and runs each
version in a separate Ubuntu matrix job, with independent test JVMs and MPS daemons. Each job uploads its test reports
even on success, and test start/completion events appear in the job log to help identify slow or stalled tests. Apply
`--tests` to a version-specific task to select tests:

```sh
./gradlew :cli:integrationTestMps2024.1.6 --tests '*CodeModeIntegrationTest'
```

The full integration matrix runs nightly on `main` at 02:17 UTC and on manual workflow dispatch. PRs and pushes to
`main` also run the CLI smoke suite on Linux, macOS, and Windows. Release validation still runs `check`, including the
full integration matrix.

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
and uses the last version listed in `supportedMpsVersions` and its matching JBR. Both tasks have separate reports. Tag a
test class or method to add focused cross-platform smoke coverage; no workflow filter needs updating. Tagged tests also
run in their normal unit/integration suites. The smoke tasks are not added to `check`, which already runs those suites.

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
schema, and checks bundled reference links. These checks catch syntax and protocol drift without starting MPS. The
pre-commit CI job generates the pages first; Markdown lint includes the generated build output as well as checked-in
references. Line-length checks apply to prose, excluding code blocks and tables globally.

`BundledExamplesIntegrationTest` creates a named JUnit dynamic test for each executable Groovy spec. It starts one
daemon and gives every example fresh models in a disposable BaseLanguage project. Each spec declares its placeholder
bindings, optional preparation, and assertions on CLI output or saved model data. The runner executes the same snippet
used to generate the Markdown. Examples requiring other fixtures declare an explicit `untested` reason. The PR smoke
suite uses the last supported MPS version, and the full integration matrix runs the specs on each version.

```sh
./gradlew :cli:smokeIntegrationTest --tests '*BundledExamplesIntegrationTest'
```

### Test-running integration tests

`TestFamiliesIntegrationTest` checks native BaseLanguage, language, generator, Jupiter, parameterized, and legacy JUnit
results. Its test methods share immutable reports from a completed full-project run and a parameterized-case selection
run. The fixture daemon stops before any method inspects those reports. Slow and crashing models are removed from that
fixture copy. Each request still executes in a separate worker process.

Integration JUnit output includes `MOPS_CLI_TIMING` for each CLI call and `MOPS_DAEMON_STOP_TIMING` for daemon shutdown
including its exit wait. CLI duration includes daemon startup when a call starts a daemon. Before deleting fixtures,
teardown copies test-run reports and preparation/Ant logs to
`cli/build/test-results/test-run-diagnostics/<MPS version>/<run ID>/`, included in CI's test-report artifact. A job
killed before fixture teardown may not retain these files.

Each test report's `timingsMillis` records process-local monotonic durations: `preparationStartup` measures preparation
entry to project opening (or the no-build shortcut), `build` includes generation/compilation and saving,
`antStartupAndDiscovery` includes Ant setup and worker startup through discovery, `execution` ends when the test plan
finishes, and `antShutdown` ends when Ant exits. Event collection has 50 ms polling resolution. `workerLifetime` is the
parent's enclosing spawn-to-exit measurement and overlaps the worker phases; do not add it to them. Missing phases were
not reached or could not be saved before a process was killed. These timings do not separately measure plugin loading or
indexing. Per-event report writes checkpoint the active duration, and orderly failures/timeouts close it.

`TestRunningIntegrationTest` uses fresh project copies for root test-case/model/module/project selection, multiple test
modules, preparation failures, and worker lifecycle behavior. The fixtures live in `test-projects/testing`. The
lifecycle test deliberately waits for two 90-second deadlines; allow several minutes per version. Runtime reload and
daemon lifecycle tests also retain their independent process boundaries.

```sh
./gradlew :cli:integrationTestMps2024.1.6 --tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest' \
  :cli:integrationTestMps2025.1.4 --tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest' \
  :cli:integrationTestMps2026.1.1 --tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest'
```

The pinned development baseline is MPS 2026.2 EAP1, build `MPS-262.9437.166` (2026-08-31), source revision
`46065cdc79a9467a53197d1a274b4e97d2a0f603`, with JBR `25.0.3-b508.16`. Run it through `integrationTestLocal` with
`--tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest'` and the extracted distribution/JBR paths.
Its Jupiter API stubs belong to the `JUnit` module; the integration fixture adjusts those imports in its temporary copy.

On macOS, MPS's IDEA environment still initializes AWT in headless mode. A sandbox that prevents application
registration can abort the JVM before tests start. Run with `--no-daemon` outside that sandbox so an existing sandboxed
Gradle daemon is not reused.
