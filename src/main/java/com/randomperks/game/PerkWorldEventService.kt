package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.content.HarvestPerks
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * 把世界事件 (击杀 / 破坏方块 / 装备变化 / 实体交互) 分发给玩家已获得的词条。
 */
object PerkWorldEventService {

    fun register() {
        // 击杀生物
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
            val killer = damageSource.attacker as? ServerPlayerEntity ?: return@register
            dispatch(killer) { ctx, perk -> perk.onKill(ctx, entity) }
        }

        // 破坏方块
        PlayerBlockBreakEvents.AFTER.register { _, player, pos, state, _ ->
            if (player !is ServerPlayerEntity) return@register
            dispatch(player) { ctx, perk -> perk.onBlockBreak(ctx, pos, state) }
        }

        // "属于你的马"交互时的兼容修复 (含旧版本召唤的马):
        // 旧代码只设了主人没设驯服标志, 未驯服的马会被原版 openInventory 的
        // isTame 检查拒绝 (表现为"按 E 打不开装备界面"), 见到就补上驯服标志。
        // 其余交互 (骑乘 / 按 E 或潜行右键开装备界面 / 剪刀剪下装备) 全部交还原版处理。
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (entity !is AbstractHorseEntity) return@register ActionResult.PASS
            val owner = entity.ownerReference ?: return@register ActionResult.PASS
            if (owner.uuid != player.uuid) return@register ActionResult.PASS

            if (!entity.isTame && HarvestPerks.SummonHorse.TAG in entity.commandTags) {
                entity.setTame(true)
            }

            ActionResult.PASS
        }
    }

    /** 装备变化 (由 EquipmentChangeMixin 注入原版 equipStack 调用) —— 即时重算装备相关词条 */
    fun dispatchEquipmentChange(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onEquipmentChange(ctx) }
    }

    /** 饱食度刚回满 (由 HungerRefillMixin 注入原版 HungerManager.update 调用) */
    fun dispatchHungerRefilled(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onHungerRefilled(ctx) }
    }

    private inline fun dispatch(
        player: ServerPlayerEntity,
        action: (PerkContext, PerkDefinition) -> Unit,
    ) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (state.ownedPerks.isEmpty()) return

        val ctx = PerkContext(player, state)
        for (perk in state.ownedPerks.toList()) {
            runCatching { action(ctx, perk) }
                .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} world-event failed", perk.id, it) }
        }
    }
}
