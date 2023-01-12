package ru.herobrine1st.vk.model

import kotlinx.serialization.Serializable
import ru.herobrine1st.vk.annotation.DelicateVkLibraryAPI
import kotlin.jvm.JvmInline


@JvmInline
@Suppress("MemberVisibilityCanBePrivate")
@Serializable
public value class PeerId private constructor(public val normalizedPeerId: Long) {
    public val isUser: Boolean get() = normalizedPeerId in 1..<GROUP_ID_DISCRIMINATOR

    public val isGroup: Boolean get() = normalizedPeerId < 0 || normalizedPeerId in GROUP_ID_DISCRIMINATOR..<CHAT_ID_DISCRIMINATOR

    public val isChat: Boolean get() = normalizedPeerId >= CHAT_ID_DISCRIMINATOR

    public fun getUserId(): Long {
        require(isUser)
        return normalizedPeerId
    }

    public fun getGroupId(): Long {
        require(isGroup)
        return if (normalizedPeerId < 0) -normalizedPeerId else normalizedPeerId - GROUP_ID_DISCRIMINATOR
    }

    public fun getChatId(): Long {
        require(isChat)
        return normalizedPeerId - CHAT_ID_DISCRIMINATOR
    }

    public companion object {
        @DelicateVkLibraryAPI
        public fun fromNormalized(normalizedPeerId: Long): PeerId = PeerId(normalizedPeerId)

        public fun fromUserId(userId: Long): PeerId = PeerId(userId)

        public fun fromChatId(chatId: Long): PeerId = PeerId(CHAT_ID_DISCRIMINATOR + chatId)

        public fun fromGroupId(groupId: Long): PeerId = PeerId(-groupId)
    }
}