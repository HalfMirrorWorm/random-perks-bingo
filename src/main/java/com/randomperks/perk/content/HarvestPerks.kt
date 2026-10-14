package com.randomperks.perk.content

import com.randomperks.effect.ChainBreak
import com.randomperks.effect.PerkEffects
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.block.BlockState
import net.minecraft.entity.passive.CatEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

/**
 * 丰饶 HARVEST —— 作物 / 树木 / 动物。
 */
object HarvestPerks {

    /** 蓝 · 园艺家: 获得 16 面包 + 16 小麦种子 */
    object Gardener : PerkDefinition {
        override val id = "harvest_gardener"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.WHEAT)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BREAD, 16))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WHEAT_SEEDS, 16))
        }
    }

    /** 蓝 · 伐木工: 获得效率 III 的石斧 */
    object Lumberjack : PerkDefinition {
        override val id = "harvest_lumberjack"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.STONE_AXE)

        override fun onAcquire(ctx: PerkContext) {
            val axe = ItemStack(Items.STONE_AXE)
            PerkEffects.enchant(ctx.player, axe, Enchantments.EFFICIENCY, 3)
            PerkEffects.giveStack(ctx.player, axe)
        }
    }

    /** 蓝 · 牧羊人: 获得剪刀 + 16 羊毛 */
    object Herder : PerkDefinition {
        override val id = "harvest_herder"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SHEARS)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SHEARS))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WHITE_WOOL, 16))
        }
    }

    /** 紫 · 樵夫之手: 破坏原木时 50% 概率额外掉落一个原木 */
    object Timber : PerkDefinition {
        override val id = "harvest_timber"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.OAK_LOG)

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: net.minecraft.block.BlockState) {
            if (!blockState.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return
            if (ctx.player.random.nextFloat() >= 0.50f) return
            PerkEffects.giveStack(ctx.player, ItemStack(blockState.block))
        }
    }

    /** 紫 · 丰收之息: 获得 32 胡萝卜 + 锄头(效率III) + 永久饱和 (减少觅食压力) */
    object Bounty : PerkDefinition {
        override val id = "harvest_bounty"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CARROT)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CARROT, 32))
            val hoe = ItemStack(Items.IRON_HOE)
            PerkEffects.enchant(ctx.player, hoe, Enchantments.EFFICIENCY, 3)
            PerkEffects.giveStack(ctx.player, hoe)
        }
    }

    /** 蓝 · 丰饶: 每当饱食度回满时, 获得一次谜之炖菜的纯正面效果 (时长与原版炖菜一致) */
    object BountifulMeal : PerkDefinition {
        override val id = "harvest_bountiful_meal"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SUSPICIOUS_STEW)

        /** 谜之炖菜的纯正面效果 */
        private val STEW_EFFECTS = listOf(
            StatusEffects.SATURATION,
            StatusEffects.NIGHT_VISION,
            StatusEffects.JUMP_BOOST,
            StatusEffects.FIRE_RESISTANCE,
            StatusEffects.REGENERATION,
        )

        /** 与原版谜之炖菜一致的效果时长 (6 秒) */
        private const val STEW_DURATION = 6 * 20

        /** 记录"上一 tick 是否满饱食度" (存放在 cooldowns 里, 值 1=满 0=未满) */
        private const val FULL_FLAG = "bountiful_meal_full"

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            val isFull = player.hungerManager.foodLevel >= 20
            val wasFull = (ctx.state.cooldowns[FULL_FLAG] ?: 0L) > 0L

            // 仅在饥饿度"刚回满"的瞬间给予一次效果
            if (isFull && !wasFull) {
                val pick = STEW_EFFECTS[player.random.nextInt(STEW_EFFECTS.size)]
                player.addStatusEffect(
                    StatusEffectInstance(pick, STEW_DURATION, 0, true, false, true)
                )
            }

            ctx.state.cooldowns[FULL_FLAG] = if (isFull) 1L else 0L
        }
    }

    /** 蓝 · 丰饶: 食用任意食物时, 恢复的饥饿值与饱和度均提升 25% (效果由 ConsumableBoostMixin 实现) */
    object Gourmet : PerkDefinition {
        override val id = "harvest_gourmet"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.GOLDEN_CARROT)
    }

    /** 蓝 · 丰饶: 立即获得 4 个随机生物蛋 (互不相同) */
    object Rancher : PerkDefinition {
        override val id = "harvest_rancher"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.EGG)

        override fun onAcquire(ctx: PerkContext) {
            val eggs = Registries.ITEM
                .filter { Registries.ITEM.getId(it).path.endsWith("_spawn_egg") }
                .shuffled()
                .take(4)
            eggs.forEach { PerkEffects.giveStack(ctx.player, ItemStack(it)) }
        }
    }

    /** 蓝 · 丰饶: 一只隐形的猫跟随你 —— 苦力怕会像害怕猫一样远离你 */
    object CatAura : PerkDefinition {
        override val id = "harvest_cat_aura"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CAT_SPAWN_EGG)

        private const val CAT_KEY = "cat_aura_entity"

        override fun onAcquire(ctx: PerkContext) = ensureCat(ctx)
        override fun onTick(ctx: PerkContext) = ensureCat(ctx)
        override fun onRespawn(ctx: PerkContext) = ensureCat(ctx)

        /** 确保隐形猫存在 (跨世界/丢失时重建), 过远时拉回身边 */
        private fun ensureCat(ctx: PerkContext) {
            val player = ctx.player
            val server = player.server ?: return
            val catId = ctx.state.memory[CAT_KEY]?.toIntOrNull()

            val cat = catId?.let { id ->
                server.worlds.firstNotNullOfOrNull { it.getEntityById(id) as? CatEntity }
            }

            if (cat != null && !cat.isRemoved) {
                if (cat.world !== player.world) {
                    // 跨世界: 清理旧的, 重建
                    cat.discard()
                } else {
                    // 太远则拉回身边
                    if (cat.squaredDistanceTo(player) > 40.0 * 40.0) {
                        cat.refreshPositionAndAngles(player.x, player.y, player.z, cat.yaw, cat.pitch)
                    }
                    return
                }
            }

            val newCat = CatEntity(net.minecraft.entity.EntityType.CAT, player.world as? ServerWorld ?: return)
            newCat.setPosition(player.x, player.y, player.z)
            newCat.setInvisible(true)
            newCat.setSilent(true)
            newCat.setInvulnerable(true)
            newCat.setPersistent()
            newCat.setTamed(true, true)
            newCat.setOwner(player)
            newCat.isSitting = false
            player.world.spawnEntity(newCat)
            ctx.state.memory[CAT_KEY] = newCat.id.toString()
        }

        override fun onRemove(ctx: PerkContext) {
            val catId = ctx.state.memory.remove(CAT_KEY)?.toIntOrNull() ?: return
            ctx.player.server?.worlds?.forEach { world ->
                world.getEntityById(catId)?.discard()
            }
        }
    }

    /** 蓝 · 丰饶: 立即在脚下召唤一匹属于你的马 (自带鞍) */
    object SummonHorse : PerkDefinition {
        override val id = "harvest_summon_horse"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SADDLE)

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val world = player.world as? ServerWorld ?: return

            val horse = net.minecraft.entity.passive.HorseEntity(net.minecraft.entity.EntityType.HORSE, world)
            horse.setPosition(player.x, player.y, player.z)
            horse.setOwner(player) // 设置主人即视为驯服 (1.21+)
            horse.equipStack(net.minecraft.entity.EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))
            horse.setPersistent()
            world.spawnEntity(horse)
        }
    }

    /** 金 · 丰饶 · 质变: 砍掉一个原木时, 整棵树会一起倒下 (最多 64 个原木) */
    object TreeFeller : PerkDefinition {
        override val id = "harvest_tree_feller"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.NETHERITE_AXE)

        private const val CAP = 64

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            if (!blockState.isIn(BlockTags.LOGS)) return
            val broken = ChainBreak.run(ctx.player, pos, CAP) { it.isIn(BlockTags.LOGS) }
            if (broken >= 3) {
                ctx.player.sendMessage(
                    Text.translatable("random-perks-bingo.message.tree_feller", broken), true,
                )
            }
        }
    }

    fun registerAll() {
        PerkRegistry.register(Gardener)
        PerkRegistry.register(Lumberjack)
        PerkRegistry.register(Herder)
        PerkRegistry.register(Timber)
        PerkRegistry.register(Bounty)
        PerkRegistry.register(BountifulMeal)
        PerkRegistry.register(Gourmet)
        PerkRegistry.register(Rancher)
        PerkRegistry.register(CatAura)
        PerkRegistry.register(SummonHorse)
        PerkRegistry.register(TreeFeller)
    }
}
