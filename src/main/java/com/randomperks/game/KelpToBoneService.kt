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
 * "海带成骨" —— 右键**干海带块**把它变成骨块。
 *
 * 骨块是骨粉的稳定来源, 而干海带块可以靠海带农场无限量产; 这条词条把两者打通。
 * 不限制手持物品 (空手或任意物品右键都可), 其余方块一律 PASS 交还原版。
 */
object KelpToBoneService {

    const val PERK_ID = "artisan_kelp_to_bone"

    fun handleUse(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS
        if (!world.getBlockState(pos).isOf(Blocks.DRIED_KELP_BLOCK)) return ActionResult.PASS

        world.setBlockState(pos, Blocks.BONE_BLOCK.defaultState, Block.NOTIFY_ALL)
        world.playSound(
            null, pos,
            SoundEvents.BLOCK_BONE_BLOCK_PLACE,
            SoundCategory.BLOCKS,
            0.8f, 1.0f,
        )
        return ActionResult.SUCCESS
    }
}
