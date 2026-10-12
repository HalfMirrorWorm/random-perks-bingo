package com.roguebingo.perk.content

import com.roguebingo.perk.PerkArchetype
import com.roguebingo.perk.PerkContext
import com.roguebingo.perk.PerkDefinition
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.perk.PerkRarity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier

/**
 * M2 阶段的测试词条 —— 三个品质/三类效果的样例, 用于打通最小闭环。
 * 正式词条库 (36 个) 在 M5 阶段填充。
 */
object TestPerks {
    fun registerAll() {
        PerkRegistry.register(SwiftSpeed)
        PerkRegistry.register(ArtisanSupply)
        PerkRegistry.register(DelverHaste)
    }
}

/** 蓝 · 疾风 · 属性强化: 移动速度 +8% */
object SwiftSpeed : PerkDefinition {
    override val id = "swift_speed"
    override val rarity = PerkRarity.BLUE
    override val archetype = PerkArchetype.SWIFT

    private val MODIFIER_ID = Identifier.of("rogue-bingo", "perk_swift_speed")

    override fun icon() = ItemStack(Items.FEATHER)

    override fun onAcquire(ctx: PerkContext) = apply(ctx)

    override fun onRespawn(ctx: PerkContext) = apply(ctx)

    private fun apply(ctx: PerkContext) {
        val attr = ctx.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) ?: return
        if (attr.getModifier(MODIFIER_ID) != null) return
        attr.addPersistentModifier(
            EntityAttributeModifier(MODIFIER_ID, 0.08, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        )
    }

    override fun onRemove(ctx: PerkContext) {
        ctx.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)
            ?.removeModifier(MODIFIER_ID)
    }
}

/** 蓝 · 匠造 · 资源获取: 立即获得一组面包与一个水桶 */
object ArtisanSupply : PerkDefinition {
    override val id = "artisan_supply"
    override val rarity = PerkRarity.BLUE
    override val archetype = PerkArchetype.ARTISAN

    override fun icon() = ItemStack(Items.BREAD)

    override fun onAcquire(ctx: PerkContext) {
        ctx.player.giveItemStack(ItemStack(Items.BREAD, 16))
        ctx.player.giveItemStack(ItemStack(Items.WATER_BUCKET))
    }
}

/** 紫 · 深掘 · 特殊能力: 获得永久急迫 II (挖掘加速) */
object DelverHaste : PerkDefinition {
    override val id = "delver_haste"
    override val rarity = PerkRarity.PURPLE
    override val archetype = PerkArchetype.DELVER

    override fun icon() = ItemStack(Items.DIAMOND_PICKAXE)

    override fun onAcquire(ctx: PerkContext) = apply(ctx)

    override fun onRespawn(ctx: PerkContext) = apply(ctx)

    private fun apply(ctx: PerkContext) {
        // 用极长持续时间模拟"本局永久" (对局结束会清理)
        ctx.player.addStatusEffect(
            StatusEffectInstance(StatusEffects.HASTE, Int.MAX_VALUE, 1, true, false, true)
        )
    }

    override fun onRemove(ctx: PerkContext) {
        ctx.player.removeStatusEffect(StatusEffects.HASTE)
    }
}
