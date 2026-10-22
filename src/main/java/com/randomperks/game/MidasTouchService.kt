package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos

/**
 * "点石成金" —— 右键**镶金黑石**把它变成金块。
 *
 * 镶金黑石只在下界堡垒遗迹里少量生成, 原版破坏通常只掉金粒; 这条词条让它变成整块金块。
 * 不限制手持物品, 其余方块一律 PASS 交还原版。
 */
object MidasTouchService {

    const val PERK_ID = "artisan_midas_touch"

    fun handleUse(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS
        if (!world.getBlockState(pos).isOf(Blocks.GILDED_BLACKSTONE)) return ActionResult.PASS

        world.setBlockState(pos, Blocks.GOLD_BLOCK.defaultState, Block.NOTIFY_ALL)
        world.playSound(
            null, pos,
            SoundEvents.BLOCK_METAL_PLACE,
            SoundCategory.BLOCKS,
            0.8f, 1.2f,
        )
        return ActionResult.SUCCESS
    }
}
