package ru.herobrine1st.vk.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class AccountId(public val value: Long) {
    public val isUser: Boolean get() = value > 0

    public val isGroup: Boolean get() = value < 0

    public companion object {
        public val NULL: AccountId = AccountId(0)
    }
}

public fun PeerId.toAccountId(): AccountId {
    return if (isUser) {
        AccountId(getUserId())
    } else if (isGroup) {
        AccountId(-getGroupId())
    } else {
        error("$this cannot be converted to AccountId")
    }
}