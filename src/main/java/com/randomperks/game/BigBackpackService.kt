package com.randomperks.game

import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Prediction
import net.minecraft.world.item.ItemStack

/**
 * "大背包" 的堆叠校准。
 *
 * <p>词条生效时玩家背包允许 4 倍堆叠 (最多 256)；词条被移除、或这一局已经结束
 * （玩家离线时 `clearAllPerkState` 会跳过 `onRemove`）时，把超过物品原版上限的
 * 堆叠拆回合法数量，避免 >64 的堆叠留到没有该词条的下一局。
 */
object BigBackpackService {

    /** 把玩家背包里所有超过物品上限的堆叠拆开，拆出来的优先进空位，放不下就掉在脚下 */
    fun normalize(player: ServerPlayer) {
        val inventory = player.inventory
        val excess = mutableListOf<ItemStack>()

        // 遍历全部槽位: 副手槽 (40) 也能放可堆叠物品, 只扫 0~35 会漏掉它
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (stack.isEmpty) continue
            val max = stack.maxStackSize
            if (stack.count <= max) continue
            val extra = stack.copy()
            extra.count = stack.count - max
            stack.count = max
            excess += extra
        }
        if (excess.isEmpty()) return

        for (extra in excess) {
            var remaining = extra
            // 只往主背包/快捷栏放 (0~35): setItem 不走槽位校验, 不能把物品塞进护甲槽
            for (slot in 0 until 36) {
                if (remaining.isEmpty) break
                val existing = inventory.getItem(slot)
                if (existing.isEmpty) {
                    inventory.setItem(slot, remaining.copy())
                    remaining = ItemStack.EMPTY
                } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                    val room = existing.maxStackSize - existing.count
                    if (room > 0) {
                        val move = minOf(room, remaining.count)
                        existing.count += move
                        remaining.count -= move
                    }
                }
            }
            if (!remaining.isEmpty) {
                player.drop(remaining, false, Prediction.SERVER_ONLY)
            }
        }

        inventory.setChanged()
        player.inventoryMenu.sendAllDataToRemote()
    }
}
