package com.randomperks.game

import com.randomperks.effect.PerkEffects
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * "开摆" (紫 · 秘藏) 的服务端逻辑。
 *
 * 获得时: 失明 + 缓慢 IV, 持续 5 分钟。
 * 熬过去: 发一枚自然罗盘 + 一枚结构罗盘 (来自 Nature's Compass / Explorer's Compass 两个 mod)。
 * 中途死亡: 只发 1 把石斧。
 *
 * 石斧在**重生时**发而不是死亡瞬间发 —— 死亡瞬间给的东西会随着背包一起掉在死亡点。
 */
object LayFlatService {

    /** 折磨时长: 5 分钟 */
    private const val DURATION_TICKS = 5 * 60 * 20

    /** 缓慢 IV (amplifier 3) */
    private const val SLOWNESS_AMPLIFIER = 3

    private val NATURES_COMPASS = Identifier.of("naturescompass", "naturescompass")
    private val EXPLORERS_COMPASS = Identifier.of("explorerscompass", "explorerscompass")

    /** 玩家 uuid → 结算 tick */
    private val active = mutableMapOf<UUID, Long>()

    /** 中途死掉、等着重生时领石斧的玩家 */
    private val failed = mutableSetOf<UUID>()

    fun register() {
        // 到期结算: 发两枚罗盘
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (active.isEmpty()) return@register
            val now = server.ticks.toLong()

            for ((uuid, endTick) in active.toList()) {
                if (now < endTick) continue
                active.remove(uuid)
                if (failed.remove(uuid)) continue   // 死了的, 石斧已经/将在重生时发
                val player = server.playerManager.getPlayer(uuid) ?: continue
                grantCompasses(player)
            }
        }

        // 中途死亡 → 标记失败并停止计时
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity !is ServerPlayerEntity) return@register
            if (!active.containsKey(entity.uuid)) return@register
            active.remove(entity.uuid)
            failed.add(entity.uuid)
        }

        // 重生 → 发石斧
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            if (!failed.remove(newPlayer.uuid)) return@register
            PerkEffects.giveStack(newPlayer, ItemStack(Items.STONE_AXE))
        }
    }

    /**
     * 两个罗盘 mod 是否都已加载 —— 决定「开摆」是否进入词条池。
     *
     * 必须查全局物品注册表而不是在 mod 初始化时判断: Fabric 的 mod 加载顺序不保证,
     * 我们的 onInitialize 可能先于 Nature's Compass 执行。
     */
    fun hasCompassMods(): Boolean {
        val registry = Registries.ITEM
        return registry.getEntry(NATURES_COMPASS).isPresent &&
            registry.getEntry(EXPLORERS_COMPASS).isPresent
    }

    /** 获得词条时调用: 上 debuff 并开始计时 */
    fun start(player: ServerPlayerEntity) {
        val server = player.server ?: return
        active[player.uuid] = server.ticks.toLong() + DURATION_TICKS

        player.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, DURATION_TICKS, 0))
        player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, DURATION_TICKS, SLOWNESS_AMPLIFIER))
    }

    /** 对局结束 / 重置时清理 */
    fun clear() {
        active.clear()
        failed.clear()
    }

    /** 发奖励 —— 两个罗盘来自外部 mod, 没装就提示并跳过 (不崩) */
    private fun grantCompasses(player: ServerPlayerEntity) {
        val registry: Registry<Item> =
            player.server?.registryManager?.getOrThrow(RegistryKeys.ITEM) ?: return

        val natures = registry.getEntry(NATURES_COMPASS).orElse(null)?.value()
        val explorers = registry.getEntry(EXPLORERS_COMPASS).orElse(null)?.value()
        if (natures == null || explorers == null) {
            player.sendMessage(
                Text.translatable("random-perks-bingo.message.lay_flat_missing_mod"),
                false,
            )
            return
        }

        PerkEffects.giveStack(player, ItemStack(natures))
        PerkEffects.giveStack(player, ItemStack(explorers))
    }
}
