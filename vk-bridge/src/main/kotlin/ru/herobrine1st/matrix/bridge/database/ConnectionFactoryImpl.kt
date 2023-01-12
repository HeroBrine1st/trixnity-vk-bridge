package ru.herobrine1st.matrix.bridge.database

import io.r2dbc.pool.PoolingConnectionFactoryProvider.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions.*
import org.reactivestreams.Publisher
import org.slf4j.helpers.CheckReturnValue
import java.time.Duration

class ConnectionFactoryImpl(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String,
    connectionIdleTime: Duration,
    maxConnectionPoolSize: Int
) : ConnectionFactory {
    private val pool: Publisher<out Connection> = ConnectionFactories.get(builder().apply {
        option(DRIVER, POOLING_DRIVER)
        option(PROTOCOL, "postgresql")
        option(HOST, host)
        option(PORT, port)
        option(USER, username)
        option(PASSWORD, password)
        option(DATABASE, database)
        option(MAX_IDLE_TIME, connectionIdleTime)
        option(MAX_SIZE, maxConnectionPoolSize)
    }.build()).create()

    @CheckReturnValue
    override suspend fun getConnection() = pool.awaitFirst()
}