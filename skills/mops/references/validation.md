# Build, check, test, and recover

| Task                                               | Command                                                  |
| -------------------------------------------------- | -------------------------------------------------------- |
| Make selected modules and their dependencies       | `mops make module sample.language sample.solution`       |
| Make all generatable project modules               | `mops make project`                                      |
| Check a model, including typesystem/checking rules | `mops check model sample.model`                          |
| Check selected modules                             | `mops check module sample.language sample.solution`      |
| Check the project                                  | `mops check project`                                     |
| Emit all model findings as JSON lines              | `mops check model sample.model --format jsonl --limit 0` |
| Build and run all project tests                    | `mops test`                                              |
| Run one test case                                  | `mops test sample.tests .tests MyCase`                   |
| Run a test node using compiled classes             | `mops test 'NODE_REF' --no-build --json`                 |
| Set the test worker deadline                       | `mops test --timeout 60`                                 |
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

```groovy
def model = project.read { mops.lookup.requireModel('sample.tests.tests@tests') }; def r = mops.testing.run(model, [build: true, timeout: 60]); [successful: r.successful, diagnostics: r.diagnostics, reportPath: r.reportPath]
```

To refresh existing module entries in an MPS build-language project from descriptor files:

```groovy
project.command { def r = mops.editing.build.reloadModulesFromDisk(mops.lookup.requireNode('BUILD_PROJECT_REF')); [succeeded: r.succeeded, messages: r.messages.collect { [kind: it.kind, text: it.text, node: it.node] }] }
```

Check the returned outcome of Code Mode make/testing operations: returned failure reports do not themselves make
`code run` exit nonzero. Tests save `report.json` and `worker.log` under the daemon workspace's `test-runs/<run-id>/`.
Code Mode has a 900-second hard deadline; `mops code run --timeout 0 task.groovy` disables it. Expiry terminates the
daemon. Test worker timeouts leave the daemon usable.

Run Code Mode, edits, make, tests, checks, and rendering sequentially for a project. Independent CLI reads/searches and
`code help` can run concurrently. Stop existing daemons after upgrading mops. If a language is missing/unloaded, use
`diagnose module` and `make module` before retrying concept lookup. For startup delays,
`MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS=600 mops daemon ping` extends the startup wait.
