package ru.herobrine1st.vk.model.longpoll

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import ru.herobrine1st.vk.annotation.DelicateVkLibraryAPI
import ru.herobrine1st.vk.model.*

@Serializable(with = LongpollUpdateSerializer::class)
public sealed interface LongpollUpdate {
    public val code: Int

    @Serializable(with = FlagsUpdateSerializer::class)
    public sealed interface FlagsUpdate : LongpollUpdate {
        public val conversationMessageId: ConversationMessageId
        public val peerId: PeerId
    }

    // FLAGS = flags
    public data class FlagsReplace(
        override val conversationMessageId: ConversationMessageId,
        val flags: Long,
        override val peerId: PeerId
    ) : FlagsUpdate {
        override val code: Int = 10001
    }

    // FLAGS |= mask
    public data class FlagsSet(
        override val conversationMessageId: ConversationMessageId,
        val mask: Long,
        override val peerId: PeerId
    ) : FlagsUpdate {
        override val code: Int = 10002
    }

    // FLAGS &= ~mask
    public data class FlagsReset(
        override val conversationMessageId: ConversationMessageId,
        val mask: Long,
        override val peerId: PeerId
    ) : FlagsUpdate {
        override val code: Int = 10003
    }

    // For polymorphic usage without casts
    // otherwise NewMessage and MessageEdit are not coupled at all
    public sealed interface MessageEvent : LongpollUpdate {
        public val conversationMessageId: ConversationMessageId
        public val flags: Long
        public val peerId: PeerId
        public val extraFields: ExtraFields
    }

    @Serializable(with = NewMessageSerializer::class)
    public data class NewMessage(
        override val conversationMessageId: ConversationMessageId,
        override val flags: Long,
        val minorId: Long, // message id but treat as opaque
        override val peerId: PeerId,
        override val extraFields: ExtraFields
    ) : MessageEvent {
        override val code: Int = 10004
    }

    @Serializable(with = MessageEditSerializer::class)
    public data class MessageEdit(
        override val conversationMessageId: ConversationMessageId,
        override val flags: Long, // "mask" for this event
        override val peerId: PeerId,
        // According to documentation, that're only fields left
//        val timestamp: Instant,
//        val newText: String,
//        val attachments: JsonObject
//        // and some kind of zero (not kidding, they put `0` to list of fields)
        // According to empirical observation, there are more fields
        override val extraFields: ExtraFields
    ) : MessageEvent {
        override val code: Int = 10005
    }

    @Serializable(with = OutboxReadSerializer::class)
    public data class OutboxRead(
        val peerId: PeerId,
        val messageId: MessageId,
        val count: Int // remaining
    ) : LongpollUpdate {
        override val code: Int get() = 10007
    }

    // update 52 membership joined [52,6,2000000004,account_id]

    /**
     * This event is replayed every 5 seconds to indicate that users are still typing
     */
    @Serializable(with = TypingSerializer::class)
    public data class UsersTyping(
        val peerId: PeerId,
        val userIds: List<AccountId>, // may have own id
        val totalCount: Int = userIds.size, // userIds may be incomplete
        val timestamp: Long = -1
    ) : LongpollUpdate {
        override val code: Int = 63
    }

    // No documentation, and those events have like three subtypes, making reverse engineering somewhat difficult
    // Their bad, they'll get additional requests when they could have none of them
    @Serializable(with = ReactionUpdateSerializer::class)
    public data class ReactionUpdate(
        override val code: Int,
        val raw: JsonElement
    ) : LongpollUpdate {
        init {
            require(code == 601 || code == 602)
        }
    }

    /**
     * Update without a known structure, i.e. unimplemented.
     */
    @Serializable(with = UnknownUpdateSerializer::class)
    public data class UnknownUpdate(
        override val code: Int,
        val raw: JsonElement
    ) : LongpollUpdate


    /**
     * A known update that can't be deserialized properly. Extracted from [UnknownUpdate] for monitoring purposes.
     */
    @Serializable(with = InvalidUpdateSerializer::class)
    public data class InvalidUpdate(
        override val code: Int,
        val raw: JsonElement
    ) : LongpollUpdate
}


