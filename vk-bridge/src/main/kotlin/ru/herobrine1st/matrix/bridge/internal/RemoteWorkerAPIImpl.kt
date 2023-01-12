package ru.herobrine1st.matrix.bridge.internal

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import ru.herobrine1st.matrix.bridge.api.RemoteWorkerAPI
import ru.herobrine1st.matrix.bridge.database.DatabaseProvider
import ru.herobrine1st.matrix.bridge.database.useDatabase
import ru.herobrine1st.matrix.bridge.vk.value.RemoteMessageIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteRoomIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteUserIdImpl

// TODO move this implementation under trixnity-bridge using repositories
class RemoteWorkerAPIImpl(private val databaseProvider: DatabaseProvider) :
    RemoteWorkerAPI<RemoteUserIdImpl, RemoteRoomIdImpl, RemoteMessageIdImpl> {
    override suspend fun getMessageEventId(id: RemoteMessageIdImpl): EventId? =
        databaseProvider.useDatabase { database ->
            database.messageQueries.getByRemoteId(id.actorId, id.peerId, id.conversationMessageId, id.index)
                .awaitAsOneOrNull()
        }

    override suspend fun getMessageEventId(id: EventId): RemoteMessageIdImpl? =
        databaseProvider.useDatabase { database ->
            database.messageQueries.getByMxId(id).awaitAsOneOrNull()
                ?.let { (actorId, peerId, conversationMessageId, index) ->
                    RemoteMessageIdImpl(actorId, peerId, conversationMessageId, index)
                }
            // TODO probably reply to edited message has a different id, didn't find this in spec
        }

    override suspend fun getMessageAuthor(id: RemoteMessageIdImpl): RemoteUserIdImpl? =
        databaseProvider.useDatabase { database ->
            database.messageQueries.getAuthorByRemoteId(id.actorId, id.peerId, id.conversationMessageId, id.index)
                .awaitAsOneOrNull()
                ?.let {
                    RemoteUserIdImpl(it)
                }
        }

    override suspend fun getPuppetId(id: RemoteUserIdImpl): UserId? = databaseProvider.useDatabase { database ->
        database.puppetQueries.getMxUser(id.accountId).awaitAsOneOrNull()
    }

    override suspend fun getPuppetId(id: UserId): RemoteUserIdImpl? = databaseProvider.useDatabase { database ->
        database.puppetQueries.getRemoteUser(id).awaitAsOneOrNull()?.let {
            RemoteUserIdImpl(it)
        }
    }

    override suspend fun isRoomBridged(id: RemoteRoomIdImpl): Boolean = databaseProvider.useDatabase { database ->
        database.roomQueries.isBridged(id.actorId, id.peerId).awaitAsOne()
    }
}