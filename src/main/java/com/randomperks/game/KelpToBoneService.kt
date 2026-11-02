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
 * "海带成骨" —— 右键**干海带块**把它变成骨块。
 *
 * 骨块是骨粉的稳定来源, 而干海带块可以靠海带农场无限量产; 这条词条把两者打通。
 * 不限制手持物品 (空手或任意物品右键都可), 其余方块一律 PASS 交还原版。
 */
object KelpToBoneService {

    const val PERK_ID = "harvest_kelp_to_bone"

    fun handleUse(player: ServerPlayer, world: ServerLevel, pos: BlockPos): InteractionResult {
        if (!PerkSession.isEnabled()) return InteractionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return InteractionResult.PASS
        if (!state.hasPerk(PERK_ID)) return InteractionResult.PASS
        if (!world.getBlockState(pos).`is`(Blocks.DRIED_KELP_BLOCK)) return InteractionResult.PASS

        world.setBlock(pos, Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL)
        world.playSound(
            null, pos,
            SoundEvents.BONE_BLOCK_PLACE,
            SoundSource.BLOCKS,
            0.8f, 1.0f,
        )
        return InteractionResult.SUCCESS
    }
}
