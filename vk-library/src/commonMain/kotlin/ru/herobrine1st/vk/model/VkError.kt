package ru.herobrine1st.vk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import ru.herobrine1st.vk.model.endpoint.Messages


@Serializable(with = VkErrorSerializer::class)
public sealed interface VkError {
    @SerialName("error_code") public val code: Int
    @SerialName("error_msg") public val message: String

    @Serializable(with = UnknownVkErrorSerializer::class)
    public data class UnknownError(
        override val code: Int,
        override val message: String,
        val raw: JsonObject
    ) : VkError


    // because some methods have internal error responses and they're different
    @ConsistentCopyVisibility
    public data class ExternalError internal constructor(
        override val code: Int,
        override val message: String
    ) : VkError {
        // probably this will be replaced with functional constructor and this class will be removed
        internal constructor(error: Messages.Delete.ResponseItem.Error) : this(error.code, error.description)
    }
}

internal object VkErrorSerializer : KSerializer<VkError> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(VkError::class.simpleName!!)

    override fun deserialize(decoder: Decoder): VkError {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        val actualSerializer = when (element.jsonObject["code"]?.jsonPrimitive?.intOrNull) {
            else -> decoder.serializersModule.serializer<VkError.UnknownError>()
        }
        return decoder.json.decodeFromJsonElement(actualSerializer, element)
    }

    override fun serialize(encoder: Encoder, value: VkError) {
        return when (value) {
            is VkError.UnknownError -> encoder.encodeSerializableValue(
                encoder.serializersModule.serializer(),
                value
            )

            is VkError.ExternalError -> error("VkError.ExternalError should not be serialized")
        }
    }
}

internal object UnknownVkErrorSerializer : KSerializer<VkError.UnknownError> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownVkErrorSerializer")

    override fun deserialize(decoder: Decoder): VkError.UnknownError {
        decoder as JsonDecoder
        val jsonObject = decoder.decodeJsonElement() as JsonObject
        return VkError.UnknownError(
            code = jsonObject["error_code"]!!.jsonPrimitive.int,
            message = jsonObject["error_msg"]!!.jsonPrimitive.content,
            raw = jsonObject
        )
    }

    override fun serialize(encoder: Encoder, value: VkError.UnknownError) {
        encoder as JsonEncoder
        encoder.encodeJsonElement(value.raw)
    }

}