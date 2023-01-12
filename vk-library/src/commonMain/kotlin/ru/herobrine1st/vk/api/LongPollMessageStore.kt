package ru.herobrine1st.vk.api

import ru.herobrine1st.vk.model.ConversationMessageId
import ru.herobrine1st.vk.model.PeerId

public interface LongPollMessageStore {
    public suspend fun setLongpollParameters(parameters: LongpollParameters)

    /**
     * @return Latest parameters set by [setLongpollParameters]
     */
    public suspend fun getLatestParameters(): LongpollParameters?

    /**
     * A special case of [setLongpollParameters] when key and server aren't changed
     */
    public suspend fun setLatestTs(ts: Long)

    /**
     * @return Latest flags on provided message
     */
    public suspend fun getFlags(peerId: PeerId, conversationMessageId: ConversationMessageId): Long?

    public suspend fun setFlags(peerId: PeerId, conversationMessageId: ConversationMessageId, flags: Long)

    public data class LongpollParameters(
        val server: String,
        val key: String,
        val ts: Long,
    )
}