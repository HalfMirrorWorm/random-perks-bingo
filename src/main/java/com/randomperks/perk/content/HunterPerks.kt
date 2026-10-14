package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * 猎杀 HUNTER —— 战斗 / 怪物掉落。
 */
object HunterPerks {

    private val MOD_ATTACK = Identifier.of("random-perks-bingo", "hunter_strength")
    private val MOD_HEALTH = Identifier.of("random-perks-bingo", "hunter_health")

    /** 蓝 · 猎人直觉: 攻击力 +15% */
    object Strength : PerkDefinition {
        override val id = "hunter_strength"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.IRON_SWORD)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.ATTACK_DAMAGE, MOD_ATTACK, 0.15, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

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

        override fun onRespawn(ctx: PerkContext) = apply(ctx)

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

    /** 紫 · 剥皮专家: 击杀怪物 30% 额外掉落战利品 */
    object Scavenger : PerkDefinition {
        override val id = "hunter_scavenger"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.BONE)

        private val loot = listOf(Items.BONE, Items.STRING, Items.LEATHER, Items.ROTTEN_FLESH, Items.FEATHER, Items.GUNPOWDER)

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (victim !is HostileEntity) return
            if (ctx.player.random.nextFloat() >= 0.30f) return
            PerkEffects.giveStack(ctx.player, ItemStack(loot[ctx.player.random.nextInt(loot.size)]))
        }
    }

    /** 金 · 灵魂收割: 击杀 8% 概率掉落"战利品袋" —— 随机强力物品 */
    object SoulHarvest : PerkDefinition {
        override val id = "hunter_soul_harvest"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.NETHER_STAR)

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (victim !is HostileEntity) return
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
            PerkEffects.giveStack(ctx.player, prize)
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

        /** 每秒校验装备状态, 穿/脱护甲时自动生效或失效 */
        override fun onTick(ctx: PerkContext) = refresh(ctx)

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

    /** 紫 · 猎杀: 随机标记一名敌方玩家 —— 持续显示其方位/坐标/距离并高亮轮廓; 朝目标移动时移速 +50% */
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
            val targetId = ctx.state.memory[TARGET_KEY]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val target = targetId?.let { player.server?.playerManager?.getPlayer(it) }

            if (target == null) {
                // 目标离线/不在场: 清除加速并尝试重新标记
                PerkEffects.removeAttribute(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
                if (targetId != null) {
                    ctx.state.memory.remove(TARGET_KEY)
                    ctx.state.memory.remove(LAST_DIST_KEY)
                    markTarget(ctx)
                }
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

        /** 随机标记一名其他队伍的在线玩家 (以原版队伍判断归属; 无队伍时视为全体为敌) */
        private fun markTarget(ctx: PerkContext) {
            val player = ctx.player
            val server = player.server ?: return
            val myTeam = player.scoreboardTeam?.name
            val candidates = server.playerManager.playerList.filter { other ->
                other.uuid != player.uuid && (myTeam == null || other.scoreboardTeam?.name != myTeam)
            }
            val target = candidates.randomOrNull() ?: return
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

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
            ctx.state.memory.remove(TARGET_KEY)
            ctx.state.memory.remove(LAST_DIST_KEY)
        }
    }

    /** 紫 · 猎杀: 击杀生物相当于抢夺 III (50% 概率额外掉落一份), 但受到其他生物 2 倍伤害 */
    object GreedyHands : PerkDefinition {
        override val id = "hunter_greedy_hands"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.GOLDEN_SWORD)

        private const val EXTRA_DROP_CHANCE = 0.5f

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (ctx.player.random.nextFloat() >= EXTRA_DROP_CHANCE) return
            val world = ctx.player.world as? ServerWorld ?: return
            val box = victim.boundingBox.expand(1.0)
            world.getEntitiesByClass(ItemEntity::class.java, box) { it.age <= 1 }
                .forEach { world.spawnEntity(ItemEntity(world, it.x, it.y, it.z, it.stack.copy())) }
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

    /** 金 · 猎杀: 每击杀一名其他玩家, 永久 +1 最大生命 (可叠加, 重生保留) */
    object Headhunter : PerkDefinition {
        override val id = "hunter_headhunter"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.HUNTER
        override fun icon() = ItemStack(Items.PLAYER_HEAD)

        private val MOD_MAX_HEALTH = Identifier.of("random-perks-bingo", "headhunter_health")
        private const val BONUS_KEY = "headhunter_bonus"

        override fun onAcquire(ctx: PerkContext) = applyBonus(ctx)
        override fun onRespawn(ctx: PerkContext) = applyBonus(ctx)

        override fun onKill(ctx: PerkContext, victim: LivingEntity) {
            if (victim !is ServerPlayerEntity) return
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
    }
}
