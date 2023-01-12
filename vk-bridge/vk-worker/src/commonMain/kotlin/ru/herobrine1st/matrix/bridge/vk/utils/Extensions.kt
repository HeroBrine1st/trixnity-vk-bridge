package ru.herobrine1st.matrix.bridge.vk.utils

import io.ktor.http.*

// toString also returns this value, but no guarantees are given
fun ContentType.toMimeType() = "$contentType/$contentSubtype"