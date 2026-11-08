package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.BoneMealFlowerService
import com.randomperks.game.ChickenDyeService
import com.randomperks.game.FungalInfectionService
import com.randomperks.game.KelpToBoneService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PigMorphService
import com.randomperks.game.WeatherService
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.resources.Identifier

/**
 * 丰饶 HARVEST —— 作物 / 树木 / 动物。
 */
object HarvestPerks {

    /** 紫 · 丰饶: **田园修补** —— 收获成熟作物时, 自动为全身装备 (护甲 4 件 + 主副手)
     *  各修复 10 点耐久 (只修有损耗的物品, 不消耗经验/材料)。 */
    object FieldMender : PerkDefinition {
        override val id = "harvest_field_mender"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.WHEAT)

        /** 修复范围: 护甲四件 + 主手 + 副手 */
        private val EQUIPMENT_SLOTS = arrayOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
        )

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            if (!PerkBoostQuery.isMatureCrop(blockState)) return

            val player = ctx.player
            for (slot in EQUIPMENT_SLOTS) {
                val stack = player.getItemBySlot(slot)
                if (stack.isEmpty || !stack.isDamageableItem) continue
                val damage = stack.damageValue
                if (damage <= 0) continue
                stack.damageValue = (damage - 10).coerceAtLeast(0)
            }
        }
    }

    /** 蓝 · 丰饶: **作物丰收** —— 随机获得一种主要作物 + 对应的一整组种子:
     *  8 干草块 + 64 小麦种子 / 8 西瓜块 + 64 西瓜种子 / 16 南瓜 + 64 南瓜种子 / 64 甜菜根 + 64 甜菜根种子 */
    object Gardener : PerkDefinition {
        override val id = "harvest_gardener"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.WHEAT)

        private const val SEED_COUNT = 64

        private data class CropSet(val crop: Item, val cropCount: Int, val seeds: Item)

        private val CROP_SETS = listOf(
            CropSet(Items.HAY_BLOCK, 8, Items.WHEAT_SEEDS),
            CropSet(Items.MELON, 8, Items.MELON_SEEDS),
            CropSet(Items.PUMPKIN, 16, Items.PUMPKIN_SEEDS),
            CropSet(Items.BEETROOT, 64, Items.BEETROOT_SEEDS),
        )

        override fun onAcquire(ctx: PerkContext) {
            val set = CROP_SETS[ctx.player.random.nextInt(CROP_SETS.size)]
            PerkEffects.giveStack(ctx.player, ItemStack(set.crop, set.cropCount))
            PerkEffects.giveStack(ctx.player, ItemStack(set.seeds, SEED_COUNT))
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

    /** 蓝 · 牧羊人: 获得剪刀 + 32 羊毛 */
    object Herder : PerkDefinition {
        override val id = "harvest_herder"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SHEARS)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SHEARS))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WOOL.white(), 32))
        }
    }

    /** 绿 · 樵夫之手: 砍**未去皮**的原木时, 20% 概率额外掉落一根**其他种类**的去皮原木
     *  (掉落在原方块位置, 不进背包)。去皮原木自身不再触发。 */
    object Timber : PerkDefinition {
        override val id = "harvest_timber"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.OAK_LOG)

        private const val CHANCE = 0.20f

        /** 可掉落的去皮原木 (含下界菌柄) */
        private val STRIPPED_LOGS = listOf(
            Items.STRIPPED_OAK_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_BIRCH_LOG,
            Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_DARK_OAK_LOG,
            Items.STRIPPED_MANGROVE_LOG, Items.STRIPPED_CHERRY_LOG, Items.STRIPPED_PALE_OAK_LOG,
            Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM,
        )

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.core.BlockPos, blockState: net.minecraft.world.level.block.state.BlockState) {
            if (!blockState.`is`(net.minecraft.tags.BlockTags.LOGS)) return

            // 只认未去皮的。原版没有 stripped_logs 标签 (LOGS 把去皮和未去皮混在一起),
            // 所以直接看注册名有没有 stripped_ 前缀 —— 原木/木头/菌柄/菌核全都能覆盖。
            val chopped = blockState.block.asItem()
            val choppedId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(chopped)
            if (choppedId.path.startsWith("stripped_")) return

            if (ctx.player.random.nextFloat() >= CHANCE) return

            val world = ctx.player.level() as? ServerLevel ?: return
            // 排除"被砍这一种"的去皮版本 (oak_log → stripped_oak_log), 剩下的里随机挑一种
            val ownStripped = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(choppedId.namespace, "stripped_" + choppedId.path),
            )
            val candidates = STRIPPED_LOGS.filter { it != ownStripped }
            val drop = candidates[ctx.player.random.nextInt(candidates.size)]
            net.minecraft.world.level.block.Block.popResource(world, pos, ItemStack(drop))
        }
    }

    /** 蓝 · 丰饶: **一键伐木** —— 手持斧破坏原木时, 一键砍倒整棵相连的同种树
     *  (最多 128 块; 额外每块消耗 1 点耐久; 掉落统一在原木位置结算)。 */
    object TreeFeller : PerkDefinition {
        override val id = "harvest_tree_feller"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.IRON_AXE)

        /** 单次最多额外砍掉的方块数 */
        private const val MAX_LOGS = 128

        private val NEIGHBORS = arrayOf(
            Triple(1, 0, 0), Triple(-1, 0, 0),
            Triple(0, 1, 0), Triple(0, -1, 0),
            Triple(0, 0, 1), Triple(0, 0, -1),
        )

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            val player = ctx.player
            val tool = player.mainHandItem
            if (!tool.`is`(ItemTags.AXES)) return
            if (!blockState.`is`(BlockTags.LOGS)) return
            val world = player.level() as? ServerLevel ?: return

            val logBlock = blockState.block
            val visited = HashSet<BlockPos>()
            val queue = ArrayDeque<BlockPos>()
            visited.add(pos)
            queue.add(pos)
            var broken = 0

            while (queue.isNotEmpty() && broken < MAX_LOGS) {
                val cur = queue.removeFirst()
                for ((dx, dy, dz) in NEIGHBORS) {
                    val next = cur.offset(dx, dy, dz)
                    if (!visited.add(next)) continue

                    val nextState = world.getBlockState(next)
                    if (nextState.block !== logBlock) continue
                    if (tool.isEmpty) return

                    if (!world.destroyBlock(next, false, player)) continue
                    if (!player.isCreative) {
                        // 额外原木的掉落统一生成在主原木位置, 与主掉落堆在一起
                        PerkEffects.spawnBlockDrops(player, world, pos, nextState)
                        nextState.spawnAfterBreak(world, next, tool, true)
                        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)
                    }

                    broken++
                    if (broken >= MAX_LOGS) return
                    queue.add(next)
                }
            }
        }
    }

    /** 蓝 · 丰收之息: 获得 32 胡萝卜 + 锄头(时运III) */
    object Bounty : PerkDefinition {
        override val id = "harvest_bounty"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CARROT)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CARROT, 32))
            val hoe = ItemStack(Items.IRON_HOE)
            PerkEffects.enchant(ctx.player, hoe, Enchantments.FORTUNE, 3)
            PerkEffects.giveStack(ctx.player, hoe)
        }
    }

    /** 绿 · 丰饶: **吃完一份食物时, 若饱食度已满**, 随机获得一种谜之炖菜的纯正面效果
     *  (时长是原版炖菜的 **2 倍**)。
     *  —— 触发点由 ConsumableBoostMixin 注入原版 ConsumableComponent.finishConsumption,
     *  此时食物已结算完毕 (含"小鸟胃"的翻倍); 判定沿用原版"饱食度满"的标准 (foodLevel >= 20)。 */
    object BountifulMeal : PerkDefinition {
        override val id = "harvest_bountiful_meal"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SUSPICIOUS_STEW)

        /** 谜之炖菜的 5 种纯正面效果 + 各自的**原版时长** (tick)。
         *  数值取自原版 data/minecraft/recipe/suspicious_stew_from_*.json ——
         *  饱和 7t(蒲公英/蓝兰花) / 抗火 60t(绒球葱) / 夜视 100t(虞美人/火把花)
         *  / 跳跃提升 100t(矢车菊) / 生命恢复 140t(滨菊); 原版并没有"统一 6 秒"这回事。 */
        private val STEW_EFFECTS = listOf(
            MobEffects.SATURATION to 7,
            MobEffects.FIRE_RESISTANCE to 60,
            MobEffects.NIGHT_VISION to 100,
            MobEffects.JUMP_BOOST to 100,
            MobEffects.REGENERATION to 140,
        )

        /** 时长倍率: 表格里存的是原版数值, 实际发放时统一乘这个数 */
        private const val DURATION_MULTIPLIER = 2

        override fun onFoodEaten(ctx: PerkContext) {
            val player = ctx.player
            val (effect, duration) = STEW_EFFECTS[player.random.nextInt(STEW_EFFECTS.size)]
            // 显示方式与原版炖菜略有出入: 不显示粒子 (保留图标) —— 避免与"回满"瞬间的视觉叠加
            player.addEffect(
                MobEffectInstance(effect, duration * DURATION_MULTIPLIER, 0, true, false, true),
            )
        }
    }

    /** 蓝 · 丰饶: 食用任意食物时, 恢复的饥饿值与饱和度提升 100% (饥饿值向上取整, 效果由 ConsumableBoostMixin 实现) */
    object Gourmet : PerkDefinition {
        override val id = "harvest_gourmet"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.GOLDEN_CARROT)
    }

    /** 绿 · 唤法: 立即获得 4 个随机生物蛋 (互不相同) */
    object Rancher : PerkDefinition {
        override val id = "summon_rancher"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.EGG)

        override fun onAcquire(ctx: PerkContext) {
            val eggs = BuiltInRegistries.ITEM
                .filter { BuiltInRegistries.ITEM.getKey(it).path.endsWith("_spawn_egg") }
                .shuffled()
                .take(4)
            eggs.forEach { PerkEffects.giveStack(ctx.player, ItemStack(it)) }
        }
    }

    /** 绿 · 丰饶: 苦力怕会像害怕猫一样害怕你并主动远离 (不生成猫; 效果由 CatAuraMixin 实现);
     *  另外泡在水里时, 每 5 秒获得一条随机鱼 */
    object CatAura : PerkDefinition {
        override val id = "harvest_cat_aura"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CAT_SPAWN_EGG)

        /** 水中获得鱼的间隔: 5 秒 */
        private const val FISH_INTERVAL_TICKS = 5 * 20

        /** 随机鱼池 (原版 4 种鱼) */
        private val FISHES = listOf(
            Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH,
        )

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            if (!player.isInWater) return

            val now = player.level().getServer().tickCount.toLong()
            if (now < (ctx.state.cooldowns[id] ?: 0L)) return
            ctx.state.cooldowns[id] = now + FISH_INTERVAL_TICKS
            PerkEffects.giveStack(player, ItemStack(FISHES[player.random.nextInt(FISHES.size)]))
        }
    }

    /** 蓝 · 唤法: 立即在脚下召唤一匹属于你的马 (自带鞍; 用剪刀右键可取下鞍) */
    object SummonHorse : PerkDefinition {
        override val id = "summon_horse"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.SADDLE)

        /** 词条召唤的马身上的命令标签 (供"剪刀取鞍"识别, 不影响其他马) */
        const val TAG = "randomperks_summoned_horse"

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val world = player.level() as? ServerLevel ?: return

            val horse = net.minecraft.world.entity.animal.equine.Horse(net.minecraft.world.entity.EntityTypes.HORSE, world)
            // 位置 + 朝向都跟随玩家 (原版生成走 refreshPositionAndAngles)
            horse.snapTo(player.x, player.y, player.z, player.yRot, player.xRot)
            horse.setOwner(player)
            // ★ 仅 setOwner 不会设置驯服标志 (Tame 是独立的 horseFlag 位):
            // 未驯服的马在原版会拒绝打开装备界面 (AbstractHorseEntity.openInventory 的 isTame 检查),
            // 表现为"骑着马按 E 没反应", 鞍也取不下来
            horse.setTamed(true)
            horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))
            horse.addTag(TAG)
            horse.setPersistenceRequired()
            // ★ 必须走一遍原版生成初始化: 随机花色 + 随机属性
            // (15~30 血 / 0.4~1.0 跳跃力 / 0.1125~0.3375 速度)。
            // 原版把这两件事都放在 initialize → AbstractHorseEntity.initialize → initAttributes(random),
            // 而 initAttributes 只被 initialize 调用 (构造函数不调) —— 漏掉的话马会停在注册表默认值
            // (53 血 / 0.7 跳跃 / 0.225 速度 / 固定花色), 比自然生成的马强一倍以上且永远同款。
            // 参数与 /summon 完全同构 (SpawnReason.COMMAND 也传 null entityData)。
            horse.finalizeSpawn(
                world,
                world.getCurrentDifficultyAt(horse.blockPosition()),
                net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED,
                null,
            )
            // ★ 属性覆盖: initialize 随机出的值一律换成固定档 —— 血量 50 (超出原版 15~30),
            // 跳跃力与移动速度都取原版上限 (0.4~1.0 / 0.1125~0.3375), 即"属性全满的马"。
            // 必须放在 initialize **之后** (initialize 会重写这些属性); 血量还要同步补满 ——
            // 只提高属性上限不会回血, 否则马身上仍是随机出的初始血量。
            horse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                ?.baseValue = HORSE_HEALTH
            horse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)
                ?.baseValue = HORSE_JUMP
            horse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                ?.baseValue = HORSE_SPEED
            horse.health = HORSE_HEALTH.toFloat()
            world.addFreshEntity(horse)
        }

        /** 召唤马的固定属性: 血量 50 (超原版上限 30), 跳跃力/速度取原版上限 */
        private const val HORSE_HEALTH = 50.0
        private const val HORSE_JUMP = 1.0
        private const val HORSE_SPEED = 0.3375
    }

    /** 蓝 · 丰饶: **大胃袋** —— 饥饿值与饱和度都满了也能继续吃 (解除原版"吃饱了就不能吃"的限制) */
    object BigStomach : PerkDefinition {
        override val id = "harvest_big_stomach"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CAKE)
        // 效果由 BigStomachMixin 在 ConsumableComponent.canConsume 实现
    }

    /** 绿 · 丰饶: **老吴!?** (猫叫的拟声) —— 猫不再害怕你, 反而会主动攻击你;
     *  你杀死猫必掉 3 根线, 并额外掉落 1 件猫的礼物 (高概率为幻翼膜) */
    object Yowl : PerkDefinition {
        override val id = "harvest_yowl"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.STRING)

        /** 猫的礼物物品池: 幻翼膜权重 60, 其余各 10 (幻翼膜 50%) */
        private val GIFT_ITEMS = listOf(
            Items.PHANTOM_MEMBRANE to 60,
            Items.RABBIT_HIDE to 10,
            Items.RABBIT_FOOT to 10,
            Items.CHICKEN to 10,
            Items.FEATHER to 10,
            Items.ROTTEN_FLESH to 10,
            Items.STRING to 10,
        )

        override fun onKill(ctx: PerkContext, victim: net.minecraft.world.entity.LivingEntity) {
            if (victim !is net.minecraft.world.entity.animal.feline.Cat) return
            val world = ctx.player.level() as? ServerLevel ?: return
            victim.spawnAtLocation(world, ItemStack(Items.STRING, 3))

            // 额外掉落 1 件猫的礼物 (幻翼膜高概率)
            val total = GIFT_ITEMS.sumOf { it.second }
            var roll = ctx.player.random.nextInt(total)
            for ((item, weight) in GIFT_ITEMS) {
                roll -= weight
                if (roll < 0) {
                    victim.spawnAtLocation(world, ItemStack(item))
                    return
                }
            }
        }
    }

    /** 蓝 · 唤法: **大狗嚼** —— 获得时召唤 **1 只属于你的狼**,
     *  之后每 **3 分钟**再召唤 1 只:
     *  血量 50 (原版驯服狼 20)、攻击 6 (原版驯服狼 4), 已驯服且不会自然消失。 */
    object BigDog : PerkDefinition {
        override val id = "summon_big_dog"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.BONE)

        /** 召唤间隔: 3 分钟 (onTick 每 20 tick 驱动一次) */
        private const val SUMMON_INTERVAL_TICKS = 3 * 60 * 20

        /** 剩余 tick 存在 state.memory 里; 用倒计时而不是绝对 tick, 服务器重启也不会算错 */
        private const val TIMER_KEY = "big_dog_timer"

        /** 固定属性: 血量 50 / 攻击 6 */
        private const val DOG_HEALTH = 50.0
        private const val DOG_ATTACK = 6.0

        override fun onAcquire(ctx: PerkContext) {
            summon(ctx)
            ctx.state.memory[TIMER_KEY] = SUMMON_INTERVAL_TICKS.toString()
        }

        override fun onTick(ctx: PerkContext) {
            val remaining = (ctx.state.memory[TIMER_KEY]?.toIntOrNull() ?: SUMMON_INTERVAL_TICKS) - 20
            if (remaining <= 0) {
                // 死亡/旁观时先不重置计时, 等能召唤了立刻补上
                if (summon(ctx)) {
                    ctx.state.memory[TIMER_KEY] = SUMMON_INTERVAL_TICKS.toString()
                } else {
                    ctx.state.memory[TIMER_KEY] = "0"
                }
            } else {
                ctx.state.memory[TIMER_KEY] = remaining.toString()
            }
        }

        override fun onRemove(ctx: PerkContext) {
            ctx.state.memory.remove(TIMER_KEY)
        }

        /** @return 是否成功召唤 (死亡/旁观/取不到世界时为 false) */
        private fun summon(ctx: PerkContext): Boolean {
            val player = ctx.player
            if (!player.isAlive || player.isSpectator) return false
            val world = player.level() as? ServerLevel ?: return false

            val wolf = net.minecraft.world.entity.animal.wolf.Wolf(
                net.minecraft.world.entity.EntityTypes.WOLF, world,
            )
            // 召唤在玩家右侧, 朝向与玩家一致
            wolf.snapTo(player.x + 1.0, player.y, player.z, player.yRot, player.xRot)
            wolf.setOwner(player)
            // ★ setTamed(true, true) 的第二个参数会让它顺便把属性改写成"驯服狼"的默认值
            //   (20 血), 所以属性覆盖必须放在它**之后** —— 否则会被这一句覆盖掉。
            wolf.setTame(true, true)
            wolf.setPersistenceRequired()

            wolf.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                ?.baseValue = DOG_HEALTH
            wolf.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                ?.baseValue = DOG_ATTACK
            // 只提高上限不会回血, 要手动补满 (否则狗身上仍是驯服时的 20 血)
            wolf.health = DOG_HEALTH.toFloat()

            world.addFreshEntity(wolf)
            return true
        }
    }

    /** 紫 · 唤法: **宝可梦** —— 获得 4 个鸡蛋; 你扔出的鸡蛋砸中非 Boss 生物时,
     *  把它变成对应的刷怪蛋 (生物消失)。实现见 PokemonEggMixin。 */
    object Pokemon : PerkDefinition {
        override val id = "summon_pokemon"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.EGG)

        /** 获得时附赠的鸡蛋数量 */
        private const val STARTING_EGGS = 16

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.EGG, STARTING_EGGS))
        }
    }

    /** 绿 · 丰饶: **植岷** —— 获得 8 个随机树苗 */
    object Saplings : PerkDefinition {
        override val id = "harvest_saplings"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.OAK_SAPLING)

        /** 发放数量 */
        private const val COUNT = 8

        /** 可开出的树苗 (含红树胎生苗与 1.21.4 的苍白橡木) */
        private val POOL = listOf(
            Items.OAK_SAPLING,
            Items.SPRUCE_SAPLING,
            Items.BIRCH_SAPLING,
            Items.JUNGLE_SAPLING,
            Items.ACACIA_SAPLING,
            Items.DARK_OAK_SAPLING,
            Items.MANGROVE_PROPAGULE,
            Items.CHERRY_SAPLING,
            Items.PALE_OAK_SAPLING,
        )

        override fun onAcquire(ctx: PerkContext) {
            val random = ctx.player.random
            repeat(COUNT) {
                PerkEffects.giveStack(ctx.player, ItemStack(POOL[random.nextInt(POOL.size)]))
            }
        }
    }

    /** 绿 · 丰饶: **拾穗** —— 收获成熟作物时, 50% 概率额外获得一个随机种子 */
    object Gleaning : PerkDefinition {
        override val id = "harvest_gleaning"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.WHEAT_SEEDS)
    }

    /** 蓝 · 唤法 · 贮藏: **乐魂** —— 在词条列表点击使用, 召唤一只**自带挽具、永久速度 I** 的快乐恶魂
     *  (1.21.6+ 的飞行坐骑), 并获得 2 根拴绳 */
    object HappyGhast : PerkDefinition {
        override val id = "summon_happy_ghast"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.HARNESS.white())

        override val isOneTime = true
        /** 16 色挽具, 随机挑一个 */
        private val HARNESSES = Items.HARNESS.asList()

        /** 赠送的拴绳数量 */
        private const val LEAD_COUNT = 2

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            val world = player.level() as? ServerLevel ?: return

            val ghast = net.minecraft.world.entity.animal.happyghast.HappyGhast(
                net.minecraft.world.entity.EntityTypes.HAPPY_GHAST, world,
            )
            // 恶魂体积大, 生成在头顶两格避免卡进方块
            ghast.snapTo(
                player.x, player.y + 2.0, player.z,
                player.yRot, player.xRot,
            )
            ghast.setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.BODY,
                ItemStack(HARNESSES[player.random.nextInt(HARNESSES.size)]),
            )
            ghast.setPersistenceRequired()
            // 永久速度 I: 快乐恶魂飞得慢, 给个常驻加速 (不显示粒子, 免得一直冒烟)
            ghast.addEffect(
                MobEffectInstance(
                    MobEffects.SPEED,
                    MobEffectInstance.INFINITE_DURATION,
                    0,
                    false,
                    false,
                )
            )
            // 速度效果只加 MOVEMENT_SPEED, 而快乐恶魂的自主飞行和骑乘都读 FLYING_SPEED,
            // 所以额外给飞行速度挂一份等量 (+20%) 的永久修饰符, 否则"永久速度 I"对飞行毫无作用
            ghast.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED)?.addPermanentModifier(
                net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        "random-perks-bingo", "happy_ghast_flying",
                    ),
                    0.2,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                )
            )
            world.addFreshEntity(ghast)

            PerkEffects.giveStack(player, ItemStack(Items.LEAD, LEAD_COUNT))

            ctx.state.markUsed(id)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)
        }
    }

    /** 金 · 丰饶: **饱食终日** —— 每 1 分 30 秒获得 7 秒的饱和效果 */
    object WellFed : PerkDefinition {
        override val id = "harvest_well_fed"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.GOLDEN_CARROT)

        /** 触发间隔 (秒); onTick 每秒触发一次 */
        private const val INTERVAL_SECONDS = 90

        /** 饱和效果持续时间: 7 秒 */
        private const val EFFECT_TICKS = 7 * 20

        private const val TIMER_KEY = "well_fed_seconds"

        override fun onAcquire(ctx: PerkContext) {
            ctx.state.memory[TIMER_KEY] = "0"
        }

        override fun onTick(ctx: PerkContext) {
            val seconds = (ctx.state.memory[TIMER_KEY]?.toIntOrNull() ?: 0) + 1
            if (seconds < INTERVAL_SECONDS) {
                ctx.state.memory[TIMER_KEY] = seconds.toString()
                return
            }
            ctx.state.memory[TIMER_KEY] = "0"
            ctx.player.addEffect(
                MobEffectInstance(MobEffects.SATURATION, EFFECT_TICKS, 0)
            )
        }
    }

    /** 绿 · 丰饶 · 贮藏: **世界一直下雨** —— 在词条列表点击使用, 自选天气 (晴 / 雨 / 雷雨),
     *  主世界持续 10 分钟。流程见 [WeatherService]。 */
    object AlwaysRain : PerkDefinition {
        override val id = WeatherService.PERK_ID
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.LIGHTNING_ROD.weathering().unaffected())

        override val isOneTime = true

        override fun onActivate(ctx: PerkContext) = WeatherService.handleRequest(ctx.player)
    }

    /** 蓝 · 丰饶: **采蜜人** —— 破坏蜂巢 / 蜂箱时, 额外掉落 2 个蜜脾和 8 瓶蜂蜜。 */
    object Beekeeper : PerkDefinition {
        override val id = "harvest_beekeeper"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.HONEYCOMB)

        private const val HONEYCOMB_COUNT = 2
        private const val HONEY_BOTTLE_COUNT = 8

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            if (!blockState.`is`(net.minecraft.tags.BlockTags.BEEHIVES)) return
            val world = ctx.player.level() as? ServerLevel ?: return

            net.minecraft.world.level.block.Block.popResource(world, pos, ItemStack(Items.HONEYCOMB, HONEYCOMB_COUNT))
            net.minecraft.world.level.block.Block.popResource(world, pos, ItemStack(Items.HONEY_BOTTLE, HONEY_BOTTLE_COUNT))
        }
    }

    /** 绿 · 丰饶: **叮咚鸡** —— 获得 4 个鸡刷怪蛋; 之后可以用**任意染料**右键**任意**鸡给它染色
     *  (纯外观, 不影响掉落与行为)。染色逻辑在 [ChickenDyeService]。 */
    object DingDongChicken : PerkDefinition {
        override val id = ChickenDyeService.PERK_ID
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CHICKEN_SPAWN_EGG)

        private const val EGGS = 4

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CHICKEN_SPAWN_EGG, EGGS))
        }
    }

    /** 蓝 · **渔者之幸** —— 钓鱼时默认拥有钓饵 III 与海之眷顾 III (不需要鱼竿附魔);
     *  每次钓起额外有 50% 概率多钓一个鹦鹉螺壳。
     *  附魔效果由 FishingEnchantMixin 在浮标创建时注入, 鹦鹉螺壳由 FishingLootMixin 追加。 */
    object LuckyFishing : PerkDefinition {
        override val id = "harvest_lucky_fishing"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.NAUTILUS_SHELL)
    }

    /** 紫 · 丰饶: **变群友** —— 你永久变成一只猪:
     *   - 视角高度与碰撞箱变成猪的 (0.9 格高, 可以站进 1 格高的空间);
     *   - 模型真的渲染成猪, 但头部保留你自己的皮肤;
     *   - 每 30 秒获得 1 根金胡萝卜。
     *  (渲染与视角伪装由客户端 PigMorphRenderer / MorphEyeHeightMixin 完成, 实体本身不变) */
    object PigMorph : PerkDefinition {
        override val id = "harvest_pig_morph"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.PIG_SPAWN_EGG)

        /** 金胡萝卜的发放间隔 (秒); onTick 每秒触发一次 */
        private const val CARROT_INTERVAL_SECONDS = 30
        private const val CARROT_TIMER_KEY = "pig_carrot_seconds"

        override fun onAcquire(ctx: PerkContext) {
            ctx.state.memory[CARROT_TIMER_KEY] = "0"
            // 立刻变形, 不等下一秒的对账
            PigMorphService.refresh(ctx.player)
        }

        override fun onRespawn(ctx: PerkContext) {
            // 重生后是新的实体 id, 需要重新打标记
            PigMorphService.refresh(ctx.player)
        }

        override fun onTick(ctx: PerkContext) {
            val seconds = (ctx.state.memory[CARROT_TIMER_KEY]?.toIntOrNull() ?: 0) + 1
            if (seconds < CARROT_INTERVAL_SECONDS) {
                ctx.state.memory[CARROT_TIMER_KEY] = seconds.toString()
                return
            }
            ctx.state.memory[CARROT_TIMER_KEY] = "0"
            PerkEffects.giveStack(ctx.player, ItemStack(Items.GOLDEN_CARROT))
        }
    }

    /** 蓝 · 丰饶: **海带成骨** —— 右键干海带块, 把它变成骨块 (交互见 KelpToBoneService) */
    object KelpToBone : PerkDefinition {
        override val id = KelpToBoneService.PERK_ID
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.BONE_BLOCK)
    }

    /** 绿 · 丰饶: **骨粉撒花** —— 对草方块使用骨粉时, 额外长出 2 朵随机花
     *  (交互见 BoneMealFlowerService + BoneMealFlowerMixin) */
    object BoneMealFlowers : PerkDefinition {
        override val id = BoneMealFlowerService.PERK_ID
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.POPPY)
    }

    /** 金 · 丰饶: **苔藓侵蚀** —— 攻击敌方玩家时, 随机把他主背包里的 2 件物品
     *  变成繁茂洞穴物品 (5 秒冷却; 同队不触发) */
    object FungalInfection : PerkDefinition {
        override val id = FungalInfectionService.PERK_ID
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SPORE_BLOSSOM)

        /** 自身感染间隔: 1 分 45 秒 */
        private const val SELF_INTERVAL_SECONDS = 105

        private const val SELF_TIMER_KEY = "fungal_self_seconds"

        override fun onAcquire(ctx: PerkContext) {
            ctx.state.memory[SELF_TIMER_KEY] = "0"
        }

        override fun onTick(ctx: PerkContext) {
            val seconds = (ctx.state.memory[SELF_TIMER_KEY]?.toIntOrNull() ?: 0) + 1
            if (seconds < SELF_INTERVAL_SECONDS) {
                ctx.state.memory[SELF_TIMER_KEY] = seconds.toString()
                return
            }
            ctx.state.memory[SELF_TIMER_KEY] = "0"
            FungalInfectionService.corruptOwnBackpack(ctx.player)
        }
    }

    /** 紫 · 丰饶: **细嚼慢咽** —— 进食食物期间, 受到的伤害 -75%
     *  (伤害修正在 LivingEntityDamageMixin) */
    object SlowChewing : PerkDefinition {
        override val id = "harvest_slow_chewing"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.BREAD)
    }

    fun registerAll() {
        PerkRegistry.register(FieldMender)
        PerkRegistry.register(Gardener)
        PerkRegistry.register(Lumberjack)
        PerkRegistry.register(Herder)
        PerkRegistry.register(Timber)
        PerkRegistry.register(TreeFeller)
        PerkRegistry.register(Bounty)
        PerkRegistry.register(BountifulMeal)
        PerkRegistry.register(Gourmet)
        PerkRegistry.register(Rancher)
        PerkRegistry.register(CatAura)
        PerkRegistry.register(SummonHorse)
        PerkRegistry.register(BigStomach)
        PerkRegistry.register(Yowl)
        PerkRegistry.register(BigDog)
        PerkRegistry.register(Pokemon)
        PerkRegistry.register(Saplings)
        PerkRegistry.register(Gleaning)
        PerkRegistry.register(HappyGhast)
        PerkRegistry.register(DingDongChicken)
        PerkRegistry.register(LuckyFishing)
        PerkRegistry.register(PigMorph)
        PerkRegistry.register(WellFed)
        PerkRegistry.register(Beekeeper)
        PerkRegistry.register(AlwaysRain)
        PerkRegistry.register(KelpToBone)
        PerkRegistry.register(BoneMealFlowers)
        PerkRegistry.register(FungalInfection)
        PerkRegistry.register(SlowChewing)
    }
}
