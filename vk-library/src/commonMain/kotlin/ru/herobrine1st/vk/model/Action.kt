package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Action(
    @SerialName("type") val type: Type,
    /**
     * For [Type.ChatInviteUser] and [Type.ChatKickUser] - user (value > 0) or mark that there's a email (value < 0)
     * For [Type.ChatPinMessage] and [Type.ChatUnpinMessage] - user who pinned/unpinned a message
     */
    @SerialName("member_id") val memberId: Long = 0,
    /**
     * For [Type.ChatCreate] and [Type.ChatTitleUpdate]
     */
    @SerialName("text") val chatName: String = "",
    /**
     * For [Type.ChatInviteUser] and [Type.ChatKickUser]
     */
    @SerialName("email") val email: String = "",
    @SerialName("photo") val chatAvatar: ChatAvatar? = null
) {
    @Serializable
    public enum class Type {
        @SerialName("chat_photo_update") ChatPhotoUpdate,
        @SerialName("chat_photo_remove") ChatPhotoRemove,
        @SerialName("chat_create") ChatCreate,
        @SerialName("chat_title_update") ChatTitleUpdate,
        @SerialName("chat_invite_user") ChatInviteUser, // probably user already joined
        @SerialName("chat_kick_user") ChatKickUser,
        @SerialName("chat_pin_message") ChatPinMessage,
        @SerialName("chat_unpin_message") ChatUnpinMessage,
        @SerialName("chat_invite_user_by_link") ChatUserJoinedByLink
    }

    @Serializable
    public data class ChatAvatar(
        // 50, 100, 200 are 50x50, 100x100, 200x200 pixels
        @SerialName("photo_50") val photo50Url: String,
        @SerialName("photo_100") val photo100Url: String,
        @SerialName("photo_200") val photo200Url: String,
    )

}
