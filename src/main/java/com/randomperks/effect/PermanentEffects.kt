package com.randomperks.effect

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "本局永久"状态效果的登记与**事件驱动**重挂 (无任何周期轮询)。
 *
 * 效果被原版机制清除 (不死图腾/牛奶等) 时, 由 EffectPersistMixin 通知本对象;
 * 若玩家此刻仍持有对应词条就立即重挂 —— 通过服务端任务队列延迟执行,
 * 这样"词条移除"流程 (onRemove 之前/之后才移出 ownedPerks) 不会被误重挂。
 */
object PermanentEffects {

    private data class Entry(
        val effect: RegistryEntry<StatusEffect>,
        val perkId: String,
        val amplifier: Int,
    )

    /** 永久效果 → 授予它的词条 / 增幅等级 */
    private val ENTRIES = listOf(
        Entry(StatusEffects.NIGHT_VISION, "delver_night_vision", 0),
        Entry(StatusEffects.RESISTANCE, "delver_toughness", 0),
        Entry(StatusEffects.HASTE, "delver_haste", 1),
        Entry(StatusEffects.JUMP_BOOST, "swift_jump", 0),
    )

    /** 单个效果被移除 (LivingEntity.removeStatusEffect 之后由 mixin 调用) */
    fun onEffectRemoved(player: ServerPlayerEntity, effect: RegistryEntry<StatusEffect>) {
        val entry = ENTRIES.firstOrNull { it.effect.value() == effect.value() } ?: return
        player.server?.execute { applyIfOwned(player, entry) }
    }

    /** 所有效果被清空 (LivingEntity.clearStatusEffects 之后由 mixin 调用) */
    fun onEffectsCleared(player: ServerPlayerEntity) {
        player.server?.execute {
            for (entry in ENTRIES) applyIfOwned(player, entry)
        }
    }

    private fun applyIfOwned(player: ServerPlayerEntity, entry: Entry) {
        if (!player.isAlive) return
        if (PerkStateManager.get(player.uuid)?.hasPerk(entry.perkId) != true) return
        PerkEffects.permanentEffect(player, entry.effect, entry.amplifier)
    }
}
