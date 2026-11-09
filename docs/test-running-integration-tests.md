# Test-running integration tests

`TestFamiliesIntegrationTest` checks the results of each kind of test in the `test-projects/testing` fixture, which its
[README](../test-projects/testing/README.md) lists. Its test methods share immutable reports from a completed
full-project run and a parameterized-case selection run. The fixture daemon stops before any method inspects those
reports. Slow and crashing models are removed from that fixture copy. Each request still executes in a separate worker
process.

Integration JUnit output includes `MOPS_CLI_TIMING` for calls through the `runCommandLine` test helper. The
`stopDaemons` helper emits `MOPS_DAEMON_STOP_TIMING` for daemon shutdown including its exit wait. CLI duration includes
daemon startup when a call starts a daemon. Before deleting fixtures, `stopDaemons` copies test-run reports and
supervisor/Ant logs to `cli/build/test-results/test-run-diagnostics/<MPS version>/<run ID>/`, included in CI's
test-report artifact. A job killed before fixture teardown may not retain these files.

Each test report's `timingsMillis` records process-local monotonic durations: `build` includes the daemon's
generation/compilation and saving, `antStartupAndDiscovery` includes Ant setup and worker startup through discovery,
`execution` ends when the test plan finishes, and `antShutdown` ends when Ant exits. Event collection has 50 ms polling
resolution. `workerLifetime` is the parent's enclosing spawn-to-exit measurement and overlaps the worker phases; do not
add it to them. Missing phases were not reached or could not be saved before a process was killed. These timings do not
separately measure plugin loading or indexing. Per-event report writes checkpoint the active duration, and orderly
failures/timeouts close it.

`TestRunningIntegrationTest` uses fresh project copies for root test-case/model/module/project selection, multiple test
modules, preparation failures, and worker lifecycle behavior. The fixtures live in `test-projects/testing`, and the
project-library and path-macro fixture in `test-projects/project-library`. The lifecycle test reacts to report updates
instead of waiting out deadlines, apart from one 15-second test execution limit. Runtime reload and daemon lifecycle
tests also retain their independent process boundaries.

`WrapperIntegrationTest` puts a copy of the project-library fixture into a Gradle build, makes its project library path
use a path macro, runs the installed `mops wrapper`, and runs a command through the written `mopsw`.

```sh
./gradlew :cli:integrationTestMps2024.1.6 --tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest' \
  :cli:integrationTestMps2025.1.4 --tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest' \
  :cli:integrationTestMps2026.1.1 --tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest'
```

The build pins MPS 2026.1.1 in `gradle/libs.versions.toml`. To test an MPS 2026.2 development distribution, use
`integrationTestLocal` with `--tests '*TestRunningIntegrationTest' --tests '*TestFamiliesIntegrationTest'` and the
extracted distribution/JBR paths. For distributions containing `lib/intellij.libraries.junit5.jar`, the integration
fixture adjusts Jupiter API imports to the `JUnit` module in its temporary copy.

On macOS, MPS's IDEA environment still initializes AWT in headless mode. A sandbox that prevents application
registration can abort the JVM before tests start. Run with `--no-daemon` outside that sandbox so an existing sandboxed
Gradle daemon is not reused.