internal object LongpollUpdateSerializer : KSerializer<LongpollUpdate> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LongpollUpdate")
    private val logger = KotlinLogging.logger {}

    private fun selectDeserializer(element: JsonElement): DeserializationStrategy<LongpollUpdate> {
        element as JsonArray
        return when (val code = element[0].jsonPrimitive.int) {
            10001, 10002, 10003 -> LongpollUpdate.FlagsUpdate.serializer()
            10004 -> LongpollUpdate.NewMessage.serializer()
            10005 -> LongpollUpdate.MessageEdit.serializer()
            10007 -> LongpollUpdate.OutboxRead.serializer()
            63 -> LongpollUpdate.UsersTyping.serializer()
            601, 602 -> LongpollUpdate.ReactionUpdate.serializer()
            else -> {
                logger.warn { "Event with code $code is not implemented" }
                logger.warn { "$element" }
                LongpollUpdate.UnknownUpdate.serializer()
            }
        }
    }

    override fun deserialize(decoder: Decoder): LongpollUpdate {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        val actualSerializer = selectDeserializer(element)

        return try {
            decoder.json.decodeFromJsonElement(actualSerializer, element)
        } catch (t: Throwable) {
            logger.error(t) { "Event could not be deserialized: $element" }
            decoder.json.decodeFromJsonElement(LongpollUpdate.InvalidUpdate.serializer(), element)
        }
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate) {
        throw NotImplementedError()
    }
}

internal object UnknownUpdateSerializer : KSerializer<LongpollUpdate.UnknownUpdate> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownUpdate")
    override fun deserialize(decoder: Decoder): LongpollUpdate.UnknownUpdate {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        return LongpollUpdate.UnknownUpdate(element.jsonArray[0].jsonPrimitive.int, element)
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.UnknownUpdate) {
        throw NotImplementedError()
    }
}

internal object InvalidUpdateSerializer : KSerializer<LongpollUpdate.InvalidUpdate> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InvalidUpdate")
    override fun deserialize(decoder: Decoder): LongpollUpdate.InvalidUpdate {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        return LongpollUpdate.InvalidUpdate(element.jsonArray[0].jsonPrimitive.int, element)
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.InvalidUpdate) {
        throw NotImplementedError()
    }
}

internal object FlagsUpdateSerializer : KSerializer<LongpollUpdate.FlagsUpdate> {
    private val logger = KotlinLogging.logger {}

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlagsUpdate")

    @OptIn(DelicateVkLibraryAPI::class)
    override fun deserialize(decoder: Decoder): LongpollUpdate.FlagsUpdate {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray.toMutableList()
        val code = array.removeFirst().jsonPrimitive.int
        val messageId = ConversationMessageId(array.removeFirst().jsonPrimitive.long)
        val flagsOrMask = array.removeFirst().jsonPrimitive.long
        val peerId = PeerId.fromNormalized(array.removeFirst().jsonPrimitive.long)

        return when (code) {
            10001 -> LongpollUpdate.FlagsReplace(messageId, flagsOrMask, peerId)
            10002 -> LongpollUpdate.FlagsSet(messageId, flagsOrMask, peerId)
            10003 -> LongpollUpdate.FlagsReset(messageId, flagsOrMask, peerId)
            else -> error("Code $code is not possible here")
        }.also {
            if (array.isNotEmpty()) {
                logger.warn { "Additional items found for $it: $array" }
            }
        }
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.FlagsUpdate) {
        throw NotImplementedError()
    }
}

internal object NewMessageSerializer : KSerializer<LongpollUpdate.NewMessage> {
    private val logger = KotlinLogging.logger {}

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NewMessage")


