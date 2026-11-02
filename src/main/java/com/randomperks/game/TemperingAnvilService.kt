package com.randomperks.game

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos

/**
 * "千锤百炼" 给的那把损坏的铁砧: **只能用一次**。
 *
 * 原版损坏的铁砧每次使用只有 12% 概率碎裂, 所以这里给物品打上自定义数据标记;
 * 放置时按坐标记录, 第一次取走铁砧产物后强制把方块打碎。
 * 未使用就被挖掉时把标记转移到掉落物上, 重新放下仍然只算一次。
 */
object TemperingAnvilService {

    const val MARKER_KEY = "randomperks_tempering_anvil"

    private val marked = mutableSetOf<GlobalPos>()

    /** 给词条发放的铁砧物品打标记 */
    fun markItem(stack: ItemStack) {
        val nbt = CompoundTag()
        nbt.putBoolean(MARKER_KEY, true)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
    }

    /** 物品是否带有"一次性铁砧"标记 */
    fun isMarked(stack: ItemStack): Boolean =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains(MARKER_KEY) == true

    /** 记录已放置的一次性铁砧 */
    fun markPlaced(world: ServerLevel, pos: BlockPos) {
        marked.add(GlobalPos.of(world.dimension(), pos.immutable()))
    }

    /** 消耗某个坐标的一次性标记 (返回是否是词条铁砧) */
    fun consume(world: ServerLevel, pos: BlockPos): Boolean =
        marked.remove(GlobalPos.of(world.dimension(), pos.immutable()))

    fun clear() = marked.clear()
}
