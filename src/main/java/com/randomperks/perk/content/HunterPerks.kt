package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.PerkSession
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.state.PerkStateManager
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.PotionContentsComponent
import java.util.UUID
import com.randomperks.mixin.MobLootInvoker
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.Monster
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.potion.Potions
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.GameRules
import net.minecraft.world.World

/**
 * 猎杀 HUNTER —— 战斗 / 怪物掉落。
 */
object HunterPerks {

    private val MOD_ATTACK = Identifier.of("random-perks-bingo", "hunter_strength")
    private val MOD_HEALTH = Identifier.of("random-perks-bingo", "hunter_health")

    /** 蓝 · 猎人直觉: 攻击力 +25% */
    object Strength : PerkDefinition {
        override val id = "hunter_strength"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.IRON_SWORD)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_ATTACK, 0.25, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_ATTACK)
    }

    /** 蓝 · 强壮体魄: 最大生命 +4 */
    object Vitality : PerkDefinition {
        override val id = "hunter_vitality"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.GOLDEN_APPLE)

        override fun onAcquire(ctx: PerkContext) {
            apply(ctx)
            ctx.player.heal(4f)
        }

        override fun onRespawn(ctx: PerkContext) {
            apply(ctx)
            // 原版重生会把血量设成"当时的最大值"(20) —— ServerPlayerEntity.copyFrom 的 alive=false 分支,
            // 而 Fabric 的 AFTER_RESPAWN 在 copyFrom 之后才触发; 不补的话重生后永远是 20/24
            ctx.player.heal(4f)
        }

        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.MAX_HEALTH, MOD_HEALTH, 4.0, EntityAttributeModifier.Operation.ADD_VALUE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MAX_HEALTH, MOD_HEALTH)
    }

    /** 蓝 · 战地急救: 击杀生物回复 2 生命 */
    object Vampire : PerkDefinition {
        override val id = "hunter_vampire"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.RED_DYE)

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            ctx.player.heal(2f)
        }
    }

    /** 紫 · 剥皮专家: 击杀怪物 30% 额外掉落一件战利品
     *  —— 以**真实掉落物**形态落在怪物死亡位置 (带默认拾取延迟), 奖池为常见材料 + 中档材料 */
    object Scavenger : PerkDefinition {
        override val id = "hunter_scavenger"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.BONE)

        /** 奖池 (物品 → 权重): 常见材料权重 3、中档材料权重 1
         *  —— 常见 6 项×3 = 18, 中档 8 项×1 = 8 → 常见占 69%、中档占 31% */
        private val loot = listOf(
            Items.BONE to 3, Items.STRING to 3, Items.LEATHER to 3,
            Items.ROTTEN_FLESH to 3, Items.FEATHER to 3, Items.GUNPOWDER to 3,
            Items.SPIDER_EYE to 1, Items.SLIME_BALL to 1, Items.MAGMA_CREAM to 1, Items.COBWEB to 1,
            Items.PHANTOM_MEMBRANE to 1, Items.BLAZE_POWDER to 1, Items.ENDER_PEARL to 1, Items.GHAST_TEAR to 1,
        )

        private val lootTotalWeight = loot.sumOf { it.second }

        /** 按权重随机抽一件 */
        private fun rollLoot(random: net.minecraft.util.math.random.Random): net.minecraft.item.Item {
            var roll = random.nextInt(lootTotalWeight)
            for ((item, weight) in loot) {
                if (roll < weight) return item
                roll -= weight
            }
            error("loot pool weights exhausted")
        }

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            // "怪物"必须用 Monster 接口判定: HostileEntity 只是个抽象类, 史莱姆 / 岩浆怪 / 恶魂 /
            // 幻翼 / 潜影贝 / 疣猪兽 / 末影龙 都只实现 Monster 接口而不继承它, 用类判定会把这批全漏掉
            if (victim !is Monster) return
            if (ctx.player.random.nextFloat() >= 0.30f) return
            val world = ctx.player.world as? ServerWorld ?: return
            victim.dropStack(world, ItemStack(rollLoot(ctx.player.random)))
        }
    }

    /** 金 · 灵魂收割: 击杀 8% 概率额外掉落**与该怪物对应**的战利品 (僵尸只掉僵尸会有的东西) */
    object SoulHarvest : PerkDefinition {
        override val id = "hunter_soul_harvest"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.NETHER_STAR)

        private const val CHANCE = 0.08f

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            // 同"剥皮专家": 用 Monster 接口判定 (HostileEntity 会漏掉史莱姆/恶魂/幻翼/末影龙等)
            if (victim !is Monster) return
            if (ctx.player.random.nextFloat() >= CHANCE) return

            val world = ctx.player.world as? ServerWorld ?: return
            val prizes = prizesFor(victim)
            if (prizes.isNotEmpty()) {
                // 有专属条目: 直接以掉落物形态落在怪物死亡位置
                prizes.forEach { victim.dropStack(world, it) }
            } else {
                // 未收录的怪物 (蠹虫/末影龙/凋灵/模组怪等): 回退成"让它自己的战利品表再滚一次",
                // 掉出来的东西天生与这只怪对应
                val source = victim.recentDamageSource ?: return
                (victim as? MobLootInvoker)?.invokeDropLoot(world, source, true)
            }
            ctx.player.sendMessage(net.minecraft.text.Text.translatable("random-perks-bingo.message.loot_bag"), false)
        }

        /**
         * 每种怪物的"灵魂战利品" —— 全部取自它自己的原版掉落表, 但**加量**,
         * 并把原本的稀有掉落 (头颅 / 唱片 / 蛙明灯 / 湿海绵 / 不死图腾 / 不祥之瓶) 转成稳定奖励,
         * 让 8% 的触发真正有实感。
         *
         * 依据: 1.21.7 的 `data/minecraft/loot_table/entities/` 下各实体的战利品表。
         */
        private fun prizesFor(victim: LivingEntity): List<ItemStack> = when (victim.type) {
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE_VILLAGER -> listOf(
                ItemStack(Items.IRON_INGOT, 3), ItemStack(Items.ROTTEN_FLESH, 16),
            )
            EntityType.DROWNED -> listOf(
                ItemStack(Items.COPPER_INGOT, 4), ItemStack(Items.ROTTEN_FLESH, 16),
            )
            EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED -> listOf(
                ItemStack(Items.BONE, 8), ItemStack(Items.ARROW, 16),
            )
            EntityType.CREEPER -> listOf(
                ItemStack(Items.GUNPOWDER, 8), ItemStack(Items.MUSIC_DISC_CAT),
            )
            EntityType.SPIDER, EntityType.CAVE_SPIDER -> listOf(
                ItemStack(Items.STRING, 8), ItemStack(Items.SPIDER_EYE, 2),
            )
            EntityType.ENDERMAN -> listOf(ItemStack(Items.ENDER_PEARL, 4))
            EntityType.BLAZE -> listOf(ItemStack(Items.BLAZE_ROD, 4))
            EntityType.GHAST -> listOf(
                ItemStack(Items.GHAST_TEAR, 2), ItemStack(Items.GUNPOWDER, 8),
            )
            EntityType.WITHER_SKELETON -> listOf(
                ItemStack(Items.WITHER_SKELETON_SKULL), ItemStack(Items.COAL, 8),
            )
            EntityType.ZOMBIFIED_PIGLIN -> listOf(
                ItemStack(Items.GOLD_INGOT, 4), ItemStack(Items.GOLD_NUGGET, 16),
            )
            EntityType.PIGLIN -> listOf(ItemStack(Items.GOLD_INGOT, 4))
            EntityType.MAGMA_CUBE -> listOf(
                ItemStack(Items.MAGMA_CREAM, 4), ItemStack(Items.OCHRE_FROGLIGHT),
            )
            EntityType.SLIME -> listOf(ItemStack(Items.SLIME_BALL, 8))
            EntityType.PHANTOM -> listOf(ItemStack(Items.PHANTOM_MEMBRANE, 4))
            EntityType.GUARDIAN -> listOf(
                ItemStack(Items.PRISMARINE_CRYSTALS, 4), ItemStack(Items.COD, 4),
            )
            EntityType.ELDER_GUARDIAN -> listOf(
                ItemStack(Items.WET_SPONGE), ItemStack(Items.PRISMARINE_CRYSTALS, 4),
            )
            EntityType.SHULKER -> listOf(ItemStack(Items.SHULKER_SHELL, 2))
            EntityType.WITCH -> listOf(
                ItemStack(Items.GLOWSTONE_DUST, 8), ItemStack(Items.REDSTONE, 8),
            )
            EntityType.PILLAGER -> listOf(ItemStack(Items.OMINOUS_BOTTLE, 2))
            EntityType.VINDICATOR -> listOf(ItemStack(Items.EMERALD, 4))
            EntityType.EVOKER -> listOf(
                ItemStack(Items.TOTEM_OF_UNDYING), ItemStack(Items.EMERALD, 4),
            )
            EntityType.RAVAGER -> listOf(ItemStack(Items.SADDLE))
            else -> emptyList()
        }
    }

    /** 蓝 · 猎杀: 未穿护甲时攻击力 +40% (高风险高收益; 鞘翅不算护甲) */
    object BareRage : PerkDefinition {
        override val id = "hunter_bare_rage"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.DIAMOND_SWORD)

        private val MOD_BARE_RAGE = Identifier.of("random-perks-bingo", "hunter_bare_rage")

        override fun onAcquire(ctx: PerkContext) = refresh(ctx)
        override fun onRespawn(ctx: PerkContext) = refresh(ctx)

        /** 穿/脱护甲的**瞬间**重算 —— EquipmentChangeMixin(手上换) + PlayerInventoryEquipMixin(界面里拖)
         *  两条路径都分发事件, 与"铁皮"同一机制, 不再每秒轮询 */
        override fun onEquipmentChange(ctx: PerkContext) = refresh(ctx)

        private fun refresh(ctx: PerkContext) {
            if (PerkEffects.hasNoArmor(ctx.player)) {
                PerkEffects.setAttribute(
                    ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_BARE_RAGE,
                    0.40, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                )
            } else {
                PerkEffects.removeAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_BARE_RAGE)
            }
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_BARE_RAGE)
    }

    /** 紫 · 猎杀: 始终标记**距离最近**的敌方玩家 (每秒跟随切换) —— 持续显示其方位/坐标/距离并高亮轮廓;
     *  **目标 256 格以内**时移速 +50% */
    object MarkedPrey : PerkDefinition {
        override val id = "hunter_marked_prey"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.COMPASS)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "marked_prey_speed")
        private const val TARGET_KEY = "marked_prey_target"

        /** 在目标这个距离内就常驻 +50% 移速 */
        private const val SPEED_RADIUS = 256

        override fun onAcquire(ctx: PerkContext) = markTarget(ctx)

        override fun onTick(ctx: PerkContext) = refresh(ctx)

        override fun onRespawn(ctx: PerkContext) = refresh(ctx)

        private fun refresh(ctx: PerkContext) {
            val player = ctx.player

            // **始终跟随当前最近的敌人**: 每秒重新评估一次, 最近的人换了就立刻切换目标
            val target = nearestEnemy(player)
            if (target == null) {
                // 场上没有敌人 (全部离线, 或只剩自己): 清掉加速与标记, 有人上线后会自动重新锁定
                PerkEffects.removeAttribute(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
                ctx.state.memory.remove(TARGET_KEY)
                return
            }

            // 记下当前目标 id (仅供调试/其他词条参考; 加速只看距离, 不再需要距离基准)
            ctx.state.memory[TARGET_KEY] = target.uuid.toString()

            // 跨维度: 两个世界的坐标硬算出来的距离/方位没有意义 → 只提示目标在哪个维度, 不给加速
            if (target.world !== player.world) {
                PerkEffects.removeAttribute(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
                player.sendMessage(
                    Text.translatable(
                        "random-perks-bingo.message.prey_tracker_dimension",
                        target.name,
                        dimensionName(target.world),
                    ),
                    true,
                )
                return
            }

            // 轮廓高亮: 仅同队可见 (每秒刷新, 5 秒过期)
            GlowMarkService.markFor(GlowMarkService.teamViewers(player), target, 5_000L)

            // **在目标 256 格以内就常驻 +50% 移速**
            // (旧逻辑是"这一秒比上一秒更近才加速", 走动/绕圈时会反复开关, 手感很差)
            val dist = player.pos.distanceTo(target.pos).toInt()
            if (dist <= SPEED_RADIUS) {
                PerkEffects.setAttribute(
                    player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                    0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                )
            } else {
                PerkEffects.removeAttribute(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
            }

            // actionbar: 方位 + 坐标 + 距离
            player.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.prey_tracker",
                    target.name,
                    dist,
                    target.blockPos.x,
                    target.blockPos.z,
                    directionHint(player.pos.x, player.pos.z, target.pos.x, target.pos.z),
                ),
                true,
            )
        }

        /** 当前**距离最近**的敌方在线玩家 (以原版队伍判断归属; 无队伍时视为全体为敌)。
         *  距离只在同维度内可比 (跨维度坐标无意义) → 同维度有敌人时取其中最近的;
         *  同维度一个敌人都没有时, 才退化为其他维度中的一名。 */
        private fun nearestEnemy(player: ServerPlayerEntity): ServerPlayerEntity? {
            val server = player.server ?: return null
            val myTeam = player.scoreboardTeam?.name
            val enemies = server.playerManager.playerList.filter { other ->
                !other.isSpectator &&
                    other.uuid != player.uuid &&
                    (myTeam == null || other.scoreboardTeam?.name != myTeam)
            }
            if (enemies.isEmpty()) return null
            return enemies.filter { it.world === player.world }
                .minByOrNull { it.pos.squaredDistanceTo(player.pos) }
                ?: enemies.first()
        }

        /** 获得词条时立即锁定当前最近的敌人 (之后由 refresh 每秒跟随切换) */
        private fun markTarget(ctx: PerkContext) {
            val target = nearestEnemy(ctx.player) ?: return
            ctx.state.memory[TARGET_KEY] = target.uuid.toString()
        }

        /** 方位提示 (N/S/E/W 组合; MC 中 +Z 为南, +X 为东) */
        private fun directionHint(px: Double, pz: Double, tx: Double, tz: Double): String {
            val dx = tx - px
            val dz = tz - pz
            val ns = when {
                dz > 8.0 -> "S"
                dz < -8.0 -> "N"
                else -> ""
            }
            val ew = when {
                dx > 8.0 -> "E"
                dx < -8.0 -> "W"
                else -> ""
            }
            return (ns + ew).ifEmpty { "~" }
        }

        /** 维度显示名 (原版三维度走本地化; 其他维度直接显示 id) */
        private fun dimensionName(world: World): Text = when (world.registryKey) {
            World.OVERWORLD -> Text.translatable("random-perks-bingo.dimension.overworld")
            World.NETHER -> Text.translatable("random-perks-bingo.dimension.nether")
            World.END -> Text.translatable("random-perks-bingo.dimension.end")
            else -> Text.literal(world.registryKey.value.toString())
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
            ctx.state.memory.remove(TARGET_KEY)
        }
    }

    /** 紫 · 猎杀: 击杀生物时 50% 概率让它的**掉落表再滚一次** (= 额外掉落一份它自己的战利品),
     *  但受到其他生物 2 倍伤害。掉落由生物自己结算 (与"剥皮专家/灵魂收割"同范式, 不扫地面物品) */
    object GreedyHands : PerkDefinition {
        override val id = "hunter_greedy_hands"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.GOLDEN_SWORD)

        private const val EXTRA_DROP_CHANCE = 0.5f

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (victim !is MobEntity) return                                    // 只有生物有掉落表 (玩家/盔甲架没有)
            if (ctx.player.random.nextFloat() >= EXTRA_DROP_CHANCE) return      // 每次击杀掷一次, 不是每件物品
            val world = ctx.player.world as? ServerWorld ?: return
            if (!world.gameRules.getBoolean(GameRules.DO_MOB_LOOT)) return      // 跟随原版"生物掉落"游戏规则
            val source = victim.recentDamageSource ?: return
            // 让这只怪的掉落表再滚一次 —— 掉落物直接从它自己身上出来; 与原版死亡掉落同一套结算
            (victim as MobLootInvoker).invokeDropLoot(world, source, true)
        }
        // "受到其他生物 2 倍伤害" 由 LivingEntityDamageMixin 实现
    }

    /** 紫 · 猎杀: 每击杀一只生物, 立即恢复 2 点饥饿值与 2 点饱和度 */
    object Predator : PerkDefinition {
        override val id = "hunter_predator"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.COOKED_BEEF)

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            // add(food, saturationModifier): 饱和度增量 = food * saturationModifier * 2
            ctx.player.hungerManager.add(2, 0.5f)
        }
    }

    /** 金 · 猎杀: 每击杀一名**敌方玩家** (非自己/非同队), 永久 +2 最大生命;
     *  通过本词条最多累计 +20 (10 颗心), 重生保留 */
    object Headhunter : PerkDefinition {
        override val id = "hunter_headhunter"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.PLAYER_HEAD)

        private val MOD_MAX_HEALTH = Identifier.of("random-perks-bingo", "headhunter_health")
        private const val BONUS_KEY = "headhunter_bonus"

        /** 每击杀一名敌方玩家 +2 最大生命 (1 颗心) */
        private const val BONUS_PER_KILL = 2.0

        /** 通过这个词条最多累计 +20 最大生命 (10 颗心) */
        private const val MAX_BONUS = 20.0

        override fun onAcquire(ctx: PerkContext) = applyBonus(ctx)

        override fun onRespawn(ctx: PerkContext) {
            applyBonus(ctx)
            // 原版重生把血量设成"当时的最大值"(20), 而 Fabric 的 AFTER_RESPAWN 在其后才触发,
            // 不补的话重生后血条永远缺 N 点 (与"强壮体魄"同一处理)
            val bonus = ctx.state.memory[BONUS_KEY]?.toDoubleOrNull() ?: return
            ctx.player.heal(bonus.toFloat())
        }

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (victim !is ServerPlayerEntity) return
            if (victim.uuid == ctx.player.uuid) return                     // 自爆不算 ("每击杀一名其他玩家")
            // 只算敌方: 与"猎杀印记"同一口径 (同队不算; 自己无队伍时视为全体为敌)
            val myTeam = ctx.player.scoreboardTeam?.name
            if (myTeam != null && victim.scoreboardTeam?.name == myTeam) return

            val current = ctx.state.memory[BONUS_KEY]?.toDoubleOrNull() ?: 0.0
            if (current >= MAX_BONUS) return                      // 已到上限, 不再叠加

            val bonus = minOf(MAX_BONUS, current + BONUS_PER_KILL)
            ctx.state.memory[BONUS_KEY] = bonus.toString()
            applyBonus(ctx)
            // 按**实际增加量**回血 (接近上限时可能只加 1)
            ctx.player.heal((bonus - current).toFloat())
        }

        private fun applyBonus(ctx: PerkContext) {
            val bonus = ctx.state.memory[BONUS_KEY]?.toDoubleOrNull() ?: return
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.MAX_HEALTH, MOD_MAX_HEALTH,
                bonus, EntityAttributeModifier.Operation.ADD_VALUE,
            )
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MAX_HEALTH, MOD_MAX_HEALTH)
    }

    /** 紫 · 猎杀: 获得 16 个火焰弹 —— 手持右键即可像恶魂一样发射火球 (发射逻辑见 FireballService) */
    object Fireball : PerkDefinition {
        override val id = "hunter_fireball"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.FIRE_CHARGE)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.FIRE_CHARGE, 16))
        }
    }

    /** 蓝 · 猎杀: 获得时弹出玩家选择器, 选中**任意一名玩家**后在他的位置召唤一次闪电;
     *  16 格内若有引雷针, 闪电会被引导过去 */
    object CallLightning : PerkDefinition {
        override val id = "hunter_call_lightning"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.LIGHTNING_ROD)

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.lightning_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "lightning",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 在他的位置劈雷 */
        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return

            // 校验: 必须是当下的合法候选 —— 否则客户端伪造 uuid 就能劈任意人 (含队友)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return
            strike(target)
        }

        /** 候选: **所有在线玩家** —— 不限队伍、不限维度, 也包括自己 (跨维度劈雷对目标依然有效,
         *  只是施法者看不到, 所以不排除); 旁观者除外 */
        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> =
            player.server?.playerManager?.playerList?.filter { !it.isSpectator } ?: emptyList()

        /** 在目标位置劈一次闪电 (16 格内的引雷针会把闪电引过去) */
        private fun strike(target: ServerPlayerEntity) {
            val world = target.world as? ServerWorld ?: return

            val rod = findNearbyLightningRod(world, target.blockPos, 16)
            val lightning = net.minecraft.entity.EntityType.LIGHTNING_BOLT
                .create(world, net.minecraft.entity.SpawnReason.TRIGGERED) ?: return

            if (rod != null) {
                lightning.refreshPositionAfterTeleport(rod.x + 0.5, rod.y.toDouble(), rod.z + 0.5)
            } else {
                lightning.refreshPositionAfterTeleport(target.x, target.y, target.z)
            }
            world.spawnEntity(lightning)
        }

        /** 在半径内寻找最近的引雷针 (找不到返回 null) */
        private fun findNearbyLightningRod(
            world: ServerWorld,
            center: net.minecraft.util.math.BlockPos,
            radius: Int,
        ): net.minecraft.util.math.BlockPos? {
            var best: net.minecraft.util.math.BlockPos? = null
            var bestDist = Int.MAX_VALUE

            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    for (dz in -radius..radius) {
                        val pos = center.add(dx, dy, dz)
                        if (!world.getBlockState(pos).isOf(net.minecraft.block.Blocks.LIGHTNING_ROD)) continue
                        val dist = dx * dx + dy * dy + dz * dz
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pos.toImmutable()
                        }
                    }
                }
            }
            return best
        }
    }

    /** 紫 · 猎杀: 获得一把**秒人斧** —— **金斧 + 锋利 1145 + 1 点耐久**。
     *
     *  与社区（EaseCation 服务器 / Funprops Killaxe）同款思路: 不靠自定义秒杀逻辑,
     *  而是把锋利堆到荒谬的等级 —— 1145 级的单次附魔伤害 ≈ 1 + 0.5 × 1144 ≈ 573,
     *  配上金斧基础伤害足以一击带走几乎任何生物 (含玩家); 代价是攻击一次斧头就坏。 */
    object NyaAxe : PerkDefinition {
        override val id = "hunter_nya_axe"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.GOLDEN_AXE)

        /** 锋利等级 (EaseCation 的经典数值) */
        private const val SHARPNESS_LEVEL = 1145

        override fun onAcquire(ctx: PerkContext) {
            val axe = ItemStack(Items.GOLDEN_AXE)
            PerkEffects.enchant(ctx.player, axe, Enchantments.SHARPNESS, SHARPNESS_LEVEL)
            // 只有 1 点耐久: 攻击一次由原版耐久结算自动损坏
            axe.damage = axe.maxDamage - 1
            // ★ 工作惩罚直接拉满: 铁砧成本一上来就 ≥ 40 级 → 永久"过于昂贵", 修不了也合不了。
            //   这是原版组件, 不显示在 tooltip 上, 也不需要额外 mixin。
            axe.set(DataComponentTypes.REPAIR_COST, 40)
            PerkEffects.giveStack(ctx.player, axe)
        }
    }

    /** 紫 · 猎杀: **老玩家** —— 完全 1.9 战斗更新前的手感:
     *  1) 手持剑时攻击冷却始终视为已蓄满 (由 OldSchoolCombatMixin 实现)
     *  2) **没有横扫攻击** (1.9 新增的机制; 把 SWEEPING_DAMAGE_RATIO 压到 0) */
    object Veteran : PerkDefinition {
        override val id = "hunter_veteran"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.IRON_SWORD)

        private val MOD_SWEEP = Identifier.of("random-perks-bingo", "veteran_no_sweep")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 横扫伤害比例默认 1.0, -1.0 后为 0 (只影响剑的横扫, 对单体攻击无影响) */
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.SWEEPING_DAMAGE_RATIO, MOD_SWEEP,
                -1.0, EntityAttributeModifier.Operation.ADD_VALUE,
            )

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.SWEEPING_DAMAGE_RATIO, MOD_SWEEP)
    }

    /** 蓝 · 猎杀: **不倒翁** —— 受到的击退效果降低 80% */
    object RolyPoly : PerkDefinition {
        override val id = "hunter_roly_poly"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.IRON_BLOCK)

        private val MOD_KNOCKBACK = Identifier.of("random-perks-bingo", "roly_poly_knockback")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 原版击退抗性属性 (0 ~ 1), +0.8 即击退距离只剩 20% */
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK,
                0.8, EntityAttributeModifier.Operation.ADD_VALUE,
            )

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK)
    }

    /** 紫 · 猎杀: **核电轻而易举** —— 获得时弹出玩家选择器, 选中一名玩家后**在他脚下**生成一只
     *  **高亮显示的闪电苦力怕** (charged creeper), 并直接让它仇恨该玩家 */
    object Nuclear : PerkDefinition {
        override val id = "hunter_easy_nuke"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.CREEPER_HEAD)

        private const val SPAWN_RADIUS = 4

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.nuke_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "nuke",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 在他脚下生成闪电苦力怕 */
        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return
            spawnChargedCreeper(target)
            player.sendMessage(
                Text.translatable("random-perks-bingo.message.nuke_spawned", target.name),
                false,
            )
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> =
            player.server?.playerManager?.playerList?.filter { !it.isSpectator } ?: emptyList()

        /** 在目标**脚下**生成一只**发光**的闪电苦力怕, 并直接让它仇恨目标。
         *
         *  仇恨是必须的: 苦力怕靠 CreeperIgniteGoal 点火, 而那个 goal 只在
         *  {@code getTarget() != null} 且距离够近时才会启动 —— 不设目标它就只会站桩。 */
        private fun spawnChargedCreeper(target: ServerPlayerEntity) {
            val world = target.world as? ServerWorld ?: return
            val random = world.random

            // 优先落在目标脚下; 那里站不住时 (水里/墙里/半砖) 再在附近找一块能站的
            val pos = findSpawnPos(target, world) ?: target.blockPos

            val creeper = net.minecraft.entity.EntityType.CREEPER
                .create(world, net.minecraft.entity.SpawnReason.EVENT) ?: return
            creeper.refreshPositionAndAngles(
                pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
                random.nextFloat() * 360f, 0f,
            )
            // 闪电苦力怕 (爆炸强度翻倍) —— 直接写 TrackedData, 不用闪电实体 (避免额外伤害/点燃)
            creeper.dataTracker.set(com.randomperks.mixin.CreeperChargedAccessor.getChargedTracker(), true)
            // 引信 2.5 秒 (原版默认 30 tick = 1.5 秒), 给目标留出反应时间
            // 注: accessor 方法运行期才注入到 CreeperEntity, 编译期需要强转
            (creeper as com.randomperks.mixin.CreeperChargedAccessor).setFuseTime(50)
            creeper.isGlowing = true     // 高亮: 隔着墙也能看到它
            // 仇恨: 没有目标时 CreeperIgniteGoal 不会启动, 苦力怕就只会站着不动
            creeper.setTarget(target)
            world.spawnEntity(creeper)
        }

        /** 目标脚下优先, 站不住时才在 SPAWN_RADIUS 内找 */
        private fun findSpawnPos(
            target: ServerPlayerEntity,
            world: ServerWorld,
        ): net.minecraft.util.math.BlockPos? {
            fun standable(pos: net.minecraft.util.math.BlockPos): Boolean =
                world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())

            val base = target.blockPos
            if (standable(base)) return base

            val random = world.random
            for (i in 0 until 16) {
                val dx = random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS
                val dz = random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS
                val top = world.getTopPosition(
                    net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, base.add(dx, 0, dz),
                )
                if (standable(top)) return top
            }
            return null
        }
    }

    /** 紫 · 猎杀: **这不是挂?** —— 获得一把弓, 箭袋**每 2 分钟**补一次:
     *  随机一种药水 × 4 支 (同一批同一种)。
     *
     *  持词条期间射出的**箭**会自动追踪 16 格内最近的敌对目标
     *  (自己、队友、自己驯服的生物除外; 由 HomingArrowMixin 每 tick 转向);
     *  其中**玩家**目标会被满转向锁定, 并在贴近时直接判定命中。 */
    object HomingBow : PerkDefinition {
        override val id = "hunter_homing_bow"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.BOW)

        /** 每次补充的支数 */
        private const val ARROWS_PER_GRANT = 4

        /** 补充间隔: 2 分钟 (onTick 每 20 tick 驱动一次) */
        private const val INTERVAL_TICKS = 2 * 60 * 20

        /** 上次补箭的 tick 记在 state.memory 里, 不额外维护全局表 */
        private const val TICK_KEY = "homing_quiver_tick"

        /** 随机药水池 —— 箭是射向敌人的, 所以以负面为主 */
        private val TIPPED_POTIONS = listOf(
            Potions.POISON,
            Potions.STRONG_POISON,
            Potions.LONG_POISON,
            Potions.SLOWNESS,
            Potions.STRONG_SLOWNESS,
            Potions.LONG_SLOWNESS,
            Potions.WEAKNESS,
            Potions.LONG_WEAKNESS,
            Potions.HARMING,
            Potions.STRONG_HARMING,
            Potions.TURTLE_MASTER,
            Potions.STRONG_TURTLE_MASTER,
        )

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BOW))
            // 先给一组, 之后每 2 分钟由 onTick 续
            ctx.player.server?.let { ctx.state.memory[TICK_KEY] = it.ticks.toString() }
            giveArrows(ctx)
        }

        override fun onTick(ctx: PerkContext) {
            val now = ctx.player.server?.ticks ?: return
            val last = ctx.state.memory[TICK_KEY]?.toLongOrNull()
            if (last == null) {
                ctx.state.memory[TICK_KEY] = now.toString()
                return
            }
            if (now - last < INTERVAL_TICKS) return
            ctx.state.memory[TICK_KEY] = now.toString()
            giveArrows(ctx)
        }

        /** 发一组: **同一种**随机药水 × 4 支 */
        private fun giveArrows(ctx: PerkContext) {
            val potion = TIPPED_POTIONS[ctx.player.random.nextInt(TIPPED_POTIONS.size)]
            repeat(ARROWS_PER_GRANT) {
                PerkEffects.giveStack(ctx.player, PotionContentsComponent.createStack(Items.TIPPED_ARROW, potion))
            }
        }
    }

    /** 金 · 猎杀: **Jockey** —— 右键任意玩家即可骑到他身上并**接管他的移动与视角**;
     *  骑乘期间受到来自被骑者的伤害降低 75%。按 Shift 下来。
     *
     *  骑乘与操控由 [com.randomperks.game.JockeyService] + 客户端 JockeyInputMixin 配合实现。 */
    object Jockey : PerkDefinition {
        override val id = "hunter_jockey"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.SADDLE)

        override fun onAcquire(ctx: PerkContext) {
            ctx.player.sendMessage(
                Text.translatable("random-perks-bingo.message.jockey_hint"),
                false,
            )
        }

        /** 对局结束/词条被清掉时别把人留在别人身上 */
        override fun onRemove(ctx: PerkContext) {
            if (ctx.player.hasVehicle()) ctx.player.stopRiding()
        }
    }

    /** 金 · 猎杀: **变群友** —— 你的攻击会把目标变成猪 **2 秒** (冷却 8 秒)。
     *  被变成猪的玩家, 猪头会换成他自己的皮肤, 视角高度也会变成猪的高度。
     *  (渲染与视角伪装由客户端 PigMorphRenderer / MorphEyeHeightMixin 完成, 实体本身不变) */
    object PigMorph : PerkDefinition {
        override val id = "hunter_pig_morph"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.PIG_SPAWN_EGG)
    }

    /** 蓝 · 猎杀: **打雪仗** —— 获得 32 个雪球; 你扔出的雪球会变大, 命中造成 3 点伤害并击飞目标 */
    object BigSnowball : PerkDefinition {
        override val id = "hunter_big_snowball"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.SNOWBALL)

        /** 发放的雪球数量 */
        private const val COUNT = 32

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SNOWBALL, COUNT))
        }
    }

    /** 金 · 猎杀: **恶魔契约** —— 你造成的伤害翻倍, 但你受到的**任何**伤害也翻倍。
     *  (两侧都由 LivingEntityDamageMixin 在 damage 的 amount 参数上结算) */
    object DemonPact : PerkDefinition {
        override val id = "hunter_demon_pact"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.WITHER_SKELETON_SKULL)
    }

    /** 紫 · 猎杀: **珠光护手** —— 你的每次攻击都会暴击 (伤害 ×1.5 + 暴击粒子);
     *  代价是剑不再能横扫, 并且永久带着**挖掘疲劳 I**。
     *  (暴击由 PearlGauntletMixin 把原版判定条件逐项 redirect 成恒真) */
    object PearlGauntlet : PerkDefinition {
        override val id = "hunter_pearl_gauntlet"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.DIAMOND_SWORD)

        private val MOD_SWEEP = Identifier.of("random-perks-bingo", "pearl_gauntlet_no_sweep")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) {
            // 剑不再横扫 (与"老玩家"同一机制: 把横扫伤害比例压到 0)
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.SWEEPING_DAMAGE_RATIO, MOD_SWEEP,
                -1.0, EntityAttributeModifier.Operation.ADD_VALUE,
            )
            // 挖掘疲劳 I (永久)
            PerkEffects.permanentEffect(ctx.player, StatusEffects.MINING_FATIGUE, 0)
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.SWEEPING_DAMAGE_RATIO, MOD_SWEEP)
            PerkEffects.removeEffect(ctx.player, StatusEffects.MINING_FATIGUE)
        }
    }

    /** 紫 · 猎杀: **良子冲击** —— 饱食度满 (饥饿值 20) 时, 你造成的伤害 +50%。
     *  (结算在 LivingEntityDamageMixin, 与"恶魔契约"等按乘法叠加) */
    object SatiationStrike : PerkDefinition {
        override val id = "hunter_satiation_strike"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.COOKED_BEEF)
    }

    /** 紫 · 猎杀: **TNT 投手** —— 获得 4 个 TNT; 手持 TNT 右键时改为抛射一枚点燃的 TNT
     *  (而非放置方块)。实现见 TntThrowService。 */
    object TntThrow : PerkDefinition {
        override val id = "hunter_tnt_throw"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.TNT)

        /** 获得时附赠的 TNT 数量 */
        private const val STARTING_TNT = 4

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.TNT, STARTING_TNT))
        }
    }

    /** 蓝 · 猎杀: **赏金猎人** —— 每击杀一种**此前没杀过**的敌对生物, 获得 3 个金锭 */
    object BountyHunter : PerkDefinition {
        override val id = "hunter_bounty_hunter"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.GOLD_INGOT)

        /** 每解锁一种新生物的赏金 */
        private const val REWARD = 3

        /** 已解锁的生物类型 (逗号分隔的注册表 id) 存在 state.memory 里, 随对局自动清理 */
        private const val MEMORY_KEY = "bounty_hunter_types"

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (victim !is Monster) return

            val typeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(victim.type).toString()
            val known = ctx.state.memory[MEMORY_KEY].orEmpty()
            if (known.split(',').contains(typeId)) return

            ctx.state.memory[MEMORY_KEY] = if (known.isEmpty()) typeId else "$known,$typeId"
            PerkEffects.giveStack(ctx.player, ItemStack(Items.GOLD_INGOT, REWARD))
        }
    }

    fun registerAll() {
        PerkRegistry.register(Strength)
        PerkRegistry.register(Vitality)
        PerkRegistry.register(Vampire)
        PerkRegistry.register(Scavenger)
        PerkRegistry.register(SoulHarvest)
        PerkRegistry.register(BareRage)
        PerkRegistry.register(MarkedPrey)
        PerkRegistry.register(GreedyHands)
        PerkRegistry.register(Predator)
        PerkRegistry.register(Headhunter)
        PerkRegistry.register(Fireball)
        PerkRegistry.register(CallLightning)
        PerkRegistry.register(NyaAxe)
        PerkRegistry.register(Veteran)
        PerkRegistry.register(RolyPoly)
        PerkRegistry.register(Nuclear)
        PerkRegistry.register(HomingBow)
        PerkRegistry.register(Jockey)
        PerkRegistry.register(PigMorph)
        PerkRegistry.register(BigSnowball)
        PerkRegistry.register(DemonPact)
        PerkRegistry.register(PearlGauntlet)
        PerkRegistry.register(SatiationStrike)
        PerkRegistry.register(TntThrow)
        PerkRegistry.register(BountyHunter)
    }
}
