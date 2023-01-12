package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import net.folivo.trixnity.core.model.UserId

class UserIdAdapter : ColumnAdapter<UserId, String> {
    override fun decode(databaseValue: String) = UserId(databaseValue)

    override fun encode(value: UserId) = value.full

}