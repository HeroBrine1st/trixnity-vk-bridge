package ru.herobrine1st.vk.model.endpoint

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.vk.model.AccountId
import ru.herobrine1st.vk.model.User

public class Users private constructor() {
    @Serializable
    @Resource("/method/users.get")
    public data class GetById(
        @SerialName("user_ids") val userIds: List<AccountId>,
    ) : VkEndpoint<Unit, List<User>>
}