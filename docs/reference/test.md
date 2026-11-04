# mops test

Generated from `mops test --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Build and run MPS tests in a separate process.

```text
Usage: mops test [-h] [--json] [--no-build] [--timeout=SECONDS]
                 [TARGET_SEGMENT...]
Build and run MPS tests in a separate process.
Parameters:
      [TARGET_SEGMENT...]   Navigation target segments of a module, model, or
                              root test case, or its serialized reference. Omit
                              for the project.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the structured test report.
      --no-build            Use existing compiled output.
      --timeout=SECONDS     Time limit for test execution, counted after build
                              and discovery; default 900, 0 disables it.

The target selects the project (omit it), a module, a model, or a root test
case; individual test methods are unsupported. The default build includes the
owning modules and their required dependencies; --no-build requires existing
compiled classes. Ctrl-C cancels the worker. Cancellation and timeouts leave
the daemon usable.

Execution uses the distribution's Ant `launchtests` machinery in IDEA test
mode, including its test session, failure detection, and XML reports. The test
JVM's classpath is built independently of the daemon's boot classpath. The
worker's module repository holds the project modules and the folders of the
project libraries (.mps/libraries.xml) as the daemon loaded them, and the
worker gets the path macros the daemon defines, including those given with
--macro. A project library whose path uses a macro the daemon does not define
is left out, with a diagnostic.

When the worker cannot load classes of some modules, for example because a
dependency is absent from its repository, the report's diagnostics list the
modules from the worker's log. Plugins are discovered automatically. On MPS
2025.1 only, Images (com.intellij.platform.images) is disabled to avoid its SVG
parser classloader defect; tests requiring Images are unsupported on that
version. Model and test-case filtering happens after module-wide class
discovery, so broken unselected classes can also fail a run, particularly with
--no-build. A matching JBR with javac is required to compile the
version-specific adapter.

Every run saves report.json under the daemon workspace's test-runs/<run-id>/
directory and updates it as results arrive. Worker output is saved next to it
in worker.log (preparation) and ant.log (test execution); XML reports are in
xml-reports/. The report retains tests, containers, skips, assumption aborts,
failures, and source node references.

Text output prints an `<outcome>: <N> passed, <N> failed, ...` summary line and
a `Report: <path>` line on stdout, and diagnostics such as a failed discovery
or a crashed test worker on stderr, one line each. --json prints the report,
with the diagnostics in its `diagnostics` field. No discovered tests,
preparation failures, test or container failures, and incomplete runs exit 1; a
run that discovers no tests reports which selection resolved to nothing. Skips
and assumption aborts alone do not fail a run.

In Code Mode, mops.testing.run(selection, [build: true, timeout: 900]) runs the
same operation outside model-access blocks; the selection is the native
project, module, model, or test node. A failed report does not change the Code
Mode exit status. The enclosing Code Mode deadline still terminates its daemon;
the test worker detects that and saves an incomplete report. Project operations
are serialized during a run, and changes are saved before launch. Tests use the
existing checkout and may change its files.
```
