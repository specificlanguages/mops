package com.specificlanguages.mops.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

val GsonCodec: Gson = GsonBuilder()
    .registerTypeAdapter(DaemonRequest::class.java, DaemonRequestJsonAdapter)
    .registerTypeAdapter(DaemonResponse::class.java, DaemonResponseJsonAdapter)
    .registerTypeAdapter(DaemonContext::class.java, DaemonContextJsonAdapter)
    .registerTypeAdapter(NodeTarget::class.java, NodeTargetJsonAdapter)
    .registerTypeAdapter(EditOperation::class.java, EditOperationJsonAdapter)
    .registerTypeAdapter(EditTarget::class.java, EditTargetJsonAdapter)
    .registerTypeHierarchyAdapter(Path::class.java, PathJsonAdapter)
    .create()

/**
 * Serialize and deserialize [Path] as a JSON string.
 */
private object PathJsonAdapter : JsonSerializer<Path>, JsonDeserializer<Path> {

    override fun serialize(src: Path, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
        JsonPrimitive(src.invariantSeparatorsPathString)

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): Path =
        Path.of(json.asJsonPrimitive.asString)
}

private object DaemonContextJsonAdapter : JsonSerializer<DaemonContext>, JsonDeserializer<DaemonContext> {
    override fun serialize(src: DaemonContext, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val result = JsonObject()
        result.addProperty("projectPath", src.realProjectPath.toString())
        result.addProperty("mpsHome", src.realMpsHome.toString())
        result.addProperty("javaHome", src.realJavaHome.toString())
        return result
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): DaemonContext {
        val result = requireObject(json, "a JSON object expected")
        return DaemonContext(
            realProjectPath = Path.of(requireNotNull(result.stringField("projectPath"))),
            realMpsHome = Path.of(requireNotNull(result.stringField("mpsHome"))),
            realJavaHome = Path.of(requireNotNull(result.stringField("javaHome")))
        )
    }
}

private object DaemonRequestJsonAdapter : JsonSerializer<DaemonRequest>, JsonDeserializer<DaemonRequest> {
    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): DaemonRequest {
        val message = requireObject(json, "message must be one JSON object, got: $json")
        val targetType = when (val type = message.messageType("request")) {
            "ping" -> PingRequest::class.java
            "stop" -> StopRequest::class.java
            "model-resave" -> ModelResaveRequest::class.java
            "model-get-node" -> ModelGetNodeRequest::class.java
            "find-usages" -> FindUsagesRequest::class.java
            "find-instances" -> FindInstancesRequest::class.java
            "model-edit" -> ModelEditRequest::class.java
            "list" -> MpsListRequest::class.java
            else -> throw JsonParseException("unsupported request type $type")
        }
        return context.deserialize(message, targetType)
    }

    override fun serialize(src: DaemonRequest, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(src, src.javaClass)
    }
}

private object NodeTargetJsonAdapter : JsonSerializer<NodeTarget>, JsonDeserializer<NodeTarget> {
    override fun serialize(src: NodeTarget, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val result = JsonObject()
        addNodeTarget(result, src)
        return result
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): NodeTarget =
        readNodeTarget(requireObject(json, "get-node target must be one JSON object, got: $json"))
}

private fun addNodeTarget(targetObject: JsonObject, target: NodeTarget) {
    when (target) {
        is NodeTarget.InModel -> {
            targetObject.addProperty("modelTarget", target.modelTarget)
            targetObject.addProperty("nodeId", target.nodeId)
        }

        is NodeTarget.NodeReference -> targetObject.addProperty("nodeReference", target.nodeReference)
    }
}

private fun readNodeTarget(targetObject: JsonObject): NodeTarget {
    val nodeReference = targetObject.stringField("nodeReference")
    if (!nodeReference.isNullOrBlank()) {
        return NodeTarget.NodeReference(nodeReference)
    }

    val modelTarget = targetObject.stringField("modelTarget")
    val nodeId = targetObject.stringField("nodeId")
    if (!modelTarget.isNullOrBlank() && !nodeId.isNullOrBlank()) {
        return NodeTarget.InModel(modelTarget = modelTarget, nodeId = nodeId)
    }

    throw JsonParseException("get-node target requires nodeReference or modelTarget plus nodeId")
}

