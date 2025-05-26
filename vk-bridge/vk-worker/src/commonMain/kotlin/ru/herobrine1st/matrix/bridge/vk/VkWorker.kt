package ru.herobrine1st.matrix.bridge.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.room.*
import ru.herobrine1st.matrix.bridge.api.*
import ru.herobrine1st.matrix.bridge.api.value.RemoteEventId
import ru.herobrine1st.matrix.bridge.exception.UnhandledEventException
import ru.herobrine1st.matrix.bridge.vk.internal.LongPollEchoSuppresor
import ru.herobrine1st.matrix.bridge.vk.utils.toMimeType
import ru.herobrine1st.matrix.bridge.vk.value.RemoteActorIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteMessageIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteRoomIdImpl
import ru.herobrine1st.matrix.bridge.vk.value.RemoteUserIdImpl
import ru.herobrine1st.matrix.compat.content.StickerEventContent
import ru.herobrine1st.vk.*
import ru.herobrine1st.vk.api.LongPollMessageStore
import ru.herobrine1st.vk.api.LongpollEvent
import ru.herobrine1st.vk.model.*
import ru.herobrine1st.vk.model.endpoint.Messages
import ru.herobrine1st.vk.model.longpoll.LongpollMessageFlags
import kotlin.time.Duration.Companion.seconds

data class VkActorImpl(
    val id: RemoteActorIdImpl,
    val mxId: UserId,
    val accountId: AccountId,
    override val token: String
) : VkActor

interface VkActorRepository {
    suspend fun getActor(id: RemoteActorIdImpl): VkActorImpl
    fun getActorIdsFlow(): Flow<List<RemoteActorIdImpl>>
}

