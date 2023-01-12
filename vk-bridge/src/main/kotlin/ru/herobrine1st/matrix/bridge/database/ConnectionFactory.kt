package ru.herobrine1st.matrix.bridge.database

import io.r2dbc.spi.Connection
import org.slf4j.helpers.CheckReturnValue

interface ConnectionFactory {
    @CheckReturnValue
    suspend fun getConnection(): Connection
}