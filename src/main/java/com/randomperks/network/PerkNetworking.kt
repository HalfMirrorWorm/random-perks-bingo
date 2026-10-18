package com.randomperks.network

import com.randomperks.RandomPerksMod
import com.randomperks.game.PerkAbilityService
import com.randomperks.game.PerkOfferService
import com.randomperks.game.StealService
import com.randomperks.perk.content.ArtisanPerks
import com.randomperks.perk.content.HunterPerks
import com.randomperks.perk.content.SeekerPerks
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.item.ItemStack
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
        PayloadTypeRegistry.playS2C().register(PerkAbilitySyncS2C.ID, PerkAbilitySyncS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(GlowMarkS2C.ID, GlowMarkS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(WaterBucketTotemS2C.ID, WaterBucketTotemS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkRequestC2S.ID, PerkRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkPickC2S.ID, PerkPickC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkListRequestC2S.ID, PerkListRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(PerkCatalogRequestC2S.ID, PerkCatalogRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(DoubleJumpC2S.ID, DoubleJumpC2S.CODEC)
        PayloadTypeRegistry.playS2C().register(StealOpenS2C.ID, StealOpenS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(StealTargetsS2C.ID, StealTargetsS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(ActionTargetsS2C.ID, ActionTargetsS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(StealRequestC2S.ID, StealRequestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(StealPickC2S.ID, StealPickC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(StealTargetPickC2S.ID, StealTargetPickC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(ActionTargetPickC2S.ID, ActionTargetPickC2S.CODEC)

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

        ServerPlayNetworking.registerGlobalReceiver(StealRequestC2S.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                StealService.handleRequest(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(StealPickC2S.ID) { payload, context ->
            val player = context.player()
            val index = payload.index
            context.server().execute {
                StealService.handlePick(player, index)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(StealTargetPickC2S.ID) { payload, context ->
            val player = context.player()
            val uuid = payload.uuid
            context.server().execute {
                StealService.handleTargetPick(player, uuid)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ActionTargetPickC2S.ID) { payload, context ->
            val player = context.player()
            val purpose = payload.purpose
            val uuid = payload.uuid
            context.server().execute {
                when (purpose) {
                    "lightning" -> HunterPerks.CallLightning.handleTargetPick(player, uuid)
                    "nuke" -> HunterPerks.Nuclear.handleTargetPick(player, uuid)
                    "puny" -> SeekerPerks.Puny.handleTargetPick(player, uuid)
                    "poisoner" -> ArtisanPerks.Poisoner.handleTargetPick(player, uuid)
                }
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

    /** 同步玩家的词条能力列表 (客户端用于启用矿透/偷窃等能力) */
    fun sendAbilitySync(player: ServerPlayerEntity, perkIds: List<String>) {
        if (!ServerPlayNetworking.canSend(player, PerkAbilitySyncS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, PerkAbilitySyncS2C(perkIds)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send ability sync: {}", it.toString()) }
    }

    /** 下发偷窃目标信息与背包内容 */
    fun sendStealOpen(player: ServerPlayerEntity, targetName: String, items: List<ItemStack>) {
        if (!ServerPlayNetworking.canSend(player, StealOpenS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, StealOpenS2C(targetName, items)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send steal open: {}", it.toString()) }
    }

    /** 下发"顺手牵羊"的候选目标列表 (客户端据此打开玩家选择器) */
    fun sendStealTargets(player: ServerPlayerEntity, targets: List<PlayerTargetEntry>) {
        if (!ServerPlayNetworking.canSend(player, StealTargetsS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, StealTargetsS2C(targets)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send steal targets: {}", it.toString()) }
    }

    /** 下发"选一名玩家后立即生效"类词条的候选列表 (purpose 区分词条: lightning / nuke) */
    fun sendActionTargets(player: ServerPlayerEntity, purpose: String, targets: List<PlayerTargetEntry>) {
        if (!ServerPlayNetworking.canSend(player, ActionTargetsS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, ActionTargetsS2C(purpose, targets)) }
            .onFailure { RandomPerksMod.logger.warn("[RandomPerks] failed to send action targets: {}", it.toString()) }
    }

    /** 让客户端把指定实体显示为发光标记 (仅发给同队玩家) */
    fun sendGlowMark(player: ServerPlayerEntity, entityIds: List<Int>, durationMillis: Long) {
        if (!ServerPlayNetworking.canSend(player, GlowMarkS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, GlowMarkS2C(entityIds, durationMillis)) }
    }

    /** 通知客户端播放"水桶演出" ("落地，水"救命的图腾式动画) */
    fun sendWaterBucketTotem(player: ServerPlayerEntity) {
        if (!ServerPlayNetworking.canSend(player, WaterBucketTotemS2C.ID)) return
        runCatching { ServerPlayNetworking.send(player, WaterBucketTotemS2C()) }
    }
}
