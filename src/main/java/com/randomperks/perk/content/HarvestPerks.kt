package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.server.world.ServerWorld
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries

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

    /** 蓝 · 樵夫之手: 破坏原木时 50% 概率额外掉落一个原木 (掉落在原方块位置, 不进背包) */
    object Timber : PerkDefinition {
        override val id = "harvest_timber"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.OAK_LOG)

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: net.minecraft.block.BlockState) {
            if (!blockState.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return
            if (ctx.player.random.nextFloat() >= 0.50f) return
            val world = ctx.player.world as? ServerWorld ?: return
            // 标准掉落物实体 (含默认拾取延迟), 与矿脉嗅觉/收藏家同范式
            PerkEffects.spawnBlockDrops(ctx.player, world, pos, blockState)
        }
    }

    /** 紫 · 丰收之息: 获得 32 胡萝卜 + 锄头(时运III) */
    object Bounty : PerkDefinition {
        override val id = "harvest_bounty"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CARROT)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CARROT, 32))
            val hoe = ItemStack(Items.IRON_HOE)
            PerkEffects.enchant(ctx.player, hoe, Enchantments.FORTUNE, 3)
            PerkEffects.giveStack(ctx.player, hoe)
        }
    }

    /** 蓝 · 丰饶: 每当饱食度回满时, 随机获得一种谜之炖菜的纯正面效果 (时长逐项与原版炖菜一致)
     *  —— 触发点由 HungerRefillMixin 注入原版 HungerManager.update, 抓"不满 → 满"的那一次跳变;
     *  "满"的判定与原版"饱和回血"分支同源 (foodLevel >= 20), 不做周期轮询, 也不需要标志位。 */
    object BountifulMeal : PerkDefinition {
        override val id = "harvest_bountiful_meal"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SUSPICIOUS_STEW)

        /** 谜之炖菜的 5 种纯正面效果 + 各自的**原版时长** (tick)。
         *  数值取自原版 data/minecraft/recipe/suspicious_stew_from_*.json ——
         *  饱和 7t(蒲公英/蓝兰花) / 抗火 60t(绒球葱) / 夜视 100t(虞美人/火把花)
         *  / 跳跃提升 100t(矢车菊) / 生命恢复 140t(滨菊); 原版并没有"统一 6 秒"这回事。 */
        private val STEW_EFFECTS = listOf(
            StatusEffects.SATURATION to 7,
            StatusEffects.FIRE_RESISTANCE to 60,
            StatusEffects.NIGHT_VISION to 100,
            StatusEffects.JUMP_BOOST to 100,
            StatusEffects.REGENERATION to 140,
        )

        override fun onHungerRefilled(ctx: PerkContext) {
            val player = ctx.player
            val (effect, duration) = STEW_EFFECTS[player.random.nextInt(STEW_EFFECTS.size)]
            // 显示方式与原版炖菜略有出入: 不显示粒子 (保留图标) —— 避免与"回满"瞬间的视觉叠加
            player.addStatusEffect(StatusEffectInstance(effect, duration, 0, true, false, true))
        }
    }

    /** 蓝 · 丰饶: 食用任意食物时, 恢复的饥饿值与饱和度至少提升 50% (饥饿值向上取整, 效果由 ConsumableBoostMixin 实现) */
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

    /** 蓝 · 丰饶: 苦力怕会像害怕猫一样害怕你并主动远离 (不生成猫; 效果由 CatAuraMixin 实现) */
    object CatAura : PerkDefinition {
        override val id = "harvest_cat_aura"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CAT_SPAWN_EGG)
    }

    /** 蓝 · 丰饶: 立即在脚下召唤一匹属于你的马 (自带鞍; 用剪刀右键可取下鞍) */
    object SummonHorse : PerkDefinition {
        override val id = "harvest_summon_horse"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SADDLE)

        /** 词条召唤的马身上的命令标签 (供"剪刀取鞍"识别, 不影响其他马) */
        const val TAG = "randomperks_summoned_horse"

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val world = player.world as? ServerWorld ?: return

            val horse = net.minecraft.entity.passive.HorseEntity(net.minecraft.entity.EntityType.HORSE, world)
            // 位置 + 朝向都跟随玩家 (原版生成走 refreshPositionAndAngles)
            horse.refreshPositionAndAngles(player.x, player.y, player.z, player.yaw, player.pitch)
            horse.setOwner(player)
            // ★ 仅 setOwner 不会设置驯服标志 (Tame 是独立的 horseFlag 位):
            // 未驯服的马在原版会拒绝打开装备界面 (AbstractHorseEntity.openInventory 的 isTame 检查),
            // 表现为"骑着马按 E 没反应", 鞍也取不下来
            horse.setTame(true)
            horse.equipStack(net.minecraft.entity.EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))
            horse.addCommandTag(TAG)
            horse.setPersistent()
            // ★ 必须走一遍原版生成初始化: 随机花色 + 随机属性
            // (15~30 血 / 0.4~1.0 跳跃力 / 0.1125~0.3375 速度)。
            // 原版把这两件事都放在 initialize → AbstractHorseEntity.initialize → initAttributes(random),
            // 而 initAttributes 只被 initialize 调用 (构造函数不调) —— 漏掉的话马会停在注册表默认值
            // (53 血 / 0.7 跳跃 / 0.225 速度 / 固定花色), 比自然生成的马强一倍以上且永远同款。
            // 参数与 /summon 完全同构 (SpawnReason.COMMAND 也传 null entityData)。
            horse.initialize(
                world,
                world.getLocalDifficulty(horse.blockPos),
                net.minecraft.entity.SpawnReason.MOB_SUMMONED,
                null,
            )
            world.spawnEntity(horse)
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
    }
}
