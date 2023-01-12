package ru.herobrine1st.matrix.bridge.vk.value

import ru.herobrine1st.matrix.bridge.api.value.RemoteRoomId
import ru.herobrine1st.vk.model.PeerId

data class RemoteRoomIdImpl(
    val actorId: RemoteActorIdImpl,
    val peerId: PeerId
): RemoteRoomId {
    override fun toAliasPart(): String {
        return "${actorId.toAliasPart()}_${peerId.normalizedPeerId}"
    }
}