private object EditOperationJsonAdapter : JsonSerializer<EditOperation>, JsonDeserializer<EditOperation> {
    override fun serialize(src: EditOperation, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val result = JsonObject()
        result.addProperty("op", src.op)
        when (src) {
            is EditOperation.SetProperty -> {
                result.add("target", context.serialize(src.target, EditTarget::class.java))
                result.addProperty("name", src.name)
                src.value?.let { result.addProperty("value", it) }
            }
        }
        return result
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): EditOperation {
        val operation = requireObject(json, "edit operation must be one JSON object, got: $json")
        return when (val op = operation.stringField("op")) {
            "setProperty" -> EditOperation.SetProperty(
                target = context.deserialize(requireNotNull(operation.get("target")) { "setProperty target is required" }, EditTarget::class.java),
                name = requireNotNull(operation.stringField("name")) { "setProperty name is required" },
                value = operation.stringField("value"),
            )

            null, "" -> throw JsonParseException("edit operation op is required")
            else -> throw JsonParseException("unsupported edit operation $op")
        }
    }
}

private object EditTargetJsonAdapter : JsonSerializer<EditTarget>, JsonDeserializer<EditTarget> {
    override fun serialize(src: EditTarget, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        when (src) {
            is EditTarget.Alias -> JsonPrimitive(src.alias)
            is EditTarget.NodeReference -> JsonPrimitive(src.nodeReference)
            is EditTarget.InModel -> JsonObject().apply {
                addProperty("model", src.modelTarget)
                addProperty("nodeId", src.nodeId)
            }
        }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): EditTarget {
        if (json == null || json.isJsonNull) {
            throw JsonParseException("edit target is required")
        }

        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            val value = json.asString
            return if (value.startsWith("$")) {
                EditTarget.Alias(value)
            } else {
                EditTarget.NodeReference(value)
            }
        }

        val targetObject = requireObject(json, "edit target must be a node reference string or JSON object, got: $json")
        val nodeReference = targetObject.stringField("nodeReference")
        if (!nodeReference.isNullOrBlank()) {
            return EditTarget.NodeReference(nodeReference)
        }

        val modelTarget = targetObject.stringField("model") ?: targetObject.stringField("modelTarget")
        val nodeId = targetObject.stringField("nodeId")
        if (!modelTarget.isNullOrBlank() && !nodeId.isNullOrBlank()) {
            return EditTarget.InModel(modelTarget = modelTarget, nodeId = nodeId)
        }

        throw JsonParseException("edit target requires a node reference string or model plus nodeId")
    }
}

private object DaemonResponseJsonAdapter : JsonSerializer<DaemonResponse>, JsonDeserializer<DaemonResponse> {
    override fun serialize(src: DaemonResponse, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(src, src.javaClass)
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): DaemonResponse {
        val message = requireObject(json, "message must be one JSON object: $json")
        val targetType =
            when (val type = message.messageType("response")) {
                "error" -> DaemonErrorResponse::class.java
                "pong" -> PongResponse::class.java
                "stop" -> StoppedResponse::class.java
                "model-resave" -> ModelResaveResponse::class.java
                "model-get-node" -> ModelGetNodeResponse::class.java
                "usages" -> FindUsagesResponse::class.java
                "nodes" -> FindInstancesResponse::class.java
                "model-edit" -> ModelEditResponse::class.java
                "list" -> MpsListResponse::class.java
                "ready" -> ReadyMessage::class.java
                else -> throw JsonParseException("unsupported response type $type")
            }
        return context.deserialize(message.withDefaultStatus(), targetType)
    }
}

private fun requireObject(json: JsonElement?, message: String): JsonObject {
    if (json == null || json.isJsonNull || !json.isJsonObject) {
        throw JsonParseException(message)
    }
    return json.asJsonObject
}

private fun JsonObject.messageType(label: String): String {
    val type = stringField("type")
    if (type.isNullOrBlank()) {
        throw JsonParseException("$label type is required")
    }
    return type
}

private fun JsonObject.stringField(name: String): String? {
    val field = get(name) ?: return null
    if (field.isJsonNull) {
        return null
    }
    return field.asString
}

private fun JsonObject.withDefaultStatus(): JsonObject {
    if (has("status")) {
        return this
    }
    return deepCopy().apply {
        addProperty("status", "ok")
    }
}
