package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
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

        // "属于你的马"的交互 (含旧版本召唤的马):
        // ① 潜行 + 空手右键 → 打开马的装备界面 (不依赖原版条件)
        // ② 剪刀右键 → 直接取下鞍
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (entity !is AbstractHorseEntity) return@register ActionResult.PASS
            val owner = entity.ownerReference ?: return@register ActionResult.PASS
            if (owner.uuid != player.uuid) return@register ActionResult.PASS

            // ① 潜行 + 空手 → 打开马的装备界面 (RideableInventory 的官方入口)
            if (player.isSneaking && player.mainHandStack.isEmpty) {
                entity.openInventory(player)
                return@register ActionResult.SUCCESS
            }

            // ② 剪刀 → 取下鞍
            if (!player.mainHandStack.isOf(Items.SHEARS)) return@register ActionResult.PASS
            val saddle = entity.getEquippedStack(EquipmentSlot.SADDLE)
            if (saddle.isEmpty) return@register ActionResult.PASS

            entity.equipStack(EquipmentSlot.SADDLE, ItemStack.EMPTY)
            player.giveItemStack(saddle)
            player.playSoundToPlayer(
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.8f, 1.0f,
            )
            ActionResult.SUCCESS
        }
    }

    /** 装备变化 (由 EquipmentChangeMixin 注入原版 equipStack 调用) —— 即时重算装备相关词条 */
    fun dispatchEquipmentChange(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onEquipmentChange(ctx) }
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
