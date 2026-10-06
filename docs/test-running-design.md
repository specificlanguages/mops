# Test running

Status: implemented. See [development.md](development.md#test-running-integration-tests) for the validation fixtures and
pinned development baseline.

## Required scope

- Support all test families supported by the selected MPS version's testing framework, including native BaseLanguage
  unit tests, language tests, generator tests, and ordinary JUnit tests recognized by MPS. Support is not limited to
  tests eligible for execution inside an existing MPS process.
- Support MPS 2024.1, 2025.1, 2026.1, and master (2026.2 development). The release validation baselines are 2024.1.6,
  2025.1.4, and 2026.1.1, as configured by the shared integration-test matrix. Pin and record the exact master
  development build used for validation.
- Provide both a direct CLI interface and a Code Mode capability.

## Execution and selection

- Execute tests in a separate process for correctness and robustness. See
  [ADR-0013](adr/0013-tests-run-in-a-separate-process.md).
- Support root test-case, model, module, and whole-project selection. Individual test methods are unsupported.
- An omitted selection runs all project tests. Selecting a non-test root or a method is an error.
- Generate and compile the selected tests' owning modules and required dependencies by default before execution.
- Save the project before launching the worker and exclude other domain operations on that project for the run. The
  worker uses the existing checkout; this does not isolate external edits or roll back filesystem changes made by tests.
- Support cancellation and timeouts. Retain results for completed tests, mark interrupted runs incomplete, and return a
  nonzero CLI exit status for them.
- Use one overall deadline covering startup, build, discovery, and execution.
- Preparation errors stop the run before test execution instead of executing a runnable subset. Diagnostics identify the
  affected tests. Once execution starts, ordinary test failures do not stop remaining tests; framework skips retain
  their normal meaning.

## CLI

`mops test [TARGET_SEGMENT...]` accepts the existing space-separated Navigation Target syntax. An omitted target selects
all project tests.

- `--no-build` uses existing compiled output instead of generating and compiling. Missing compiled classes are errors.
- `--timeout SECONDS` sets the overall deadline, defaults to 900 seconds, and accepts 0 to disable it.
- Ctrl-C cancels the worker and retains completed results.
- `--json` emits the structured report. Text output provides a concise summary and the saved report path.
- Successful runs exit 0; unsuccessful runs exit 1. The report distinguishes failure causes.

## Code Mode

`mops.testing.run(selection, options)` accepts a native MPS project, module, model, or test node. It must be called
outside a model-access block and uses the same execution policy as the CLI.

The operation returns a structured report for test, build, discovery, and interruption outcomes. Invalid arguments and
failures that prevent obtaining a report throw. A failed test report does not automatically change the exit status of
`mops code run`; the calling program can inspect the report and continue.

The earlier of the test-run deadline and the enclosing Code Mode deadline wins. Expiration of the Code Mode deadline
retains its existing daemon-termination behavior and must also terminate the test worker. The saved partial report
remains accessible. A standalone `mops test` timeout terminates its worker and leaves the daemon usable.

## Results

- Discovering zero tests is an error.
- Skips and assumption aborts are reported but do not themselves fail the run, including runs with no passed tests.
- Failed tests, failed setup or containers, discovery errors, and interrupted execution produce a nonzero CLI exit
  status. Build failures stop execution and are reported separately from test failures.
- JSON results distinguish these outcomes and retain individual results, failure details, and source references.
- Every run automatically saves a report and updates it as results arrive, retaining completed results after
  cancellation or worker failure.

## Compatibility validation

Use the shared CLI integration-test infrastructure documented in [development.md](development.md). The
`integrationTestMps2024.1.6`, `integrationTestMps2025.1.4`, and `integrationTestMps2026.1.1` tasks resolve their MPS
distributions and matching host JBRs. `integrationTest` aggregates the release matrix and participates in `check` and
CI. The `integrationTestLocal` task accepts an extracted distribution and JBR, providing a validation path for a pinned
master build without introducing a separate harness. Master is not currently included in the default release matrix.

Feature validation must exercise representative native BaseLanguage, language, generator, and ordinary JUnit tests
across the supported versions, including ordinary legacy JUnit classes recognized by Ant. Verify supported selection
levels, build failure, missing classes, zero tests, skips and aborts, test and container failures, cancellation,
deadlines, retained partial reports, and daemon usability after standalone worker termination.

Existing Code Mode integration tests establish infrastructure behavior; they do not establish support for executing MPS
tests. Runtime probes and feature tests must validate the version-specific execution adapters.

## API evidence

Use the test-execution note in `specificlanguages/mps-api-research`, at `docs/test-execution.md`, for discovery,
classloading, session lifecycle, and version-specific execution APIs. Its source-verified contracts and runtime
validation requirements are distinct; support must be demonstrated with representative execution fixtures.

The companion `docs/legacy-test-execution.md` note records the environment-aware legacy runner contract and runtime
observations across the four validation baselines.

## Worker startup

The daemon resolves selection to persistent identities and descriptor paths. A separate controller performs the optional
incremental build in a normal-mode preparation environment and saves the project. No-build runs start Ant without
initializing a preparation MPS environment. It starts the selected distribution's Ant `launchtests` task on an
independent classpath. Ant constructs the test JVM arguments and classpath; a small `LaunchTestWorker` subclass inherits
test-mode environment startup and loads a version-specific launcher adapter through the stock launcher module's
classloader. The adapter filters stock module discovery, forwards listener callbacks to the stock failure detector, and
publishes atomic protocol events. The preparation process collects those events while supervising the Ant process.
Cancellation, deadline expiration, and parent termination kill the whole process tree.

Automatic plugin discovery loads installed plugins through normal plugin loading. MPS 2025.1 disables
`com.intellij.platform.images` in the isolated test configuration because its SVG parser on the Ant application
classpath cannot resolve the plugin's SVG language. Tests requiring Images are unsupported on 2025.1. No blacklist is
applied to 2024.1, 2026.1, or development versions. The normal daemon retains product-info boot-classpath parsing and
applies declared bootstrap-classpath JVM arguments.

The adapters preserve Ant's session, execution, XML reporting, and failure detection. They do not add the IDE's separate
environment-aware legacy runner. Model filtering happens after module-wide discovery and class loading; unselected
broken classes can still fail discovery. The adapters are compiled when building mops, against MPS 2024.1.6 and
2026.1.1, and packaged as separate JARs with Java 17 bytecode. The worker selects the launcher adapter by its
constructor API before creating a project or executing tests. Adapter selection does not retry test execution on linkage
failures. Running tests does not require `javac`.
