package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import net.folivo.trixnity.core.model.RoomId

class RoomIdAdapter : ColumnAdapter<RoomId, String> {
    override fun decode(databaseValue: String) = RoomId(databaseValue)

    override fun encode(value: RoomId) = value.full
}