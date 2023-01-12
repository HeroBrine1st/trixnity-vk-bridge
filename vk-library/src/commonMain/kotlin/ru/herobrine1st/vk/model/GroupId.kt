package ru.herobrine1st.vk.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class GroupId(public val value: Long)

public fun GroupId.toAccountId() = AccountId(-value)
public fun AccountId.toGroupId(): GroupId {
    require(this.isGroup) { "$this is not a group id" }
    return GroupId(-value)
}