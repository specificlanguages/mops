package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.*
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.assertTrue

internal fun Path.mpsProject(name: String = "project"): Path {
    val project = resolve(name).createDirectories()
    project.resolve(".mps").createDirectory()
    return project.toRealPath()
}

internal fun Path.mpsHome(name: String = "mps", buildNumber: String = "MPS-213.7172.1079", withBundledJbr: Boolean = false): Path {
    val mpsHome = resolve(name).createDirectories()
    mpsHome.resolve("build.properties").writeText("mps.build.number=$buildNumber\n")

    if (withBundledJbr) {
        mpsHome.bundledJbrForCurrentOs()
    }
    return mpsHome.toRealPath()
}

internal fun Path.javaHome(): Path {
    val jbrHome = resolve("jbr").createDirectories()
    jbrHome.resolve("bin").createDirectory()
    return jbrHome.toRealPath()
}

internal fun Path.bundledJbrForCurrentOs(): Path {
    val result = if (System.getProperty("os.name").startsWith("Mac"))
        resolve("jbr/Contents/Home").createDirectories()
    else
        resolve("jbr").createDirectories()

    return result.also { resolve("bin").createDirectory() }.toRealPath()
}

internal fun daemonRecord(
    project: Path,
    workspace: Path,
    port: Int,
    token: String = "secret",
    pid: Long = 1234,
    mpsHome: Path,
    startupTime: String = "2026-05-12T12:00:00Z",
): DaemonRecord =
    DaemonRecord(
        port = port,
        token = token,
        pid = pid,
        daemonVersion = "0.3.0-SNAPSHOT",
        context = DaemonContext.fromLivePaths(
            projectPath = project,
            mpsHome = mpsHome,
            javaHome = Path.of(System.getProperty("java.home")),
        ),
        workspace = workspace,
        startupTime = startupTime,
    )

internal fun startPrerecordedDaemon(vararg responses: DaemonResponse): RecordingDaemon {
    val daemon = RecordingDaemon(responses.iterator())
    daemon.start()
    assertTrue(daemon.serverReady.await(5, TimeUnit.SECONDS), "fake daemon did not bind")
    return daemon
}

internal class RecordingPool : DaemonPool {
    var context: DaemonContext? = null
    var modelTarget: Path? = null

    override fun ensureDaemon(context: DaemonContext): DaemonClient {
        this.context = context

        return object : DaemonClient {
            override fun ping(): PongResponse = PongResponse(
                projectPath = context.realProjectPath.pathString,
                mpsHome = context.realMpsHome.pathString,
                workspacePath = "irrelevant",
            )

            override fun resave(modelTarget: Path): ModelResaveResponse {
                this@RecordingPool.modelTarget = modelTarget
                return ModelResaveResponse(modelTarget = modelTarget.pathString)
            }

            override fun getNode(modelTarget: String?, nodeId: String?, nodeReference: String?): ModelGetNodeResponse =
                throw UnsupportedOperationException()
        }
    }

    override fun findRecords(spec: DaemonPool.Spec): List<StoredDaemonRecord> {
        TODO("Not yet implemented")
    }

    override fun stop(record: StoredDaemonRecord): Boolean {
        TODO("Not yet implemented")
    }
}