class VkWorker(
    apiServer: Url,
    private val workerApi: RemoteWorkerAPI<RemoteUserIdImpl, RemoteRoomIdImpl, RemoteMessageIdImpl>,
    private val longpollStoreFactory: (AccountId) -> LongPollMessageStore,
    private val mxClient: MatrixClientServerApiClient,
    private val echoSuppresor: LongPollEchoSuppresor,
    private val actorRepository: VkActorRepository,
    private val roomWhitelist: List<PeerId>,
    private val errorNotifier: ErrorNotifier
) : RemoteWorker<RemoteActorIdImpl, RemoteUserIdImpl, RemoteRoomIdImpl, RemoteMessageIdImpl> {
    private val vkApiClient = VkApiClient(apiServer = apiServer) {
        HttpClient(CIO) {
            it()

            install(Logging) {
                val loggerImpl = object : Logger {
                    val logger = KotlinLogging.logger {}
                    override fun log(message: String) {
                        if (level == LogLevel.ALL)
                            this.logger.trace { message }
                        else
                            this.logger.debug { message }
                    }
                }

                logger = loggerImpl
                level = if (loggerImpl.logger.isTraceEnabled())
                    LogLevel.ALL
                else LogLevel.INFO
            }
        }

    }
    private val vkApi = VkApi(vkApiClient)
    private val vkLongpollClient = VkLongpollClient(vkApiClient)
    private val logger = KotlinLogging.logger {}

    override suspend fun EventHandlerScope<RemoteMessageIdImpl>.handleEvent(
        actorId: RemoteActorIdImpl,
        roomId: RemoteRoomIdImpl,
        event: ClientEvent.RoomEvent<*>
    ) {
        val actor = actorRepository.getActor(actorId)
        val peerId = roomId.also {
            it.ensureApplicable(actor.id)
        }.peerId
        when (event) {
            is ClientEvent.RoomEvent.MessageEvent<*> -> {
                when (val contentRaw = event.content) {
                    is RoomMessageEventContent -> {
                        if (contentRaw.relatesTo?.relationType == RelationType.Thread) {
                            logger.info { "$event is not processed because it is in a thread" }
                            return
                        }

                        val relation = contentRaw.relatesTo
                        val forward = when (relation) {
                            is RelatesTo.Reply -> workerApi.getMessageEventId(relation.replyTo.eventId)
                                ?.also { it.ensureApplicable(actorId, peerId) }
                                ?.let {
                                    Messages.Send.Forward(
                                        peerId = peerId,
                                        conversationMessageIds = listOf(it.conversationMessageId),
                                        isReply = true
                                    )
                                }

                            else -> null
                        }
                        val editedMessage = when (relation) {
                            // for now, it ignores that author of replacement and original event can be different
                            // (currently it is the same because one user per actor)
                            is RelatesTo.Replace -> workerApi.getMessageEventId(relation.eventId)
                                ?.also { it.ensureApplicable(actorId, peerId) }

                            else -> null
                        }
                        val content =
                            if (editedMessage != null) (relation as RelatesTo.Replace).newContent else contentRaw
                        if (content !is RoomMessageEventContent) {
                            throw UnhandledEventException("Got something other than RoomMessageEventContent from replacement event")
                        }
                        when (content) {
                            is RoomMessageEventContent.TextBased.Text -> {
                                // TODO add reply fallback back if relation is RelatesTo.Reply but forward is null
                                val text = content.bodyWithoutFallback
                                    .also {
                                        if (it.length > 4095) {
                                            throw UnhandledEventException("The message is too big (${it.length}/4095 symbols)")
                                        }
                                    }
                                if (editedMessage != null) {
                                    vkApi.messages.edit(
                                        actor,
                                        peerId,
                                        editedMessage.conversationMessageId,
                                        body = text,
                                        keepForwardMessages = true
                                    ).getOrThrow()
                                } else {
                                    val randomId = echoSuppresor.getRandomId(event.id)
                                    val response =
                                        vkApi.messages.send(
                                            actor,
                                            peerId,
                                            body = text,
                                            forward = forward,
                                            randomId = randomId
                                        ).getOrThrow().single()
                                    linkMessageId(
                                        RemoteMessageIdImpl(
                                            actor.id,
                                            response.peerId,
                                            response.conversationMessageId
                                        )
                                    )
                                }
                            }

                            is RoomMessageEventContent.FileBased -> {
                                // TODO implement captions
                                val url = content.url
                                val caption = if (content.fileName != null && content.fileName != content.body)
                                    content.bodyWithoutFallback
                                        .also {
                                            if (it.length > 4095) {
                                                throw UnhandledEventException("The message is too big (${it.length}/4095 symbols)")
                                            }
                                        }
                                else null
                                if (url == null) {
                                    logger.info { "$event is not processed because it does not have image url" }
                                    return
                                }

                                val attachmentId = when (content) {
                                    is RoomMessageEventContent.FileBased.Image -> {
                                        mxClient.media.download(url) {
                                            logger.trace { "Uploading image $it to vk" }
                                            vkApi.media.uploadMessagePhoto(
                                                actor, peerId, VkApi.MediaWrappers.UploadMedia(
                                                    it.content, it.contentLength,
                                                    // SAFETY: As per spec, server MUST respond with both of these headers
                                                    // https://spec.matrix.org/v1.12/client-server-api/#get_matrixclientv1mediadownloadservernamemediaid
                                                    it.contentType!!, it.contentDisposition!!
                                                )
                                            ).getOrThrow()
                                        }.onFailure {
                                            throw UnhandledEventException(message = "Couldn't upload image", it)
                                        }.getOrThrow()
                                    }

                                    is RoomMessageEventContent.FileBased.File -> {
                                        mxClient.media.download(url) {
                                            logger.trace { "Uploading file $it to vk" }
                                            vkApi.media.uploadMessageFile(
                                                actor, peerId, VkApi.MediaWrappers.UploadMedia(
                                                    it.content, it.contentLength,
                                                    // SAFETY: As per spec, server MUST respond with both of these headers
                                                    // https://spec.matrix.org/v1.12/client-server-api/#get_matrixclientv1mediadownloadservernamemediaid
                                                    it.contentType!!, it.contentDisposition!!
                                                )
                                            ).getOrThrow()
                                        }.onFailure {
                                            throw UnhandledEventException(message = "Couldn't upload file", it)
                                        }.getOrThrow()
                                    }

                                    is RoomMessageEventContent.FileBased.Audio,
                                    is RoomMessageEventContent.FileBased.Video -> {
                                        throw UnhandledEventException(message = "This event type is not implemented")
                                    }
                                }

                                if (editedMessage != null) {
                                    vkApi.messages.edit(
                                        actor,
                                        peerId,
                                        editedMessage.conversationMessageId,
                                        body = caption,
                                        attachments = listOf(attachmentId),
                                        keepForwardMessages = true
                                    ).getOrThrow()
                                } else {
                                    val randomId = echoSuppresor.getRandomId(event.id)
                                    val response =
                                        vkApi.messages.send(
                                            actor,
                                            peerId,
                                            body = caption,
                                            forward = forward,
                                            randomId = randomId,
                                            attachments = listOf(attachmentId)
                                        ).getOrThrow().single()
                                    linkMessageId(
                                        RemoteMessageIdImpl(
                                            actor.id,
                                            response.peerId,
                                            response.conversationMessageId
                                        )
                                    )
                                }
                            }

                            is RoomMessageEventContent.TextBased.Emote -> {
                                throw UnhandledEventException(message = "This event type is not implemented")
                            }

                            is RoomMessageEventContent.TextBased.Notice,
                            is RoomMessageEventContent.VerificationRequest,
                            is RoomMessageEventContent.Unknown -> {
                                logger.trace { "$event is ignored" }
                            }

                            else -> logger.warn { "$event is not processed due to its content not being supported" }
                        }
                    }

                    is RedactionEventContent -> {
                        val remoteMessageId = workerApi.getMessageEventId(contentRaw.redacts) ?: run {
                            logger.warn { "Can't replicate $event due to lack of data" }
                            return
                        }
                        remoteMessageId.ensureApplicable(actorId, peerId)
                        if (workerApi.getMessageAuthor(remoteMessageId) != null) {
                            logger.warn { "Can't replicate $event due to message author being a puppet" }
                            return
                        }
                        vkApi.messages.delete(actor, peerId, listOf(remoteMessageId.conversationMessageId))
                            .recover {
                                if (it !is VkApiException) throw it
                                if (it.code != 15) throw it
                                logger.warn { "Got vk api error trying to delete message, assuming idempotency hit ({${it.code}: ${it.message})" }
                            }
                            .getOrThrow()
                    }

                    else -> {
                        logger.info { "$event is not processed due to not being room message" }
                    }
                }
            }

            is ClientEvent.RoomEvent.StateEvent<*> -> {
                // TODO
            }
        }

        // Handling complex event chains
        // 1. Received events should be almost raw matrix events. The only reason of "Worker events" is decoupling
        //    a worker from bridge state, so it does not manage it. On the other hand, HS is doing state management
        //    with its own events, so we can only provide additional metadate and leave it as-is.
        //    Because we don't want worker to handle state, we can expose required data with API and generally
        //    delegate state management to HS.
        // 2. Friendship is "first invite, then create room", but in matrix it is "first create room, then invite"
        //    We can ignore that. First we create room locally, then send invite to local replica of remote account
        //    then bridge sends friendship request on remote side, and when it is accepted, manages to create remote
        //    room if needed, and also accepts invite locally.
        //    It should check if room is already created and invite is sent locally to avoid unnecessary room creation
    }

    override fun getActorsFlow() = actorRepository.getActorIdsFlow()

    override fun getEvents(actorId: RemoteActorIdImpl): Flow<WorkerEvent<RemoteUserIdImpl, RemoteRoomIdImpl, RemoteMessageIdImpl>> =
        flow {
//        launch {
//            // We can't determine whether we are connected, so this could be good approximation
//            // i.e. if there's no errors in 1 second, then probably it's good
//            delay(1000)
//            send(WorkerEvent.Connected)
//        }
            val actor = actorRepository.getActor(actorId)
            val accountId = actor.accountId

            vkLongpollClient.wrappedSubscription(actor = actor, longpollStoreFactory(accountId))
                .collect { event ->
                    when (event) {
                        is LongpollEvent.NewMessage -> {
                            if (event.peerId !in roomWhitelist && roomWhitelist.isNotEmpty()) return@collect

                            // Handle local echo
                            if (LongpollMessageFlags.OUTBOX in event.flags && echoSuppresor.check(event.originalUpdate.extraFields.randomId))
                                return@collect

                            val sender = RemoteUserIdImpl(event.sender ?: accountId)
                            val attachments = event.originalUpdate.extraFields.attachments

                            //region Handle sticker
                            if (attachments.attachmentTypes.singleOrNull()?.type==AttachmentType.STICKER) {
                                val sticker =
                                    attachments.attachmentTypes.single().attachment?.let { it as? MessageAttachment.Sticker }
                                        ?: run<_, MessageAttachment.Sticker?> {
                                            logger.warn { "Sticker object expected, got ${attachments.attachmentTypes.single().attachment}" }
                                            logger.info { "Fetching message ${event.conversationMessageId} in ${event.peerId}" }
                                            val message = vkApi.messages.getByConversationMessageId(
                                                actor = actor,
                                                peerId = event.peerId,
                                                conversationMessageIds = listOf(event.conversationMessageId)
                                            ).getOrThrow().items[0]
                                            message.attachments.filterIsInstance<MessageAttachment.Sticker>()
                                                .firstOrNull()
                                        }

                                val content: MessageEventContent
                                if (sticker == null) {
                                    logger.error { "Couldn't fetch sticker from message ${event.conversationMessageId} in ${event.peerId} due to sticker being null" }
                                    content = RoomMessageEventContent.TextBased.Notice(
                                        body = "Couldn't fetch sticker ${attachments.attachmentTypes.single().id}"
                                    )
                                } else {
                                    val image = sticker.images.maxBy {
                                        it.width
                                    }
                                    val externalUrl = image.url

                                    content = runCatching {
                                        vkApiClient.baseClient.get(externalUrl) {
                                            expectSuccess = true
                                        }
                                    }.mapCatching { stickerResponse ->
                                        mxClient.media.upload(
                                            Media(
                                                content = stickerResponse.bodyAsChannel(),
                                                contentLength = stickerResponse.contentLength(),
                                                contentType = stickerResponse.contentType(),
                                                contentDisposition = null
                                            )
                                        ).getOrThrow() to stickerResponse
                                    }.map { (it, stickerResponse) ->
                                        val thumbnail = ThumbnailInfo(
                                            height = 256,
                                            width = (256 * image.width.toFloat() / image.height).toInt(),
                                            mimeType = stickerResponse.contentType()?.toMimeType(),
                                            size = stickerResponse.contentLength()
                                        )
                                        // TODO apparently vk stickers can have a reply
                                        // spec does not prohibit nor allow it explicitly
                                        StickerEventContent(
                                            body = "sticker",
                                            info = ImageInfo(
                                                height = thumbnail.height,
                                                width = thumbnail.width,
                                                mimeType = thumbnail.mimeType,
                                                size = thumbnail.size,
                                                thumbnailUrl = it.contentUri,
                                                thumbnailInfo = thumbnail
                                            ),
                                            url = it.contentUri,
                                            externalUrl = externalUrl
                                        )
                                    }.recover {
                                        logger.error(
                                            it
                                        ) { "Couldn't fetch sticker from message ${event.conversationMessageId} in ${event.peerId} due to exception" }
                                        RoomMessageEventContent.TextBased.Notice(
                                            body = "Couldn't fetch sticker ${attachments.attachmentTypes.single().id} ($it)"
                                        )
                                    }.getOrThrow()
                                }

                                emit(
                                    WorkerEvent.RemoteEvent(
                                        RoomEvent.MessageEvent(
                                            roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                                            eventId = buildEventId(actor.id, event),
                                            sender = sender,
                                            content = content,
                                            messageId = RemoteMessageIdImpl(
                                                actor.id,
                                                event.peerId,
                                                event.conversationMessageId
                                            )
                                        )
                                    )
                                )

                                return@collect
                            }
                            //endregion

                            renderMessage(event, actor).forEach { renderedEvent ->
                                emit(WorkerEvent.RemoteEvent(renderedEvent))
                            }
                        }

                        is LongpollEvent.MessageEdit -> {
                            if (event.peerId !in roomWhitelist && roomWhitelist.isNotEmpty()) return@collect

                            renderMessage(event, actor).forEach { renderedEvent ->
                                emit(WorkerEvent.RemoteEvent(renderedEvent))
                            }
                        }

                        is LongpollEvent.MessageDelete -> {
                            if (event.peerId !in roomWhitelist && roomWhitelist.isNotEmpty()) return@collect

                            // Waiting for MSC3382 or MSC2881 to properly delete message with all attachments
                            // (don't want to save related messages locally - this worker tries to be as stateless as possible)
                            val text = "<deleted message>"

                            val id = RemoteMessageIdImpl(actor.id, event.peerId, event.conversationMessageId)
                            val author = workerApi.getMessageAuthor(id)

                            if (author == null) {
                                // it is possible to check if it is an echo
                                logger.debug { "Couldn't handle $event due to lack of author data, is it echo?" }
                                return@collect
                            }
                            val originalMessageId =
                                workerApi.getMessageEventId(id)

                            emit(
                                WorkerEvent.RemoteEvent<RemoteUserIdImpl, RemoteRoomIdImpl, RemoteMessageIdImpl>(
                                    RoomEvent.MessageEvent(
                                        roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                                        eventId = buildEventId(actor.id, event),
                                        sender = author,
                                        content = RoomMessageEventContent.TextBased.Text(
                                            body = "* $text",
                                            relatesTo = originalMessageId?.let {
                                                RelatesTo.Replace(
                                                    eventId = it,
                                                    newContent = RoomMessageEventContent.TextBased.Text(
                                                        body = text
                                                    )
                                                )
                                            }
                                        )
                                    )
                                )
                            )
                        }

                        is LongpollEvent.UsersTyping -> {
                            // TODO
                        }

                        is LongpollEvent.OutboxRead -> { // TODO
                        }

                        is LongpollEvent.ReactionUpdate -> {
                            // TODO
                        }

                        is LongpollEvent.InvalidUpdate -> {
                            errorNotifier.notify(
                                "Could not deserialise update ${event.update.code}. Json follows:\n\n${event.update.raw.toString()}",
                                null
                            )
                        }
                    }
                }
        }.retryWhen { cause, attempt ->
            if (cause is UnresolvedAddressException || cause is HttpRequestTimeoutException) {
                logger.warn { "Got ${cause}, waiting 1 second then trying again" }
                delay(1.seconds)
                true
            } else false
        }

    // Common method for both new messages and edits
    private suspend fun renderMessage(
        event: LongpollEvent.MessageInfo,
        actor: VkActorImpl,
    ): List<RoomEvent.MessageEvent<RemoteUserIdImpl, RemoteRoomIdImpl, RemoteMessageIdImpl>> {
        val sender = RemoteUserIdImpl(event.sender ?: actor.accountId)
        val attachments = event.originalUpdate.extraFields.attachments
        val eventId = buildEventId(actor.id, event)

        // stickers are handled only in new message event

        var text: String = event.text.trim()

        val message =
            if (attachments.attachmentTypes.isNotEmpty() ||
                attachments.hasForwards
            ) vkApi.messages.getByConversationMessageId(
                actor = actor,
                peerId = event.peerId,
                conversationMessageIds = listOf(event.conversationMessageId)
            ).getOrThrow().items[0] else null

        if (attachments.attachmentTypes.isNotEmpty() || attachments.hasGeo) {
            if (text.isNotEmpty()) text += "\n\n"
            text += attachments.attachmentTypes.joinToString(
                "\n",
                prefix = "Attachments:\n"
            ) { "${it.type} - ${it.id}" }
            if (attachments.hasGeo) text += "\ngeo - unknown"
        }

        var relatesToReply: RelatesTo.Reply? = null

        if (attachments.hasForwards) {
            message!!

            val reply = message.repliedTo
            if (message.forwardedMessages.isNotEmpty()) {
                // TODO render forwarded messages
                text += "\n\nThis message has forwarded messages"
            } else if (reply != null) {
                // If we're in MessageEdit event, there's no way to override this reply so fetching message seems unnecessary
                // but MessageEdit (code 5) event does not distinguish between forwards and replies, so we should fetch message anyway

                val id = RemoteMessageIdImpl(actor.id, event.peerId, reply.conversationMessageId)
                workerApi.getMessageEventId(id)
                    .also { if (it == null) logger.error { "Reply target is not registered in database (id=$id)" } }
                    ?.let {
                        relatesToReply = RelatesTo.Reply(RelatesTo.ReplyTo(it))
                    }
            } else {
                logger.error { "Message has forwards, but no forwards or reply found: $event $message" }
            }
        }

        when (event) {
            is LongpollEvent.NewMessage -> {
                val attachmentEvents = message?.attachments?.mapIndexed { i, attachment ->
                    when (attachment) {
                        is MessageAttachment.Photo, is MessageAttachment.File -> {
                            val url = when (attachment) {
                                is MessageAttachment.File -> attachment.url
                                is MessageAttachment.Photo -> attachment.sizes.maxBy { it.width }.url
                                else -> error("Not possible!")
                            }

                            val content = runCatching {
                                vkApiClient.baseClient.get(url) {
                                    expectSuccess = true
                                }
                            }.mapCatching { fileResponse ->
                                mxClient.media.upload(
                                    Media(
                                        content = fileResponse.bodyAsChannel(),
                                        contentLength = fileResponse.contentLength(),
                                        contentType = fileResponse.contentType(),
                                        contentDisposition = null
                                    )
                                ).getOrThrow() to fileResponse
                            }.map { (media, fileResponse) ->
                                when (attachment) {
                                    is MessageAttachment.File -> {
                                        RoomMessageEventContent.FileBased.File(
                                            body = attachment.title,
                                            fileName = attachment.title,
                                            url = media.contentUri,
                                            info = FileInfo(
                                                size = fileResponse.contentLength(),
                                                mimeType = fileResponse.contentType()?.toMimeType()
                                            )
                                        )
                                    }

                                    is MessageAttachment.Photo -> {
                                        val size = attachment.sizes.maxBy { it.width }
                                        require(size.url == url)
                                        RoomMessageEventContent.FileBased.Image(
                                            body = "photo${attachment.ownerId.value}_${attachment.id}.${fileResponse.contentType()?.contentSubtype}",
                                            fileName = "photo${attachment.ownerId.value}_${attachment.id}.${fileResponse.contentType()?.contentSubtype}",
                                            url = media.contentUri,
                                            info = ImageInfo(
                                                width = size.width,
                                                height = size.height,
                                                size = fileResponse.contentLength(),
                                                mimeType = fileResponse.contentType()?.toMimeType()
                                            )
                                        )
                                    }

                                    else -> error("Not possible!")
                                }
                            }.recover {
                                logger.error(
                                    it
                                ) { "Couldn't fetch $attachment from message ${event.conversationMessageId} in ${event.peerId} due to exception" }
                                RoomMessageEventContent.TextBased.Notice(
                                    body = "Couldn't fetch $attachment ($it)"
                                )
                            }.getOrThrow()

                            RoomEvent.MessageEvent(
                                roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                                eventId = eventId.indexed(i),
                                sender = sender,
                                content = content,
                                messageId = RemoteMessageIdImpl(
                                    actor.id,
                                    event.peerId,
                                    event.conversationMessageId, (i + 1).toShort()
                                )
                            )
                        }

                        is MessageAttachment.Video -> {
                            RoomEvent.MessageEvent(
                                roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                                eventId = eventId.indexed(i),
                                sender = sender,
                                content = RoomMessageEventContent.TextBased.Text(
                                    body = "Video attachment: \"${attachment.title}\" (${attachment.link})"
                                ),
                                messageId = RemoteMessageIdImpl(
                                    actor.id,
                                    event.peerId,
                                    event.conversationMessageId, (i + 1).toShort()
                                )
                            )
                        }

                        is MessageAttachment.WallPost -> {
                            RoomEvent.MessageEvent(
                                roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                                eventId = eventId.indexed(i),
                                sender = sender,
                                content = RoomMessageEventContent.TextBased.Text(
                                    body = "Wall post attachment: ${attachment.link}"
                                ),
                                messageId = RemoteMessageIdImpl(
                                    actor.id,
                                    event.peerId,
                                    event.conversationMessageId,
                                    index = (i + 1).toShort()
                                )
                            )
                        }

//                        is MessageAttachment.Sticker -> null

                        is MessageAttachment.UnknownAttachment, is MessageAttachment.InvalidAttachment -> RoomEvent.MessageEvent(
                            roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                            eventId = eventId.indexed(i),
                            sender = sender,
                            content = RoomMessageEventContent.TextBased.Notice(
                                body = "Unknown or invalid attachment $attachment"
                            ),
                            messageId = RemoteMessageIdImpl(
                                actor.id,
                                event.peerId,
                                event.conversationMessageId,
                                index = (i + 1).toShort()
                            )
                        )

                        else -> RoomEvent.MessageEvent(
                            roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                            eventId = eventId.indexed(i),
                            sender = sender,
                            content = RoomMessageEventContent.TextBased.Notice(
                                body = "Unhandled $attachment"
                            ),
                            messageId = RemoteMessageIdImpl(
                                actor.id,
                                event.peerId,
                                event.conversationMessageId,
                                index = (i + 1).toShort()
                            )
                        )
                    }
                } ?: emptyList()

                // render all including attachments
                return listOf(
                    RoomEvent.MessageEvent(
                        roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                        eventId = eventId,
                        sender = sender,
                        content = RoomMessageEventContent.TextBased.Text(
                            body = text.trim(),
                            relatesTo = relatesToReply
                        ),
                        messageId = RemoteMessageIdImpl(actor.id, event.peerId, event.conversationMessageId)
                    )
                ) + attachmentEvents
            }

            is LongpollEvent.MessageEdit -> {
                val remoteMessageId = RemoteMessageIdImpl(
                    actor.id,
                    event.peerId,
                    event.conversationMessageId
                )
                var bodyPrefix = "* "
                val originalMessageId = workerApi.getMessageEventId(remoteMessageId)

                if (LongpollMessageFlags.OUTBOX in event.flags && workerApi.getMessageAuthor(remoteMessageId) != sender /*can change to == null here*/) {
                    // if sender is actor account, then it is either normal edit or bypassed edit
                    // - If it is normal edit, the message is edited already and it is an echo
                    // - If it is bypassed edit, the sender is an actor account, and condition fires if mx author is not a puppet
                    //   so message can't be changed as sender is mismatched.
                    logger.debug {
                        "Got outbox edit on $remoteMessageId (mxid=$originalMessageId) which can't be replicated due to sender mismatch. " +
                                "Most probably it is echo but it can be bridge bypass. Ignoring."
                    }
                    // TODO RemoteWorkerAPI can have these methods to accomodate this behavior:
                    //      - getMessageAuthor (queries homeserver, returns actor account puppet (or null like now, whichever is easier) if sent by actual user)
                    //      - isMessageSentByPuppet (returns true except when sent by actual user) (if previous method returns null, then this method is not needed)
                    return emptyList()
                }

                // only send replacement for first event
                return listOf(
                    RoomEvent.MessageEvent(
                        roomId = RemoteRoomIdImpl(actor.id, event.peerId),
                        eventId = eventId,
                        sender = sender,
                        content = RoomMessageEventContent.TextBased.Text(
                            body = "$bodyPrefix$text",
                            relatesTo = originalMessageId?.let {
                                RelatesTo.Replace(
                                    eventId = it,
                                    newContent = RoomMessageEventContent.TextBased.Text(
                                        body = text
                                    )
                                )
                            }
                        )
                    )
                )
            }
        }
    }

    override suspend fun getUser(actorId: RemoteActorIdImpl, id: RemoteUserIdImpl): RemoteUser<RemoteUserIdImpl> {
        val actor = actorRepository.getActor(actorId)
        val accountId = id.accountId

        if (accountId.isUser) {
            val user = vkApi.users.getById(actor, listOf(accountId)).getOrThrow().single()
            check(id.accountId == user.id) // TODO move these checks to AppServiceWorker
            return user.toRemoteUser()
        } else if (accountId.isGroup) {
            val group = vkApi.groups.getById(actor, listOf(accountId.toGroupId())).getOrThrow().groups.single()
            check(accountId == group.id.toAccountId())
            return group.toRemoteUser()
        } else {
            error("Got invalid account id: $id")
        }
    }

    override suspend fun getRoom(actorId: RemoteActorIdImpl, id: RemoteRoomIdImpl): RemoteRoom {
        val actor = actorRepository.getActor(actorId)
        val peerId = id.also {
            it.ensureApplicable(actor.id)
        }.peerId

        return if (peerId.isChat) {
            val chat = vkApi.messages.getChat(actor, listOf(peerId.toChatId())).getOrThrow().single()
            require(chat.id == peerId.toChatId())
            RemoteRoom(
                id,
                displayName = chat.title,
                isDirect = false
            )
        } else if (peerId.isUser || peerId.isGroup) {
            RemoteRoom(
                id,
                displayName = null,
                isDirect = true
            )
        } else {
            error("Invalid peerId $peerId")
        }
    }

    override fun getRoomMembers(
        actorId: RemoteActorIdImpl,
        remoteId: RemoteRoomIdImpl
    ): Flow<Pair<RemoteUserIdImpl, RemoteUser<RemoteUserIdImpl>?>> = flow {
        val actor = actorRepository.getActor(actorId)
        remoteId.ensureApplicable(actor.id)
        val peerId = remoteId.peerId
        when {
            // only two members in peer - us and them. Bridge will fetch second user.
            peerId.isUser || peerId.isGroup -> emit(RemoteUserIdImpl(peerId.toAccountId()) to null)
            // provide full info on all members
            peerId.isChat -> {
                val (_, items, _, users, groups) = vkApi.messages
                    .getConversationMembers(actor, remoteId.peerId, extended = true).getOrThrow()
                items.map { item ->
                    val remoteUser = when {
                        item.memberId.isUser -> users.first { it.id == item.memberId }.toRemoteUser()
                        item.memberId.isGroup -> groups.first { it.id == item.memberId.toGroupId() }.toRemoteUser()
                        else -> error("Got null account id as member of room $remoteId from $actorId! $item")
                    }

                    RemoteUserIdImpl(item.memberId) to remoteUser
                }
                    .filterNot { it.second.remoteId.accountId == actor.accountId } // remove actor account
                    .forEach { emit(it) }
            }

        }

    }

    private fun User.toRemoteUser(): RemoteUser<RemoteUserIdImpl> {
        return RemoteUser(
            RemoteUserIdImpl(id),
            displayName = "$firstName $lastName".trim()
        )
    }

    private fun Group.toRemoteUser(): RemoteUser<RemoteUserIdImpl> {
        return RemoteUser(
            RemoteUserIdImpl(id.toAccountId()),
            displayName = name
        )
    }

    //region "string format safety"

    private fun RemoteRoomIdImpl.ensureApplicable(remoteActorId: RemoteActorIdImpl) =
        require(actorId == remoteActorId)


    private fun RemoteMessageIdImpl.ensureApplicable(remoteActorId: RemoteActorIdImpl, peerId: PeerId) =
        require(actorId == remoteActorId && this.peerId == peerId)

    // index is a workaround: message with attachments is not 1 message on matrix side

    // Those two aren't read
    private fun buildEventId(actorId: RemoteActorIdImpl, event: LongpollEvent.MessageEvent) =
    // This should be pretty valid idempotency key, becasue [update.ts] is a unique identifier of an update
    // and on matrix side transactionId should be unique for device-endpoint pair, so that probably eventId can be reused
        // FIXME update.ts is not unique identifier of an update, it can change
        RemoteEventId("${actorId.value}_${event.peerId.normalizedPeerId}_${event.conversationMessageId.value}_${event.ts}").also {
            logger.debug { "Built event id $it for event $event}" }
        }

    private fun RemoteEventId.indexed(index: Int) = RemoteEventId("${value}_$index") // it is never parsed, just helper

    //endregion
}