package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.PerkSession
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import com.randomperks.state.PerkStateManager
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * 匠造 ARTISAN —— 工具 / 建造 / 功能性物品。
 */
object ArtisanPerks {

    /** 蓝 · 战地补给: 16 面包 + 水桶 */
    object Supply : PerkDefinition {
        override val id = "artisan_supply"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.BREAD)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BREAD, 16))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WATER_BUCKET))
        }
    }

    /** 蓝 · 工具保养: 获得一把效率 II 的铁镐 (耐久只剩 24 点, 挖几下就报废) */
    object Toolkit : PerkDefinition {
        override val id = "artisan_toolkit"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onAcquire(ctx: PerkContext) {
            val pickaxe = ItemStack(Items.IRON_PICKAXE)
            PerkEffects.enchant(ctx.player, pickaxe, Enchantments.EFFICIENCY, 2)
            // 只剩 24 点耐久: ItemStack 只存"已损耗值"(damage), 所以要写成 上限 - 剩余
            // (铁镐上限 250 → 已损耗 226; 铁砧/经验修补修一下即可恢复满耐久)
            pickaxe.damage = pickaxe.maxDamage - REMAINING_DURABILITY
            PerkEffects.giveStack(ctx.player, pickaxe)
        }

        /** 发放的铁镐剩余耐久 */
        private const val REMAINING_DURABILITY = 24
    }

    /** 蓝 · 建筑师: 工作台 + 16 原木 + 建材 + 火把 */
    object Builder : PerkDefinition {
        override val id = "artisan_builder"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.CRAFTING_TABLE)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CRAFTING_TABLE))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.OAK_LOG, 16))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.COBBLESTONE, 32))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.TORCH, 16))
        }
    }

    /** 蓝 · 附魔学徒: 获得一本随机实用附魔书 */
    /** 蓝 · 附魔学徒: 立即获得一本随机实用附魔书, 之后每 5 分钟再获得一本 */
    object Enchanter : PerkDefinition {
        override val id = "artisan_enchanter"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.ENCHANTED_BOOK)

        private const val COOLDOWN_TICKS = 5 * 60 * 20

        override fun onAcquire(ctx: PerkContext) {
            grant(ctx)
            ctx.state.cooldowns[id] = (now(ctx) + COOLDOWN_TICKS)
        }

        override fun onTick(ctx: PerkContext) {
            val current = now(ctx)
            val next = ctx.state.cooldowns[id] ?: 0L
            if (current < next) return
            ctx.state.cooldowns[id] = current + COOLDOWN_TICKS
            grant(ctx)
        }

        private fun now(ctx: PerkContext): Long = ctx.player.server?.ticks?.toLong() ?: 0L

        /** 随机发一本实用附魔书 (效率 III / 耐久 III / 时运 II / 保护 III) */
        private fun grant(ctx: PerkContext) {
            val book = ItemStack(Items.ENCHANTED_BOOK)
            when (ctx.player.random.nextInt(4)) {
                0 -> PerkEffects.enchant(ctx.player, book, Enchantments.EFFICIENCY, 3)
                1 -> PerkEffects.enchant(ctx.player, book, Enchantments.UNBREAKING, 3)
                2 -> PerkEffects.enchant(ctx.player, book, Enchantments.FORTUNE, 2)
                else -> PerkEffects.enchant(ctx.player, book, Enchantments.PROTECTION, 3)
            }
            PerkEffects.giveStack(ctx.player, book)
        }
    }

    /** 金 · 匠造: 手持时钟右键 —— 首次设置传送点 (**设置后不可重设**), 之后右键传送回去 (10 分钟冷却, 不限距离) */
    object WaypointClock : PerkDefinition {
        override val id = "artisan_waypoint_clock"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.CLOCK)

        /** 赠送一个时钟作为媒介 (右键逻辑由 WaypointService / UseItemCallback 处理) */
        override fun onAcquire(ctx: PerkContext) =
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CLOCK))
    }

    /** 紫 · 匠造: 方块与实体交互距离 +4 格
     *  (注: `ENTITY_INTERACTION_RANGE` 同时决定近战攻击有效距离 —— 3.0 → 7.0, 所以按紫档定位) */
    object LongArms : PerkDefinition {
        override val id = "artisan_long_arms"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.ARMOR_STAND)

        private val MOD_BLOCK = Identifier.of("random-perks-bingo", "long_arms_block")
        private val MOD_ENTITY = Identifier.of("random-perks-bingo", "long_arms_entity")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) {
            PerkEffects.setAttribute(ctx.player, EntityAttributes.BLOCK_INTERACTION_RANGE, MOD_BLOCK, 4.0)
            PerkEffects.setAttribute(ctx.player, EntityAttributes.ENTITY_INTERACTION_RANGE, MOD_ENTITY, 4.0)
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.BLOCK_INTERACTION_RANGE, MOD_BLOCK)
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.ENTITY_INTERACTION_RANGE, MOD_ENTITY)
        }
    }

    /** 蓝 · 匠造: 获得高炉、烟熏炉与营火各一个 */
    object FireTemper : PerkDefinition {
        override val id = "artisan_fire_temper"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.BLAST_FURNACE)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BLAST_FURNACE))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SMOKER))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CAMPFIRE))
        }
    }

    /** 紫 · 匠造: **绝命毒师** —— 获得时弹出玩家选择器, 选中一名玩家后给他施加
     *  **随机一种负面效果** (等级 I, 持续 30 秒) */
    object Poisoner : PerkDefinition {
        override val id = "artisan_poisoner"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.FERMENTED_SPIDER_EYE)

        /** 负面效果持续 30 秒 */
        private const val DURATION_TICKS = 30 * 20

        /** 随机负面效果池 (等级 I) */
        private val DEBUFFS = listOf(
            StatusEffects.SLOWNESS,
            StatusEffects.MINING_FATIGUE,
            StatusEffects.NAUSEA,
            StatusEffects.BLINDNESS,
            StatusEffects.HUNGER,
            StatusEffects.WEAKNESS,
            StatusEffects.POISON,
            StatusEffects.WITHER,
            StatusEffects.LEVITATION,
            StatusEffects.DARKNESS,
            StatusEffects.UNLUCK,
        )

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.poisoner_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "poisoner",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 给他灌一瓶随机毒药 */
        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return

            val effect = DEBUFFS[target.random.nextInt(DEBUFFS.size)]
            target.addStatusEffect(StatusEffectInstance(effect, DURATION_TICKS, 0))

            val effectName = Text.translatable(effect.value().translationKey)
            player.sendMessage(
                Text.translatable("random-perks-bingo.message.poisoner_applied", target.name, effectName),
                false,
            )
            target.sendMessage(
                Text.translatable("random-perks-bingo.message.poisoner_received", effectName),
                true,
            )
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> =
            player.server?.playerManager?.playerList?.filter { !it.isSpectator } ?: emptyList()
    }

    fun registerAll() {
        PerkRegistry.register(Supply)
        PerkRegistry.register(Toolkit)
        PerkRegistry.register(Builder)
        PerkRegistry.register(Enchanter)
        PerkRegistry.register(WaypointClock)
        PerkRegistry.register(LongArms)
        PerkRegistry.register(FireTemper)
        PerkRegistry.register(Poisoner)
    }
}
