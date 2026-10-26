package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.PerkAbilityService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.SoulOutService
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.FireworksComponent
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
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

    /** 蓝 · 柔软鞋底: 摔落伤害 -70% */
    object FeatherFall : PerkDefinition {
        override val id = "swift_feather_fall"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.LEATHER_BOOTS)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.FALL_DAMAGE_MULTIPLIER, MOD_FALL, -0.7, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.FALL_DAMAGE_MULTIPLIER, MOD_FALL)
    }

    /** 蓝 · 弹簧腿: 跳跃高度提升到约 2 格 (原版默认约 1.25 格) */
    object Jump : PerkDefinition {
        override val id = "swift_jump"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.RABBIT_FOOT)

        private val MOD_JUMP = Identifier.of("random-perks-bingo", "swift_jump")

        /** 原版跳跃初速度 0.42 → 约 1.25 格; 跳跃高度 ∝ 初速度², 故 0.42 + 0.13 = 0.55 → 约 2.1 格 */
        private const val JUMP_BONUS = 0.13

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.JUMP_STRENGTH, MOD_JUMP, JUMP_BONUS)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.JUMP_STRENGTH, MOD_JUMP)
    }

    /** 紫 · 风之步伐: 疾跑时移动速度额外 +20% —— 与原版疾跑加成同机制 (属性修饰符),
     *  由 WindStrideMixin / WindStrideClientMixin 在疾跑状态切换时挂/摘 (无轮询, 即开即停) */
    object WindStride : PerkDefinition {
        override val id = "swift_wind_stride"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SUGAR)

        /** 获得时若正在疾跑, 立即生效 (否则等下一次疾跑状态切换) */
        override fun onAcquire(ctx: PerkContext) {
            if (ctx.player.isSprinting) PerkBoostQuery.applyWindStride(ctx.player)
        }

        override fun onRemove(ctx: PerkContext) = PerkBoostQuery.clearWindStride(ctx.player)
    }

    /** 紫 · 顺风而行: 移动速度 +10%, 每个其他疾风词条额外 +3% (流派联动; 词条列表变化时即时重算, 无定时轮询) */
    object Tailwind : PerkDefinition {
        override val id = "swift_tailwind"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.PHANTOM_MEMBRANE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 获得/移除任一词条时即时重算 (替代每秒轮询) */
        override fun onPerkListChanged(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) {
            val allies = (ctx.countArchetype(PerkArchetype.SWIFT) - 1).coerceAtLeast(0)
            val bonus = 0.10 + 0.03 * allies
            PerkEffects.setAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_TAILWIND, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_TAILWIND)
    }

    /** 金 · 疾风 · 质变: 空中可以再跳跃一次 (每段离地一次; 由 PerkAbilityService + PlayerLandingMixin 实现) */
    object DoubleJump : PerkDefinition {
        override val id = "swift_double_jump"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SLIME_BALL)

        /** 重生后复位"每段离地一次"标记 (新实体的空中状态已重置) */
        override fun onRespawn(ctx: PerkContext) = PerkAbilityService.clearDoubleJumpUsed(ctx.player)
    }

    /** 金 · 疾风 · 质变: 获得一只**剩余 3/4 耐久**的鞘翅和 2 个烟花火箭, 移动速度降低 30%。 */
    object ElytraBond : PerkDefinition {
        override val id = "swift_elytra_bond"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.ELYTRA)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "elytra_bond_speed")

        override fun onAcquire(ctx: PerkContext) {
            applySpeed(ctx)

            val elytra = ItemStack(Items.ELYTRA)
            // 最大耐久 432 → 扣掉 1/4 = 108, 剩余 324 (3/4)
            elytra.damage = elytra.maxDamage / 4
            PerkEffects.giveStack(ctx.player, elytra)

            // 附赠 2 个烟花火箭 (飞行时长 2) —— 没有 FIREWORKS 组件的裸火箭是废品, 飞不了
            val rocket = ItemStack(Items.FIREWORK_ROCKET, 2)
            rocket.set(DataComponentTypes.FIREWORKS, FireworksComponent(2, emptyList()))
            PerkEffects.giveStack(ctx.player, rocket)
        }

        override fun onRespawn(ctx: PerkContext) = applySpeed(ctx)

        /** 移动速度降低 30% (总速度 x0.7) */
        private fun applySpeed(ctx: PerkContext) {
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                -0.3, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            )
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
        }
    }

    /** 绿 · 疾风: 仅在受到**致命**摔落伤害时, 背包里的水桶会像不死图腾一样救你一命
     *  (只消耗水, 保留空桶; 带"水桶上浮旋转"的图腾式演出) */
    object LifeBucket : PerkDefinition {
        override val id = "swift_life_bucket"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.WATER_BUCKET)
        // 效果由 FallBucketMixin 实现 (仅摔落伤害)
    }

    /** 金 · 疾风: 获得一把**激流 I** 的三叉戟 —— 在水中或雨中按住右键即可朝准星方向高速冲出
     *  (原版机制: 激流只在水中/雨中可用, 陆地或晴天只能当普通三叉戟近战) */
    object GuraShark : PerkDefinition {
        override val id = "swift_gura_shark"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.TRIDENT)

        private const val RIPTIDE_LEVEL = 1

        override fun onAcquire(ctx: PerkContext) {
            val trident = ItemStack(Items.TRIDENT)
            PerkEffects.enchant(ctx.player, trident, Enchantments.RIPTIDE, RIPTIDE_LEVEL)
            PerkEffects.giveStack(ctx.player, trident)
        }
    }

    /** 蓝 · 疾风: **全向移动** —— 八个方向都能疾跑, 而不是单纯加移速。
     *
     *  原版疾跑判定只认"前进": ClientPlayerEntity 的 canStartSprinting / shouldStopSprinting /
     *  shouldStopSwimSprinting 里用的都是 input.hasForwardMovement(), 所以侧移和后退永远跑不起来。
     *  OmnimovementMixin 在客户端把这一条换成"任意方向有输入", 于是 W/A/S/D 与四个斜向
     *  都能进入并保持疾跑 (疾跑本身的 +30% 仍由原版 LivingEntity.setSprinting 挂)。 */
    object Omnimovement : PerkDefinition {
        override val id = "swift_omnimovement"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.FEATHER)

        /** 旧实现是常驻 +30% 移速修饰符, 而且是 addPersistentModifier (会写进玩家 NBT)。
         *  改成疾跑判定放行后不再需要它 —— 获得/重生/失去时都主动清一遍, 老存档里的残留
         *  也能顺带抹掉。 */
        private val LEGACY_SPEED = Identifier.of("random-perks-bingo", "omnimovement_speed")

        override fun onAcquire(ctx: PerkContext) = clearLegacy(ctx)
        override fun onRespawn(ctx: PerkContext) = clearLegacy(ctx)
        override fun onRemove(ctx: PerkContext) = clearLegacy(ctx)

        private fun clearLegacy(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, LEGACY_SPEED)
    }

    /** 金 · 疾风: **冲刺** —— 在空中按下潜行键, 朝视线水平方向冲刺一次 (每次腾空限一次, 冷却 3 秒)。
     *  由客户端 AbilityHandler 检测按键, 服务端 PerkAbilityService.handleDash 验证并施加冲量。 */
    object Dash : PerkDefinition {
        override val id = "swift_dash"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.WIND_CHARGE)

        /** 重生后复位"每段离地一次"标记 (新实体的空中状态已重置) */
        override fun onRespawn(ctx: PerkContext) = PerkAbilityService.clearDashUsed(ctx.player)
    }

    /** 金 · 疾风: **灵魂出鞘** (贮藏) —— 在 V 界面点击使用后灵魂离体 60 秒:
     *  可飞行、水平穿墙、隐身且免疫普通伤害; 结束时回到身体所在位置。 */
    object SoulOut : PerkDefinition {
        override val id = "swift_soul_out"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SOUL_LANTERN)

        override val isOneTime = true

        override fun onActivate(ctx: PerkContext) = SoulOutService.activate(ctx)
        override fun onTick(ctx: PerkContext) = SoulOutService.tick(ctx)

        /** 词条被移除 / 对局结束时归位 (万一对局结束时还飘在外面) */
        override fun onRemove(ctx: PerkContext) =
            SoulOutService.end(ctx.player, ctx.state, teleportBack = true)

        /** 重生时清理 (正常情况下灵魂状态免疫伤害, 不会走到这里) */
        override fun onRespawn(ctx: PerkContext) =
            SoulOutService.end(ctx.player, ctx.state, teleportBack = false)
    }

    /** 蓝 · 迅捷: **逃跑计划** —— 受到生物伤害后, 自动获得速度 II 与抗性提升 I, 持续 10 秒。
     *  (触发逻辑在 PerkWorldEventService 的 ServerLivingEntityEvents.AFTER_DAMAGE) */
    object EscapePlan : PerkDefinition {
        override val id = "swift_escape_plan"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.RABBIT_FOOT)
    }

    fun registerAll() {
        com.randomperks.perk.PerkRegistry.register(EscapePlan)
        com.randomperks.perk.PerkRegistry.register(Speed)
        com.randomperks.perk.PerkRegistry.register(FeatherFall)
        com.randomperks.perk.PerkRegistry.register(Jump)
        com.randomperks.perk.PerkRegistry.register(WindStride)
        com.randomperks.perk.PerkRegistry.register(Tailwind)
        com.randomperks.perk.PerkRegistry.register(DoubleJump)
        com.randomperks.perk.PerkRegistry.register(ElytraBond)
        com.randomperks.perk.PerkRegistry.register(LifeBucket)
        com.randomperks.perk.PerkRegistry.register(GuraShark)
        com.randomperks.perk.PerkRegistry.register(Omnimovement)
        com.randomperks.perk.PerkRegistry.register(Dash)
        com.randomperks.perk.PerkRegistry.register(SoulOut)
    }
}
