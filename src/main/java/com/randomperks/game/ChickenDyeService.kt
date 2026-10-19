package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.passive.ChickenEntity
import net.minecraft.entity.passive.ChickenVariant
import net.minecraft.entity.passive.ChickenVariants
import net.minecraft.item.DyeItem
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.DyeColor
import net.minecraft.util.Hand

/**
 * "叮咚鸡" —— 用染料右键**任意**鸡给它染色。
 *
 * 这里的"染色"直接用原版的**鸡变体** (1.21.5 加入的那套): 染料按冷暖分成三组,
 * 分别对应温带 (白) / 暖水 (棕) / 寒冷 (蓝灰) 三种鸡。
 *
 * 变体是实体的数据组件, 会自动同步给客户端、也会存进 NBT —— 所以既不需要自定义渲染,
 * 也不需要额外的网络包, 区块卸载重载后颜色也还在。
 */
object ChickenDyeService {

    const val PERK_ID = "harvest_dingdong_chicken"

    /** 暖色染料 → 暖水鸡 (棕色) */
    private val WARM_DYES = setOf(
        DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW,
        DyeColor.BROWN, DyeColor.PINK, DyeColor.MAGENTA,
    )

    /** 冷色染料 → 寒冷鸡 (蓝灰色) */
    private val COLD_DYES = setOf(
        DyeColor.LIGHT_BLUE, DyeColor.CYAN, DyeColor.BLUE,
        DyeColor.PURPLE, DyeColor.LIME, DyeColor.GREEN,
    )

    /** 手持染料右键鸡时调用; 返回 PASS 表示"这不是我管的事", 交还原版 */
    fun tryDye(player: ServerPlayerEntity, target: ChickenEntity, hand: Hand): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS

        val stack = player.getStackInHand(hand)
        val dye = stack.item as? DyeItem ?: return ActionResult.PASS

        // 剩下的白 / 浅灰 / 灰 / 黑落到温带鸡
        val variantKey: RegistryKey<ChickenVariant> = when (dye.color) {
            in WARM_DYES -> ChickenVariants.WARM
            in COLD_DYES -> ChickenVariants.COLD
            else -> ChickenVariants.TEMPERATE
        }

        val registry = player.server?.registryManager?.getOrThrow(RegistryKeys.CHICKEN_VARIANT)
            ?: return ActionResult.PASS
        target.variant = registry.getOrThrow(variantKey)

        if (!player.abilities.creativeMode) stack.decrement(1)
        player.world.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ITEM_DYE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f,
        )
        return ActionResult.SUCCESS
    }
}
