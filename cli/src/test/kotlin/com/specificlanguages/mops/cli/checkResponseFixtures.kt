package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummary

/**
 * A `check` response for tests. The counts default to those of [findings], so a test that shows every finding names
 * only the findings; a truncated response passes the counts of the full result.
 */
internal fun checkResponse(
    findings: List<ModelCheckFindingJson> = emptyList(),
    errors: Int = findings.count { it.severity == FindingSeverity.ERROR },
    warnings: Int = findings.count { it.severity == FindingSeverity.WARNING },
    infos: Int = findings.count { it.severity == FindingSeverity.INFO },
    limit: Int = 20,
    truncated: Boolean = false,
): ModelCheckResponse = ModelCheckResponse(
    summary = ModelCheckSummary(errors = errors, warnings = warnings, infos = infos, limit = limit, truncated = truncated),
    findings = findings,
)
