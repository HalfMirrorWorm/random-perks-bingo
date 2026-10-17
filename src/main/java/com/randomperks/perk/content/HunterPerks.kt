package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.mixin.MobLootInvoker
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.Monster
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
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

    /** 金 · 灵魂收割: 击杀 8% 概率掉落"战利品袋" —— 随机强力物品 */
    object SoulHarvest : PerkDefinition {
        override val id = "hunter_soul_harvest"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.NETHER_STAR)

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            // 同"剥皮专家": 用 Monster 接口判定 (HostileEntity 会漏掉史莱姆/恶魂/幻翼/末影龙等)
            if (victim !is Monster) return
            if (ctx.player.random.nextFloat() >= 0.08f) return

            val roll = ctx.player.random.nextInt(4)
            val prize = when (roll) {
                0 -> ItemStack(Items.GOLDEN_APPLE)
                1 -> ItemStack(Items.DIAMOND, 3)
                2 -> ItemStack(Items.ENDER_PEARL, 4)
                else -> ItemStack(Items.ENCHANTED_BOOK).also {
                    PerkEffects.enchant(ctx.player, it, Enchantments.SHARPNESS, 3)
                }
            }
            val world = ctx.player.world as? ServerWorld ?: return
            // "战利品袋"以真实掉落物形态落在怪物死亡位置 (与"剥皮专家"同口径)
            victim.dropStack(world, prize)
            ctx.player.sendMessage(net.minecraft.text.Text.translatable("random-perks-bingo.message.loot_bag"), false)
        }
    }

    /** 紫 · 猎杀: 未穿护甲时攻击力 +30% (高风险高收益; 鞘翅不算护甲) */
    object BareRage : PerkDefinition {
        override val id = "hunter_bare_rage"
        override val rarity = PerkRarity.PURPLE
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
                    0.30, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                )
            } else {
                PerkEffects.removeAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_BARE_RAGE)
            }
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_BARE_RAGE)
    }

    /** 紫 · 猎杀: 始终标记**距离最近**的敌方玩家 (每秒跟随切换) —— 持续显示其方位/坐标/距离并高亮轮廓; 朝目标移动时移速 +50% */
    object MarkedPrey : PerkDefinition {
        override val id = "hunter_marked_prey"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.COMPASS)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "marked_prey_speed")
        private const val TARGET_KEY = "marked_prey_target"
        private const val LAST_DIST_KEY = "marked_prey_last_dist"

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
                ctx.state.memory.remove(LAST_DIST_KEY)
                return
            }

            val targetId = target.uuid.toString()
            if (ctx.state.memory[TARGET_KEY] != targetId) {
                // 目标发生切换: 重置距离基准 —— 否则会拿"到新目标的距离"与"到旧目标的距离"相比, 误判成正在接近
                ctx.state.memory[TARGET_KEY] = targetId
                ctx.state.memory.remove(LAST_DIST_KEY)
            }

            // 跨维度: 两个世界的坐标硬算出来的距离/方位没有意义 → 只提示目标在哪个维度, 不给加速
            if (target.world !== player.world) {
                PerkEffects.removeAttribute(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
                ctx.state.memory.remove(LAST_DIST_KEY)
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

            // 距离变化 → 接近中则 +50% 移速
            val dist = player.pos.distanceTo(target.pos).toInt()
            val previous = ctx.state.memory[LAST_DIST_KEY]?.toIntOrNull()
            ctx.state.memory[LAST_DIST_KEY] = dist.toString()

            if (previous != null && dist < previous) {
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
                other.uuid != player.uuid && (myTeam == null || other.scoreboardTeam?.name != myTeam)
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
            ctx.state.memory.remove(LAST_DIST_KEY)
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
            ctx.state.memory.remove(LAST_DIST_KEY)
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

    /** 金 · 猎杀: 每击杀一名**敌方玩家** (非自己/非同队), 永久 +1 最大生命 (可叠加, 重生保留) */
    object Headhunter : PerkDefinition {
        override val id = "hunter_headhunter"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.PLAYER_HEAD)

        private val MOD_MAX_HEALTH = Identifier.of("random-perks-bingo", "headhunter_health")
        private const val BONUS_KEY = "headhunter_bonus"

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

            val bonus = (ctx.state.memory[BONUS_KEY]?.toDoubleOrNull() ?: 0.0) + 1.0
            ctx.state.memory[BONUS_KEY] = bonus.toString()
            applyBonus(ctx)
            ctx.player.heal(1f)
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

    /** 蓝 · 猎杀: 随机选一名敌方玩家, 在他的位置召唤一次闪电; 16 格内若有引雷针, 闪电会被引导过去 */
    object CallLightning : PerkDefinition {
        override val id = "hunter_call_lightning"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.LIGHTNING_ROD)

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            // 落点: 随机一名敌方在线玩家 (跨维度劈雷双方都看不到, 所以只在本维度找; 本维度没敌人就不劈)
            val target = pickEnemy(player) ?: return
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

        /** 随机挑一名敌方在线玩家 (以原版队伍判断归属; 无队伍时视为全体为敌), 仅限同维度 */
        private fun pickEnemy(player: ServerPlayerEntity): ServerPlayerEntity? {
            val server = player.server ?: return null
            val myTeam = player.scoreboardTeam?.name
            return server.playerManager.playerList
                .filter { other ->
                    other.uuid != player.uuid &&
                        other.world === player.world &&
                        (myTeam == null || other.scoreboardTeam?.name != myTeam)
                }
                .randomOrNull()
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
    }
}
