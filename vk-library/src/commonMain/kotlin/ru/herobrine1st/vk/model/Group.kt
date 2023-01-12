package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.herobrine1st.vk.serializer.BooleanAsIntSerializer

// https://dev.vk.com/reference/objects/group
@Serializable
public data class Group(
    @SerialName("id") val id: GroupId,
    @SerialName("name") val name: String,
    @SerialName("screen_name") val screenName: String,
    @SerialName("is_closed") val isClosed: Int, // 0..2
    @SerialName("deactivated") val deactivated: String? = null,
    @Serializable(BooleanAsIntSerializer::class) @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("admin_level") val adminLevel: Int = 0,
    @Serializable(BooleanAsIntSerializer::class) @SerialName("is_member") val isMember: Boolean = false,
    @Serializable(BooleanAsIntSerializer::class) @SerialName("is_advertiser") val isAdvertiser: Boolean = false,
    @SerialName("invited_by") val invitedBy: AccountId? = null,
    @SerialName("type") val type: String = "group",
    @SerialName("photo_50") val photo50Url: String? = null,
    @SerialName("photo_100") val photo100Url: String? = null,
    @SerialName("photo_200") val photo200Url: String? = null
)
