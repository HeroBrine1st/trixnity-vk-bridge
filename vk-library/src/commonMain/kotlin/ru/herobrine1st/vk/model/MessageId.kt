package ru.herobrine1st.vk.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class MessageId(public val value: Long)

