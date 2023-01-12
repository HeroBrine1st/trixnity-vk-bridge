package ru.herobrine1st.matrix.bridge.vk.value

import ru.herobrine1st.matrix.bridge.api.value.RemoteActorId

@JvmInline
value class RemoteActorIdImpl(
    val value: Int
) : RemoteActorId {
    override fun toAliasPart() = value.toString()
}