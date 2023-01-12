package ru.herobrine1st.vk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*


@PublishedApi
@Serializable(with = VkResponseSerializer::class)
internal sealed interface VkResponse<Response> {
    @Serializable
    data class Error<Response>(@SerialName("error") val error: VkError) : VkResponse<Response>

    @Serializable
    data class Ok<Response>(@SerialName("response") val response: Response) : VkResponse<Response>
}

internal class VkResponseSerializer<T>(private val responseSerializer: KSerializer<T>) : KSerializer<VkResponse<T>> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("VkResponseSerializer", responseSerializer.descriptor)

    override fun deserialize(decoder: Decoder): VkResponse<T> {
        decoder as JsonDecoder
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return when {
            "response" in jsonObject -> VkResponse.Ok(
                decoder.json.decodeFromJsonElement(responseSerializer, jsonObject["response"]!!)
            )

            "error" in jsonObject -> VkResponse.Error(
                decoder.json.decodeFromJsonElement(jsonObject["error"]!!)
            )

            else -> error("Response is not ok nor errorneous")
        }
    }

    override fun serialize(encoder: Encoder, value: VkResponse<T>) {
        encoder as JsonEncoder
        val element = buildJsonObject {
            when (value) {
                is VkResponse.Error -> {
                    put("error", encoder.json.encodeToJsonElement(value.error))
                }

                is VkResponse.Ok -> put("response", encoder.json.encodeToJsonElement(responseSerializer, value.response))
            }
        }
        encoder.encodeJsonElement(element)
    }
}