package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class DocumentType {
    @SerialName("doc")
    DOCUMENT,
    @SerialName("audio_message")
    AUDIO_MESSAGE
}