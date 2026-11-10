package com.randomperks.effect

import com.randomperks.game.NyaAxeService
import com.randomperks.state.PerkStateManager
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION

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
        val effect: Holder<MobEffect>,
        val perkId: String,
        val amplifier: Int,
        /** 额外的重挂条件 (默认总是重挂) */
        val condition: (ServerPlayer) -> Boolean = { true },
    )

    /** 永久效果 → 授予它的词条 / 增幅等级 */
    private val ENTRIES = listOf(
        Entry(MobEffects.NIGHT_VISION, "delver_night_vision", 0),
        Entry(MobEffects.RESISTANCE, "artisan_toughness", 0),
        Entry(MobEffects.HASTE, "delver_haste", 1),
        Entry(MobEffects.DOLPHINS_GRACE, "swift_gura_shark", 0),
        Entry(MobEffects.CONDUIT_POWER, "swift_gura_shark", 0),
        // 深海之息 / 喵人斧 描述都是"永久", 被牛奶或有限时长覆盖后要自动补回
        Entry(MobEffects.WATER_BREATHING, "swift_deep_breath", 0),
        // 喵人斧的虚弱只在**使用后**才有, 没用过时不要把药水带来的虚弱补成永久
        Entry(MobEffects.WEAKNESS, "artisan_nya_axe", 0) { NyaAxeService.hasUsed(it) },
        // 注: "弹簧腿"只加 JUMP_STRENGTH 属性, 不再登记 JUMP_BOOST ——
        // 两者叠加会让跳跃高度超过描述, 而且牛奶后补回的效果会一直留到词条移除
    )

    /** 效果被移除 (由 EffectPersistMixin 在所有移除路径的汇聚点调用) */
    fun onEffectsRemoved(player: ServerPlayer, removed: Collection<MobEffectInstance>) {
        val affected = ENTRIES.filter { entry ->
            removed.any { it.effect.value() == entry.effect.value() }
        }
        if (affected.isEmpty()) return

        player.level().getServer().execute {
            for (entry in affected) applyIfOwned(player, entry)
        }
    }

    private fun applyIfOwned(player: ServerPlayer, entry: Entry) {
        if (!player.isAlive) return
        if (PerkStateManager.get(player.uuid)?.hasPerk(entry.perkId) != true) return
        if (!entry.condition(player)) return
        PerkEffects.permanentEffect(player, entry.effect, entry.amplifier)
    }

    /**
     * 移除词条时只摘掉**我们自己挂的**永久效果。
     *
     * 当前实例必须同时满足"无限时长 + 增幅等级与词条登记一致"才移除；
     * 玩家从药水 / 信标 / 其他来源获得的同类有限时长效果不会被误删。
     */
    fun removePermanent(player: ServerPlayer, effect: Holder<MobEffect>, amplifier: Int) {
        val instance = player.getEffect(effect) ?: return
        if (instance.duration != INFINITE_DURATION) return
        if (instance.amplifier != amplifier) return
        player.removeEffect(effect)
    }
}
