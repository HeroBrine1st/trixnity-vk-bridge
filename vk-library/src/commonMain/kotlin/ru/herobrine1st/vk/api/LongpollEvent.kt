package ru.herobrine1st.vk.api

import ru.herobrine1st.vk.model.AccountId
import ru.herobrine1st.vk.model.ConversationMessageId
import ru.herobrine1st.vk.model.MessageId
import ru.herobrine1st.vk.model.PeerId
import ru.herobrine1st.vk.model.longpoll.LongpollMessageFlags
import ru.herobrine1st.vk.model.longpoll.LongpollUpdate

public sealed interface LongpollEvent {
    public val ts: Long

    public sealed interface MessageEvent: LongpollEvent {
        public val conversationMessageId: ConversationMessageId
        public val peerId: PeerId
        public val flags: Set<LongpollMessageFlags>
        public val hash: String
    }

    // TODO better naming
    public sealed interface MessageInfo: MessageEvent {
        public val text: String
        // TODO remove nullability by replacing with our id
        // do not use AccountId.NULL as this may lead to strange errors
        public val sender: AccountId? // if null, then it is outbound
        public val originalUpdate: LongpollUpdate.MessageEvent
    }

    public data class NewMessage(
        override val ts: Long,
        override val conversationMessageId: ConversationMessageId,
        override val peerId: PeerId,
        override val flags: Set<LongpollMessageFlags>,
        override val text: String,
        override val sender: AccountId?,
        override val originalUpdate: LongpollUpdate.NewMessage,
    ) : MessageInfo {
        override val hash: String get() = "creation"
    }

    public data class MessageEdit(
        override val ts: Long,
        override val conversationMessageId: ConversationMessageId,
        override val peerId: PeerId,
        override val flags: Set<LongpollMessageFlags>,
        override val text: String,
        override val sender: AccountId?,
        override val originalUpdate: LongpollUpdate.MessageEdit,
    ) : MessageInfo {
        override val hash: String
            get() = Pair(
                text,
                originalUpdate.extraFields.updateTimestamp?.toEpochMilliseconds(),
            ).hashCode().toString()
    }

    public data class MessageDelete(
        override val ts: Long,
        override val conversationMessageId: ConversationMessageId,
        override val peerId: PeerId,
        override val flags: Set<LongpollMessageFlags>,
        val originalUpdate: LongpollUpdate.FlagsUpdate,
    ) : MessageEvent {
        override val hash: String get() = "deletion"
    }

    public data class UsersTyping(
        override val ts: Long,
        val peerId: PeerId,
        val userIds: List<AccountId>, // may have own id
        val totalCount: Int = userIds.size, // userIds may be incomplete
        val originalUpdate: LongpollUpdate.UsersTyping
    ): LongpollEvent

    public data class OutboxRead(
        override val ts: Long,
        val peerId: PeerId,
        val messageId: MessageId,
        val count: Int // remaining
    ): LongpollEvent

    public data class ReactionUpdate(override val ts: Long): LongpollEvent

    public data class InvalidUpdate(override val ts: Long, val update: LongpollUpdate.InvalidUpdate) : LongpollEvent
}