# Daemon request concurrency

The daemon accepts connections concurrently. Authenticated pings return daemon metadata without entering project
admission, so a long build, test run, or Code Mode program does not block discovery pings.

Domain requests enter a FIFO queue outside MPS model actions. With no active request, the first queued caller refreshes
external changes exclusively. If it is a parallel reader, all consecutive readers queued when that refresh finishes
share the refreshed state and run concurrently. The first exclusive request ends the batch. Requests arriving after
admission wait for another refresh after the active batch finishes. There is no batching delay.

```text
refresh → [list, get-node, find] → refresh → edit → refresh → [list, find]
                         ping → pong (independent of project admission)
```

Parallel readers include list, get-node, find, module diagnostics, and Code Mode reference requests. Their model reads
still use MPS read actions. Edits, creation, builds, tests, and arbitrary Code Mode execution retain exclusive
admission. Model checks also remain exclusive because they invoke shared checker and typesystem services. Editor
rendering remains exclusive and coordinates its own EDT/model access. Neither admission nor an entire extra operation
holds an MPS model lock; extra operations establish their own access boundaries.

Read-only requests do not save the project. Successful exclusive requests that may modify it retain their existing save
behavior. Refresh failure fails every reader in the affected batch; request and refresh failures release admission for
the next queued request.

Stop acknowledges shutdown, closes the listening socket, and drains accepted connections before the MPS environment is
disposed. A connection that does not complete its request line times out after ten seconds. The CLI's existing stop
process deadline can force termination of a daemon that is still draining. The idle timeout counts from the last
completed connection and cannot shut down the daemon while an accepted connection is active. A zero idle timeout
disables idle shutdown.

MPS uses a shared read lock and synchronizes lazy model loading between readers; see the verified
[model-access implementation](https://github.com/JetBrains/MPS/blob/2026.1.1/core/smodel/source/jetbrains/mps/smodel/ModelAccess.java)
and
[lazy-loading support](https://github.com/JetBrains/MPS/blob/2026.1.1/core/kernel/source/jetbrains/mps/smodel/loading/PartialModelDataSupport.java).
`ConcurrentReadsSemanticsTest` verifies overlapping MPS read actions and compares concurrent lookup, search, and
diagnostic results with serial results. `ProjectRequestGateTest` covers refresh exclusion, batching, FIFO fairness, and
failure release; `ProjectDaemonSocketTest` covers responsive authenticated pings, stop draining, and idle shutdown.
