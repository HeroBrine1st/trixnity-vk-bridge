package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://dev.vk.com/reference/objects/user
@Serializable
public data class User(
    @SerialName("id") val id: AccountId,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("deactivated") val deactivated: String? = null,
    @SerialName("is_closed") val isClosed: Boolean,
    @SerialName("can_access_closed") val canAccessClosed: Boolean
) {
    init {
        require(id.isUser)
    }
}