package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MopsBuild
import picocli.CommandLine.IVersionProvider

class MopsDaemonVersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> = arrayOf("mops-daemon ${MopsBuild.version}")
}
