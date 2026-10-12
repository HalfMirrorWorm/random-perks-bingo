package com.roguebingo.client

import com.roguebingo.client.net.ClientPerkNetworking
import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object RogueBingoClient : ClientModInitializer {

    val logger = LoggerFactory.getLogger("rogue-bingo/client")!!

    override fun onInitializeClient() {
        logger.info("[RogueBingo] client initializing")

        ClientPerkNetworking.registerClient()
        Keybinds.register()
    }
}
