package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.core.BlockPos

/**
 * "点石成金" —— 右键**镶金黑石**把它变成金块。
 *
 * 镶金黑石只在下界堡垒遗迹里少量生成, 原版破坏通常只掉金粒; 这条词条让它变成整块金块。
 * 不限制手持物品, 其余方块一律 PASS 交还原版。
 */
object MidasTouchService {

    const val PERK_ID = "artisan_midas_touch"

    fun handleUse(player: ServerPlayer, world: ServerLevel, pos: BlockPos): InteractionResult {
        if (!PerkSession.isEnabled()) return InteractionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return InteractionResult.PASS
        if (!state.hasPerk(PERK_ID)) return InteractionResult.PASS
        if (!world.getBlockState(pos).`is`(Blocks.GILDED_BLACKSTONE)) return InteractionResult.PASS

        world.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL)
        world.playSound(
            null, pos,
            SoundEvents.METAL_PLACE,
            SoundSource.BLOCKS,
            0.8f, 1.2f,
        )
        return InteractionResult.SUCCESS
    }
}
