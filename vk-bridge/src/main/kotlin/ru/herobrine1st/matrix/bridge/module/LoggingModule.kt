package ru.herobrine1st.matrix.bridge.module

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*

fun Application.loggingModule() {
    install(CallLogging)
}