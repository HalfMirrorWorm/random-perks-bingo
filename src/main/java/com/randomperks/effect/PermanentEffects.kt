package com.randomperks.effect

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "本局永久"状态效果的登记与**事件驱动**重挂 (无任何周期轮询)。
 *
 * 效果被任何途径移除 (主动驱散/牛奶、清空、以及**自然到期**——例如被同类药水/信标
 * 覆盖成有限时长后到期) 时, 由 EffectPersistMixin 在 LivingEntity.onStatusEffectsRemoved
 * 统一通知本对象; 若玩家此刻仍持有对应词条就立即重挂。
 *
 * 重挂经服务端任务队列延迟一拍执行, 与"词条移除"流程 (先/后移出 ownedPerks) 不会互相干扰。
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
        Entry(StatusEffects.DOLPHINS_GRACE, "swift_gura_shark", 0),
    )

    /** 效果被移除 (由 EffectPersistMixin 在所有移除路径的汇聚点调用) */
    fun onEffectsRemoved(player: ServerPlayerEntity, removed: Collection<StatusEffectInstance>) {
        val affected = ENTRIES.filter { entry ->
            removed.any { it.effectType.value() == entry.effect.value() }
        }
        if (affected.isEmpty()) return

        player.server?.execute {
            for (entry in affected) applyIfOwned(player, entry)
        }
    }

    private fun applyIfOwned(player: ServerPlayerEntity, entry: Entry) {
        if (!player.isAlive) return
        if (PerkStateManager.get(player.uuid)?.hasPerk(entry.perkId) != true) return
        PerkEffects.permanentEffect(player, entry.effect, entry.amplifier)
    }
}
