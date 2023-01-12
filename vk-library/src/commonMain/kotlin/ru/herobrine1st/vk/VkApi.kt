package ru.herobrine1st.vk

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.*
import ru.herobrine1st.vk.model.*
import ru.herobrine1st.vk.model.endpoint.*
import ru.herobrine1st.vk.model.endpoint.Messages.Send.Forward
import ru.herobrine1st.vk.model.upload.FileUploadResponse
import ru.herobrine1st.vk.model.upload.PhotoUploadResponse

@Suppress("unused")
public class VkApi(private val httpClient: VkApiClient) {

    public val messages: MessageMethods by lazy { MessageMethods(httpClient) }
    public val users: UserMethods by lazy { UserMethods(httpClient) }
    public val groups: GroupMethods by lazy { GroupMethods(httpClient) }
    public val media: MediaWrappers by lazy { MediaWrappers(httpClient) }

    public class GroupMethods(private val httpClient: VkApiClient) {
        public suspend fun getById(actor: VkActor, groupIds: List<GroupId>): Result<Groups.GetById.Response> =
            httpClient.request(
                actor, Groups.GetById(
                    groupIds
                )
            )
    }

    public class MessageMethods(private val httpClient: VkApiClient) {
        public suspend fun getLongPollServer(actor: VkActor): Result<Messages.GetLongPollServer.Response> =
            httpClient.request(
                actor,
                Messages.GetLongPollServer()
            )

        public suspend fun getByConversationMessageId(
            actor: VkActor,
            peerId: PeerId,
            conversationMessageIds: List<ConversationMessageId>,
            extended: Boolean = false,
            fields: List<String> = emptyList(),
            groupId: Int = 0
        ): Result<Messages.GetByConversationMessageId.Response> = httpClient.request(
            actor,
            Messages.GetByConversationMessageId(
                peerId = peerId,
                conversationMessageIds = conversationMessageIds,
                extended = extended,
                fields = fields,
                groupId = groupId
            )
        )

        public suspend fun getById(
            actor: VkActor,
            messageIds: List<MessageId>,
            extended: Boolean = false,
            fields: List<String> = emptyList(),
            groupId: Int = 0,
        ): Result<Messages.GetById.Response> = httpClient.request(
            actor,
            Messages.GetById(
                messageIds = messageIds,
                extended = extended,
                fields = fields,
                groupId = groupId
            )
        )

        public suspend fun getChat(actor: VkActor, chatIds: List<ChatId>): Result<List<Chat>> =
            httpClient.request(actor, Messages.GetChat(chatIds))

        public suspend fun send(
            actor: VkActor,
            peerId: PeerId,
            randomId: Int = 0,
            body: String? = null,
            attachments: List<AttachmentId> = emptyList(),
            replyTo: MessageId? = null,
            forward: Forward? = null
        ): Result<List<Messages.Send.ResponseItem>> = httpClient.request(
            actor,
            Messages.Send,
            Messages.Send.Request(listOf(peerId), randomId, body, attachments, replyTo, forward)
        )

        public suspend fun getConversationMembers(
            actor: VkActor,
            peerId: PeerId,
            extended: Boolean = false
        ): Result<Messages.GetConversationMembers.Response> =
            httpClient.request(actor, Messages.GetConversationMembers(peerId, extended))

        public suspend fun edit(
            actor: VkActor,
            peerId: PeerId,
            conversationMessageId: ConversationMessageId,
            body: String? = null,
            attachments: List<AttachmentId> = emptyList(),
            keepForwardMessages: Boolean = true
        ): Result<Unit> = httpClient.request(
            actor,
            Messages.Edit,
            Messages.Edit.Request(peerId, conversationMessageId, body, attachments, keepForwardMessages)
        ).mapCatching {
            if (it != 1) error("Unknown error while editing message $conversationMessageId in $peerId, , response is $it")
        }

