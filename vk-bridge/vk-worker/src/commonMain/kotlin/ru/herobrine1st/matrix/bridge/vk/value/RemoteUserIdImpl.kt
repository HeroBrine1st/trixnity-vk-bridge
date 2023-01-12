package ru.herobrine1st.matrix.bridge.vk.value

import ru.herobrine1st.matrix.bridge.api.value.RemoteUserId
import ru.herobrine1st.vk.model.AccountId

@JvmInline
value class RemoteUserIdImpl(
    val accountId: AccountId
) : RemoteUserId {
    override fun toUsernamePart() = accountId.value.toString()
}
