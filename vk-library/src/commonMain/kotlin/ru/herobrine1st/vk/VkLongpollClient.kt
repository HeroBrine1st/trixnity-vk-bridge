package ru.herobrine1st.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import ru.herobrine1st.vk.annotation.DelicateVkLibraryAPI
import ru.herobrine1st.vk.api.LongPollMessageStore
import ru.herobrine1st.vk.api.LongpollEvent
import ru.herobrine1st.vk.model.endpoint.Messages
import ru.herobrine1st.vk.model.longpoll.*
import ru.herobrine1st.vk.model.longpoll.LongpollMessageFlags.DELETED
import ru.herobrine1st.vk.model.longpoll.LongpollMessageFlags.DELETED_ALL
import ru.herobrine1st.vk.model.toAccountId


public class VkLongpollClient(
    private val apiClient: VkApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val logger = KotlinLogging.logger {}

    private suspend fun userLongpollRequest(
        timestamp: Long,
        server: String,
        key: String,
        mode: Int = MODE_ATTACHMENTS or MODE_EXTRA_EVENTS or MODE_EXTRA_DATA_FOR_ONLINE_EVENT or MODE_RETURN_RANDOM_ID,
        timeout: Int = 25,
        version: Int = LONGPOLL_VERSION
    ): LongpollResponse {

        val url = Url(
            "https://${
                server.removePrefix("https://").removePrefix("http://")
            }?act=a_check&key=${key}&ts=${timestamp}&wait=$timeout&mode=$mode&version=$version"
        )

        val response = apiClient.baseClient.request(url) {
            onUpload { bytesSentTotal, contentLength ->
                logger.trace { "Longpoll upload: $bytesSentTotal/$contentLength" }
            }
        }

        val element = response.body<JsonElement>()
        try {
            return json.decodeFromJsonElement(element)
        } catch (t: Throwable) {
            logger.error(t) { "An error occurred while deserializing longpoll response: $element" }
            throw t
        }
    }

    public fun wrappedSubscription(actor: VkActor, store: LongPollMessageStore): Flow<LongpollEvent> = flow {
        var (server, key, ts) = store.getLatestParameters() ?: apiClient.request(actor, Messages.GetLongPollServer())
            .map { (server, key, ts) -> LongPollMessageStore.LongpollParameters(server, key, ts) }
            .getOrThrow()
            .also {
                store.setLongpollParameters(it)
            }

        while (true) {
            when (val updates = userLongpollRequest(ts, server, key)) {
                is LongpollResponse.Update -> {
                    @OptIn(DelicateVkLibraryAPI::class)
                    logger.debug { "Got updates with ts=${updates.ts}" }
                    logger.trace { "Updates: $updates" }
                    // PAST INCIDENT: Exception in the middle of update bundle processing led to partial processing with
                    //   some events replicated and some not. New TS was not written to database as idempotency measure.
                    //   After repair, server-supplied TS is updated to new value for the same event bundle. As this TS is used
                    //   in transaction ID, it changed transaction ID and made a repeated event, but persisting this repeat
                    //   fails because message ID is the same as already sent but matrix event ID is new.
                    // Mitigation: use database-stored TS (request TS), which is guaranteed to change only after all messages
                    //   in bundle are processed.
                    emit(updates to ts)
                    @OptIn(DelicateVkLibraryAPI::class)
                    ts = updates.ts
                    logger.trace { "Updating latest ts to $ts" }
                    store.setLatestTs(ts)
                }

                LongpollResponse.Failed.InvalidVersion -> error("Server returned invalid version error")

                LongpollResponse.Failed.InvalidateAll, LongpollResponse.Failed.UpdateKey -> {
                    logger.debug { "Updating key-server pair due to $updates" }
                    val response = apiClient.request(actor, Messages.GetLongPollServer()).getOrThrow()
                    server = response.server
                    key = response.key
                    if (updates is LongpollResponse.Failed.InvalidateAll) {
                        logger.warn { "Server requested full invalidation, updating ts from $ts to ${response.ts}" }
                        ts = response.ts
                    }
                    store.setLongpollParameters(
                        LongPollMessageStore.LongpollParameters(
                            server = server,
                            key = key,
                            ts = ts
                        )
                    )
                }

                is LongpollResponse.Failed.UpdateTimestamp -> {
                    logger.debug { "Updating ts to ${updates.ts}" }
                    ts = updates.ts
                    store.setLatestTs(updates.ts)
                }
            }
        }
    }.transform { (bundle, requestTs) ->
        bundle.updates.forEach { update: LongpollUpdate ->
            when (update) {
                is LongpollUpdate.FlagsUpdate -> {
                    val oldFlags = store.getFlags(update.peerId, update.conversationMessageId)
                        ?: 0 // FIXME fall back gracefully (e.g. ignore as it is unseen message)
                    val newFlags = when (update) {
                        is LongpollUpdate.FlagsReplace -> update.flags
                        is LongpollUpdate.FlagsReset -> oldFlags and update.mask.inv()
                        is LongpollUpdate.FlagsSet -> oldFlags or update.mask
                    }
                    store.setFlags(update.peerId, update.conversationMessageId, newFlags)

                    val oldFlagsRich = LongpollMessageFlags.entries.filter { (oldFlags and it.flag) > 0 }.toSet()
                    val newFlagsRich = LongpollMessageFlags.entries.filter { (newFlags and it.flag) > 0 }.toSet()

                    val deletedFlags = oldFlagsRich - newFlagsRich
                    val definedFlags = newFlagsRich - oldFlagsRich

                    logger.trace { "For update $update" }
                    logger.trace { "Old flags:  0b${oldFlags.toString(2)}" }
                    logger.trace { "New flags:  0b${newFlags.toString(2)}" }
                    logger.trace { "Difference: 0b${oldFlags.xor(newFlags).toString(2)}" }
                    logger.trace { "Deleted: $deletedFlags, defined: $definedFlags" }

                    if (DELETED_ALL in definedFlags && DELETED in definedFlags) {
                        emit(
                            LongpollEvent.MessageDelete(
                                ts = requestTs,
                                conversationMessageId = update.conversationMessageId,
                                peerId = update.peerId,
                                flags = LongpollMessageFlags.parse(newFlags),
                                originalUpdate = update,
                            ),
                        )
                    }
                }

                is LongpollUpdate.MessageEdit -> {
                    val oldFlags = store.getFlags(update.peerId, update.conversationMessageId)
                        ?: 0 // FIXME fall back gracefully (e.g. ignore as it is unseen message)
                    logger.trace { "For update $update" }
                    logger.trace { "Old flags: 0b${oldFlags.toString(2)}" }
                    logger.trace { "Mask:      0b${update.flags.toString(2)}" }
                    store.setFlags(update.peerId, update.conversationMessageId, update.flags)
                    if ((update.flags xor oldFlags) and LongpollMessageFlags.HAS_REACTION.flag != 0L) {
                        logger.debug { "Ignoring update $update because reaction added or removed" }
                        return@forEach
                    }
                    val flags = LongpollMessageFlags.parse(update.flags)
                    emit(
                        LongpollEvent.MessageEdit(
                            ts = requestTs,
                            conversationMessageId = update.conversationMessageId,
                            peerId = update.peerId,
                            flags = flags,
                            text = update.extraFields.text,
                            sender = if (LongpollMessageFlags.OUTBOX in flags) null
                            else (update.extraFields.extraValues.fromUserId ?: update.peerId.toAccountId()),
                            originalUpdate = update,
                        ),
                    )
                }

                is LongpollUpdate.NewMessage -> {
                    store.setFlags(update.peerId, update.conversationMessageId, update.flags)
                    val flags = LongpollMessageFlags.parse(update.flags)
                    emit(
                        LongpollEvent.NewMessage(
                            ts = requestTs,
                            conversationMessageId = update.conversationMessageId,
                            peerId = update.peerId,
                            flags = flags,
                            sender = if (LongpollMessageFlags.OUTBOX in flags) null
                            else (update.extraFields.extraValues.fromUserId ?: update.peerId.toAccountId()),
                            text = update.extraFields.text,
                            originalUpdate = update,
                        ),
                    )
                }

                is LongpollUpdate.UsersTyping -> {
                    // TODO this event is dispatched every 5 seconds
                    // use channelFlow and run background job
                    emit(LongpollEvent.UsersTyping(requestTs, update.peerId, update.userIds, update.totalCount, update))
                }

                is LongpollUpdate.UnknownUpdate -> logger.warn { "Ignoring $update as unknown" }

                is LongpollUpdate.InvalidUpdate -> emit(LongpollEvent.InvalidUpdate(requestTs, update))

                is LongpollUpdate.OutboxRead -> emit(
                    LongpollEvent.OutboxRead(
                        requestTs,
                        update.peerId,
                        update.messageId,
                        update.count,
                    ),
                )

                is LongpollUpdate.ReactionUpdate -> emit(LongpollEvent.ReactionUpdate(requestTs))
            }
        }
    }

}