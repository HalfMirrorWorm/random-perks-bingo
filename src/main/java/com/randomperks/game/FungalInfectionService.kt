package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "真菌感染" ——
 * - 攻击**敌方玩家**时, 随机把他主背包里的 **2 件物品**变成繁茂洞穴物品;
 * - 另外, 持有者自己**每 1 分 45 秒**会有 1 格背包物品 (不含快捷栏) 变成繁茂洞穴物品。
 *
 * - 只动主背包 (0..35), 不碰盔甲与副手, 也不动空槽;
 * - 同队玩家不触发 (未分队时视为敌方);
 * - 每次成功感染后 5 秒冷却, 背包为空时不消耗冷却。
 */
object FungalInfectionService {

    const val PERK_ID = "harvest_fungal_infection"

    /** 每次成功感染后的冷却 */
    private const val COOLDOWN_TICKS = 5 * 20L

    /** 每次转化的物品数量 */
    private const val SLOT_COUNT = 2

    /** 主背包槽位数 (0..35; 36..40 是盔甲与副手) */
    private const val MAIN_INVENTORY_SIZE = 36

    /** 快捷栏槽位数 (自身感染时不包含 0..8) */
    private const val HOTBAR_SIZE = 9

    /** 繁茂洞穴物品池 */
    private val LUSH_ITEMS: List<Item> = listOf(
        Items.GLOW_BERRIES,
        Items.MOSS_BLOCK,
        Items.MOSS_CARPET,
        Items.AZALEA,
        Items.FLOWERING_AZALEA,
        Items.AZALEA_LEAVES,
        Items.FLOWERING_AZALEA_LEAVES,
        Items.ROOTED_DIRT,
        Items.HANGING_ROOTS,
        Items.SPORE_BLOSSOM,
        Items.SMALL_DRIPLEAF,
        Items.BIG_DRIPLEAF,
        Items.GLOW_LICHEN,
    )

    fun handleAttack(attacker: ServerPlayerEntity, victim: ServerPlayerEntity) {
        if (!PerkSession.isEnabled()) return
        if (attacker.uuid == victim.uuid) return
        if (!PerkBoostQuery.hasFungalInfection(attacker)) return
        if (victim.isSpectator) return

        // 同队不感染 (未分队时视为敌方)
        val team = attacker.scoreboardTeam?.name
        if (team != null && victim.scoreboardTeam?.name == team) return

        val state = PerkStateManager.get(attacker.uuid) ?: return
        val now = attacker.server?.ticks?.toLong() ?: return
        if (now < (state.cooldowns[PERK_ID] ?: 0L)) return

        if (infect(victim)) {
            state.cooldowns[PERK_ID] = now + COOLDOWN_TICKS
        }
    }

    /** 自身感染: 把背包里 (不含快捷栏) 随机 1 格物品变成繁茂洞穴物品 */
    fun corruptOwnBackpack(player: ServerPlayerEntity): Boolean {
        if (!PerkSession.isEnabled()) return false

        val inventory = player.inventory
        val candidates = (HOTBAR_SIZE until MAIN_INVENTORY_SIZE)
            .filter { !inventory.getStack(it).isEmpty }
        if (candidates.isEmpty()) return false

        val slot = candidates[player.random.nextInt(candidates.size)]
        val original = inventory.getStack(slot)
        val replacement = LUSH_ITEMS[player.random.nextInt(LUSH_ITEMS.size)]
        inventory.setStack(slot, ItemStack(replacement, original.count))
        inventory.markDirty()
        return true
    }

    private fun infect(victim: ServerPlayerEntity): Boolean {
        val inventory = victim.inventory
        val candidates = (0 until MAIN_INVENTORY_SIZE)
            .filter { !inventory.getStack(it).isEmpty }
            .toMutableList()
        if (candidates.isEmpty()) return false

        repeat(minOf(SLOT_COUNT, candidates.size)) {
            val slot = candidates.removeAt(victim.random.nextInt(candidates.size))
            val original = inventory.getStack(slot)
            val replacement = LUSH_ITEMS[victim.random.nextInt(LUSH_ITEMS.size)]
            // 保持原堆叠数量 (数量本来就被原版限制在物品上限内)
            inventory.setStack(slot, ItemStack(replacement, original.count))
        }
        inventory.markDirty()
        return true
    }
}
