package com.specificlanguages.mops.cli.output

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.ProtocolJson

internal fun renderJson(response: DaemonResponse): String = ProtocolJson.encodeResponse(response)

internal fun renderJson(node: MpsNodeJson): String = ProtocolJson.encodeNode(node)

internal fun renderJson(entry: MpsListEntryJson): String = ProtocolJson.encodeListEntry(entry)

internal fun renderJson(finding: ModelCheckFindingJson): String = ProtocolJson.encodeFinding(finding)
