package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.animal.chicken.ChickenVariant
import net.minecraft.world.entity.animal.chicken.ChickenVariants
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.DyeColor
import net.minecraft.world.InteractionHand

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
    fun tryDye(player: ServerPlayer, target: Chicken, hand: InteractionHand): InteractionResult {
        if (!PerkSession.isEnabled()) return InteractionResult.PASS
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return InteractionResult.PASS
        if (!state.hasPerk(PERK_ID)) return InteractionResult.PASS

        val stack = player.getItemInHand(hand)
        val dyeColor = stack.get(DataComponents.DYE) ?: return InteractionResult.PASS

        // 剩下的白 / 浅灰 / 灰 / 黑落到温带鸡
        val variantKey: ResourceKey<ChickenVariant> = when (dyeColor) {
            in WARM_DYES -> ChickenVariants.WARM
            in COLD_DYES -> ChickenVariants.COLD
            else -> ChickenVariants.TEMPERATE
        }

        val registry = player.level().getServer().registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT)
            ?: return InteractionResult.PASS
        target.variant = registry.getOrThrow(variantKey)

        if (!player.abilities.instabuild) stack.shrink(1)
        player.level().playSound(
            null, target.x, target.y, target.z,
            SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0f, 1.0f,
        )
        return InteractionResult.SUCCESS
    }
}