        public suspend fun delete(
            actor: VkActor,
            peerId: PeerId,
            conversationMessageIds: List<ConversationMessageId>,
            forAll: Boolean = true
        ): Result<Unit> =
            httpClient.request(actor, Messages.Delete(peerId, conversationMessageIds, forAll)).mapCatching {
                it.forEachIndexed { i, item ->
                    if (!item.success)
                        item.error?.let { throw VkApiException(VkError.ExternalError(it)) }
                            ?: error("Unknown error while deleting ${conversationMessageIds[i]} in peer ${peerId}, response is $it")
                }
            }
    }

    public class UserMethods(private val httpClient: VkApiClient) {
        public suspend fun getById(
            actor: VkActor,
            userIds: List<AccountId>
        ): Result<List<User>> = httpClient.request(
            actor,
            Users.GetById(userIds)
        )
    }

    public class MediaWrappers(private val httpClient: VkApiClient) {
        public suspend fun uploadMessagePhoto(
            actor: VkActor,
            peerId: PeerId,
            media: UploadMedia,
        ): Result<AttachmentId> = runCatching {
            val uploadServer = httpClient.request(actor, Photos.GetMessagesUploadServer(peerId)).getOrThrow()
            val uploadResponse = httpClient.baseClient.submitFormWithBinaryData(
                url = uploadServer.uploadUrl,
                formData = formData {
                    append(
                        "file0",
                        ChannelProvider(media.contentLength) { media.content },
                        headers {
                            append(HttpHeaders.ContentType, media.contentType)
                            append(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Inline.withParameter(
                                    "filename",
//                                    "image.${
//                                        media.contentDisposition.parameter("filename")?.substringAfterLast(".") ?:
//                                        media.contentType.takeIf { it.contentType == "image" }?.contentSubtype ?:
//                                        "jpg"
//                                    }"
                                    "image.jpeg"
                                )
                            )
                        }
                    )
                }
            ) {
                bearerAuth(actor.token)
                expectSuccess = true
            }.body<PhotoUploadResponse>()
            if (uploadResponse.photo == "[]") {
                throw RuntimeException("Image rejected")
            }
            val saveResponse = httpClient.baseClient.submitFormWithBinaryData(
                url = "/method/photos.saveMessagesPhoto",
                formData = formData {
                    append("server", uploadResponse.server)
                    append("photo", uploadResponse.photo)
                    append("hash", uploadResponse.hash)
                }
            ) {
                bearerAuth(actor.token)
                expectSuccess = true
            }.body<VkResponse<List<MessageAttachment.Photo>>>()
            when (saveResponse) {
                is VkResponse.Error -> throw VkApiException(saveResponse.error)
                is VkResponse.Ok -> {
                    saveResponse.response.single().toAttachmentId()
                }
            }
        }

        public suspend fun uploadMessageFile(
            actor: VkActor,
            peerId: PeerId,
            media: UploadMedia,
        ): Result<AttachmentId> = runCatching {
            val uploadServer = httpClient.request(actor, Docs.GetMessagesUploadServer(peerId)).getOrThrow()
            val uploadResponse = httpClient.baseClient.submitFormWithBinaryData(
                url = uploadServer.uploadUrl,
                formData = formData {
                    append(
                        "file",
                        ChannelProvider(media.contentLength) { media.content },
                        headers {
                            append(HttpHeaders.ContentType, media.contentType)
                            append(HttpHeaders.ContentDisposition, media.contentDisposition)
                        }
                    )
                }
            ) {
                bearerAuth(actor.token)
                expectSuccess = true
            }.body<FileUploadResponse>()
            // TODO handle rejection
            val saveResponse = httpClient.baseClient.submitFormWithBinaryData(
                url = "/method/docs.save",
                formData = formData {
                    append("file", uploadResponse.file)
                }
            ) {
                bearerAuth(actor.token)
                expectSuccess = true
            }.body<VkResponse<MessageAttachment>>()
            when (saveResponse) {
                is VkResponse.Error -> throw VkApiException(saveResponse.error)
                is VkResponse.Ok -> {
                    (saveResponse.response as MessageAttachment.File).toAttachmentId()
                }
            }
        }

        public data class UploadMedia(
            val content: ByteReadChannel,
            val contentLength: Long?,
            val contentType: ContentType,
            val contentDisposition: ContentDisposition,
        )
    }
}