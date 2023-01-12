package ru.herobrine1st.matrix.bridge.database

import app.cash.sqldelight.driver.r2dbc.R2dbcDriver

object SingletonDatabaseProvider: DatabaseProvider {
    private lateinit var applicationConnectionFactory_: ConnectionFactory

    fun setApplicationConnectionFactory(connectionFactory: ConnectionFactory) {
        require(!SingletonDatabaseProvider::applicationConnectionFactory_.isInitialized)
        applicationConnectionFactory_ = connectionFactory
    }
    override suspend fun getDriver(): R2dbcDriver = applicationConnectionFactory_.getConnection().toDriver()
}