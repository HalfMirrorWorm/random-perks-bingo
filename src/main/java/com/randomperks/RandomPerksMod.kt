package com.randomperks

import com.randomperks.command.RandomPerksCommands
import com.randomperks.config.PerkConfig
import com.randomperks.game.ContainerOpenService
import com.randomperks.game.CreativeFlashService
import com.randomperks.game.DragonTamerService
import com.randomperks.game.FireballService
import com.randomperks.game.LayFlatService
import com.randomperks.game.MagnetService
import com.randomperks.game.ModVersionService
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkTickService
import com.randomperks.game.PigMorphService
import com.randomperks.game.WaypointService
import com.randomperks.game.PerkWorldEventService
import com.randomperks.game.SavageMagicService
import com.randomperks.game.SoulOutService
import com.randomperks.game.SummonedHorseService
import com.randomperks.game.TntThrowService
import com.randomperks.game.VerminSwarmService
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

        // "变群友"的持久变形状态 (自维护 + 广播给客户端)
        PigMorphService.register()

        // "驯龙高手": 骑乘其他生物并控制它们使用能力
        DragonTamerService.register()

        // "野人魔了": 职业方块把傻子村民变成大师级村民
        SavageMagicService.register()

        // 神话词条的创造闪光调度
        CreativeFlashService.register()

        // 传送时钟的右键逻辑
        WaypointService.register()

        // 烈焰喷射的右键逻辑
        FireballService.register()

        // "!?区区!?" 的蠹虫骚扰调度
        VerminSwarmService.register()

        // "开摆" 的五分钟诅咒与结算
        LayFlatService.register()

        // "磁力升级" 的 16 格拾取扫描
        MagnetService.register()

        // "马来"召唤的马会像驯服的狼一样跟着主人
        SummonedHorseService.register()

        // "磁力升级": 手持潜影盒 / 末影箱右键直接打开
        ContainerOpenService.register()

        // "TNT 投手" 的右键抛射
        TntThrowService.register()

        // 世界事件 (击杀 / 破坏方块) → 词条
        PerkWorldEventService.register()

        // 玩家加入时同步词条能力状态 (断线重连后恢复)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            PerkGrantService.syncAbilities(handler.player)
            // "灵魂出窍"进行中断线重连: 把灵魂相机状态补回去
            SoulOutService.syncOnJoin(handler.player)
        }

        // 客户端/服务端 mod 版本一致性校验 (登录阶段, 不一致或客户端没装则拒绝进入)
        ModVersionService.registerServer()

        // 调试命令
        RandomPerksCommands.register()

        logger.info("[RandomPerks] ready — {} perk(s) registered", PerkRegistry.size())
    }
}
