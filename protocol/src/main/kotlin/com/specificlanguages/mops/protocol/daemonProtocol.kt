package com.specificlanguages.mops.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * JSON codec for the daemon protocol, backed by kotlinx.serialization.
 *
 * The runtime is relocated into this jar (see the protocol build), so the generated serializers reference the shaded
 * package and never shadow MPS's own kotlinx.serialization on the daemon's flat classpath.
 *
 * The sealed request/response discriminator field is `type` and unknown fields are ignored. Unlike Gson's reflective
 * adapter, missing non-null fields are rejected with a [SerializationException] instead of being silently left null.
 */
object ProtocolJson {
    private val json: Json = Json {
        // The default class discriminator is already "type"; leaf names come from @SerialName.
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    // The set of valid request `type` discriminators, derived from the sealed serializer so it stays in sync with the
    // leaf @SerialName values.
    private val requestTypeNames: Set<String> =
        DaemonRequest.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()

    // These methods are intentionally non-inline and expose no kotlinx.serialization type in their signatures: consumers
    // compile against the thin protocol jar but run against the relocated shaded jar, so any inlined body or shaded type
    // in a public signature would fail to link. All serialization stays inside this (relocated) module.

    /**
     * Forces the request codec and the lifecycle request classes to load while the classloader is still healthy.
     *
     * A [StopRequest] is deserialized lazily, so its class is normally first loaded only when a stop actually arrives.
     * By then the MPS/IntelliJ platform may be tearing down and unable to resolve a not-yet-loaded class, which would
     * make the stop undecodable and leave the daemon unable to shut itself down. Round-tripping a stop request here
     * loads those classes up front so a later stop is always decodable.
     */
    fun warmUpRequestCodec() {
        decodeRequest(encodeRequest(StopRequest(token = "")))
    }

    fun encodeRequest(request: DaemonRequest): String = json.encodeToString(request)

    /**
     * Decodes a daemon request, reporting malformed input with domain-meaningful messages (the daemon module cannot
     * inspect JSON itself, since it only sees the relocated codec through this plain-typed API).
     */
    fun decodeRequest(text: String): DaemonRequest {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw ProtocolJsonException("request must be one newline-delimited JSON object")
        val type = root.stringField("type")
        if (type.isNullOrBlank()) {
            throw ProtocolJsonException("request type is required")
        }
        if (type !in requestTypeNames) {
            throw ProtocolJsonException("unsupported request type $type")
        }
        return json.decodeFromJsonElement(DaemonRequest.serializer(), root)
    }

    fun encodeResponse(response: DaemonResponse): String = json.encodeToString(response)

    fun decodeResponse(text: String): DaemonResponse = json.decodeFromString(text)

    fun encodeBatch(batch: EditBatch): String = json.encodeToString(batch)

    fun decodeBatch(text: String): EditBatch = json.decodeFromString(text)

    /**
     * Decodes an edit batch through a two-pass, notation-level path, returning either the batch or a structured
     * [BatchDecodeResult.Failure] that already carries a self-correcting message (what is wrong, at which operation, the
     * expected shape, and the `mops explain` page to read). Nothing here reaches the daemon; the CLI surfaces the
     * failure's detail verbatim.
     */
    fun decodeBatchOrError(text: String): BatchDecodeResult = BatchNotationDecoder.decode(json, text)

    fun encodeNode(node: MpsNodeJson): String = json.encodeToString(node)

    fun decodeNode(text: String): MpsNodeJson = json.decodeFromString(text)

    fun encodeListEntry(entry: MpsListEntryJson): String = json.encodeToString(entry)

    fun decodeListEntry(text: String): MpsListEntryJson = json.decodeFromString(text)

    fun encodeRecord(record: DaemonRecord): String = json.encodeToString(record)

    fun decodeRecord(text: String): DaemonRecord = json.decodeFromString(text)
}

/**
 * Serializes a [Path] as its invariant-separator string form.
 */
internal object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.nio.file.Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) =
        encoder.encodeString(value.invariantSeparatorsPathString)

    override fun deserialize(decoder: Decoder): Path = Path.of(decoder.decodeString())
}

/**
 * Raised when protocol JSON is structurally invalid in a way the custom serializers detect.
 */
open class ProtocolJsonException(message: String) : SerializationException(message)

/**
 * Raised when an Inline Subtree position — a child spec, Move/Copy Leaf, or inline reference — is malformed. Carried
 * separately from [ProtocolJsonException] so the batch decoder points such failures at the `inline-subtree` page rather
 * than the host operation's.
 */
class InlineSubtreeException(message: String) : ProtocolJsonException(message)

/**
 * Raised when a JSON-only custom serializer is used with a non-JSON format.
 */
internal class UnsupportedJsonOnlySerializerException(typeName: String) :
    SerializationException("$typeName can only be serialized as JSON")


object ChildPositionSerializer : KSerializer<ChildPosition> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ChildPosition", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ChildPosition) {
        when (value) {
            ChildPosition.First -> encoder.encodeString("first")
            ChildPosition.Last -> encoder.encodeString("last")
            ChildPosition.Only -> encoder.encodeString("only")
            is ChildPosition.Index -> encoder.encodeInt(value.index)
        }
    }

    override fun deserialize(decoder: Decoder): ChildPosition {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("ChildPositionSerializer only supports JSON")

        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive

        return if (primitive is JsonNull) ChildPosition.Only else if (primitive.isString) {
            when (val value = primitive.contentOrNull) {
                null, "only" -> ChildPosition.Only
                "first" -> ChildPosition.First
                "last" -> ChildPosition.Last
                else -> throw SerializationException(
                    "Expected \"first\", \"last\", or integer, got \"$value\""
                )
            }
        } else {
            primitive.intOrNull?.let { ChildPosition.Index(it) }
                ?: throw SerializationException(
                    "Expected \"first\", \"last\", or integer, got $primitive"
                )
        }
    }
}
