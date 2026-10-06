# Build, check, test, and recover

{{table:commands|Command}}

Run make, render, and testing helpers outside access blocks:

```groovy
{{make}}
```

To refresh existing module entries in an MPS build-language project from descriptor files:

```groovy
{{reload-build}}
```

## Run tests

{{table:tests|Command}}

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
{{run-tests}}
```

Check the returned outcome of Code Mode make/testing operations: returned failure reports do not themselves make
`code run` exit nonzero. Tests save `report.json` and `worker.log` under the daemon workspace's `test-runs/<run-id>/`.

## Deadlines and recovery

`test --timeout SECONDS` covers the overall request, including preparation; its default is 900 and 0 disables it.
Code Mode has a 900-second hard deadline; `{{disable-deadline}}` disables it. Expiry terminates the
daemon. Test worker timeouts leave the daemon usable.

Run Code Mode, edits, make, tests, checks, and rendering sequentially for a project. Independent CLI reads/searches and
`code help` can run concurrently. Stop existing daemons after upgrading mops. If a language is missing/unloaded, use
`diagnose module` and `make module` before retrying concept lookup. For startup delays,
`MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS=600 mops daemon ping` extends the startup wait.
