package ru.herobrine1st.matrix.bridge.vk.internal

import net.folivo.trixnity.core.model.EventId

interface LongPollEchoSuppresor {

    suspend fun getRandomId(eventId: EventId): Int


    suspend fun check(randomId: Int): Boolean
}