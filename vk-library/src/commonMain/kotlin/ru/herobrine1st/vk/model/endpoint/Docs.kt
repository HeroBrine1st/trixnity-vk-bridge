package ru.herobrine1st.vk.model.endpoint

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.vk.model.DocumentType
import ru.herobrine1st.vk.model.PeerId

public class Docs private constructor() {
    @Serializable
    @Resource("/method/docs.getMessagesUploadServer")
    public data class GetMessagesUploadServer(
        @SerialName("peer_id") val peerId: PeerId,
        @SerialName("type") val documentType: DocumentType? = null
    ) : VkEndpoint<Unit, GetMessagesUploadServer.Response> {
        @Serializable
        public data class Response(
            @SerialName("upload_url") val uploadUrl: String,
        )
    }
}