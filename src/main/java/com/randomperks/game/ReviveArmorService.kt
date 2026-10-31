package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.DeathProtectionComponent
import net.minecraft.entity.EntityStatuses
import net.minecraft.entity.damage.DamageSource
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Hand

/**
 * "复活甲" (紫 · 匠造) 的服务端逻辑。
 *
 * 玩家受到**致命伤害**时, 自动复刻一次不死图腾的结算 (不消耗任何物品):
 * 生命设为 1, 清空负面效果并给予生命恢复 II / 伤害吸收 II / 抗火, 播放图腾音效与粒子。
 * **整局只能触发一次** (没有冷却), 触发过后再受致命伤害就正常死亡。
 *
 * 触发点在原版 {@code LivingEntity.tryUseDeathProtector} 的 HEAD (见 ReviveArmorMixin) ——
 * 原版只有在生命值归零后才会调用那个方法, 所以天然满足"致命伤害"的条件。
 */
object ReviveArmorService {

    const val PERK_ID = "artisan_revive_armor"

    /** "已触发"标记 (存在 state.memory 里, 对局结束随词条状态一起清空) */
    private const val USED_KEY = "revive_armor_used"

    /**
     * 尝试为玩家触发一次复活甲。
     *
     * @return true 表示已触发 (调用方应跳过原版死亡结算)
     */
    fun tryTrigger(player: ServerPlayerEntity, source: DamageSource): Boolean {
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasReviveArmor(player)) return false

        // 与原版不死图腾一致: 无视无敌的伤害 (虚空 / /kill 等) 无法触发
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false

        // 手上已经有真图腾: 交给原版处理, 不浪费词条冷却
        if (hasTotemInHand(player, Hand.MAIN_HAND) || hasTotemInHand(player, Hand.OFF_HAND)) return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        if (state.memory[USED_KEY] == "1") return false

        state.memory[USED_KEY] = "1"

        // 复刻原版 tryUseDeathProtector 的结算 (不消耗物品)
        player.setHealth(1.0f)
        DeathProtectionComponent.TOTEM_OF_UNDYING.applyDeathEffects(
            ItemStack(Items.TOTEM_OF_UNDYING),
            player,
        )
        player.world.sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING)
        return true
    }

    private fun hasTotemInHand(player: ServerPlayerEntity, hand: Hand): Boolean =
        player.getStackInHand(hand).get(DataComponentTypes.DEATH_PROTECTION) != null
}
