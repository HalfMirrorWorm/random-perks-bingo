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
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.EnchantmentTags
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

    /** 蓝 · 附魔学徒: 立即获得一本**满级 / 宝藏**附魔书, 之后每 6 分钟再获得一本 */
    object Enchanter : PerkDefinition {
        override val id = "artisan_enchanter"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.ENCHANTED_BOOK)

        private const val COOLDOWN_TICKS = 6 * 60 * 20

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

        /** 随机一本**满级**附魔书: 从整个附魔注册表里抽 (宝藏附魔也在其中), 一律按该附魔的
         *  {@code max_level} 发放。诅咒不算"满级或宝藏", 排除掉。 */
        private fun grant(ctx: PerkContext) {
            val registry = ctx.player.server?.registryManager?.getOrThrow(RegistryKeys.ENCHANTMENT) ?: return

            val candidates = registry.entrySet
                .map { registry.getEntry(it.value) }
                .filter { !it.isIn(EnchantmentTags.CURSE) }
            if (candidates.isEmpty()) return

            val entry = candidates[ctx.player.random.nextInt(candidates.size)]
            val book = ItemStack(Items.ENCHANTED_BOOK)
            book.addEnchantment(entry, entry.value().definition().maxLevel())
            PerkEffects.giveStack(ctx.player, book)
        }
    }

    /** 紫 · 匠造: **快速附魔** —— 获得时弹出装备选择器 (4 个护甲槽 + 副手), 给选中的那件装备
     *  随机附加一个**该装备吃得下的满级附魔**, 不消耗经验。
     *
     *  只生效一次 (获得词条那一刻), 所以"附在哪件装备上"是个一次性决策 —— 底材越好收益越大。 */
    object QuickEnchant : PerkDefinition {
        override val id = "artisan_quick_enchant"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.ANVIL)

        private const val PURPOSE = "quick_enchant"

        /** 可选的装备槽 —— 与客户端 EquipmentPickScreen 里那份保持一致 (这里还会再校验一次) */
        private val EQUIP_SLOTS = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND,
        )

        /** 物品栏格子的 key 前缀, 后面跟 PlayerInventory 的原始索引 (0-8 是快捷栏) */
        private const val INV_PREFIX = "inv:"

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            if (!hasAnyItem(player)) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.quick_enchant_no_item"), true)
                return
            }
            PerkNetworking.sendEquipmentPickOpen(player, PURPOSE)
        }

        /** 客户端在物品选择器里选中了一件东西 → 给它一个随机满级附魔 */
        fun handlePick(player: ServerPlayerEntity, key: String) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return

            // 客户端可以伪造 key, 所以只认自己这份白名单, 并当场把物品查出来
            val stack = stackAt(player, key) ?: return
            if (stack.isEmpty) return

            val registry = player.server?.registryManager?.getOrThrow(RegistryKeys.ENCHANTMENT) ?: return

            // 已有附魔既不能重复给, 也不能给和它互斥的 —— 原版的互斥组只允许留一个
            // (比如"锋利"和"亡灵杀手"), 硬塞进去会得到一个原版不可能出现的组合。
            val existing = EnchantmentHelper.getEnchantments(stack).enchantments
            val candidates = registry.entrySet
                .map { registry.getEntry(it.value) }
                .filter { it.value().isSupportedItem(stack) }
                .filter { !existing.contains(it) }
                .filter { candidate -> existing.all { Enchantment.canBeCombined(candidate, it) } }
            if (candidates.isEmpty()) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.quick_enchant_none"), true)
                return
            }

            val picked = candidates[player.random.nextInt(candidates.size)]
            val level = picked.value().definition().maxLevel()
            stack.addEnchantment(picked, level)

            // 改的是物品栏里已有的 stack (引用没变), 不会自动触发同步 —— 手动推一次
            player.playerScreenHandler.syncState()

            player.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.quick_enchant_done",
                    stack.name,
                    Enchantment.getName(picked, level),
                ),
                false,
            )
        }

        /** 身上有没有任何可以附魔的东西 (装备槽 + 整个物品栏) */
        private fun hasAnyItem(player: ServerPlayerEntity): Boolean {
            if (EQUIP_SLOTS.any { !player.getEquippedStack(it).isEmpty }) return true
            return (0 until player.inventory.size()).any { !player.inventory.getStack(it).isEmpty }
        }

        /** 把客户端回传的 key 解析成一件真实存在的物品; 越界 / 名字不认识一律返回 null */
        private fun stackAt(player: ServerPlayerEntity, key: String): ItemStack? {
            if (key.startsWith(INV_PREFIX)) {
                val index = key.removePrefix(INV_PREFIX).toIntOrNull() ?: return null
                if (index !in 0 until player.inventory.size()) return null
                return player.inventory.getStack(index)
            }
            val slot = EQUIP_SLOTS.firstOrNull { it.getName() == key } ?: return null
            return player.getEquippedStack(slot)
        }
    }

    /** 金 · 匠造: 手持时钟右键 —— 传送到**上次死亡地点** (5 分钟冷却, 跨维度也可)。
     *  死亡地点由 WaypointService 在 AFTER_DEATH 自动记录 (只记录持词条者, 再死一次覆盖旧点)。 */
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

    /** 紫 · 匠造: **百宝箱** —— 随机开出 **1~5 件**盔甲或工具 (材质上限钻石, 白板不附魔) */
    object TreasureChest : PerkDefinition {
        override val id = "artisan_treasure_chest"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.CHEST)

        /** 最少 / 最多开出的件数 */
        private const val MIN_COUNT = 1
        private const val MAX_COUNT = 5

        /** 可开出的装备池: 盔甲 (皮革→钻石) + 工具 (木→钻石) */
        private val POOL: List<Item> = listOf(
            // 盔甲
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            // 工具
            Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE,
            Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE,
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
            Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
        )

        override fun onAcquire(ctx: PerkContext) {
            val random = ctx.player.random
            val count = MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1)
            repeat(count) {
                PerkEffects.giveStack(ctx.player, ItemStack(POOL[random.nextInt(POOL.size)]))
            }
        }
    }

    /** 蓝 · 匠造: **随身仓库** —— 获得一个潜影盒 (27 格随身存储) */
    object ShulkerBox : PerkDefinition {
        override val id = "artisan_shulker_box"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.SHULKER_BOX)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SHULKER_BOX))
        }
    }

    /** 蓝 · 匠造: **千锤百炼** —— 获得一个损坏的铁砧 (放置后还能用 1 次)、熔炉、切石机 */
    object Tempering : PerkDefinition {
        override val id = "artisan_tempering"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.DAMAGED_ANVIL)

        override fun onAcquire(ctx: PerkContext) {
            // DAMAGED_ANVIL 是铁砧的第三阶段: 放置后再用一次就会碎裂
            PerkEffects.giveStack(ctx.player, ItemStack(Items.DAMAGED_ANVIL))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.FURNACE))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.STONECUTTER))
        }
    }

    fun registerAll() {
        PerkRegistry.register(Supply)
        PerkRegistry.register(Toolkit)
        PerkRegistry.register(Builder)
        PerkRegistry.register(Enchanter)
        PerkRegistry.register(QuickEnchant)
        PerkRegistry.register(WaypointClock)
        PerkRegistry.register(LongArms)
        PerkRegistry.register(FireTemper)
        PerkRegistry.register(Poisoner)
        PerkRegistry.register(TreasureChest)
        PerkRegistry.register(ShulkerBox)
        PerkRegistry.register(Tempering)
    }
}
