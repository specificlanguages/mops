package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ProtocolJson

/**
 * Prints a [MakeResponse] and returns the process exit code: `1` when the make failed, `0` otherwise (including when
 * there was nothing to generate). Errors and warnings go to stderr, the summary to stdout.
 */
fun renderMakeResult(response: MakeResponse, json: Boolean): Int {
    if (json) {
        println(ProtocolJson.encodeResponse(response))
        return if (response.outcome == MakeOutcome.FAILED) 1 else 0
    }

    for (message in response.messages) {
        System.err.println("${message.kind.name.lowercase()}\t${message.text}")
    }

    val summary = when (response.outcome) {
        MakeOutcome.SUCCESS -> "made ${response.moduleCount} module(s): success"
        MakeOutcome.FAILED ->
            "made ${response.moduleCount} module(s): FAILED (${response.messages.count { it.kind == MakeMessageKind.ERROR }} error(s))"
        MakeOutcome.NOTHING_TO_GENERATE -> "nothing to generate"
    }
    println(summary)

    return if (response.outcome == MakeOutcome.FAILED) 1 else 0
}
