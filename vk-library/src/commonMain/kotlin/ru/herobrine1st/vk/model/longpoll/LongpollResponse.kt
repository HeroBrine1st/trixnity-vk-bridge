package ru.herobrine1st.vk.model.longpoll

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.herobrine1st.vk.annotation.DelicateVkLibraryAPI

public const val LONGPOLL_VERSION: Int = 19

// Add required modes together (2+8=10 etc.) and use as `mode` field.
public const val MODE_ATTACHMENTS: Int = 2
public const val MODE_EXTRA_EVENTS: Int = 8
public const val MODE_RETURN_PTS: Int = 32
public const val MODE_EXTRA_DATA_FOR_ONLINE_EVENT: Int = 64
public const val MODE_RETURN_RANDOM_ID: Int = 128

public const val LONGPOLL_FAILED_UPDATE_TIMESTAMP: Int = 1

@Serializable(with = LongpollResponseSerializer::class)
public sealed interface LongpollResponse {
    @Serializable
    public data class Update(
        /**
         * Delicate API: this ts does not correspond to this update
         */
        @property:DelicateVkLibraryAPI
        @SerialName("ts") val ts: Long,
        @SerialName("updates") val updates: List<LongpollUpdate>,
        @SerialName("pts") val pts: Long = -1,
    ) : LongpollResponse

    @Serializable(with = FailedResponseSerializer::class)
    public sealed interface Failed : LongpollResponse {
        public val failed: Int

        @Serializable
        public data class UpdateTimestamp(
            @SerialName("ts") val ts: Long
        ) : Failed {
            @SerialName("failed")
            override val failed: Int = LONGPOLL_FAILED_UPDATE_TIMESTAMP
        }

        @Serializable
        public data object UpdateKey : Failed {
            @SerialName("failed")
            override val failed: Int = 2
        }

        @Serializable
        public data object InvalidateAll : Failed {
            @SerialName("failed")
            override val failed: Int = 3
        }

        @Serializable
        public data object InvalidVersion : Failed {
            @SerialName("failed")
            override val failed: Int = 4
        }
    }
}

internal object LongpollResponseSerializer : KSerializer<LongpollResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LongpollResponseSerializer")

    override fun deserialize(decoder: Decoder): LongpollResponse {
        decoder as JsonDecoder
        val tree = decoder.decodeJsonElement().jsonObject

        val delegate = when {
            "failed" in tree -> LongpollResponse.Failed.serializer()
            else -> LongpollResponse.Update.serializer()
        }

        return decoder.json.decodeFromJsonElement(delegate, tree)
    }

    override fun serialize(encoder: Encoder, value: LongpollResponse) {
        throw NotImplementedError()
    }

}

internal object FailedResponseSerializer : KSerializer<LongpollResponse.Failed> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LongpollResponseSerializer")

    override fun deserialize(decoder: Decoder): LongpollResponse.Failed {
        decoder as JsonDecoder
        val tree = decoder.decodeJsonElement().jsonObject
        return when (val code = tree["failed"]!!.jsonPrimitive.int) {
            LONGPOLL_FAILED_UPDATE_TIMESTAMP -> decoder.json.decodeFromJsonElement(
                LongpollResponse.Failed.UpdateTimestamp.serializer(),
                tree
            )
            LongpollResponse.Failed.UpdateKey.failed -> LongpollResponse.Failed.UpdateKey
            LongpollResponse.Failed.InvalidateAll.failed -> LongpollResponse.Failed.InvalidateAll
            LongpollResponse.Failed.InvalidVersion.failed -> LongpollResponse.Failed.InvalidVersion
            else -> error("$code is not a valid `failed` value")
        }
    }

    override fun serialize(encoder: Encoder, value: LongpollResponse.Failed) {
        throw NotImplementedError()
    }

}