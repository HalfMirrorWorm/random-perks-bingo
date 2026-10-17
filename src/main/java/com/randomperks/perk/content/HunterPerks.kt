package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.PerkSession
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.state.PerkStateManager
import net.minecraft.component.DataComponentTypes
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

    /** 紫 · 猎杀: 始终标记**距离最近**的敌方玩家 (每秒跟随切换) —— 持续显示其方位/坐标/距离并高亮轮廓;
     *  **目标 512 格以内**时移速 +50% */
    object MarkedPrey : PerkDefinition {
        override val id = "hunter_marked_prey"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.COMPASS)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "marked_prey_speed")
        private const val TARGET_KEY = "marked_prey_target"

        /** 在目标这个距离内就常驻 +50% 移速 */
        private const val SPEED_RADIUS = 512

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

            // **在目标 512 格以内就常驻 +50% 移速**
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
            PerkNetworking.sendLightningTargets(
                player,
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

    /** 紫 · 猎杀: **老玩家** —— 手持剑时攻击冷却始终视为已蓄满, 连点即满伤害 (1.9 战斗更新前的手感) */
    object Veteran : PerkDefinition {
        override val id = "hunter_veteran"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.IRON_SWORD)
        // 效果由 OldSchoolCombatMixin 在 PlayerEntity.getAttackCooldownProgress 实现
    }

    /** 蓝 · 猎杀: **不倒翁** —— 受到的击退效果降低 50% */
    object RolyPoly : PerkDefinition {
        override val id = "hunter_roly_poly"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.IRON_BLOCK)

        private val MOD_KNOCKBACK = Identifier.of("random-perks-bingo", "roly_poly_knockback")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 原版击退抗性属性 (0 ~ 1), +0.5 即击退距离减半 */
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK,
                0.5, EntityAttributeModifier.Operation.ADD_VALUE,
            )

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK)
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
    }
}
