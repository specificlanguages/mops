package com.specificlanguages.mops.cli.output

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.ProtocolJson

internal fun renderJson(response: DaemonResponse): String = ProtocolJson.encodeResponse(response)

internal fun renderJson(node: MpsNodeJson, pretty: Boolean = false): String = ProtocolJson.encodeNode(node, pretty)

internal fun renderJson(summary: MpsNodeSummaryJson, pretty: Boolean = false): String =
    ProtocolJson.encodeNodeSummary(summary, pretty)

internal fun renderJson(entry: MpsListEntryJson): String = ProtocolJson.encodeListEntry(entry)

internal fun renderJson(finding: ModelCheckFindingJson): String = ProtocolJson.encodeFinding(finding)

internal fun renderJson(summary: ModelCheckSummaryJson): String = ProtocolJson.encodeCheckSummary(summary)
