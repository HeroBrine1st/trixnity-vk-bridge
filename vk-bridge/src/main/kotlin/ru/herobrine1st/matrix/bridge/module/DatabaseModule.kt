package ru.herobrine1st.matrix.bridge.module

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.async.coroutines.awaitQuery
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.r2dbc.R2dbcDriver
import app.cash.sqldelight.driver.r2dbc.R2dbcPreparedStatement
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import ru.herobrine1st.matrix.bridge.Database
import ru.herobrine1st.matrix.bridge.database.ConnectionFactoryImpl
import ru.herobrine1st.matrix.bridge.database.SingletonDatabaseProvider
import ru.herobrine1st.matrix.bridge.database.toDriver
import ru.herobrine1st.matrix.bridge.database.awaitCompletion
import ru.herobrine1st.matrix.bridge.database.completeAnyway
import java.time.Duration

fun Application.databaseModule() {

    val connectionFactory = ConnectionFactoryImpl(
        host = environment.config.property("ktor.deployment.database.host").getString(),
        port = environment.config.property("ktor.deployment.database.port").getString().toInt(),
        database = environment.config.property("ktor.deployment.database.name").getString(),
        username = environment.config.property("ktor.deployment.database.username").getString(),
        password = environment.config.property("ktor.deployment.database.password").getString(),
        connectionIdleTime = Duration.ofMillis(
            environment.config.property("ktor.deployment.database.pool.idleTimeMs").getString().toLong()
        ),
        maxConnectionPoolSize = environment.config.property("ktor.deployment.database.pool.maxSize")
            .getString().toInt()
    )
    SingletonDatabaseProvider.setApplicationConnectionFactory(connectionFactory)

    val logger = environment.log

    // Block on migration
    runBlocking {
        connectionFactory.getConnection().toDriver().use { driver ->
            val version = driver.getVersion()
            logger.info("Database version is $version")
            if (version == 0L) {
                logger.info("Creating database from scratch..")
                driver.migrationBoilerplate { Database.Schema.awaitCreate(driver) }
                logger.info("Database successfully created")
            } else if (version < Database.Schema.version) {
                logger.info("Migrating from $version to ${Database.Schema.version}")
                driver.migrationBoilerplate {
                    Database.Schema.awaitMigrate(driver, version, Database.Schema.version)
                }
                logger.info("Migration complete")
            } else {
                logger.info("No migration needed")
            }
        }
    }
}

private suspend fun SqlDriver.getVersion(): Long {
    await(null, "CREATE TABLE IF NOT EXISTS metadata(version BIGINT NOT NULL)", 0)
    return awaitQuery(
        identifier = null,
        sql = """
                SELECT CASE (SELECT COUNT(*) FROM metadata)
                    WHEN 0 THEN 0
                    ELSE (SELECT version FROM metadata)
                END
                """.trimIndent(),
        mapper = {
            it.next().await()
            it.getLong(0)!!
        },
        parameters = 0,
    )
}

private suspend fun R2dbcDriver.setVersion(version: Long) {
    await(null, "DELETE FROM metadata", 0)
    execute(null, "INSERT INTO metadata VALUES ($1)", 1) {
        check(this is R2dbcPreparedStatement)
        bindLong(0, version)
    }.await()
}



private suspend inline fun R2dbcDriver.migrationBoilerplate(block: () -> Unit) {
    connection.beginTransaction().awaitCompletion()
    try {
        block()
        setVersion(Database.Schema.version)
        connection.commitTransaction().awaitCompletion()
    } catch (t: Throwable) {
        connection.rollbackTransaction().completeAnyway()
        throw t
    }
}