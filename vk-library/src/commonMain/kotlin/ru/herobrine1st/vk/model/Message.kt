package ru.herobrine1st.vk.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.herobrine1st.vk.model.AccountId.Companion.NULL
import ru.herobrine1st.vk.serializer.InstantEpochSecondsSerializer


@Serializable
public data class Message(
    @SerialName("id") val id: MessageId,
    @Serializable(with = InstantEpochSecondsSerializer::class)
    @SerialName("date") val date: Instant,
    @SerialName("peer_id") val peerId: PeerId,
    @SerialName("from_id") val sender: AccountId,
    @SerialName("text") val text: String,
    @SerialName("random_id") val randomId: Int = 0,
    @SerialName("ref") val ref: String = "",
    @SerialName("ref_source") val refSource: String = "",
    @SerialName("attachments") val attachments: List<MessageAttachment> = emptyList(),
    @SerialName("important") val important: Boolean = false,
    @SerialName("geo") val geo: Geo? = null,
    @SerialName("payload") val payload: String = "",
    @SerialName("keyboard") val keyboard: JsonObject? = null,
    @SerialName("fwd_messages") val forwardedMessages: List<ForwardedMessage> = emptyList(),
    @SerialName("reply_message") val repliedTo: Message? = null,
    @SerialName("action") val action: Action? = null,
    @SerialName("admin_author_id") val userIdOfGroupAdmin: AccountId = NULL,
    @SerialName("conversation_message_id") val conversationMessageId: ConversationMessageId,
    @SerialName("is_cropped") val isCropped: Boolean = false,
    @Serializable(with = InstantEpochSecondsSerializer::class)
    @SerialName("pinned_at") val pinnedAt: Instant? = null,
    // for https://notify.mail.ru
    @SerialName("message_tag") val messageTag: String? = null,
    // i.e. if true, actor is mentioned
    @SerialName("is_mentioned_user") val isMentionedUser: Boolean = false,
) {
    @Serializable
    public data class ForwardedMessage(
        @Serializable(with = InstantEpochSecondsSerializer::class)
        @SerialName("date") val date: Instant,
        @SerialName("from_id") val sender: AccountId,
        @SerialName("text") val text: String,
        @SerialName("attachments") val attachments: List<MessageAttachment> = emptyList(),
        @SerialName("conversation_message_id") val conversationMessageId: ConversationMessageId,
    )
}
