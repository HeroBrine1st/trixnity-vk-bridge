package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import net.folivo.trixnity.core.model.EventId

class EventIdAdapter: ColumnAdapter<EventId, String> {
    override fun decode(databaseValue: String) = EventId(databaseValue)

    override fun encode(value: EventId) = value.full
}