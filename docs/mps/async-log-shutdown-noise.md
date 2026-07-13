# Silencing "Exception in thread ... Check failed" stderr noise at platform shutdown

**Question this answers:** an in-process IntelliJ/MPS environment that is disposed during JVM shutdown prints stack
traces to stderr *after* the useful work finishes, e.g.

```
Exception in thread "DefaultDispatcher-worker-8 @...FileTypeManagerImpl#286" kotlinx.coroutines.CompletionHandlerException: ...
Caused by: java.lang.IllegalStateException: Check failed.
	at com.intellij.openapi.diagnostic.AsyncLog.log(asyncLog.kt:95)
	at com.intellij.openapi.diagnostic.AsyncLogKt.log(asyncLog.kt:27)
	at com.intellij.openapi.diagnostic.JulLogger.info(JulLogger.java:83)
	...
Exception in thread "Periodic tasks thread" java.lang.IllegalStateException: Check failed.
	at com.intellij.openapi.diagnostic.AsyncLog.log(asyncLog.kt:95)
	at com.intellij.util.concurrency.AppDelayQueue$TransferThread.run(AppDelayQueue.java:107)
```

What is this, and how do you stop it?

**Answer:** it is a shutdown-ordering race inside the platform's asynchronous logger, not a fault in the calling code.
Launch the JVM with **`-Dintellij.platform.log.sync=true`** and the platform logs synchronously, removing the race and
the noise. The property is read once in a static initializer, so it must be a launch-time `-D` argument, never a runtime
`System.setProperty`.

Verified against MPS **2025.1.2** (`com.jetbrains:mps:2025.1.2`); the classes ship in `lib/util-8.jar`. Read from
bytecode (`javap -c`) — these are platform Kotlin classes with no source in the MPS checkout.

## Why the exception happens

`com.intellij.openapi.diagnostic.AsyncLog` (`util-8.jar`) is the platform's async log sink. Its state and the two methods
that matter:

```kotlin
class AsyncLog {
  private val queue: Channel<LogQueueItem>       // records to write, drained by a coroutine job
  private val job: Job

  fun log(event: LogEvent) {                      // asyncLog.kt:95
    check(queue.trySend(event).isSuccess)         // <-- throws IllegalStateException("Check failed.")
  }

  fun shutdown() {
    queue.close()                                 // closes the channel first...
    runSuspend { /* await job drains */ }         // ...then waits for the drain coroutine
  }
}
```

`log` offers the record to `queue` with `trySend` and asserts success. Once `shutdown()` has **closed** the channel,
`trySend` returns a closed/failure result and the `check` throws `IllegalStateException("Check failed.")`.

Routing: `JulLogger.info/debug` → `AsyncLogKt.log(event)` →, if the async sink exists, `AsyncLog.log`. `AsyncLog.shutdown`
is invoked from `AsyncLogKt.shutdownLogProcessing()`, which the platform calls while disposing the application.

The race: application dispose calls `shutdownLogProcessing()` (closing the channel), but several platform **background
threads outlive that dispose and keep logging** — coroutine `Dispatchers.Default` workers finalizing cancelled jobs (a
cancelled `FileTypeManagerImpl` scope logs an `.info` from `FileTypeDetectionService`), and the `AppDelayQueue`
"Periodic tasks thread" logs a `.debug`. Their records hit the already-closed channel, `AsyncLog.log` throws, and because
these are late-lifecycle background threads with no catch around the log call, the exception is uncaught and the default
handler prints one stack trace per thread to stderr. It happens strictly during teardown, after all real work, and does
not affect results — it is pure cosmetic noise.

## The fix: synchronous logging

`AsyncLogKt`'s static initializer gates whether the async sink exists at all:

```kotlin
// AsyncLogKt.<clinit>
private val asyncLog: AsyncLog? =
    if (java.lang.Boolean.getBoolean("intellij.platform.log.sync")) null
    else try { AsyncLog() } catch (_: Throwable) { null }

fun log(event: LogEvent) {
    if (asyncLog != null) asyncLog.log(event)     // async path — the one that can throw at shutdown
    else logNow(event)                            // synchronous path — writes straight to java.util.logging
}
```

With `-Dintellij.platform.log.sync=true`, `asyncLog` is null and every record takes `logNow` — a direct
`java.util.logging.Logger.log` on the calling thread. No channel, nothing to close, no `check`, so a late log during
shutdown just writes and returns. The only cost is that log calls no longer hand off to a background writer; for headless
/ test JVMs the volume is small and synchronous ordering is if anything more predictable.

`java.lang.Boolean.getBoolean` reads the property at class-initialization time — the first touch of the logging
subsystem, which is very early in an environment boot. So the flag has to be set as a JVM launch argument
(`-Dintellij.platform.log.sync=true`); a runtime `System.setProperty` after boot loses the race and has no effect.

## When you see it (and when you do not)

The noise appears only when the platform runs its **normal dispose during JVM shutdown** — e.g. a shutdown hook that lets
the environment-owning thread return and dispose. A process that instead ends with `Runtime.getRuntime().halt()` skips
shutdown hooks and never disposes the application, so it never reaches `shutdownLogProcessing()` and never emits the
noise — at the cost of no orderly dispose at all.

## Gotchas

- **Launch-time only.** Read in `AsyncLogKt.<clinit>`; a runtime `System.setProperty("intellij.platform.log.sync", ...)`
  is too late.
- **Boolean-valued.** `java.lang.Boolean.getBoolean` — only the exact string `true` enables it; absent or any other value
  keeps the async sink.
- **Diagnostic, not causal.** The `IllegalStateException("Check failed.")` and the coroutine
  `CompletionHandlerException` wrapping it are symptoms of logging into a closed channel during teardown; they do not
  indicate a real failure in whatever code appears lower in the stack (e.g. `FileTypeDetectionService`).

## Re-verify triggers

- On an MPS upgrade, re-read `AsyncLog.log` and `AsyncLogKt.<clinit>` in `lib/util-8.jar`: confirm the `check(trySend(
  ).isSuccess)` shape and that `intellij.platform.log.sync` still selects the synchronous path. Both are internal
  platform details and can move between jars or be renamed.
