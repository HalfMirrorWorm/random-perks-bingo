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
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * 神话品质词条 (全局唯一: 同时只能被一名玩家拥有)。
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
    }

    /** 神话 · 猎杀 · 全局唯一: **天启日** —— 效果持续 20 分钟:
     *  - 期间被你击杀的前 4 名玩家强制掉落**背包位**物品 (无视 keepInventory);
     *  - 你被敌方玩家击杀时也会强制掉落背包位物品, 随后效果立即结束。
     *  (由 DoomsdayMixin 在 ServerPlayerEntity.onDeath 实现) */
    object Doomsday : PerkDefinition {
        override val id = "mythic_doomsday"
        override val rarity = PerkRarity.MYTHIC
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.WITHER_ROSE)

        override fun onAcquire(ctx: PerkContext) {
            PerkBoostQuery.beginDoomsday(ctx.player)
        }
    }

    /** 神话 · 唤法 · 全局唯一: **游戏BUG** —— 获得时立即弹出玩家选择器, 选中一名玩家后,
     *  他背包里的**每一种物品各自随机变成另一种物品** (数量不变)
     *  (神话词条不允许贮藏到 V 界面, 必须获得瞬间生效) */
    object Hodgepodge : PerkDefinition {
        override val id = "mythic_hodgepodge"
        override val rarity = PerkRarity.MYTHIC
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.BUNDLE)

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.hodgepodge_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "hodgepodge",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 把他背包里的每种物品各自换掉 */
        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return
            val registry = target.server?.registryManager?.getOrThrow(RegistryKeys.ITEM) ?: return

            var changed = 0
            val main = target.inventory.mainStacks
            for (index in 0 until main.size) {
                val stack = main.get(index)
                if (stack.isEmpty) continue
                val replacement = randomItem(registry, target.random) ?: continue
                main.set(index, ItemStack(replacement, stack.count))
                changed++
            }
            state.markUsed(id)

            player.sendMessage(
                Text.translatable("random-perks-bingo.message.hodgepodge_applied", target.name, changed),
                false,
            )
            target.sendMessage(
                Text.translatable("random-perks-bingo.message.hodgepodge_received"),
                false,
            )
        }

        /** 从物品注册表里随机挑一个非空气物品 (getRandom 有小概率给到 AIR, 重试几次) */
        private fun randomItem(
            registry: Registry<Item>,
            random: net.minecraft.util.math.random.Random,
        ): Item? {
            repeat(16) {
                val entry = registry.getRandom(random).orElse(null) ?: return null
                val item = entry.value()
                if (item != Items.AIR) return item
            }
            return null
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> =
            player.server?.playerManager?.playerList?.filter { !it.isSpectator } ?: emptyList()
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
