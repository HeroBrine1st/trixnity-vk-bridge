package ru.herobrine1st.matrix.bridge.internal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import ru.herobrine1st.matrix.bridge.database.DatabaseProvider
import ru.herobrine1st.matrix.bridge.database.useDatabase
import ru.herobrine1st.vk.api.LongPollMessageStore
import ru.herobrine1st.vk.model.AccountId
import ru.herobrine1st.vk.model.ConversationMessageId
import ru.herobrine1st.vk.model.PeerId

class LongPollMessageStoreImpl(private val databaseProvider: DatabaseProvider, private val accountId: AccountId) :
    LongPollMessageStore {

    override suspend fun setLongpollParameters(parameters: LongPollMessageStore.LongpollParameters) =
        databaseProvider.useDatabase { database ->
            database.longpollStoreQueries.set(
                vkId = accountId,
                ts = parameters.ts,
                key = parameters.key,
                server = parameters.server
            )
        }

    override suspend fun getLatestParameters(): LongPollMessageStore.LongpollParameters? =
        databaseProvider.useDatabase { database ->
            val (ts, key, server) = database.longpollStoreQueries.get(accountId).awaitAsOneOrNull()
                ?: return@useDatabase null
            LongPollMessageStore.LongpollParameters(server = server, key = key, ts = ts)
        }

    override suspend fun setLatestTs(ts: Long) = databaseProvider.useDatabase { database ->
        database.longpollStoreQueries.setTs(ts = ts, accountId = accountId)
    }

    override suspend fun getFlags(peerId: PeerId, conversationMessageId: ConversationMessageId): Long? =
        databaseProvider.useDatabase { database ->
            database.longpollMessageFlagsQueries.get(accountId, conversationMessageId, peerId).awaitAsOneOrNull()
        }

    override suspend fun setFlags(peerId: PeerId, conversationMessageId: ConversationMessageId, flags: Long) =
        databaseProvider.useDatabase { database ->
            database.longpollMessageFlagsQueries.set(accountId, peerId, conversationMessageId, flags)
        }
}