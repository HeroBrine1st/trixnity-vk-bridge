package ru.herobrine1st.vk.model.upload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TODO we can use content body transformation to define request model
@Serializable
public data class PhotoUploadResponse(
    // Opaque values
    @SerialName("server") val server: Long,
    @SerialName("photo") val photo: String,
    @SerialName("hash") val hash: String
)
