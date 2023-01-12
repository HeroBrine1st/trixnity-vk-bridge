package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import ru.herobrine1st.vk.model.AccountId

object AccountIdAdapter: ColumnAdapter<AccountId, Long> {
    override fun decode(databaseValue: Long) = AccountId(databaseValue)
    override fun encode(value: AccountId): Long = value.value
}