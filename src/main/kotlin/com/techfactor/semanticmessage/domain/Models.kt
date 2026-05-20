package com.techfactor.semanticmessage.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*
import kotlin.time.Instant

@Serializable
data class MessageRequest(
    val content: String
)

@Serializable
data class Message(
    @Contextual val id: UUID,
    val timestamp: Instant,
    val content: String
)

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int = 10
)

@Serializable
data class SearchResult(
    val message: Message,
    val similarity: Double
)


//TODO move out of Models.kt
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}
