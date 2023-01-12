package ru.herobrine1st.vk.model.longpoll


/**
 * If flag is present, doc applies to message
 */
public enum class LongpollMessageFlags(public val flag: Long) {
    /**
     * Message is not read (by whom?)
     */
    @Deprecated(message = "Obsolete flag")
    UNREAD(1),

    /**
     * Message is outgoing
     */
    OUTBOX(2),

    /**
     * Message has at least one reply
     */
    REPLIED(4),

    /**
     * Message is marked as important
     */
    IMPORTANT(8),

    /**
     * Message is sent via chat
     */
    @Deprecated(message = "Obsolete flag")
    CHAT(16),

    /**
     * Message is sent by a friend. Does not apply to messages from group chats.
     */
    FRIENDS(32),

    /**
     * Message is marked as spam
     */
    @Deprecated(message = "Ambigious documentation")
    SPAM(64),

    /**
     * Message is deleted for myself
     */
    DELETED(128),

    /**
     * Message is tested for being a spam
     */
    @Deprecated(message = "Obsolete flag")
    FIXED(256),

    /**
     * Message contains mediacontent
     */
    @Deprecated(message = "Obsolete flag")
    MEDIA(512),

    /**
     * Message is no longer marked as spam
     */
    @Deprecated(message = "Ambigious documentation")
    CANCEL_SPAM(32768),

    /**
     * Message is deleted for all
     */
    DELETED_ALL(131072),

    HAS_REACTION(16777216);

    public companion object {
        public fun parse(flags: Long): Set<LongpollMessageFlags> {
            return LongpollMessageFlags.entries.filter { flags and it.flag != 0L }.toSet()
        }
    }
}