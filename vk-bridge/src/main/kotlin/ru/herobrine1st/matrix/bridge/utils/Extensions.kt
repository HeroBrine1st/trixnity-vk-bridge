package ru.herobrine1st.matrix.bridge.utils

import io.ktor.server.config.*
import ru.herobrine1st.matrix.bridge.config.BridgeConfig
import ru.herobrine1st.matrix.bridge.config.BridgeConfig.Presence
import ru.herobrine1st.matrix.bridge.config.BridgeConfig.Provisioning

fun BridgeConfig.Companion.fromConfig(config: ApplicationConfig) = BridgeConfig(
    homeserverDomain = config.property("homeserverDomain").getString(),
    botLocalpart = config.property("botLocalpart").getString(),
    puppetPrefix = config.property("puppetPrefix").getString(),
    roomAliasPrefix = config.property("roomAliasPrefix").getString(),
    provisioning = Provisioning(
        whitelist = config.property("provisioning.whitelist").getList().map {
            Regex(it)
        },
        blacklist = config.property("provisioning.blacklist").getList().map {
            Regex(it)
        }
    ),
    presence = Presence(
        remote = config.property("presence.remote").getString().toBoolean(),
        local = config.property("presence.local").getString().toBoolean()
    )
)