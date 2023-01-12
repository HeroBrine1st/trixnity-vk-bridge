package ru.herobrine1st.matrix.bridge.database

import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.r2dbc.R2dbcDriver
import org.slf4j.helpers.CheckReturnValue
import ru.herobrine1st.matrix.bridge.*
import ru.herobrine1st.matrix.bridge.database.adapter.*
import kotlin.experimental.ExperimentalTypeInference


fun SqlDriver.toDatabase() = Database(
    this,
//    eventAdapter = Event.Adapter(
//        mxRoomIdAdapter = RoomIdAdapter(),
//        remoteIdAdapter = RemoteRoomIdAdapter()
//    ),
    RoomAdapter = Room.Adapter(
        mxIdAdapter = RoomIdAdapter(),
        actorIdAdapter = RemoteActorIdAdapter,
        peerIdAdapter = PeerIdAdapter,
    ),
    PuppetAdapter = Puppet.Adapter(
        mxIdAdapter = UserIdAdapter(),
        accountIdAdapter = AccountIdAdapter
    ),
    MessageAdapter = Message.Adapter(
        conversationMessageIdAdapter = ConversationMessageIdAdapter,
        mxIdAdapter = EventIdAdapter()
    ),
    // DECOUPLING: this code applies to remote part of this bridge
    LongpollStoreAdapter = LongpollStore.Adapter(AccountIdAdapter),
    LongpollMessageFlagsAdapter = LongpollMessageFlags.Adapter(AccountIdAdapter, PeerIdAdapter, ConversationMessageIdAdapter),
    LongpollEchoSuppressorAdapter = LongpollEchoSuppressor.Adapter(EventIdAdapter()),
    ActorAdapter = Actor.Adapter(
        idAdapter = RemoteActorIdAdapter,
        mxIdAdapter = UserIdAdapter(),
        accountIdAdapter = AccountIdAdapter
    )
)

interface DatabaseProvider {
    @CheckReturnValue
    suspend fun getDriver(): R2dbcDriver
}
suspend inline fun <R> DatabaseProvider.useDriver(block: (SqlDriver) -> R): R = getDriver().use(block)

suspend inline fun <R> DatabaseProvider.useDatabase(block: (database: Database) -> R): R = useDriver {
    block(it.toDatabase())
}

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
@JvmName("useDatabaseWithTransactionWithReturn")
suspend inline fun <R> DatabaseProvider.useDatabaseWithTransaction(crossinline block: suspend SuspendingTransactionWithReturn<R>.(database: Database) -> R): R =
    useDatabase {
        it.transactionWithResult {
            block(it)
        }
    }

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
suspend inline fun DatabaseProvider.useDatabaseWithTransaction(crossinline block: suspend SuspendingTransactionWithoutReturn.(database: Database) -> Unit) =
    useDatabase {
        it.transaction {
            block(it)
        }
    }