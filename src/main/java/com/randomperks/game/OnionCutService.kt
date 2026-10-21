package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.registry.tag.ItemTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

/**
 * "切洋葱" —— 手持**镐**右键黑曜石 / 哭泣的黑曜石, 在两者之间来回切换。
 *
 * 原版里哭泣的黑曜石无法合成, 只能靠废墟/传送门残骸/猪灵交易获得; 这条词条把它变成
 * 可自由转换的方块 (冲重生锚用)。只认主手镐, 其他物品与方块一律 PASS 交还原版。
 */
object OnionCutService {

    const val PERK_ID = "delver_onion_cut"

    fun handleUse(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS
        if (!player.getStackInHand(Hand.MAIN_HAND).isIn(ItemTags.PICKAXES)) return ActionResult.PASS

        val target = when {
            world.getBlockState(pos).isOf(Blocks.OBSIDIAN) -> Blocks.CRYING_OBSIDIAN
            world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN) -> Blocks.OBSIDIAN
            else -> return ActionResult.PASS
        }

        world.setBlockState(pos, target.defaultState, Block.NOTIFY_ALL)
        world.playSound(
            null, pos,
            SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN,
            SoundCategory.BLOCKS,
            0.7f, 1.2f,
        )
        return ActionResult.SUCCESS
    }
}
