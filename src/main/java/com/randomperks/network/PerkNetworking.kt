package com.randomperks.network

import com.randomperks.RandomPerksMod
import com.randomperks.game.PerkAbilityService
import com.randomperks.game.PerkOfferService
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 服务端网络层: 注册 payload 与接收器。
 */
object PerkNetworking {

    /** 必须在 mod 初始化时调用 (PayloadTypeRegistry 只在启动期可注册) */
    fun registerServer() {
        PayloadTypeRegistry.playS2C().register(PerkNotifyS2C.ID, PerkNotifyS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PerkOfferS2C.ID, PerkOfferS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PerkListS2C.ID, PerkListS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PerkCatalogS2C.ID, PerkCatalogS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkRequestC2S.ID, PerkRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkPickC2S.ID, PerkPickC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkListRequestC2S.ID, PerkListRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkCatalogRequestC2S.ID, PerkCatalogRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(DoubleJumpC2S.ID, DoubleJumpC2S.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(PerkRequestC2S.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                PerkOfferService.handleRequest(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PerkPickC2S.ID) { payload, context ->
            val player = context.player()
            val index = payload.index
            context.server().execute {
                PerkOfferService.handlePick(player, index)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PerkListRequestC2S.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                PerkOfferService.handleListRequest(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(PerkCatalogRequestC2S.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                PerkOfferService.handleCatalogRequest(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(DoubleJumpC2S.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                PerkAbilityService.handleDoubleJump(player)
            }
        }
    }

    /** 通知玩家当前待选机会数 (客户端未安装本 mod 时静默跳过) */
    fun sendNotify(player: ServerPlayerEntity, pendingCount: Int) {
        if (!ServerPlayNetworking.canSend(player, PerkNotifyS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, PerkNotifyS2C(pendingCount)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send notify: {}", it.toString()) }
    }

    /** 下发三选一候选 */
    fun sendOffer(player: ServerPlayerEntity, options: List<PerkOptionData>) {
        if (!ServerPlayNetworking.canSend(player, PerkOfferS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, PerkOfferS2C(options)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send offer: {}", it.toString()) }
    }

    /** 下发已获得词条列表 */
    fun sendPerkList(player: ServerPlayerEntity, pendingCount: Int, perks: List<PerkOptionData>) {
        if (!ServerPlayNetworking.canSend(player, PerkListS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, PerkListS2C(pendingCount, perks)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send perk list: {}", it.toString()) }
    }

    /** 下发词条百科 (全部词条 + 已收集 id) */
    fun sendCatalog(player: ServerPlayerEntity, ownedIds: List<String>, allPerks: List<PerkOptionData>) {
        if (!ServerPlayNetworking.canSend(player, PerkCatalogS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, PerkCatalogS2C(ownedIds, allPerks)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send catalog: {}", it.toString()) }
    }
}
