package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier

/**
 * 疾风 SWIFT —— 跑图 / 位移 / 机动。
 */
object SwiftPerks {

    private val MOD_SPEED = Identifier.of("random-perks-bingo", "swift_speed")
    private val MOD_FALL = Identifier.of("random-perks-bingo", "swift_feather_fall")
    private val MOD_TAILWIND = Identifier.of("random-perks-bingo", "swift_tailwind")

    /** 蓝 · 轻装疾行: 移动速度 +8% */
    object Speed : PerkDefinition {
        override val id = "swift_speed"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.FEATHER)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED, 0.08, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
    }

    /** 蓝 · 柔软鞋底: 摔落伤害 -50% */
    object FeatherFall : PerkDefinition {
        override val id = "swift_feather_fall"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.LEATHER_BOOTS)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.FALL_DAMAGE_MULTIPLIER, MOD_FALL, -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.FALL_DAMAGE_MULTIPLIER, MOD_FALL)
    }

    /** 蓝 · 弹簧腿: 跳跃提升 I */
    object Jump : PerkDefinition {
        override val id = "swift_jump"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.RABBIT_FOOT)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 每秒校验: 被不死图腾/牛奶等清除后自动重挂 */
        override fun onTick(ctx: PerkContext) =
            PerkEffects.ensurePermanentEffect(ctx.player, StatusEffects.JUMP_BOOST, 0)

        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, StatusEffects.JUMP_BOOST, 0)

        override fun onRemove(ctx: PerkContext) = PerkEffects.removeEffect(ctx.player, StatusEffects.JUMP_BOOST)
    }

    /** 紫 · 风之步伐: 疾跑时持续获得速度 I */
    object WindStride : PerkDefinition {
        override val id = "swift_wind_stride"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SUGAR)

        override fun onTick(ctx: PerkContext) {
            if (ctx.player.isSprinting) {
                // 持续刷新 (比 tick 略长, 停止疾跑后自动消失)
                ctx.player.addStatusEffect(
                    StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false, false)
                )
            }
        }
    }

    /** 紫 · 顺风而行: 移动速度 +10%, 每个其他疾风词条额外 +3% (流派联动) */
    object Tailwind : PerkDefinition {
        override val id = "swift_tailwind"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.PHANTOM_MEMBRANE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 每秒重算, 以响应后续获得的同流派词条 */
        override fun onTick(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) {
            val allies = (ctx.countArchetype(PerkArchetype.SWIFT) - 1).coerceAtLeast(0)
            val bonus = 0.10 + 0.03 * allies
            PerkEffects.setAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_TAILWIND, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_TAILWIND)
    }

    /** 金 · 疾风 · 质变: 空中可以再跳跃一次 (客户端按键触发, 服务端验证) */
    object DoubleJump : PerkDefinition {
        override val id = "swift_double_jump"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SLIME_BALL)
        // 效果由 PerkAbilityService (双击跳跃 C2S 包) 处理
    }

    /** 金 · 疾风 · 质变: 胸甲槽永久绑定鞘翅 (获得飞行能力), 但移动速度减半 */
    object ElytraBond : PerkDefinition {
        override val id = "swift_elytra_bond"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.ELYTRA)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "elytra_bond_speed")

        override fun onAcquire(ctx: PerkContext) = enforce(ctx)
        override fun onRespawn(ctx: PerkContext) = enforce(ctx)

        /** 每秒校验一次: 移速减半 + 胸甲槽必须为鞘翅 (被换下则重新绑定) */
        override fun onTick(ctx: PerkContext) = enforce(ctx)

        private fun enforce(ctx: PerkContext) {
            val player = ctx.player

            // 移动速度减半 (总速度 x0.5)
            PerkEffects.setAttribute(
                player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            )

            // 胸甲槽强制为鞘翅; 被顶下来的装备退回背包 (放不下则会掉落)
            val current = player.getEquippedStack(EquipmentSlot.CHEST)
            if (current.isOf(Items.ELYTRA)) return

            player.equipStack(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
            if (!current.isEmpty) {
                player.giveItemStack(current)
            }
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
            // 取下鞘翅并放回背包 (放不下则掉落)
            val chest = ctx.player.getEquippedStack(EquipmentSlot.CHEST)
            if (chest.isOf(Items.ELYTRA)) {
                ctx.player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY)
                ctx.player.giveItemStack(chest)
            }
        }
    }

    /** 金 · 疾风: 仅在受到摔落伤害时, 副手的水桶会像不死图腾一样救你一命 (消耗水桶, 留下空桶) */
    object LifeBucket : PerkDefinition {
        override val id = "swift_life_bucket"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.WATER_BUCKET)
        // 效果由 FallBucketMixin 实现 (仅摔落伤害)
    }

    fun registerAll() {
        com.randomperks.perk.PerkRegistry.register(Speed)
        com.randomperks.perk.PerkRegistry.register(FeatherFall)
        com.randomperks.perk.PerkRegistry.register(Jump)
        com.randomperks.perk.PerkRegistry.register(WindStride)
        com.randomperks.perk.PerkRegistry.register(Tailwind)
        com.randomperks.perk.PerkRegistry.register(DoubleJump)
        com.randomperks.perk.PerkRegistry.register(ElytraBond)
        com.randomperks.perk.PerkRegistry.register(LifeBucket)
    }
}
