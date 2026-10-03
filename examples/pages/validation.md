# Build, check, test, and recover

| Task                                               | Command          |
| -------------------------------------------------- | ---------------- |
| Make selected modules and their dependencies       | `{{example-4}}`  |
| Make all generatable project modules               | `{{example-5}}`  |
| Check a model, including typesystem/checking rules | `{{example-6}}`  |
| Check selected modules                             | `{{example-7}}`  |
| Check the project                                  | `{{example-8}}`  |
| Emit all model findings as JSON lines              | `{{example-9}}`  |
| Build and run all project tests                    | `{{example-10}}` |
| Run one test case                                  | `{{example-11}}` |
| Run a test node using compiled classes             | `{{example-12}}` |
| Set the test worker deadline                       | `{{example-13}}` |
| Diagnose unloaded project modules                  | `{{example-14}}` |
| Include loaded modules in diagnosis                | `{{example-15}}` |
| Inspect a module's load/dependency problem         | `{{example-16}}` |
| Start/reuse the daemon and check connectivity      | `{{example-17}}` |
| Inspect all known daemons                          | `{{example-18}}` |
| Stop the project's daemon                          | `{{example-19}}` |
| Stop all known daemons                             | `{{example-20}}` |

Run make, render, and testing helpers outside access blocks:

```groovy
{{make}}
```

<!-- markdownlint-disable MD013 -->

```groovy
{{run-tests}}
```

<!-- markdownlint-enable MD013 -->

To refresh existing module entries in an MPS build-language project from descriptor files:

<!-- markdownlint-disable MD013 -->

```groovy
{{reload-build}}
```

<!-- markdownlint-enable MD013 -->

Check the returned outcome of Code Mode make/testing operations: returned failure reports do not themselves make
`code run` exit nonzero. Tests save `report.json` and `worker.log` under the daemon workspace's `test-runs/<run-id>/`.
Code Mode has a 900-second hard deadline; `{{example-21}}` disables it. Expiry terminates the
daemon. Test worker timeouts leave the daemon usable.

Run Code Mode, edits, make, tests, checks, and rendering sequentially for a project. Independent CLI reads/searches and
`code help` can run concurrently. Stop existing daemons after upgrading mops. If a language is missing/unloaded, use
`diagnose module` and `make module` before retrying concept lookup. For startup delays,
`MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS=600 mops daemon ping` extends the startup wait.
