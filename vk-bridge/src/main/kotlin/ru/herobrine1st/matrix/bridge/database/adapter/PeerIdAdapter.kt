package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import ru.herobrine1st.vk.annotation.DelicateVkLibraryAPI
import ru.herobrine1st.vk.model.PeerId

object PeerIdAdapter: ColumnAdapter<PeerId, Long> {
    @OptIn(DelicateVkLibraryAPI::class)
    override fun decode(databaseValue: Long) = PeerId.fromNormalized(databaseValue)
    override fun encode(value: PeerId): Long = value.normalizedPeerId
}