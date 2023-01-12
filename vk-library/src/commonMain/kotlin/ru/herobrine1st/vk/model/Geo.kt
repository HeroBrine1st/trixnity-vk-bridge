package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class Geo(
    @SerialName("type") val type: String,
    @SerialName("coordinates") val coordinates: JsonElement, // TODO in docs it's both array and object
    @SerialName("place") val place: Place,
    @SerialName("showmap") val showMap: Int
)

@Serializable
public data class Place(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("created") val created: Long, // there's no documentation for format of this field. It's long. Probably wide. Or Tall.
    @SerialName("icon") val iconUrl: String,
    @SerialName("country") val country: String,
    @SerialName("city") val city: String
)
