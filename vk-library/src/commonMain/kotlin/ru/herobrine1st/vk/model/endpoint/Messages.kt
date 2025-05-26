package ru.herobrine1st.vk.model.endpoint

import io.ktor.resources.*
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.herobrine1st.vk.model.*
import ru.herobrine1st.vk.model.longpoll.LONGPOLL_VERSION
import ru.herobrine1st.vk.serializer.BooleanAsIntSerializer
import ru.herobrine1st.vk.serializer.InstantEpochSecondsSerializer

public class Messages private constructor() {
    // https://dev.vk.com/method/messages.getByConversationMessageId
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @Resource("/method/messages.getByConversationMessageId")
    public data class GetByConversationMessageId(
        @SerialName("peer_id") val peerId: PeerId,
        @SerialName("conversation_message_ids") val conversationMessageIds: List<ConversationMessageId>,
        @Serializable(BooleanAsIntSerializer::class)
        @SerialName("extended") @EncodeDefault(EncodeDefault.Mode.NEVER) val extended: Boolean = false,
        @SerialName("fields") val fields: List<String> = emptyList(),
        @SerialName("group_id") @EncodeDefault(EncodeDefault.Mode.NEVER) val groupId: Int = 0
    ) : VkEndpoint<Unit, GetByConversationMessageId.Response> {
        init {
            require(groupId >= 0)
        }

        @Serializable
        public data class Response(
            val count: Int,
            val items: List<Message>
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @Resource("/method/messages.getById")
    public data class GetById(
        @SerialName("message_ids") val messageIds: List<MessageId>,
        @Serializable(BooleanAsIntSerializer::class)
        @SerialName("extended") @EncodeDefault(EncodeDefault.Mode.NEVER) val extended: Boolean = false,
        @SerialName("fields") val fields: List<String> = emptyList(),
        @SerialName("group_id") @EncodeDefault(EncodeDefault.Mode.NEVER) val groupId: Int = 0,
        // cmids - undocumented
        // peerId - undocumented
    ) : VkEndpoint<Unit, GetById.Response> {

        @Serializable
        public data class Response(
            val count: Int,
            val items: List<Message>
        )
    }

    @Serializable
    @Resource("/method/messages.getLongPollServer")
    public data class GetLongPollServer(
        @SerialName("need_pts") val needPts: Int = 0,
        @SerialName("group_id") val groupId: Int? = null,
        @SerialName("lp_version") val longpollVersion: Int = LONGPOLL_VERSION
    ) : VkEndpoint<Unit, GetLongPollServer.Response> {
        @Serializable
        public data class Response(
            @SerialName("server") val server: String,
            @SerialName("key") val key: String,
            @SerialName("ts") val ts: Long
        )
    }

    // https://dev.vk.com/method/messages.getChat
    @Serializable
    @Resource("/method/messages.getChat")
    public data class GetChat(
        @SerialName("chat_ids") val chatIds: List<ChatId>
    ) : VkEndpoint<Unit, List<Chat>>

    // https://dev.vk.com/ru/method/messages.send
    @Serializable
    @Resource("/method/messages.send")
    public data object Send: VkEndpoint<Send.Request, List<Send.ResponseItem>> {
        @Serializable
        public data class Request(
            @SerialName("peer_ids") val peerIds: List<PeerId>,
            @SerialName("random_id") val randomId: Int = 0,
            @SerialName("message") val body: String? = null,
            @SerialName("attachment") val attachments: List<AttachmentId> = emptyList(),
            @SerialName("reply_to") val replyTo: MessageId? = null,
            @SerialName("forward") val forward: Forward? = null
        ) {
            init {
                require(body != null || attachments.isNotEmpty()) { "Message must not be empty" }
            }
        }

        @Serializable
        public data class Forward(
            @SerialName("peer_id") val peerId: PeerId,
            @SerialName("conversation_message_ids") val conversationMessageIds: List<ConversationMessageId>,
            @SerialName("is_reply") val isReply: Boolean
        )

        @Serializable
        public data class ResponseItem(
            @SerialName("peer_id") val peerId: PeerId,
            @SerialName("message_id") val messageId: MessageId,
            @SerialName("conversation_message_id") val conversationMessageId: ConversationMessageId
        )
    }

    // https://dev.vk.com/ru/method/messages.getConversationMembers
    @Serializable
    @Resource("/method/messages.getConversationMembers")
    public data class GetConversationMembers(
        @SerialName("peer_id") val peerId: PeerId,
        @Serializable(with = BooleanAsIntSerializer::class)
        @SerialName("extended") val extended: Boolean = false
    ) : VkEndpoint<Unit, GetConversationMembers.Response> {
        @Serializable
        public data class Response(
            @SerialName("count") val count: Int,
            @SerialName("items") val items: List<Item>,
            @SerialName("chat_restrictions") val chatRestrictions: JsonObject,
            @SerialName("profiles") val users: List<User> = emptyList(),
            @SerialName("groups") val groups: List<Group> = emptyList()
        ) {
            @Serializable
            public data class Item(
                @SerialName("member_id") val memberId: AccountId,
                // these fields aren't returned if peerId.isChat is false
                @SerialName("invited_by") val invitedBy: AccountId = AccountId.NULL,
                @Serializable(with = InstantEpochSecondsSerializer::class)
                @SerialName("join_date") val joinedAt: Instant = Instant.DISTANT_PAST,
                @Serializable(with = BooleanAsIntSerializer::class)
                @SerialName("is_admin") val isAdmin: Boolean = false,
                @Serializable(with = BooleanAsIntSerializer::class)
                @SerialName("can_kick") val canKick: Boolean = false // can be kicked by current actor
            )
        }
    }

    // https://dev.vk.com/ru/method/messages.edit
    @Serializable
    @Resource("/method/messages.edit")
    public data object Edit :
        VkEndpoint<Edit.Request, Int /*as boolean, no support for custom deserialisers currently*/> {
        @Serializable
        public data class Request(
            @SerialName("peer_id") val peerId: PeerId,
            @SerialName("cmid") val conversationMessageId: ConversationMessageId,
            @SerialName("message") val body: String? = null,
            @SerialName("attachment") val attachments: List<AttachmentId> = emptyList(),
            @Serializable(with = BooleanAsIntSerializer::class)
            @SerialName("keep_forward_messages") val keepForwardMessages: Boolean = false
        )
    }

    // https://dev.vk.com/ru/method/messages.delete
    @Serializable
    @Resource("/method/messages.delete")
    public data class Delete(
        @SerialName("peer_id") val peerId: PeerId,
        @SerialName("cmids") val conversationMessageIds: List<ConversationMessageId>,
        @Serializable(with = BooleanAsIntSerializer::class)
        @SerialName("delete_for_all") val forAll: Boolean = true
    ) : VkEndpoint<Unit, List<Delete.ResponseItem>> {
        @Serializable
        public data class ResponseItem(
            @SerialName("peer_id") val peerId: PeerId,
            @SerialName("conversation_message_id") val conversationMessageId: ConversationMessageId,
            @Serializable(with = BooleanAsIntSerializer::class)
            @SerialName("response") val success: Boolean = false,
            @SerialName("error") val error: Error? = null
        ) {
            @Serializable
            public data class Error(
                // it differs from standard error response
                @SerialName("code") val code: Int,
                @SerialName("description") val description: String
            )
        }
    }
}