package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelEditRequest
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.FindByNameRequest
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindNodeByIdRequest
import com.specificlanguages.mops.protocol.FindNodeByIdResponse
import com.specificlanguages.mops.protocol.NodeFilter
import com.specificlanguages.mops.protocol.FindInstancesRequest
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesRequest
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.MakeModulesRequest
import com.specificlanguages.mops.protocol.MakeProjectRequest
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ModelCheckRequest
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.DiagnoseModuleRequest
import com.specificlanguages.mops.protocol.DiagnoseModulesRequest
import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.ModelGetNodeRequest
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelRenderNodeRequest
import com.specificlanguages.mops.protocol.ModelRenderNodeResponse
import com.specificlanguages.mops.protocol.MpsListRequest
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PongResponse
import com.specificlanguages.mops.protocol.StopRequest
import com.specificlanguages.mops.protocol.StoppedResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.time.Duration

/**
 * Synchronous daemon client.
 *
 * Each call opens one short-lived socket, writes exactly one request, and reads exactly one response. Higher-level
 * launcher code owns daemon discovery and startup; this class only speaks to a daemon record that is already known.
 */
class DefaultDaemonClient(
    private val port: Int,
    private val token: String,
    private val timeout: Duration = Duration.ofSeconds(5)
) : DaemonClient {

    private var lastPongResponse: PongResponse? = null

    override fun ping(): PongResponse {
        return exchange(PingRequest(token = token), PongResponse::class.java)
            .also { lastPongResponse = it }
    }

    fun stop(): StoppedResponse =
        exchange(StopRequest(token = token), StoppedResponse::class.java)

    override fun getNode(target: NodeTarget, ancestry: Boolean): ModelGetNodeResponse =
        exchange(
            ModelGetNodeRequest(token = token, target = target, ancestry = ancestry),
            ModelGetNodeResponse::class.java
        )

    override fun renderNode(target: NodeTarget, allowReflective: Boolean): ModelRenderNodeResponse =
        exchange(
            ModelRenderNodeRequest(token = token, target = target, allowReflective = allowReflective),
            ModelRenderNodeResponse::class.java
        )

    override fun checkModel(target: String, limit: Int): ModelCheckResponse =
        exchange(
            ModelCheckRequest(token = token, target = target, limit = limit),
            ModelCheckResponse::class.java
        )

    override fun findUsages(target: NodeTarget, scope: List<String>?, limit: Int): FindUsagesResponse =
        exchange(
            FindUsagesRequest(token = token, target = target, scope = scope, limit = limit),
            FindUsagesResponse::class.java
        )

    override fun findInstances(
        concept: String,
        exact: Boolean,
        scope: List<String>?,
        filters: List<NodeFilter>,
        limit: Int,
    ): FindInstancesResponse =
        exchange(
            FindInstancesRequest(
                token = token,
                concept = concept,
                exact = exact,
                scope = scope,
                filters = filters,
                limit = limit,
            ),
            FindInstancesResponse::class.java
        )

    override fun findByName(pattern: String, scope: List<String>?, limit: Int): FindByNameResponse =
        exchange(
            FindByNameRequest(token = token, pattern = pattern, scope = scope, limit = limit),
            FindByNameResponse::class.java
        )

    override fun findNodeById(nodeId: String, scope: List<String>?, limit: Int): FindNodeByIdResponse =
        exchange(
            FindNodeByIdRequest(token = token, nodeId = nodeId, scope = scope, limit = limit),
            FindNodeByIdResponse::class.java
        )

    override fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement): ModelEditResponse =
        exchange(
            ModelEditRequest(token = token, batch = batch, constraints = constraints),
            ModelEditResponse::class.java
        )

    override fun list(target: List<String>?, depth: Int, limit: Int, summary: Boolean, role: String?): MpsListResponse =
        exchange(
            MpsListRequest(token = token, target = target, depth = depth, limit = limit, summary = summary, role = role),
            MpsListResponse::class.java
        )

    override fun diagnoseModules(): ModulesDiagnosticsResponse =
        exchange(
            DiagnoseModulesRequest(token = token),
            ModulesDiagnosticsResponse::class.java
        )

    override fun diagnoseModule(module: String): ModuleDiagnosticResponse =
        exchange(
            DiagnoseModuleRequest(token = token, module = module),
            ModuleDiagnosticResponse::class.java
        )

    override fun makeModules(modules: List<String>): MakeResponse =
        exchange(
            MakeModulesRequest(token = token, modules = modules),
            MakeResponse::class.java,
            timeout = MAKE_TIMEOUT,
        )

    override fun makeProject(): MakeResponse =
        exchange(
            MakeProjectRequest(token = token),
            MakeResponse::class.java,
            timeout = MAKE_TIMEOUT,
        )

    private fun <T : DaemonResponse> exchange(
        request: DaemonRequest,
        responseType: Class<T>,
        timeout: Duration = this.timeout,
    ): T {
        val response = ProtocolJson.decodeResponse(exchangeLine(request, timeout))

        if (response is DaemonErrorResponse) {
            throw IllegalStateException(response.message)
        }

        if (!responseType.isInstance(response)) {
            throw IllegalStateException("daemon returned unexpected response type ${response::class.simpleName}")
        }

        return responseType.cast(response)
    }

    private fun exchangeLine(request: DaemonRequest, timeout: Duration): String {
        return Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = timeout.toMillis().toInt()
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    writer.println(ProtocolJson.encodeRequest(request))
                    reader.readLine() ?: throw IllegalStateException("daemon closed connection")
                }
            }
        }
    }

    companion object {
        // A make runs generation and compilation with no natural time bound, so its socket read must not time out. A
        // zero soTimeout blocks indefinitely; the daemon closing the socket still unblocks the read with EOF.
        private val MAKE_TIMEOUT: Duration = Duration.ZERO

        fun fromRecord(record: DaemonRecord) = DefaultDaemonClient(record.port, record.token)
    }
}
