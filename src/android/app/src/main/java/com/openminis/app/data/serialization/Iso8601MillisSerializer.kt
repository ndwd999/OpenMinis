package com.openminis.app.data.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A `createdAt`-style timestamp that WRITES an ISO-8601 UTC string but READS
 * either form:
 *   - a JSON string → parsed as ISO-8601;
 *   - a JSON number → treated as epoch milliseconds (older Android builds
 *     wrote `createdAt` as a bare Long, a shape iOS rejected outright).
 *
 * The in-memory value stays epoch-millis (Long) so callers are unchanged; only
 * the wire representation moves to a string. Attached to model fields via
 * `@Serializable(with = ...)` — see [com.openminis.app.data.model.ProviderConfig]
 * and [com.openminis.app.data.model.SubAgentDefinition].
 *
 * This lived in `com.openminis.app.backup.BackupFormat` while a backup/restore
 * feature existed. It is not backup-specific — the model classes that need it
 * are ordinary persisted entities — so it moved here when that feature was
 * removed, rather than being deleted along with it.
 */
object Iso8601MillisSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Iso8601Millis", PrimitiveKind.STRING)

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(fmt().format(value))
    }

    override fun deserialize(decoder: Decoder): Long {
        // Tolerate both a JSON string (ISO-8601) and a JSON number (legacy
        // epoch millis). Use JsonDecoder to peek at the actual element type so
        // an old numeric payload doesn't throw.
        val jd = decoder as? JsonDecoder
            ?: return runCatching { fmt().parse(decoder.decodeString())?.time }.getOrNull() ?: 0L
        val el = jd.decodeJsonElement()
        val prim = el as? JsonPrimitive ?: return 0L
        prim.longOrNull?.let { return it } // legacy numeric epoch millis
        val s = prim.content
        return runCatching { fmt().parse(s)?.time }.getOrNull() ?: 0L
    }
}

/**
 * Nullable peer of [Iso8601MillisSerializer] for optional timestamps (e.g.
 * `ModelEntry.userModifiedAt`). null round-trips as JSON null; otherwise
 * identical rules — writes ISO-8601, reads ISO strings or legacy
 * epoch-millis numbers.
 */
object Iso8601MillisNullableSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Iso8601MillisNullable", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull()
        else Iso8601MillisSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Long? {
        val jd = decoder as? JsonDecoder ?: return Iso8601MillisSerializer.deserialize(decoder)
        val el = jd.decodeJsonElement()
        if (el is JsonNull) return null
        val prim = el as? JsonPrimitive ?: return null
        prim.longOrNull?.let { return it }
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(prim.content)?.time
        }.getOrNull()
    }
}
