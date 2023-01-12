package ru.herobrine1st.vk.model.upload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class FileUploadResponse(
    @SerialName("file") val file: String,
)