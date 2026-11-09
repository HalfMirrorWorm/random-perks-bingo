package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.effect.PermanentEffects
import com.randomperks.game.MidasTouchService
import com.randomperks.game.PerkSession
import com.randomperks.game.ReviveArmorService
import com.randomperks.game.TemperingAnvilService
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import com.randomperks.state.PerkStateManager
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.Items
import net.minecraft.core.registries.Registries
import net.minecraft.tags.EnchantmentTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.UUID

/**
 * 匠造 ARTISAN —— 工具 / 建造 / 功能性物品。
 */
object ArtisanPerks {

    /** 蓝 · 战地补给: 16 烤马铃薯 + 水桶 */
    object Supply : PerkDefinition {
        override val id = "artisan_supply"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.BAKED_POTATO)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BAKED_POTATO, 16))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WATER_BUCKET))
        }
    }

    /** 蓝 · 工具保养: 获得一把效率 II 的铁镐 (剩余耐久 60 点) */
    object Toolkit : PerkDefinition {
        override val id = "artisan_toolkit"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onAcquire(ctx: PerkContext) {
            val pickaxe = ItemStack(Items.IRON_PICKAXE)
            PerkEffects.enchant(ctx.player, pickaxe, Enchantments.EFFICIENCY, 2)
            // 只剩 60 点耐久: ItemStack 只存"已损耗值"(damage), 所以要写成 上限 - 剩余
            // (铁镐上限 250 → 已损耗 190; 铁砧/经验修补修一下即可恢复满耐久)
            pickaxe.damageValue = pickaxe.maxDamage - REMAINING_DURABILITY
            PerkEffects.giveStack(ctx.player, pickaxe)
        }

        /** 发放的铁镐剩余耐久 */
        private const val REMAINING_DURABILITY = 60
    }

    /** 绿 · 建筑师: 工作台 + 16 原木 + 建材 + 火把 */
    object Builder : PerkDefinition {
        override val id = "artisan_builder"
        override val rarity = PerkRarity.GREEN
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

        private fun now(ctx: PerkContext): Long = ctx.player.level().getServer().tickCount.toLong()

        /** 随机一本**满级**附魔书: 从整个附魔注册表里抽 (宝藏附魔也在其中), 一律按该附魔的
         *  {@code max_level} 发放。诅咒不算"满级或宝藏", 排除掉。 */
        private fun grant(ctx: PerkContext) {
            val registry = ctx.player.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT) ?: return

            val candidates = registry.entrySet()
                .map { registry.wrapAsHolder(it.value) }
                .filter { !it.`is`(EnchantmentTags.CURSE) }
            if (candidates.isEmpty()) return

            val entry = candidates[ctx.player.random.nextInt(candidates.size)]
            val book = ItemStack(Items.ENCHANTED_BOOK)
            book.enchant(entry, entry.value().definition().maxLevel())
            PerkEffects.giveStack(ctx.player, book)
        }
    }

    /** 紫 · 匠造: **快速附魔** —— 在 V 界面点击使用后弹出装备选择器 (4 个护甲槽 + 副手),
     *  给选中的那件装备随机附加一个**该装备吃得下的满级附魔**, 不消耗经验。
     *
     *  只生效一次 (获得词条那一刻), 所以"附在哪件装备上"是个一次性决策 —— 底材越好收益越大。 */
    object QuickEnchant : PerkDefinition {
        override val id = "artisan_quick_enchant"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.ANVIL)

        override val isOneTime = true

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

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            if (!hasAnyItem(player)) {
                player.sendSystemMessage(Component.translatable("random-perks-bingo.message.quick_enchant_no_item"), true)
                return
            }
            PerkNetworking.sendEquipmentPickOpen(player, PURPOSE)
        }

        /** 客户端在物品选择器里选中了一件东西 → 给它一个随机满级附魔 */
        fun handlePick(player: ServerPlayer, key: String) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            // 客户端可以伪造 key, 所以只认自己这份白名单, 并当场把物品查出来
            val stack = stackAt(player, key) ?: return
            if (stack.isEmpty) return

            val registry = player.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT) ?: return

            // 已有附魔既不能重复给, 也不能给和它互斥的 —— 原版的互斥组只允许留一个
            // (比如"锋利"和"亡灵杀手"), 硬塞进去会得到一个原版不可能出现的组合。
            val existing = stack.enchantments.keySet()
            val candidates = registry.entrySet()
                .map { registry.wrapAsHolder(it.value) }
                .filter { it.value().isSupportedItem(stack) }
                .filter { !existing.contains(it) }
                .filter { candidate -> existing.all { Enchantment.areCompatible(candidate, it) } }
            if (candidates.isEmpty()) {
                player.sendSystemMessage(Component.translatable("random-perks-bingo.message.quick_enchant_none"), true)
                return
            }

            val picked = candidates[player.random.nextInt(candidates.size)]
            val level = picked.value().definition().maxLevel()
            stack.enchant(picked, level)

            // 改的是物品栏里已有的 stack (引用没变), 不会自动触发同步 —— 手动推一次
            player.inventoryMenu.sendAllDataToRemote()
            state.markUsed(id)

            player.sendSystemMessage(
                Component.translatable(
                    "random-perks-bingo.message.quick_enchant_done",
                    stack.hoverName,
                    Enchantment.getFullname(picked, level),
                ),
                false,
            )
        }

        /** 身上有没有任何可以附魔的东西 (装备槽 + 整个物品栏) */
        private fun hasAnyItem(player: ServerPlayer): Boolean {
            if (EQUIP_SLOTS.any { !player.getItemBySlot(it).isEmpty }) return true
            return (0 until player.inventory.containerSize).any { !player.inventory.getItem(it).isEmpty }
        }

        /** 把客户端回传的 key 解析成一件真实存在的物品; 越界 / 名字不认识一律返回 null */
        private fun stackAt(player: ServerPlayer, key: String): ItemStack? {
            if (key.startsWith(INV_PREFIX)) {
                val index = key.removePrefix(INV_PREFIX).toIntOrNull() ?: return null
                if (index !in 0 until player.inventory.containerSize) return null
                return player.inventory.getItem(index)
            }
            val slot = EQUIP_SLOTS.firstOrNull { it.getName() == key } ?: return null
            return player.getItemBySlot(slot)
        }
    }

    /** 金 · 匠造: 方块与实体交互距离 +4 格
     *  (注: `ENTITY_INTERACTION_RANGE` 同时决定近战攻击有效距离 —— 3.0 → 7.0) */
    object LongArms : PerkDefinition {
        override val id = "artisan_long_arms"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.ARMOR_STAND)

        private val MOD_BLOCK = Identifier.fromNamespaceAndPath("random-perks-bingo", "long_arms_block")
        private val MOD_ENTITY = Identifier.fromNamespaceAndPath("random-perks-bingo", "long_arms_entity")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) {
            PerkEffects.setAttribute(ctx.player, Attributes.BLOCK_INTERACTION_RANGE, MOD_BLOCK, 4.0)
            PerkEffects.setAttribute(ctx.player, Attributes.ENTITY_INTERACTION_RANGE, MOD_ENTITY, 4.0)
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, Attributes.BLOCK_INTERACTION_RANGE, MOD_BLOCK)
            PerkEffects.removeAttribute(ctx.player, Attributes.ENTITY_INTERACTION_RANGE, MOD_ENTITY)
        }
    }

    /** 绿 · 匠造: 获得高炉、烟熏炉与营火各一个 */
    object FireTemper : PerkDefinition {
        override val id = "artisan_fire_temper"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.BLAST_FURNACE)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BLAST_FURNACE))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SMOKER))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CAMPFIRE))
        }
    }

    /** 紫 · 匠造: **绝命毒师** —— 在 V 界面点击使用后弹出玩家选择器, 选中一名玩家后给他施加
     *  **随机一种负面效果** (等级 I, 持续 1 分钟) */
    object Poisoner : PerkDefinition {
        override val id = "artisan_poisoner"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.FERMENTED_SPIDER_EYE)

        override val isOneTime = true

        /** 负面效果持续 1 分钟 */
        private const val DURATION_TICKS = 60 * 20

        /** 随机负面效果池 (等级 I) */
        private val DEBUFFS = listOf(
            MobEffects.SLOWNESS,
            MobEffects.MINING_FATIGUE,
            MobEffects.NAUSEA,
            MobEffects.BLINDNESS,
            MobEffects.HUNGER,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.LEVITATION,
            MobEffects.DARKNESS,
            MobEffects.UNLUCK,
        )

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendSystemMessage(Component.translatable("random-perks-bingo.message.poisoner_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "poisoner",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 给他灌一瓶随机毒药 */
        fun handleTargetPick(player: ServerPlayer, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return

            val effect = DEBUFFS[target.random.nextInt(DEBUFFS.size)]
            target.addEffect(MobEffectInstance(effect, DURATION_TICKS, 0))
            state.markUsed(id)

            val effectName = Component.translatable(effect.value().descriptionId)
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.poisoner_applied", target.name, effectName),
                false,
            )
            target.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.poisoner_received", effectName),
                true,
            )
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayer): List<ServerPlayer> =
            player.level().getServer().playerList.players.filter { !it.isSpectator }
    }

    /**
     * 金 · 匠造: **有福同享** —— 256 格内的队友持续获得你身上的正面药水效果。
     *
     * 只共享**有限时长**的 beneficial 效果: 药水 / 信标 / 潮涌等;
     * 词条给的永久效果 (无限时长) 和负面效果不参与, 避免把整队变成永久词条叠加。
     * 每 3 秒刷新一次, 队友离开范围或你失去效果后最多 3 秒自然结束 —— 是范围光环, 不是一次性复制。
     */
    object SharedFortune : PerkDefinition {
        override val id = "artisan_shared_fortune"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)

        /** 共享半径 (格) */
        private const val RANGE = 256.0

        /** 光环刷新时长 (tick): onTick 每 20 tick 一次, 3 秒足够覆盖 */
        private const val AURA_TICKS = 60

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            if (player.isSpectator) return

            val team = player.team?.name ?: return
            val effects = player.activeEffects.filter {
                it.effect.value().isBeneficial() &&
                    !it.isInfiniteDuration() &&
                    it.duration > 0
            }
            if (effects.isEmpty()) return

            val server = player.level().getServer()
            for (other in server.playerList.players) {
                if (other === player || other.isSpectator || !other.isAlive) continue
                if (other.team?.name != team) continue
                if (other.level() !== player.level()) continue
                if (other.distanceToSqr(player) > RANGE * RANGE) continue

                for (effect in effects) {
                    other.addEffect(
                        MobEffectInstance(
                            effect.effect,
                            minOf(effect.duration, AURA_TICKS),
                            effect.amplifier,
                            effect.isAmbient,
                            effect.isVisible,
                            effect.showIcon(),
                        )
                    )
                }
            }
        }
    }

    /** 紫 · 匠造: **百宝箱** —— 随机开出 **1~5 件**装备 (白板不附魔) */
    object TreasureChest : PerkDefinition {
        override val id = "artisan_treasure_chest"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.CHEST)
        /** 最少 / 最多开出的件数 */
        private const val MIN_COUNT = 1
        private const val MAX_COUNT = 5

        /** 可开出的装备池: 盔甲 (皮革→下界合金) + 工具 (木→下界合金) + 远程/特殊装备 + 功能工具 */
        private val POOL: List<Item> = listOf(
            // 盔甲
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.TURTLE_HELMET,
            // 工具
            Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE,
            Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE,
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
            Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
            // 远程 / 特殊装备
            Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.SHIELD, Items.ELYTRA,
            // 功能工具
            Items.FISHING_ROD, Items.SHEARS, Items.FLINT_AND_STEEL, Items.SPYGLASS,
            Items.CARROT_ON_A_STICK, Items.WARPED_FUNGUS_ON_A_STICK,
        )

        override fun onAcquire(ctx: PerkContext) {
            val random = ctx.player.random
            val count = MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1)
            repeat(count) {
                PerkEffects.giveStack(ctx.player, ItemStack(POOL[random.nextInt(POOL.size)]))
            }
        }
    }

    /** 绿 · 匠造: **随身仓库** —— 获得一个潜影盒 (27 格随身存储) */
    object ShulkerBox : PerkDefinition {
        override val id = "artisan_shulker_box"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.SHULKER_BOX)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SHULKER_BOX))
        }
    }

    /** 蓝 · 匠造: **千锤百炼** —— 获得一个只能用一次的铁砧、熔炉、切石机 */
    object Tempering : PerkDefinition {
        override val id = "artisan_tempering"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.DAMAGED_ANVIL)

        override fun onAcquire(ctx: PerkContext) {
            // 损坏的铁砧本身可以反复用 (每次 12% 碎裂), 这里打上标记, 由
            // TemperingAnvilService + 两个 mixin 强制"第一次使用后必碎"
            val anvil = ItemStack(Items.DAMAGED_ANVIL)
            TemperingAnvilService.markItem(anvil)
            PerkEffects.giveStack(ctx.player, anvil)
            PerkEffects.giveStack(ctx.player, ItemStack(Items.FURNACE))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.STONECUTTER))
        }
    }

    /** 金 · 匠造: **喵人斧** —— 获得一把**秒人斧** —— **金斧 + 锋利 1145 + 1 点耐久**;
     *  代价是**永久虚弱 I**。
     *
     *  与社区（EaseCation 服务器 / Funprops Killaxe）同款思路: 不靠自定义秒杀逻辑,
     *  而是把锋利堆到荒谬的等级 —— 1145 级的单次附魔伤害 ≈ 1 + 0.5 × 1144 ≈ 573,
     *  配上金斧基础伤害足以一击带走几乎任何生物 (含玩家); 代价是攻击一次斧头就坏。 */
    object NyaAxe : PerkDefinition {
        override val id = "artisan_nya_axe"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.GOLDEN_AXE)

        /** 锋利等级 (EaseCation 的经典数值) */
        private const val SHARPNESS_LEVEL = 1145

        override fun onAcquire(ctx: PerkContext) {
            val axe = ItemStack(Items.GOLDEN_AXE)
            PerkEffects.enchant(ctx.player, axe, Enchantments.SHARPNESS, SHARPNESS_LEVEL)
            // ★ 耐久上限直接压成 1 点: 无论用铁砧、经验修补还是任何手段"修回来"，
            //   上限仍然只有 1 —— 这把斧头永远只有一次机会。
            axe.set(DataComponents.MAX_DAMAGE, 1)
            // ★ 工作惩罚直接拉满: 铁砧成本一上来就 ≥ 40 级 → 永久"过于昂贵", 修不了也合不了。
            //   这是原版组件, 不显示在 tooltip 上, 也不需要额外 mixin。
            axe.set(DataComponents.REPAIR_COST, 40)
            PerkEffects.giveStack(ctx.player, axe)

            // 代价: 永久虚弱 I
            PerkEffects.permanentEffect(ctx.player, MobEffects.WEAKNESS, 0)
        }

        override fun onRespawn(ctx: PerkContext) =
            PerkEffects.permanentEffect(ctx.player, MobEffects.WEAKNESS, 0)

        override fun onRemove(ctx: PerkContext) =
            PermanentEffects.removePermanent(ctx.player, MobEffects.WEAKNESS, 0)
    }

    /** 绿 · 匠造: **点石成金** —— 右键镶金黑石, 把它变成金块 (交互见 MidasTouchService) */
    object MidasTouch : PerkDefinition {
        override val id = MidasTouchService.PERK_ID
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.GOLD_BLOCK)
    }

    /** 绿 · 匠造: **神龟护体** —— 获得 3 瓶喷溅型神龟药水 */
    object TurtleMaster : PerkDefinition {
        override val id = "artisan_turtle_master"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() =
            PotionContents.createItemStack(Items.SPLASH_POTION, Potions.TURTLE_MASTER)

        private const val COUNT = 3

        override fun onAcquire(ctx: PerkContext) {
            repeat(COUNT) {
                PerkEffects.giveStack(
                    ctx.player,
                    PotionContents.createItemStack(Items.SPLASH_POTION, Potions.TURTLE_MASTER),
                )
            }
        }
    }

    /** 蓝 · 匠造: **纹饰护体** —— 每穿戴一件带盔甲纹饰的装备, 生命上限 +5 (最多 4 件) */
    object TrimmedArmor : PerkDefinition {
        override val id = "artisan_trimmed_armor"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE)

        private val MOD_HEALTH = Identifier.fromNamespaceAndPath("random-perks-bingo", "trimmed_armor_health")

        private val ARMOR_SLOTS = arrayOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
        )

        /** 每件纹饰装备提供的生命上限 */
        private const val HEALTH_PER_PIECE = 8.0

        override fun onAcquire(ctx: PerkContext) = refresh(ctx)
        override fun onRespawn(ctx: PerkContext) = refresh(ctx)

        /** 穿/脱护甲的瞬间即时重算 (EquipmentChangeMixin + PlayerInventoryEquipMixin 分发) */
        override fun onEquipmentChange(ctx: PerkContext) = refresh(ctx)

        private fun refresh(ctx: PerkContext) {
            val trimmed = ARMOR_SLOTS.count { slot ->
                ctx.player.getItemBySlot(slot).get(DataComponents.TRIM) != null
            }

            if (trimmed == 0) {
                PerkEffects.removeAttribute(ctx.player, Attributes.MAX_HEALTH, MOD_HEALTH)
            } else {
                PerkEffects.setAttribute(
                    ctx.player,
                    Attributes.MAX_HEALTH,
                    MOD_HEALTH,
                    HEALTH_PER_PIECE * trimmed,
                    AttributeModifier.Operation.ADD_VALUE,
                )
            }
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, Attributes.MAX_HEALTH, MOD_HEALTH)
    }

    /** 金 · 匠造: **不痛不痒** —— 最终伤害在 2~5 点时改为只受 1 点 (由 SmallDamageGuardMixin 实现) */
    object BarelyAScratch : PerkDefinition {
        override val id = "artisan_barely_a_scratch"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.SHIELD)
    }

    /** 紫 · 匠造: **复活甲** —— 受到致命伤害时自动触发一次不死图腾效果 (**整局一次**, 无冷却) */
    object ReviveArmor : PerkDefinition {
        override val id = ReviveArmorService.PERK_ID
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.TOTEM_OF_UNDYING)
    }

    /** 蓝 · 匠造: **便携工作台** —— 手持工作台右键直接打开 3×3 合成界面;
     *  潜行右键保留原版放置 (PortableCraftingService)。 */
    object PortableCrafting : PerkDefinition {
        override val id = "artisan_portable_crafting"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.CRAFTING_TABLE)
    }

    /** 蓝 · 匠造: **大背包** —— 背包内物品的堆叠上限 ×4 (64 → 256, 16 → 64; 不可堆叠物品保持 1)。
     *  只作用于玩家自己的背包, 箱子等容器保持原版 (BigBackpackInventoryMixin / BigBackpackSlotMixin)。
     *  原版 ItemStack 存档 codec 的 count 上限是 99, 超过就写不进存档 —— 由
     *  BigBackpackCodecMixin 把这一个字段放宽到 256。 */
    object BigBackpack : PerkDefinition {
        override val id = "artisan_big_backpack"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.BUNDLE)

        /** 移除词条后背包格子回到原版上限: 把超额堆叠拆开, 拆出来的进空位, 放不下就掉在脚下,
         *  不能留下存不进存档的非法堆叠。 */
        override fun onRemove(ctx: PerkContext) {
            val player = ctx.player
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
                if (!remaining.isEmpty && player is net.minecraft.server.level.ServerPlayer) {
                    player.drop(remaining, false, net.minecraft.util.Prediction.SERVER_ONLY)
                }
            }
            inventory.setChanged()
            if (player is net.minecraft.server.level.ServerPlayer) {
                player.inventoryMenu.sendAllDataToRemote()
            }
        }
    }

    fun registerAll() {
        PerkRegistry.register(Supply)
        PerkRegistry.register(Toolkit)
        PerkRegistry.register(Builder)
        PerkRegistry.register(Enchanter)
        PerkRegistry.register(QuickEnchant)
        PerkRegistry.register(LongArms)
        PerkRegistry.register(FireTemper)
        PerkRegistry.register(Poisoner)
        PerkRegistry.register(SharedFortune)
        PerkRegistry.register(TreasureChest)
        PerkRegistry.register(ShulkerBox)
        PerkRegistry.register(Tempering)
        PerkRegistry.register(NyaAxe)
        PerkRegistry.register(MidasTouch)
        PerkRegistry.register(ReviveArmor)
        PerkRegistry.register(BarelyAScratch)
        PerkRegistry.register(TurtleMaster)
        PerkRegistry.register(TrimmedArmor)
        PerkRegistry.register(PortableCrafting)
        PerkRegistry.register(BigBackpack)
    }
}
