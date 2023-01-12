package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import ru.herobrine1st.matrix.bridge.vk.value.RemoteActorIdImpl

object RemoteActorIdAdapter : ColumnAdapter<RemoteActorIdImpl, Int> {
    override fun decode(databaseValue: Int) = RemoteActorIdImpl(databaseValue)
    override fun encode(id: RemoteActorIdImpl): Int = id.value
}