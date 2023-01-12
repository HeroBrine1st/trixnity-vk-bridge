package ru.herobrine1st.vk.model.endpoint

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import ru.herobrine1st.vk.model.Group
import ru.herobrine1st.vk.model.GroupId

public class Groups private constructor() {
    @Serializable
    @Resource("/method/groups.getById")
    public data class GetById(
        @SerialName("group_ids") val groupIds: List<GroupId>
    ): VkEndpoint<Unit, GetById.Response> {
        @Serializable
        public data class Response(
            @SerialName("groups") val groups: List<Group>,
            @SerialName("profiles") val profiles: JsonArray // wtf is that
        )
    }
}