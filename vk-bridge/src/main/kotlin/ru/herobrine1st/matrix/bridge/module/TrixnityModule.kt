package ru.herobrine1st.matrix.bridge.module

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.applicationserviceapi.server.matrixApplicationServiceApiServer
import net.folivo.trixnity.applicationserviceapi.server.matrixApplicationServiceApiServerRoutes
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import org.slf4j.LoggerFactory
import ru.herobrine1st.matrix.bridge.api.ErrorNotifier
import ru.herobrine1st.matrix.bridge.config.BridgeConfig
import ru.herobrine1st.matrix.bridge.database.*
import ru.herobrine1st.matrix.bridge.internal.LongPollEchoSuppresorImpl
import ru.herobrine1st.matrix.bridge.internal.LongPollMessageStoreImpl
import ru.herobrine1st.matrix.bridge.internal.RemoteWorkerAPIImpl
import ru.herobrine1st.matrix.bridge.utils.fromConfig
import ru.herobrine1st.matrix.bridge.vk.VkActorImpl
import ru.herobrine1st.matrix.bridge.vk.VkActorRepository
import ru.herobrine1st.matrix.bridge.vk.VkWorker
import ru.herobrine1st.matrix.bridge.vk.value.RemoteActorIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteMessageIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteRoomIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteUserIdImpl
import ru.herobrine1st.matrix.bridge.worker.AppServiceWorker
import ru.herobrine1st.matrix.compat.ServiceMembersContentSerializerMappings
import ru.herobrine1st.matrix.compat.StickerEventContentSerializerMappings
import ru.herobrine1st.vk.annotation.DelicateVkLibraryAPI
import ru.herobrine1st.vk.model.AccountId
import ru.herobrine1st.vk.model.PeerId

