package ru.herobrine1st.vk.model.endpoint

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.vk.model.PeerId

public class Photos private constructor() {
    @Serializable
    @Resource("/method/photos.getMessagesUploadServer")
    public data class GetMessagesUploadServer(
        @SerialName("peer_id") val peerId: PeerId,
    ) : VkEndpoint<Unit, GetMessagesUploadServer.Response> {
        @Serializable
        public data class Response(
            @SerialName("album_id") val albumId: Long,
            @SerialName("upload_url") val uploadUrl: String,
            @SerialName("user_id") val userId: Long = 0,
            @SerialName("group_id") val groupId: Long = 0
        )
    }

//    @Serializable
//    @Resource("/method/photos.getMessagesUploadServer")
//    data class SaveMessagesPhoto(
//        @SerialName("peer_id") val peerId: PeerId,
//    ) : VkEndpoint<Unit, GetMessagesUploadServer.Response> {
//
//    }
}