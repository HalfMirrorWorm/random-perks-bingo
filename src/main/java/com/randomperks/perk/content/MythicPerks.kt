package com.randomperks.perk.content

import com.randomperks.game.CreativeFlashService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.PerkSession
import com.randomperks.game.RewindService
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import com.randomperks.state.PerkStateManager
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * 神话 (全局唯一: 同时只能被一名玩家拥有) 与红色 (禁忌, 不唯一) 词条。
 *
 * 注: "天启日" / "游戏BUG" 的 id 仍带 `mythic_` 前缀 —— 是为了兼容旧存档与外部引用,
 * 它们的稀有度已经改为红色。
 */
object MythicPerks {

    /** 神话 · 匠造 · 全局唯一: 获得 1.5 秒创造模式 */
    object CreativeFlash : PerkDefinition {
        override val id = "mythic_creative_flash"
        override val rarity = PerkRarity.MYTHIC
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.COMMAND_BLOCK)

        override fun onAcquire(ctx: PerkContext) {
            CreativeFlashService.flash(ctx.player)
        }

        /** 对局结束 / 命令移除时不等计时器, 立刻把模式还回去 */
        override fun onRemove(ctx: PerkContext) {
            CreativeFlashService.cancel(ctx.player.uuid)
        }
    }

    /** 红 · 猎杀: **天启日** —— 效果持续 20 分钟:
     *  - 期间被你击杀的前 4 名玩家强制掉落**背包位**物品 (无视 keepInventory);
     *  - 你被敌方玩家击杀时也会强制掉落背包位物品, 随后效果立即结束。
     *  (由 DoomsdayMixin 在 ServerPlayerEntity.onDeath 实现) */
    object Doomsday : PerkDefinition {
        override val id = "mythic_doomsday"
        override val rarity = PerkRarity.RED
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.WITHER_ROSE)

        override fun onAcquire(ctx: PerkContext) {
            PerkBoostQuery.beginDoomsday(ctx.player)
        }

        /** 剩余时长每 20 tick 扣一次 (不再用绝对 tickCount, 重启/回档后也不会算错) */
        override fun onTick(ctx: PerkContext) {
            PerkBoostQuery.tickDoomsday(ctx.player)
        }
    }

    /** 红 · 唤法: **游戏BUG** —— 获得时立即弹出玩家选择器, 选中一名玩家后,
     *  他背包里的**每一种物品各自随机变成另一种物品** (数量不变)
     *  (获得瞬间弹出选择器, 不进入 V 界面) */
    object Hodgepodge : PerkDefinition {
        override val id = "mythic_hodgepodge"
        override val rarity = PerkRarity.RED
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.BUNDLE)

        /** 贮藏标记: 选择器被关掉之后, 可以再从 V 界面点一次 */
        override val isOneTime = true

        override fun onAcquire(ctx: PerkContext) {
            openPicker(ctx.player)
        }

        /** 选择器被 Esc 关掉、或者发包失败时, 玩家可以再从 V 界面点一次 */
        override fun onActivate(ctx: PerkContext) {
            openPicker(ctx.player)
        }

        private fun openPicker(player: ServerPlayer) {
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendSystemMessage(Component.translatable("random-perks-bingo.message.hodgepodge_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "hodgepodge",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 把他背包里的每种物品各自换掉 */
        fun handleTargetPick(player: ServerPlayer, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return
            val registry = target.level().getServer().registryAccess().lookupOrThrow(Registries.ITEM) ?: return

            var changed = 0
            val main = target.inventory.nonEquipmentItems
            for (index in 0 until main.size) {
                val stack = main.get(index)
                if (stack.isEmpty) continue
                val replacement = randomItem(registry, target.random, stack.count, stack.item) ?: continue

                // 换成堆叠上限更低的物品时不能直接照抄数量 (64 个圆石 → 64 把剑是非法堆叠),
                // 放得下的放进原格, 多出来的按上限分堆掉在脚下, 不允许凭空消失
                val max = ItemStack(replacement).maxStackSize
                val newCount = minOf(stack.count, max)
                main.set(index, ItemStack(replacement, newCount))
                var excess = stack.count - newCount
                while (excess > 0) {
                    val chunk = minOf(excess, max)
                    target.spawnAtLocation(target.level() as ServerLevel, ItemStack(replacement, chunk))
                    excess -= chunk
                }
                changed++
            }
            state.markUsed(id)

            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.hodgepodge_applied", target.name, changed),
                false,
            )
            target.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.hodgepodge_received"),
                false,
            )
        }

        /**
         * 从物品注册表里随机挑一个非空气物品:
         * - 排除原来那个物品, 否则"变成另一种物品"可能什么都没变
         * - 优先挑堆叠上限放得下当前数量的, 减少不必要的拆分掉落
         */
        private fun randomItem(
            registry: Registry<Item>,
            random: net.minecraft.util.RandomSource,
            count: Int,
            avoid: Item,
        ): Item? {
            val need = minOf(count, 64)
            var fallback: Item? = null
            repeat(32) {
                val entry = registry.getRandom(random).orElse(null) ?: return fallback
                val item = entry.value()
                if (item == Items.AIR || item == avoid) return@repeat
                if (ItemStack(item).maxStackSize >= need) return item
                if (fallback == null) fallback = item
            }
            return fallback
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayer): List<ServerPlayer> =
            player.level().getServer().playerList.players.filter { !it.isSpectator }
    }

    /** 神话 · 秘藏 · 全局唯一: **逆转时间** —— 获得时立即触发,
     *  本局回到最近一次检查点 (开局与每 30 分钟各记录一次, 最多回退 30 分钟):
     *  玩家状态/背包/位置、容器物品、地形、重要生物、Bingo 进度与词条一起回退。
     *  新生成地形、已死亡的重要生物与离线玩家不还原 (伪回溯)。 */
    object TimeRewind : PerkDefinition {
        override val id = "mythic_time_rewind"
        override val rarity = PerkRarity.MYTHIC
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.CLOCK)

        override fun onAcquire(ctx: PerkContext) {
            RewindService.begin(ctx.player, id)
        }
    }

    fun registerAll() {
        PerkRegistry.register(CreativeFlash)
        PerkRegistry.register(Doomsday)
        PerkRegistry.register(Hodgepodge)
        PerkRegistry.register(TimeRewind)
    }
}