fun Application.trixnityModule() {
    val bridgeConfig = BridgeConfig.fromConfig(environment.config.config("bridge"))

    val mappings = DefaultEventContentSerializerMappings +
            // VkWorker vitals
            StickerEventContentSerializerMappings +
            // AppServiceWorker vitals
            ServiceMembersContentSerializerMappings

    val mxClient = MatrixClientServerApiClientImpl(
        baseUrl = Url(environment.config.property("ktor.deployment.homeserverUrl").getString()),
        eventContentSerializerMappings = mappings
    ).apply {
        accessToken.value = environment.config.property("ktor.deployment.asToken").getString()
    }

    val databaseProvider = SingletonDatabaseProvider
    val api = RemoteWorkerAPIImpl(databaseProvider = databaseProvider)

    launch {
        databaseProvider.useDatabase { database ->
            if (database.actorQueries.getAllIds().awaitAsList().isEmpty()) {
                environment.config.configList("vk.tokens").forEach {
                    with(it) {
                        database.actorQueries.create(
                            UserId(property("localUserId").getString()),
                            AccountId(property("remoteId").getString().toLong()),
                            property("token").getString()
                        )
                    }
                }
            }
        }
    }

    val actorRepository = object : VkActorRepository, ActorRepository<RemoteActorIdImpl> {
        override suspend fun getActor(id: RemoteActorIdImpl): VkActorImpl = databaseProvider.useDatabase { database ->
            return database.actorQueries.get(id).awaitAsOne().let { (id, mxId, accountId, token) ->
                VkActorImpl(id, mxId, accountId, token)
            }
        }

        override fun getActorIdsFlow(): Flow<List<RemoteActorIdImpl>> = flow {
            databaseProvider.useDatabase { database ->
                emitAll(database.actorQueries.getAllIds().asFlow().map { it.awaitAsList() })
            }
        }

        override suspend fun getLocalUserIdForActor(remoteActorId: RemoteActorIdImpl): UserId? =
            databaseProvider.useDatabase { database ->
                database.actorQueries.getLocalUserIdForActor(remoteActorId).awaitAsOne()
            }

        override suspend fun getActorIdByLocalUserId(userId: UserId): RemoteActorIdImpl? =
            databaseProvider.useDatabase { database ->
                database.actorQueries.getActorIdByLocalUserId(userId).awaitAsOne()
            }

        override suspend fun getMxUserOfActorPuppet(actorId: RemoteActorIdImpl): UserId? =
            databaseProvider.useDatabase { database ->
                database.actorQueries.findCorrespondingPuppetForActor(actorId).awaitAsOneOrNull()
            }
    }

    val ntfyClient = HttpClient {
        defaultRequest {
            url(this@trixnityModule.environment.config.property("ntfy.endpoint").getString())
            bearerAuth(this@trixnityModule.environment.config.property("ntfy.token").getString())
        }
    }

    val errorNotifier = ErrorNotifier { message, cause ->
        this@trixnityModule.launch {
            ntfyClient.post {
                header("Title", "Trixnity bridge internal error")
                header("Tags", "warning")
                if (cause != null) {
                    setBody("$message\n${cause.stackTraceToString()}")
                    header("Priority", "5")
                } else {
                    setBody(message)
                }
            }
        }
    }

    val vkWorker = VkWorker(
        apiServer = Url(environment.config.property("vk.apiServer").getString()),
        workerApi = api,
        longpollStoreFactory = {
            LongPollMessageStoreImpl(
                databaseProvider = SingletonDatabaseProvider,
                accountId = it
            )
        },
        mxClient,
        LongPollEchoSuppresorImpl(SingletonDatabaseProvider),
        actorRepository = actorRepository,
        roomWhitelist = @OptIn(DelicateVkLibraryAPI::class) environment.config.property("vk.whitelist").getList().map {
            PeerId.fromNormalized(it.toLong())
        },
        errorNotifier = errorNotifier
    )

    val transactionRepository = object : TransactionRepository {
        override suspend fun isTransactionProcessed(txnId: String) = databaseProvider.useDatabase {
            it.transactionsQueries.get(txnId).awaitAsOneOrNull() != null
        }

        override suspend fun onTransactionProcessed(txnId: String) = databaseProvider.useDatabase {
            it.transactionsQueries.create(txnId)
        }

        override suspend fun getHandledEventsInTransaction(txnId: String): Collection<EventId> {
            return emptyList()
        }

        override suspend fun onEventHandled(txnId: String, eventId: EventId) {

        }
    }

    val roomRepository = object : RoomRepository<RemoteRoomIdImpl> {
        private val logger = LoggerFactory.getLogger(RoomRepository::class.java)
        override suspend fun getRemoteRoom(id: RoomId): RemoteRoomIdImpl? = databaseProvider.useDatabase {
            it.roomQueries.getRemoteRoom(id).awaitAsOneOrNull()?.let { (actorId, peerId) ->
                RemoteRoomIdImpl(actorId, peerId)
            }
        }

        override suspend fun getMxRoom(id: RemoteRoomIdImpl): RoomId? = databaseProvider.useDatabase {
            it.roomQueries.getMxRoom(id.actorId, id.peerId).awaitAsOneOrNull()
        }

        override suspend fun create(
            mxId: RoomId,
            remoteId: RemoteRoomIdImpl,
            isDirect: Boolean
        ) = databaseProvider.useDatabase { database ->
            logger.trace("Adding room {}-{} (isDirect={})", mxId, remoteId, isDirect)
            database.roomQueries.create(mxId, remoteId.actorId, remoteId.peerId, isDirect)
        }

    }

    val puppetRepository = object : PuppetRepository<RemoteUserIdImpl> {
        override suspend fun getMxUser(id: RemoteUserIdImpl): UserId? = databaseProvider.useDatabase {
            it.puppetQueries.getMxUser(id.accountId).awaitAsOneOrNull()
        }

        override suspend fun createPuppet(
            mxId: UserId,
            remoteId: RemoteUserIdImpl
        ): Unit = databaseProvider.useDatabase {
            it.puppetQueries.create(mxId, remoteId.accountId)
        }
    }

    val eventRepository = object : MessageRepository<RemoteUserIdImpl, RemoteMessageIdImpl> {
        override suspend fun createByRemoteAuthor(
            remoteMessageId: RemoteMessageIdImpl,
            mxEventId: EventId,
            sender: RemoteUserIdImpl
        ) = databaseProvider.useDatabase { database ->
            database.messageQueries.createByRemoteAuthor(
                actorId = remoteMessageId.actorId,
                peerId = remoteMessageId.peerId,
                conversationMessageId = remoteMessageId.conversationMessageId,
                index = remoteMessageId.index,
                mxId = mxEventId,
                authorId = sender.accountId
            )
        }

        override suspend fun createByMxAuthor(
            remoteMessageId: RemoteMessageIdImpl,
            mxEventId: EventId
        ) = databaseProvider.useDatabase { database ->
            database.messageQueries.createByLocalAuthor(
                remoteMessageId.actorId,
                remoteMessageId.peerId,
                remoteMessageId.conversationMessageId,
                remoteMessageId.index,
                mxEventId
            )
        }

    }

    val worker = AppServiceWorker(
        coroutineContext[Job]!!,
        client = mxClient,
        remoteWorker = vkWorker,
        actorRepository = actorRepository,
        transactionRepository = transactionRepository,
        roomRepository = roomRepository,
        puppetRepository = puppetRepository,
        messageRepository = eventRepository,
        errorNotifier = errorNotifier,
        bridgeConfig = bridgeConfig
    )

    val migrationRooms = environment.config.configList("vk.migrate").map {
        with(it) {
            @OptIn(DelicateVkLibraryAPI::class)
            Triple(
                RoomId(property("mxid").getString()),
                PeerId.fromNormalized(property("room_id").getString().toLong()),
                property("is_direct").getString().toBooleanStrict()
            )
        }
    }

    // Intentionally block startup
    runBlocking {
        worker.createAppServiceBot()
        this@trixnityModule.launch {
            ntfyClient.post {
                setBody("Trixnity bridge started")
                header("Priority", "2")
            }
        }
        if (migrationRooms.isNotEmpty()) {
            val actorId = databaseProvider.useDatabase {
                val list = it.actorQueries.getAllIds().awaitAsList()
                if (list.size != 1) {
                    error("Migration is supported only for single actor! Currently there are ${list.size} actors")
                }
                list.single()
            }
            migrationRooms.forEach { (mxid, peerId, isDirect) ->
                worker.migrateRoom(mxid, RemoteRoomIdImpl(actorId, peerId), isDirect)
            }
        }
    }

    worker.startup()

    matrixApplicationServiceApiServer(environment.config.property("ktor.deployment.hsToken").getString()) {
        matrixApplicationServiceApiServerRoutes(
            worker,
            eventContentSerializerMappings = mappings
        )
    }
}