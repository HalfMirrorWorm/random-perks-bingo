package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.tags.ItemTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos

/**
 * "切洋葱" —— 手持**镐**右键黑曜石 / 哭泣的黑曜石, 在两者之间来回切换。
 *
 * 原版里哭泣的黑曜石无法合成, 只能靠废墟/传送门残骸/猪灵交易获得; 这条词条把它变成
 * 可自由转换的方块 (冲重生锚用)。只认主手镐, 其他物品与方块一律 PASS 交还原版。
 */
object OnionCutService {

    const val PERK_ID = "delver_onion_cut"

    fun handleUse(player: ServerPlayer, world: ServerLevel, pos: BlockPos): InteractionResult {
        if (!PerkSession.isEnabled()) return InteractionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return InteractionResult.PASS
        if (!state.hasPerk(PERK_ID)) return InteractionResult.PASS
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).`is`(ItemTags.PICKAXES)) return InteractionResult.PASS

        val target = when {
            world.getBlockState(pos).`is`(Blocks.OBSIDIAN) -> Blocks.CRYING_OBSIDIAN
            world.getBlockState(pos).`is`(Blocks.CRYING_OBSIDIAN) -> Blocks.OBSIDIAN
            else -> return InteractionResult.PASS
        }

        world.setBlock(pos, target.defaultBlockState(), Block.UPDATE_ALL)
        world.playSound(
            null, pos,
            SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
            SoundSource.BLOCKS,
            0.7f, 1.2f,
        )
        return InteractionResult.SUCCESS
    }
}
