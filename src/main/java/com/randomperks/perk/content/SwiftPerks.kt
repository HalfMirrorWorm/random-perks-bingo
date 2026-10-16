package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.ElytraBondService
import com.randomperks.game.PerkAbilityService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
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
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, StatusEffects.JUMP_BOOST, 0)

        override fun onRemove(ctx: PerkContext) = PerkEffects.removeEffect(ctx.player, StatusEffects.JUMP_BOOST)
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
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SLIME_BALL)

        /** 重生后复位"每段离地一次"标记 (新实体的空中状态已重置) */
        override fun onRespawn(ctx: PerkContext) = PerkAbilityService.clearDoubleJumpUsed(ctx.player)
    }

    /** 金 · 疾风 · 质变: 胸甲槽永久绑定鞘翅 (获得飞行能力), 但移动速度减半。
     *  绑定 = 绑定诅咒 (界面层锁死, 拿不下来);
     *  死亡不掉落 = 死亡时暂存、重生原样归还 (ElytraBondService + ElytraBondDeathMixin);
     *  词条移除时直接收回鞘翅 (不归还)。 */
    object ElytraBond : PerkDefinition {
        override val id = "swift_elytra_bond"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.ELYTRA)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "elytra_bond_speed")

        override fun onAcquire(ctx: PerkContext) {
            applySpeed(ctx)
            enforceChest(ctx)
        }

        override fun onRespawn(ctx: PerkContext) {
            applySpeed(ctx)
            // 死亡暂存的鞘翅原样装回 (同一件, 耐久等数据保留); 没有暂存则重新绑定
            val saved = ElytraBondService.takeSaved(ctx.player)
            if (saved != null && saved.isOf(Items.ELYTRA)) {
                ctx.player.equipStack(EquipmentSlot.CHEST, saved)
            } else {
                enforceChest(ctx)
            }
        }

        /** 胸甲槽被换下鞘翅的瞬间立即换回 (覆盖被替换/清空等非界面路径; 界面路径已被绑定诅咒锁死。
         *  注: 鞘翅"飞坏"(同一件耐久耗尽) 不触发补装 —— 玩家需自行用幻翼膜修复) */
        override fun onEquipmentChange(ctx: PerkContext) = enforceChest(ctx)

        /** 移动速度减半 (总速度 x0.5) */
        private fun applySpeed(ctx: PerkContext) {
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            )
        }

        /** 胸甲槽强制为鞘翅 (带绑定诅咒, 界面层锁死); 被顶下来的装备退回背包 (放不下则会掉落) */
        private fun enforceChest(ctx: PerkContext) {
            val player = ctx.player
            if (!player.isAlive) return  // 死亡瞬间不补装 (此时鞘翅已被死亡暂存取走)

            val current = player.getEquippedStack(EquipmentSlot.CHEST)
            if (current.isOf(Items.ELYTRA)) return

            val elytra = ItemStack(Items.ELYTRA)
            PerkEffects.enchant(player, elytra, Enchantments.BINDING_CURSE, 1)  // 界面层锁死: 拿不下来
            player.equipStack(EquipmentSlot.CHEST, elytra)
            if (!current.isEmpty) {
                player.giveItemStack(current)
            }
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
            // 词条移除: 直接收回这只由词条发放的鞘翅 (不归还 —— 对局结束/命令移除统一语义;
            // 死亡暂存中的那只也一并作废)
            ctx.state.savedChestItem = null
            val chest = ctx.player.getEquippedStack(EquipmentSlot.CHEST)
            if (chest.isOf(Items.ELYTRA)) {
                ctx.player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY)
            }
        }
    }

    /** 金 · 疾风: 仅在受到**致命**摔落伤害时, 副手的水桶会像不死图腾一样救你一命
     *  (只消耗水, 保留空桶; 带"水桶上浮旋转"的图腾式演出) */
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
