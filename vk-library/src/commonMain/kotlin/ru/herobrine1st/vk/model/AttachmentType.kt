package ru.herobrine1st.vk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class AttachmentType {
    @SerialName("photo")
    PHOTO,

    @SerialName("video")
    VIDEO,

    @SerialName("audio")
    AUDIO,

    @SerialName("doc")
    FILE,

    @SerialName("link")
    LINK,

    @SerialName("market")
    MARKET,

    @SerialName("market_album")
    MARKET_ALBUM,

    @SerialName("wall")
    WALL_POST,

    @SerialName("wall_reply")
    WALL_REPLY,

    @SerialName("sticker")
    STICKER,

    // Also two types not listed in documentation
    @SerialName("poll")
    POLL, // found by empirical observation

    @SerialName("question")
    QUESTION, // source is unknown

    @SerialName("audio_message")
    AUDIO_MESSAGE,

    @SerialName("graffiti")
    GRAFFITI,

    UNKNOWN;
}