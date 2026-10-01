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
`main` run only the daemon lifecycle subset on Linux, macOS, and Windows. Release validation still runs `check`,
including the full integration matrix.

To test an already extracted distribution, use the separate local task:

```sh
./gradlew :cli:integrationTestLocal -PtestMpsHome=/path/to/mps -PtestJbrHome=/path/to/java/home
```

The local task is excluded from `check` and the supported-version matrix. These tests use the generic MPS archive
through the daemon launcher, without IDE-plugin host-platform validation.

### Code-mode runtime tests

The installed CLI integration test exercises model lookup, an edit, saving and reopening the project, extension
dispatch, and Groovy class identity. It runs both with the selected MPS distribution and with a test copy that omits its
Groovy JAR. CI includes code-mode tests for every supported MPS version.

### Test-running integration tests

`TestFamiliesIntegrationTest` checks native BaseLanguage, language, generator, Jupiter, parameterized, and legacy JUnit
results. Its test methods share immutable reports from a completed full-project run and a parameterized-method selection
run. The fixture daemon stops before any method inspects those reports. Slow and crashing models are removed from that
fixture copy. Each request still executes in a separate worker process.

`TestRunningIntegrationTest` uses fresh project copies for selection at all five levels, preparation failures, and
worker lifecycle behavior. The fixtures live in `test-projects/testing`. The lifecycle test deliberately waits for two
90-second deadlines; allow several minutes per version. Runtime reload and daemon lifecycle tests also retain their
independent process boundaries.

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
