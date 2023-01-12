package ru.herobrine1st.matrix.bridge.vk.value

import ru.herobrine1st.matrix.bridge.api.value.RemoteMessageId
import ru.herobrine1st.vk.model.ConversationMessageId
import ru.herobrine1st.vk.model.PeerId

data class RemoteMessageIdImpl(
    // TODO replace with RemoteRoomIdImpl
    val actorId: RemoteActorIdImpl,
    val peerId: PeerId,
    val conversationMessageId: ConversationMessageId,
    val index: Short = 0
) : RemoteMessageId