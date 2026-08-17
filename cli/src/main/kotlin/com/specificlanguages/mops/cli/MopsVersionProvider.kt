package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MopsBuild
import picocli.CommandLine.IVersionProvider

class MopsVersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> = arrayOf("mops ${MopsBuild.version}")
}
