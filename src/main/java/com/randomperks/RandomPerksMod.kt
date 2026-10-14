package com.randomperks

import com.randomperks.command.RandomPerksCommands
import com.randomperks.config.PerkConfig
import com.randomperks.game.CreativeFlashService
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkTickService
import com.randomperks.game.PerkWorldEventService
import com.randomperks.lifecycle.BingoLifecycle
import com.randomperks.network.PerkNetworking
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.content.PerkContent
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.LoggerFactory

object RandomPerksMod : ModInitializer {
    const val MOD_ID = "random-perks-bingo"

    val logger = LoggerFactory.getLogger(MOD_ID)!!

    override fun onInitialize() {
        logger.info("[RandomPerks] initializing")

        // 加载配置 (词条系统总开关)
        PerkConfig.load()

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

        // 神话词条的创造闪光调度
        CreativeFlashService.register()

        // 世界事件 (击杀 / 破坏方块) → 词条
        PerkWorldEventService.register()

        // 玩家加入时同步词条能力状态 (断线重连后恢复)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            PerkGrantService.syncAbilities(handler.player)
        }

        // 调试命令
        RandomPerksCommands.register()

        logger.info("[RandomPerks] ready — {} perk(s) registered", PerkRegistry.size())
    }
}
