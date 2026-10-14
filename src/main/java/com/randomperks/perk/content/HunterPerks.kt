package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier

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

    fun registerAll() {
        PerkRegistry.register(Strength)
        PerkRegistry.register(Vitality)
        PerkRegistry.register(Vampire)
        PerkRegistry.register(Scavenger)
        PerkRegistry.register(SoulHarvest)
        PerkRegistry.register(BareRage)
    }
}
