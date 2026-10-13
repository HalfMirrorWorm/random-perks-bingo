package com.roguebingo

import com.roguebingo.command.RogueBingoCommands
import com.roguebingo.game.PerkTickService
import com.roguebingo.game.PerkWorldEventService
import com.roguebingo.lifecycle.BingoLifecycle
import com.roguebingo.network.PerkNetworking
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.perk.content.PerkContent
import com.roguebingo.server.ServerHolder
import com.roguebingo.state.PerkStateManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.LoggerFactory

object RogueBingoMod : ModInitializer {
    const val MOD_ID = "rogue-bingo"

    val logger = LoggerFactory.getLogger(MOD_ID)!!

    override fun onInitialize() {
        logger.info("[RogueBingo] initializing")

        // 词条注册
        PerkContent.registerAll()

        // 网络层 (payload 注册必须在启动期完成)
        PerkNetworking.registerServer()

        // 服务端实例追踪
        ServerLifecycleEvents.SERVER_STARTED.register { ServerHolder.server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            ServerHolder.server = null
            PerkStateManager.resetAll()
        }

        // 对接 bingo 游戏生命周期 (开局赠送 / 结束清理)
        BingoLifecycle.register()

        // 驱动词条周期回调与重生重挂
        PerkTickService.register()

        // 世界事件 (击杀 / 破坏方块) → 词条
        PerkWorldEventService.register()

        // 调试命令
        RogueBingoCommands.register()

        logger.info("[RogueBingo] ready — {} perk(s) registered", PerkRegistry.size())
    }
}
