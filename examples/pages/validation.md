# Build, check, test, and recover

{{table:commands|Command}}

Run make, render, and testing helpers outside access blocks:

```groovy
{{make}}
```

```groovy
{{run-tests}}
```

To refresh existing module entries in an MPS build-language project from descriptor files:

```groovy
{{reload-build}}
```

Check the returned outcome of Code Mode make/testing operations: returned failure reports do not themselves make
`code run` exit nonzero. Tests save `report.json` and `worker.log` under the daemon workspace's `test-runs/<run-id>/`.
Code Mode has a 900-second hard deadline; `{{disable-deadline}}` disables it. Expiry terminates the
daemon. Test worker timeouts leave the daemon usable.

Run Code Mode, edits, make, tests, checks, and rendering sequentially for a project. Independent CLI reads/searches and
`code help` can run concurrently. Stop existing daemons after upgrading mops. If a language is missing/unloaded, use
`diagnose module` and `make module` before retrying concept lookup. For startup delays,
`MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS=600 mops daemon ping` extends the startup wait.
