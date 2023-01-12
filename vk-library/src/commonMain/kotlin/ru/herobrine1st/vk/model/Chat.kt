package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

// https://dev.vk.com/ru/reference/objects/chat
@Serializable
public data class Chat(
    @SerialName("id") val id: ChatId,
    @SerialName("type") val type: String,
    @SerialName("title") val title: String,
    @SerialName("admin_id") val adminId: AccountId,
    @SerialName("users") val users: JsonArray, // FUCKING IDK IT IS SAID THAT IT IS A LIST OF INTEGERS BUT IN ANOTHER PLACE THAT ARRAY ELEMENTS HAVE PROPERTIES
    @SerialName("push_settings") val pushSettings: JsonObject? = null,
    @SerialName("photo_50") val photo50Url: String = "",
    @SerialName("photo_100") val photo100Url: String = "",
    @SerialName("photo_200") val photo200Url: String = "",
    @SerialName("photo_base") val photoBaseUrl: String? = null,
    // Those two always set to 1, as said in reference
    @SerialName("left") val left: Int = 1,
    @SerialName("kicked") val kicked: Int = 1
)

@JvmInline
@Serializable
public value class ChatId(public val value: Long)

public fun PeerId.toChatId(): ChatId {
    require(isChat)
    return ChatId(getChatId())
}

public fun ChatId.toPeerId() = PeerId.fromChatId(value)