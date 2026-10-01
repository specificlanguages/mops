# Tests run in a separate process

Test execution uses a separate process for correctness and robustness. The supported scope includes all test families
recognized by the selected MPS testing framework, including tests that cannot run inside an existing MPS process.
Process isolation provides an enforceable termination boundary for cancellation and timeouts without relying on tests to
cooperate, at the cost of process and MPS initialization time.

The project is saved before worker launch, and other domain operations on that project wait until the run finishes to
prevent mops edits or builds from changing its inputs during execution. The worker uses the existing checkout; process
isolation does not provide a filesystem snapshot or rollback of test side effects.

Authenticated pings and stop requests bypass domain admission. Stop acknowledges shutdown while accepted domain requests
drain; the CLI retains its process-termination fallback if the daemon does not exit within its stop deadline.
