package ru.herobrine1st.matrix.bridge.internal

import app.cash.sqldelight.async.coroutines.awaitAsOne
import net.folivo.trixnity.core.model.EventId
import ru.herobrine1st.matrix.bridge.database.DatabaseProvider
import ru.herobrine1st.matrix.bridge.database.useDatabase
import ru.herobrine1st.matrix.bridge.vk.internal.LongPollEchoSuppresor

class LongPollEchoSuppresorImpl(private val databaseProvider: DatabaseProvider) : LongPollEchoSuppresor {
    override suspend fun getRandomId(eventId: EventId): Int = databaseProvider.useDatabase { database ->
        return@useDatabase database.longpollEchoSuppressorQueries.create(eventId).awaitAsOne()
    }

    override suspend fun check(randomId: Int): Boolean = databaseProvider.useDatabase { database ->
        return@useDatabase database.longpollEchoSuppressorQueries.check(randomId).awaitAsOne()
    }
}