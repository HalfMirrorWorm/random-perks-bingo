package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkSession
import com.randomperks.game.SwapService
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.state.PerkStateManager
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

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
     *  然后从自己的物品栏里挑选 4 格物品传送给他
     *  (交互见 ActionTargetPick / ItemSelect 的 remote_transfer 分支) */
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

        private const val PURPOSE = "remote_transfer"

        /** 已选中的队友 uuid, 等待玩家挑选物品 */
        private const val TARGET_KEY = "remote_transfer_target"

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
                PURPOSE,
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端选中队友 → 记住目标, 打开物品选择界面 */
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

            if (itemSlots(player).isEmpty()) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.remote_transfer_empty"),
                    true,
                )
                return
            }

            state.memory[TARGET_KEY] = target.uuid.toString()
            val items = (0 until SLOT_COUNT).map { player.inventory.getStack(it).copy() }
            PerkNetworking.sendItemSelectOpen(player, PURPOSE, items, TRANSFER_COUNT)
        }

        /** 客户端提交选中的物品格 → 传送过去 */
        fun handleItemPick(player: ServerPlayerEntity, slots: List<Int>) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            val targetUuid = state.memory[TARGET_KEY]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return
            val target = candidates(player).firstOrNull { it.uuid == targetUuid }
            if (target == null) {
                state.memory.remove(TARGET_KEY)
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.remote_transfer_no_target"),
                    true,
                )
                return
            }

            // 校验: 选中的格数必须等于"最多 4 格且不超过现有非空格数", 且互不重复、都非空
            val nonEmpty = (0 until SLOT_COUNT).count { !player.inventory.getStack(it).isEmpty }
            val required = minOf(TRANSFER_COUNT, nonEmpty)
            val distinct = slots.toSet()
            if (required == 0 || slots.size != required || distinct.size != slots.size) return
            if (distinct.any { it !in 0 until SLOT_COUNT || player.inventory.getStack(it).isEmpty }) return

            val taken = mutableListOf<ItemStack>()
            for (slot in distinct) {
                val stack = player.inventory.removeStack(slot)
                if (!stack.isEmpty) taken += stack
            }
            if (taken.isEmpty()) return

            // 在插入背包前把物品名抄下来 —— insertStack 会把 stack 的数量清零
            val itemSummary = summarize(taken)

            // 目标背包放不下时, 剩余部分显式掉在他脚下 —— 不允许静默吞掉
            for (stack in taken) {
                val remaining = stack.copy()
                target.inventory.insertStack(remaining)
                if (!remaining.isEmpty) {
                    target.dropItem(remaining, false)
                }
            }
            target.inventory.markDirty()

            state.markUsed(id)
            state.memory.remove(TARGET_KEY)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)

            player.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.remote_transfer_success",
                    itemSummary,
                    target.name,
                ),
                false,
            )
            target.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.remote_transfer_received",
                    player.name,
                    itemSummary,
                ),
                false,
            )
        }

        /** 把物品列表拼成 "钻石 ×4, 面包 ×16" 这样的提示文本 */
        private fun summarize(stacks: List<ItemStack>): Text {
            var text = Text.empty()
            stacks.forEachIndexed { index, stack ->
                if (index > 0) text = text.append(Text.literal(", "))
                text = text.append(stack.name.copy())
                if (stack.count > 1) text = text.append(Text.literal(" ×${stack.count}"))
            }
            return text
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

    /** 紫 · 唤法: **何易位** —— 在 V 界面点击使用后连续选择两名玩家 (可以选自己), 交换他们的位置。
     *  允许跨维度: 两人各自传送到对方所在维度的坐标。 */
    object Swap : PerkDefinition {
        override val id = "summon_swap"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.ENDER_PEARL)

        override val isOneTime = true

        private const val PURPOSE = "swap"

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            if (SwapService.hasPending(player.uuid)) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.swap_in_progress"),
                    true,
                )
                return
            }
            val candidates = player.server?.playerManager?.playerList
                ?.filter {
                    it.isAlive && !it.isSpectator &&
                        // "天启日"持有者免疫何易位, 直接不给选
                        !PerkBoostQuery.hasDoomsday(it)
                }
                ?.map { PlayerTargetEntry(it.uuid, it.name.string) }
                ?: emptyList()

            // 少于两人就没得选 —— 包括自己也算一个候选
            if (candidates.size < 2) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.swap_not_enough"), true)
                return
            }
            PerkNetworking.sendDualTargets(player, PURPOSE, candidates)
        }

        /** 客户端两次选择都完成后调用: 交换两人的位置 (朝向各自保留) */
        fun handlePick(player: ServerPlayerEntity, first: UUID, second: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return
            if (first == second) return

            val server = player.server ?: return
            val a = server.playerManager.getPlayer(first) ?: return
            val b = server.playerManager.getPlayer(second) ?: return
            if (a === b) return

            // "天启日"持有者免疫何易位 (候选已过滤, 这里兜底防止 uuid 被伪造)
            if (PerkBoostQuery.hasDoomsday(a) || PerkBoostQuery.hasDoomsday(b)) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.swap_doomsday_immune"),
                    true,
                )
                return
            }

            // 10 秒倒计时后由 SwapService 结算; 成功启动才真正消耗词条 (结算时标记)
            if (!SwapService.start(player, a, b)) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.swap_in_progress"),
                    true,
                )
            }
        }
    }

    /** 蓝 · 唤法 · 贮藏: **失眠** —— 点击使用并选择一名玩家, 在他附近生成 3 只幻翼 */
    object Insomnia : PerkDefinition {
        override val id = "summon_insomnia"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.PHANTOM_MEMBRANE)

        override val isOneTime = true

        private const val PHANTOM_COUNT = 3
        private const val PURPOSE = "phantoms"

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.phantoms_no_target"),
                    true,
                )
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                PURPOSE,
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return
            val world = target.world as? ServerWorld ?: return

            var spawned = 0
            for (index in 0 until PHANTOM_COUNT) {
                val phantom = EntityType.PHANTOM.create(world, SpawnReason.COMMAND) ?: continue

                // 在目标周围 4-8 格、上方 3-7 格找一个空气位置
                var placed = false
                for (attempt in 0 until 16) {
                    val angle = world.random.nextDouble() * Math.PI * 2
                    val radius = 4.0 + world.random.nextDouble() * 4.0
                    val x = target.x + cos(angle) * radius
                    val z = target.z + sin(angle) * radius
                    val y = target.y + 3.0 + world.random.nextDouble() * 4.0
                    val pos = BlockPos.ofFloored(x, y, z)
                    if (!world.isAir(pos)) continue

                    phantom.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360f, 0f)
                    placed = true
                    break
                }
                if (!placed) {
                    phantom.refreshPositionAndAngles(
                        target.x, target.y + 6.0, target.z,
                        world.random.nextFloat() * 360f, 0f,
                    )
                }

                phantom.setPhantomSize(1)
                phantom.setTarget(target)
                world.spawnEntity(phantom)
                spawned++
            }

            if (spawned <= 0) return

            state.markUsed(id)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)

            player.sendMessage(
                Text.translatable("random-perks-bingo.message.insomnia_spawned", target.name),
                false,
            )
            target.sendMessage(
                Text.translatable("random-perks-bingo.message.insomnia_target", player.name),
                true,
            )
        }

        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> =
            player.server?.playerManager?.playerList?.filter { !it.isSpectator } ?: emptyList()
    }

    /** 金 · 唤法: **Boss盲盒** —— 随机获得一个 Boss 刷怪蛋 */
    object BossEgg : PerkDefinition {
        override val id = "summon_boss_egg"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.ENDER_DRAGON_SPAWN_EGG)

        private val BOSS_EGGS = listOf(
            Items.ENDER_DRAGON_SPAWN_EGG,
            Items.WITHER_SPAWN_EGG,
            Items.WARDEN_SPAWN_EGG,
            Items.ELDER_GUARDIAN_SPAWN_EGG,
        )

        override fun onAcquire(ctx: PerkContext) {
            val egg = BOSS_EGGS[ctx.player.random.nextInt(BOSS_EGGS.size)]
            PerkEffects.giveStack(ctx.player, ItemStack(egg))
        }
    }

    fun registerAll() {
        PerkRegistry.register(DragonTamer)
        PerkRegistry.register(Insomnia)
        PerkRegistry.register(RemoteTransfer)
        PerkRegistry.register(Swap)
        PerkRegistry.register(BossEgg)
    }
}
