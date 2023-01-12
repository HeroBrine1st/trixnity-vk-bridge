package ru.herobrine1st.vk.model.endpoint

import kotlinx.serialization.Serializable
import ru.herobrine1st.vk.model.AccountId
import ru.herobrine1st.vk.model.MessageAttachment
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class AttachmentId private constructor(public val value: String) {

    public constructor(type: Type, ownerId: AccountId, mediaId: Long, accessKey: String?) :
            this(
                listOfNotNull(ownerId.value.toString(), mediaId.toString(), accessKey)
                    .joinToString(separator = "_", prefix = type.apiName)
            )

    public val type: Type get() = Type.entries.first { value.startsWith(it.apiName) }

    public val ownerId: AccountId get() = AccountId(value.removePrefix(type.apiName).substringBefore('_').toLong())


    public val mediaId: String get() = value.substringAfter('_').substringBefore('_')

    public val accessKey: String? get() = value.substringAfter('_').substringAfter('_').takeIf { it.isNotBlank() }

    public enum class Type(public val apiName: String) {
        PHOTO("photo"),
        VIDEO("video"),
        AUDIO("audio"),
        FILE("doc"),
        WALL_POST("wall"),
        MARKET("market"),
        POLL("poll"),
        QUESTION("question");
    }
}


public fun MessageAttachment.Photo.toAttachmentId() = AttachmentId(AttachmentId.Type.PHOTO, ownerId, id, null)

public fun MessageAttachment.File.toAttachmentId() = AttachmentId(AttachmentId.Type.FILE, ownerId, id, null)