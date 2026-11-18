package com.randomperks.game

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.level.block.Block

/**
 * "核电轻而易举" 的爆炸结算: 召唤出的闪电苦力怕炸掉的方块会变成随机矿石。
 *
 * <p>只有被本词条召唤、带 [CREEPER_TAG] 的苦力怕会触发;
 * 由 [NukeExplosionMixin] 在 {@code ServerExplosion.explode} 结束后按位置列表补矿。
 */
object NukeExplosionService {

    /** 召唤出的闪电苦力怕的标记 (原版实体 scoreboard tag, 随实体保存) */
    const val CREEPER_TAG = "randomperks_nuke_creeper"

    fun mark(creeper: Creeper) {
        creeper.addTag(CREEPER_TAG)
    }

    fun isMarked(entity: Entity?): Boolean =
        entity is Creeper && entity.entityTags().contains(CREEPER_TAG)

    /**
     * 把被爆炸炸成空气的格子填成随机矿石。
     * 抗爆方块 (仍然是原方块)、火/流体、其他爆炸后留下的方块都不动。
     */
    fun fillOres(level: ServerLevel, positions: List<BlockPos>) {
        val ores = ORE_BLOCKS
        if (ores.isEmpty()) return
        for (pos in positions) {
            if (!level.getBlockState(pos).isAir) continue
            val ore = ores[level.random.nextInt(ores.size)]
            level.setBlockAndUpdate(pos, ore.defaultBlockState())
        }
    }

    /** 原版 {@code #minecraft:ores} 标签里的全部矿石方块 (含深板岩变种与下界石英) */
    private val ORE_BLOCKS: List<Block> by lazy {
        val list = mutableListOf<Block>()
        for (block in BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().`is`(BlockTags.ORES)) list.add(block)
        }
        list
    }
}