    @OptIn(DelicateVkLibraryAPI::class)
    override fun deserialize(decoder: Decoder): LongpollUpdate.NewMessage {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray.toMutableList()
        require(array.removeFirst().jsonPrimitive.int == 10004)
        return LongpollUpdate.NewMessage(
            // TODO there may be only messageId and flags (occurred once)
            conversationMessageId = ConversationMessageId(array.removeFirst().jsonPrimitive.long),
            flags = array.removeFirst().jsonPrimitive.long,
            minorId = array.removeFirst().jsonPrimitive.long,
            peerId = PeerId.fromNormalized(array.removeFirst().jsonPrimitive.long),
            extraFields = ExtraFields(
                timestamp = Instant.fromEpochSeconds(array.removeFirst().jsonPrimitive.long),
                text = array.removeFirst().jsonPrimitive.also { require(it.isString) }.content.unescapeHtml(),
                extraValues = decoder.json.decodeFromJsonElement(ExtraValues.serializer(), array.removeFirst()),
                attachments = decoder.json.decodeFromJsonElement(Attachments.serializer(), array.removeFirst()),
                randomId = array.removeFirst().jsonPrimitive.int,
                messageId = MessageId(array.removeFirst().jsonPrimitive.long),
                updateTimestamp = array.removeFirst().jsonPrimitive.long.let {
                    if (it != 0.toLong()) Instant.fromEpochSeconds(it) else null
                }
            )
        ).also {
            if (array.isNotEmpty()) {
                logger.warn { "Additional items found for $it: $array" }
            }
        }
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.NewMessage) {
        throw NotImplementedError()
    }
}

internal object MessageEditSerializer : KSerializer<LongpollUpdate.MessageEdit> {
    private val logger = KotlinLogging.logger {}

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlagsUpdate")

    @OptIn(DelicateVkLibraryAPI::class)
    override fun deserialize(decoder: Decoder): LongpollUpdate.MessageEdit {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray.toMutableList()
        require(array.removeFirst().jsonPrimitive.int == 10005)
        return LongpollUpdate.MessageEdit(
            conversationMessageId = ConversationMessageId(array.removeFirst().jsonPrimitive.long),
            flags = array.removeFirst().jsonPrimitive.long,
            peerId = PeerId.fromNormalized(array.removeFirst().jsonPrimitive.long),
            extraFields = ExtraFields(
                timestamp = Instant.fromEpochSeconds(array.removeFirst().jsonPrimitive.long),
                text = array.removeFirst().jsonPrimitive.content.unescapeHtml(),
                extraValues = decoder.json.decodeFromJsonElement(ExtraValues.serializer(), array.removeFirst()),
                attachments = decoder.json.decodeFromJsonElement(Attachments.serializer(), array.removeFirst()),
                randomId = array.removeFirst().jsonPrimitive.int,
                messageId = MessageId(array.removeFirst().jsonPrimitive.long),
                updateTimestamp = array.removeFirst().jsonPrimitive.long.let {
                    if (it != 0.toLong()) Instant.fromEpochSeconds(it) else null
                }
            )
        ).also {
            if (array.isNotEmpty()) {
                logger.warn { "Additional items found for $it: $array" }
            }
        }
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.MessageEdit) {
        throw NotImplementedError()
    }

}

internal object OutboxReadSerializer : KSerializer<LongpollUpdate.OutboxRead> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlagsUpdate")

    @OptIn(DelicateVkLibraryAPI::class)
    override fun deserialize(decoder: Decoder): LongpollUpdate.OutboxRead {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray
        require(array[0].jsonPrimitive.int == 10007)
        return LongpollUpdate.OutboxRead(
            peerId = PeerId.fromNormalized(array[1].jsonPrimitive.long),
            messageId = MessageId(array[2].jsonPrimitive.long),
            count = array[3].jsonPrimitive.int
        )
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.OutboxRead) {
        throw NotImplementedError()
    }

}

public data class ExtraFields(
    // For event 5, timestamp isn't changed after edit, it still points to message creations
    val timestamp: Instant,
    // newText for event 5
    val text: String,
    val extraValues: ExtraValues,
    val attachments: Attachments,
    val randomId: Int = 0,
    val messageId: MessageId,
    val updateTimestamp: Instant?
)

