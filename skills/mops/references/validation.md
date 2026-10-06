# Build, check, test, and recover

| Task                                               | Command                                                  |
| -------------------------------------------------- | -------------------------------------------------------- |
| Make selected modules and their dependencies       | `mops make module sample.language sample.solution`       |
| Make all generatable project modules               | `mops make project`                                      |
| Check a model, including typesystem/checking rules | `mops check model sample.model`                          |
| Check selected modules                             | `mops check module sample.language sample.solution`      |
| Check the project                                  | `mops check project`                                     |
| Emit all model findings as JSON lines              | `mops check model sample.model --format jsonl --limit 0` |
| Diagnose unloaded project modules                  | `mops diagnose project`                                  |
| Include loaded modules in diagnosis                | `mops diagnose project --all`                            |
| Inspect a module's load/dependency problem         | `mops diagnose module sample.language`                   |
| Start/reuse the daemon and check connectivity      | `mops daemon ping`                                       |
| Inspect all known daemons                          | `mops daemon status --all`                               |
| Stop the project's daemon                          | `mops daemon stop`                                       |
| Stop all known daemons                             | `mops daemon stop --all`                                 |

Run make, render, and testing helpers outside access blocks:

```groovy
project.make()
```

To refresh existing module entries in an MPS build-language project from descriptor files:

```groovy
project.command { def r = mops.editing.build.reloadModulesFromDisk(mops.lookup.requireNode('BUILD_PROJECT_REF')); [succeeded: r.succeeded, messages: r.messages.collect { [kind: it.kind, text: it.text, node: it.node] }] }
```

## Run tests

| Task                                        | Command                                       |
| ------------------------------------------- | --------------------------------------------- |
| Build and run all project tests             | `mops test`                                   |
| Build and run all tests in a module         | `mops test sample.tests --json`               |
| Build and run all tests in a model          | `mops test 'sample.tests.tests@tests' --json` |
| Run one root test case                      | `mops test sample.tests .tests MyCase`        |
| Run a root test case using compiled classes | `mops test 'NODE_REF' --no-build --json`      |
| Set the overall test deadline (seconds)     | `mops test --timeout 60`                      |

Use project, module, model, or root test-case targets. Individual test methods are unsupported. Obtain targets with
`list`, `find`, or `get node`, and copy serialized references whole. With no target, `test` selects the project.
The runner builds by default; use `--no-build` only when compiled output matches the models being tested.

Tests run in a separate MPS worker with test mode enabled, through the distribution's Ant test machinery. The daemon
remains in normal mode. MPS 2024.1, 2025.1, and 2026.1 are supported, with a pinned development build also checked.
Installed plugins are loaded, except Images on 2025.1 to work around its SVG classloading failure. Tests requiring that
plugin cannot run with this workaround. Model/root selection filters after module discovery, so an unselected broken
class in the module can still fail discovery. Explicit test execution does not establish registration in the project's
CI build; check that build's test configuration separately.

### Inspect the result

`mops test` exits zero only for a complete, successful run; otherwise it exits nonzero. Use `--json` to inspect
`outcome`, `complete`, `diagnostics`, `build`, and per-test `results` (including `status`, `detail`, and source references
when available). Read container failures too: fixture setup can fail before any individual test runs. Distinguish
assertion failures from build, discovery, startup, timeout, and worker failures. Partial results after an interrupted
worker are not evidence of a successful run. Use `reportPath` to retain the report. Inspect the adjacent `worker.log`
for preparation failures and `ant.log` for test execution failures.

### Run from Code Mode

Resolve the target inside a read block, then run tests outside model access:

```groovy
def model = project.read { mops.lookup.requireModel('sample.tests.tests@tests') }; def r = mops.testing.run(model, [build: true, timeout: 60]); [successful: r.successful, diagnostics: r.diagnostics, reportPath: r.reportPath]
```

Check the returned outcome of Code Mode make/testing operations: returned failure reports do not themselves make
`code run` exit nonzero. Tests save `report.json` and `worker.log` under the daemon workspace's `test-runs/<run-id>/`.

## Deadlines and recovery

`test --timeout SECONDS` covers the overall request, including preparation; its default is 900 and 0 disables it.
Code Mode has a 900-second hard deadline; `mops code run --timeout 0 task.groovy` disables it. Expiry terminates the
daemon. Test worker timeouts leave the daemon usable.

Run Code Mode, edits, make, tests, checks, and rendering sequentially for a project. Independent CLI reads/searches and
`code help` can run concurrently. Stop existing daemons after upgrading mops. If a language is missing/unloaded, use
`diagnose module` and `make module` before retrying concept lookup. For startup delays,
`MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS=600 mops daemon ping` extends the startup wait.
