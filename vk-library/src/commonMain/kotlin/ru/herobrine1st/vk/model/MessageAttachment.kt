package ru.herobrine1st.vk.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*


@Serializable(with = AttachmentSerializer::class)
public sealed interface MessageAttachment {
    public val type: AttachmentType

    @Serializable
    public data class Photo(
        @SerialName("id")
        val id: Long,
        @SerialName("owner_id")
        val ownerId: AccountId,
        // I don't care
//        @SerialName("album_id")
//        val albumId: Long,
//        @SerialName("user_id")
//        val userId: AccountId? = null,
        @SerialName("sizes")
        val sizes: List<Size>
    ) : MessageAttachment {
        override val type: AttachmentType get() = TYPE

        @Serializable
        public data class Size(
            // type ignored
            @SerialName("url") val url: String,
            @SerialName("width") val width: Int,
            @SerialName("height") val height: Int
        )

        internal companion object {
            val TYPE = AttachmentType.PHOTO
        }
    }

    @Serializable
    public data class Video(
        @SerialName("id")
        val id: Long,
        @SerialName("owner_id")
        val ownerId: AccountId,
        @SerialName("title")
        val title: String,
        @SerialName("duration")
        val durationSeconds: Int
    ) : MessageAttachment {

        override val type: AttachmentType get() = TYPE

        val link: String get() = "https://vk.com/video${ownerId.value}_${id}"

        internal companion object {
            val TYPE = AttachmentType.VIDEO
        }
    }

    @Serializable
    public data class File(
        @SerialName("id")
        val id: Long,
        @SerialName("owner_id")
        val ownerId: AccountId,
        @SerialName("title")
        val title: String,
        @SerialName("url")
        val url: String
    ) : MessageAttachment {

        override val type: AttachmentType get() = TYPE

        internal companion object {
            val TYPE = AttachmentType.FILE
        }
    }

    @Serializable
    public data class Sticker(
        /**
         * Sticker set id
         */
        @SerialName("product_id")
        val productId: Int,
        /**
         * Sticker id
         */
        @SerialName("sticker_id")
        val stickerId: Int,
        @SerialName("images")
        val images: List<Image>,
        // images_with_background ignored
        @SerialName("animation_url")
        val animationUrl: String = "",
        @SerialName("is_allowed")
        val isAvailable: Boolean? = null
    ) : MessageAttachment {
        override val type: AttachmentType get() = TYPE

        internal companion object {
            val TYPE = AttachmentType.STICKER
        }

        @Serializable
        public data class Image(
            val url: String,
            val width: Int,
            val height: Int
        )
    }

    @Serializable
    public data class WallPost(
        @SerialName("id")
        val id: Int,
        @SerialName("owner_id")
        val ownerId: AccountId
    ) : MessageAttachment {
        override val type: AttachmentType get() = TYPE

        val link: String get() = "https://vk.com/wall${ownerId.value}_${id}"

        internal companion object {
            val TYPE = AttachmentType.WALL_POST
        }
    }

    public data class UnknownAttachment(
        val actualType: String,
        val raw: JsonElement
    ) : MessageAttachment {
        override val type: AttachmentType = AttachmentType.UNKNOWN
    }

    public data class InvalidAttachment(
        override val type: AttachmentType,
        val raw: JsonElement
    ) : MessageAttachment
}

internal object AttachmentSerializer : KSerializer<MessageAttachment> {
    private val logger = KotlinLogging.logger {}
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Attachment")

    // This serializer handles following scheme:
    // {
    //   "type": "<TYPE>",
    //   "<TYPE>": { ... }
    // }
    // This scheme is so cursed even Jackson can't handle that by itself

    override fun deserialize(decoder: Decoder): MessageAttachment {
        decoder as JsonDecoder
        val jsonObject = decoder.decodeJsonElement().jsonObject
        if ("type" !in jsonObject) throw SerializationException("No type field found in attachment")
        val typeString = jsonObject["type"]!!.jsonPrimitive.content
        if (typeString !in jsonObject) throw SerializationException("No actual attachment found in attachment")
        val attachment = jsonObject[typeString]!!

        val type = try {
            decoder.json.decodeFromJsonElement<AttachmentType>(jsonObject["type"]!!)
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Attachment $typeString is unknown ($attachment)" }
            return MessageAttachment.UnknownAttachment(typeString, attachment)
        }

        val deserializer = when (type) {
            MessageAttachment.Sticker.TYPE -> MessageAttachment.Sticker.serializer()
            MessageAttachment.Photo.TYPE -> MessageAttachment.Photo.serializer()
            MessageAttachment.File.TYPE -> MessageAttachment.File.serializer()
            MessageAttachment.Video.TYPE -> MessageAttachment.Video.serializer()
            MessageAttachment.WallPost.TYPE -> MessageAttachment.WallPost.serializer()
            else -> {
                logger.warn { "Encountered known attachment with no deserialisation logic: type=$type, $attachment" }
                return MessageAttachment.UnknownAttachment(typeString, attachment)
            }
        }

        return try {
            decoder.json.decodeFromJsonElement(deserializer, attachment)
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Couldn't deserialize attachment $attachment (type=$type)" }
            MessageAttachment.InvalidAttachment(type, attachment)
        }
    }

    override fun serialize(encoder: Encoder, value: MessageAttachment) {
        encoder as JsonEncoder
        val type = encoder.json.encodeToJsonElement(value.type)
        buildJsonObject {
            put("type", type)
            put(
                type.jsonPrimitive.content,
                element = when (value) {
                    is MessageAttachment.InvalidAttachment -> value.raw
                    is MessageAttachment.UnknownAttachment -> value.raw
                    // Don't merge them! Every line has its own serializer
                    is MessageAttachment.Photo -> encoder.json.encodeToJsonElement(value)
                    is MessageAttachment.Sticker -> encoder.json.encodeToJsonElement(value)
                    is MessageAttachment.File -> encoder.json.encodeToJsonElement(value)
                    is MessageAttachment.Video -> encoder.json.encodeToJsonElement(value)
                    is MessageAttachment.WallPost -> encoder.json.encodeToJsonElement(value)
                }
            )
        }.let {
            encoder.encodeJsonElement(it)
        }
    }

}