@Serializable
public data class ExtraValues(
    @SerialName("title") val title: String? = null,
    @SerialName("from") val fromUserId: AccountId? = null,
    @SerialName("mentions") val mentions: List<JsonElement> = emptyList(),
    @SerialName("marked_users") val markedUsers: List<JsonElement> = emptyList(),
    @SerialName("client_platform_info") val clientPlatformInfo: String? = null
)

internal object TypingSerializer : KSerializer<LongpollUpdate.UsersTyping> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UsersTyping")

    @OptIn(DelicateVkLibraryAPI::class)
    override fun deserialize(decoder: Decoder): LongpollUpdate.UsersTyping {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray
        require(array[0].jsonPrimitive.int == 63)
        return LongpollUpdate.UsersTyping(
            peerId = PeerId.fromChatId(array[1].jsonPrimitive.long),
            userIds = array[2].jsonArray.map { AccountId(it.jsonPrimitive.long) },
            totalCount = array[3].jsonPrimitive.int,
            timestamp = array[4].jsonPrimitive.long
        )
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.UsersTyping) {
        throw NotImplementedError()
    }

}

internal object AttachmentsSerializer : KSerializer<Attachments> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Attachments")

    override fun deserialize(decoder: Decoder): Attachments {
        decoder as JsonDecoder
        val jsonObject = decoder.decodeJsonElement().jsonObject

        val pushedAttachments = jsonObject["attachments"]?.jsonPrimitive?.content?.let {
            decoder.json.decodeFromString<List<MessageAttachment>>(it)
        } ?: emptyList()

        return Attachments(
            hasForwards = "fwd" in jsonObject,
            hasGeo = "geo" in jsonObject || "geo_provider" in jsonObject,
            attachmentTypes = buildList {
                (1..MAX_ATTACHMENTS_IN_MESSAGE).forEach { i ->
                    if ("attach${i}_type" !in jsonObject) return@buildList

                    val type = decoder.json.decodeFromJsonElement<LongpollMessageAttachmentType>(
                        // SAFETY: jsonObject key presence is checked above
                        jsonObject["attach${i}_type"]!!
                    )

                    add(
                        LongpollAttachment(
                            type = type,
                            id = jsonObject["attach${i}"]?.jsonPrimitive?.content ?: "",
                            attachment = pushedAttachments.find { it.type == type }
                        )
                    )
                }
            },
            sourceAct = jsonObject["source_act"]?.jsonPrimitive?.content,
            sourceMid = jsonObject["source_mid"]?.jsonPrimitive?.long?.let { AccountId(it) },
            hasReply = "reply" in jsonObject,
            raw = jsonObject
        )
    }

    override fun serialize(encoder: Encoder, value: Attachments) {
        throw NotImplementedError()
    }

}

@Serializable(with = AttachmentsSerializer::class)
public data class Attachments(
    val hasForwards: Boolean, // "fwd" is present and, for me, it is "0_0", i.e. no info. Reply is considered forwarded message.
    val hasGeo: Boolean, // it is explicitly said that fields are only a flag of presence
    val attachmentTypes: List<LongpollAttachment>,
    val sourceAct: String?,
    val sourceMid: AccountId?,
    @Deprecated("Valid only for code 4, use hasForwards which is valid for code 5 too")
    val hasReply: Boolean, // "reply" is present and has conversationMessageId, which requires translation
    val raw: JsonObject
)

public data class LongpollAttachment(
    val type: LongpollMessageAttachmentType,
    val id: String = "",
    val attachment: MessageAttachment?
)

internal class ReactionUpdateSerializer : KSerializer<LongpollUpdate.ReactionUpdate> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ReactionUpdate")

    override fun deserialize(decoder: Decoder): LongpollUpdate.ReactionUpdate {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray
        return LongpollUpdate.ReactionUpdate(code = array[0].jsonPrimitive.int, raw = array)
    }

    override fun serialize(encoder: Encoder, value: LongpollUpdate.ReactionUpdate) {
        throw NotImplementedError()
    }

}