package ru.herobrine1st.matrix.bridge.database.adapter

import app.cash.sqldelight.ColumnAdapter
import ru.herobrine1st.vk.model.ConversationMessageId
import ru.herobrine1st.vk.model.MessageId

object ConversationMessageIdAdapter: ColumnAdapter<ConversationMessageId, Long> {
    override fun decode(databaseValue: Long) = ConversationMessageId(databaseValue)
    override fun encode(value: ConversationMessageId): Long = value.value
}