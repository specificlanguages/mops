# Test running

Status: implemented. See [Test-running integration tests](test-running-integration-tests.md) for the validation fixtures
and pinned development baseline.

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
- Limit the time tests may run. The limit counts from the start of test execution, so startup, build, and discovery do
  not use it up. The test worker enforces it, because only the worker knows when execution starts.
- Preparation errors stop the run before test execution instead of executing a runnable subset. Diagnostics identify the
  affected tests. Once execution starts, ordinary test failures do not stop remaining tests; framework skips retain
  their normal meaning.

## CLI

`mops test [TARGET_SEGMENT...]` accepts the existing space-separated Navigation Target syntax. An omitted target selects
all project tests.

- `--no-build` uses existing compiled output instead of generating and compiling. Missing compiled classes are errors.
- `--timeout SECONDS` limits test execution time, defaults to 900 seconds, and accepts 0 to disable it.
- Ctrl-C cancels the worker and retains completed results.
- `--json` emits the structured report. Text output provides a concise summary and the saved report path.
- Successful runs exit 0; unsuccessful runs exit 1. The report distinguishes failure causes.

## Code Mode

`mops.testing.run(selection, options)` accepts a native MPS project, module, model, or test node. It must be called
outside a model-access block and uses the same execution policy as the CLI.

The operation returns a structured report for test, build, discovery, and interruption outcomes. Invalid arguments and
failures that prevent obtaining a report throw. A failed test report does not automatically change the exit status of
`mops code run`; the calling program can inspect the report and continue.

The `timeout` option limits test execution like `--timeout`. The enclosing Code Mode deadline still covers the whole
script, including the run's startup, build, and discovery; whichever expires first stops the run. Expiration of the Code
Mode deadline retains its existing daemon-termination behavior and must also terminate the test worker. The saved
partial report remains accessible. A standalone `mops test` timeout terminates its worker and leaves the daemon usable.

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
`integrationTestMps2024.1.6`, `integrationTestMps2025.1.4`, `integrationTestMps2026.1.1`, and
`integrationTestMpsPrerelease` tasks resolve their MPS distributions and matching host JBRs. `integrationTest`
aggregates the matrix and participates in `check` and CI. The `integrationTestLocal` task accepts an extracted
distribution and JBR, providing a validation path for a pinned master build without introducing a separate harness.

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

The daemon resolves selection to persistent identities and descriptor paths. It also records the worker's repository and
path macros as the daemon loaded them: the project module descriptors, the folders of the project libraries from
`ProjectLibraryManager` with macros expanded, and the user path macros from `PathMacros`. The worker does not open the
project before discovery, so it never reads `.mps/libraries.xml` itself; without these folders, modules that depend on
project-library modules cannot load classes. A project library whose path keeps an undefined macro is left out, with a
report diagnostic. The Ant file passes the folders as `<modules dir>` and the macros as `<macro name path>`, so tests
that open a project through a macro-based `TestInfo.projectPath` find it.

The daemon performs the optional incremental build itself, with the same make that `mops make` uses, and saves the
project. The daemon already has the project open, so a build does not start a second normal-mode MPS environment with
its own startup, memory, and system directory. The make checks the run's cancellation between its steps, so cancelling
during the build stops the run before the worker starts. A Code Mode deadline that terminates the daemon during the
build leaves the report incomplete in the build phase.

The daemon then starts a small supervisor JVM on the daemon's classpath, without an MPS environment. The supervisor
starts the selected distribution's Ant `launchtests` task on an independent classpath. Ant constructs the test JVM
arguments and classpath; a small `LaunchTestWorker` subclass inherits test-mode environment startup and loads a
version-specific launcher adapter through the stock launcher module's classloader. The adapter filters stock module
discovery, forwards listener callbacks to the stock failure detector, and publishes atomic protocol events.

The supervisor collects those events while supervising the Ant process. When a run is unsuccessful, it adds the worker's
class-loading warnings from `test-log/idea.log` (modules MPS marked invalid for class loading) to the report's
diagnostics, so a missing dependency is not visible only as a `ClassNotFoundException`. Cancellation, deadline
expiration, and parent termination kill the whole process tree. The supervisor outlives a killed daemon, so it records
the parent termination in the report.

In test mode, MPS waits for indexes before it opens a project, and it indexes the source roots of every repository
module, including the whole distribution. So that a run does not scan and index all of it again, the test worker keeps
its IntelliJ system directory (VFS, indexes, caches) between runs, in `test-system-cache/<MPS build number>/worker`
under the daemon home, shared by all projects of that daemon home. The build number in the path keeps one build from
reading another build's indexes. Configuration and logs stay per run. MPS 2026.3 deletes the test-mode system directory
at startup unless `mps.test.keep.system.dir` is set, so the worker sets it.

The daemon takes an exclusive file lock on the directory until the run's processes have exited; killing a process tree
waits for the descendants to exit. IntelliJ does not protect a system directory shared by processes: the test-mode
application takes no directory lock. When another run, for example in another project's daemon, holds the lock, the run
uses a fresh directory under its run directory and adds a report diagnostic naming both directories.

A reused VFS keeps file records and content from earlier runs, and the headless platform does not refresh them
synchronously when it opens a project; the test-mode platform skips that refresh. So before use, the worker refreshes
from disk the project folder, the repository folders, and the module folders of the selection that are not inside
another of these folders, and MPS reloads what changed. A recursive refresh checks only files loaded into memory, and a
file loaded later keeps its stored timestamp, length, and content (IJPL-219404), so the worker first loads every file
the VFS has stored under these folders. The daemon refreshes its own persistent VFS the same way before each request.
IntelliJ's own version and corruption checks rebuild outdated or broken storage. mops adds no check of its own: deleting
the cache is the documented way out and costs one cold run.

The load includes generated output folders: generation compares new output with the content the VFS holds and skips
unchanged files, and it finds and deletes stale files through the VFS. Test-worker class loading reads `deps.cp` from
the generator caches through the VFS.

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
