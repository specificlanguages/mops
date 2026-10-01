package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*

/** Each replacement is atomic, so a killed worker leaves a complete JSON snapshot. */
class TestReportStore(private val path: Path) {
    private var report = if (Files.exists(path)) ProtocolJson.decodeTestReport(Files.readString(path))
        else TestRunReport(path.toAbsolutePath().toString())

    @Synchronized fun snapshot(): TestRunReport = report
    @Synchronized fun update(change: (TestRunReport) -> TestRunReport) {
        report = change(report)
        Files.createDirectories(path.parent)
        val pending = path.resolveSibling("${path.fileName}.pending")
        Files.writeString(pending, ProtocolJson.encodeTestReport(report))
        Files.move(pending, path, ATOMIC_MOVE, REPLACE_EXISTING)
    }
    fun phase(phase: String) = update { it.copy(phase = phase) }
    fun finish(outcome: String, complete: Boolean, diagnostic: String? = null) = update {
        if (it.outcome in setOf("CANCELLED", "TIMED_OUT", "PARENT_TERMINATED")) it
        else it.copy(outcome = outcome, complete = complete,
            diagnostics = it.diagnostics + listOfNotNull(diagnostic))
    }
    fun event(event: Map<String, Any?>) = update { current ->
        if (event["discovered"] != null) current.copy(discovered = (event["discovered"] as Number).toInt())
        else {
            val result = TestResult(
                id = event.getValue("id").toString(), name = event.getValue("name").toString(),
                kind = event.getValue("kind").toString(), status = event.getValue("status").toString(),
                source = event["source"]?.toString(), className = event["className"]?.toString(),
                methodName = event["methodName"]?.toString(), detail = event["detail"]?.toString(),
            )
            current.copy(results = current.results.filterNot { it.id == result.id } + result)
        }
    }
}
