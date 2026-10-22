package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkSession
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.state.PerkStateManager
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * 唤法 SUMMON —— 召唤 / 随从 / 驭兽。
 */
object SummonPerks {

    /** 金 · 唤法: **驯龙高手** —— 右键任意生物即可骑乘 (玩家与 Boss 除外);
     *  骑乘时用 WASD 控制移动, 右键让坐骑使用它的能力 (远程攻击 / 近战 / 火球等),
     *  潜行下马。由 [com.randomperks.game.DragonTamerService] 实现。 */
    object DragonTamer : PerkDefinition {
        override val id = "summon_dragon_tamer"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.DRAGON_HEAD)

        override fun onAcquire(ctx: PerkContext) {
            ctx.player.sendMessage(
                Text.translatable("random-perks-bingo.message.dragon_tamer_hint"),
                false,
            )
        }

        /** 对局结束/词条被清掉时别把人留在坐骑上 */
        override fun onRemove(ctx: PerkContext) {
            if (ctx.player.hasVehicle()) ctx.player.stopRiding()
        }
    }

    /** 蓝 · 唤法 · 贮藏: **远程传输** —— 点击使用并选择一名**同队**玩家,
     *  随机把物品栏里的 4 件物品传送给他 (交互见 ActionTargetPick 的 remote_transfer 分支) */
    object RemoteTransfer : PerkDefinition {
        override val id = "summon_remote_transfer"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.ENDER_CHEST)

        override val isOneTime = true

        /** 主背包格数 (快捷栏 9 + 主背包 27) */
        private const val SLOT_COUNT = 36

        /** 传送的物品数量 (按格计算) */
        private const val TRANSFER_COUNT = 4

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.remote_transfer_no_target"),
                    true,
                )
                return
            }
            if (itemSlots(player).isEmpty()) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.remote_transfer_empty"),
                    true,
                )
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "remote_transfer",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名队友 → 随机抽 4 件物品传送过去 */
        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            // 校验: 必须是当下的合法候选, 防止客户端伪造 uuid 传给队外玩家
            val target = candidates(player).firstOrNull { it.uuid == uuid }
            if (target == null) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.remote_transfer_no_target"),
                    true,
                )
                return
            }

            val slots = itemSlots(player)
            if (slots.isEmpty()) return

            val taken = mutableListOf<ItemStack>()
            repeat(minOf(TRANSFER_COUNT, slots.size)) {
                val slot = slots.removeAt(player.random.nextInt(slots.size))
                val stack = player.inventory.removeStack(slot)
                if (!stack.isEmpty) taken += stack
            }
            if (taken.isEmpty()) return

            // 目标背包放不下时 offerOrDrop 会掉在他脚下, 不会蒸发
            for (stack in taken) PerkEffects.giveStack(target, stack)

            state.markUsed(id)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)

            player.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.remote_transfer_success",
                    taken.size,
                    target.name,
                ),
                false,
            )
            target.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.remote_transfer_received",
                    player.name,
                    taken.size,
                ),
                false,
            )
        }

        /** 主背包里所有非空格子 */
        private fun itemSlots(player: ServerPlayerEntity): MutableList<Int> {
            val slots = mutableListOf<Int>()
            for (slot in 0 until SLOT_COUNT) {
                if (!player.inventory.getStack(slot).isEmpty) slots += slot
            }
            return slots
        }

        /** 候选: 与自己在同一支原版队伍里的其他在线玩家 (无队伍时没有候选) */
        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> {
            val myTeam = player.scoreboardTeam?.name ?: return emptyList()
            return player.server?.playerManager?.playerList?.filter {
                it.uuid != player.uuid &&
                    !it.isSpectator &&
                    it.scoreboardTeam?.name == myTeam
            } ?: emptyList()
        }
    }

    fun registerAll() {
        PerkRegistry.register(DragonTamer)
        PerkRegistry.register(RemoteTransfer)
    }
}
