package com.roguebingo.effect

import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * 词条效果的工具函数 (属性 / 状态效果 / 物品)。
 */
object PerkEffects {

    /** 应用/更新一个属性修饰符 (同 id 的旧修饰符会被替换) */
    fun setAttribute(
        player: ServerPlayerEntity,
        attribute: RegistryEntry<EntityAttribute>,
        id: Identifier,
        value: Double,
        operation: EntityAttributeModifier.Operation = EntityAttributeModifier.Operation.ADD_VALUE,
    ) {
        val instance = player.getAttributeInstance(attribute) ?: return
        instance.removeModifier(id)
        instance.addPersistentModifier(EntityAttributeModifier(id, value, operation))
    }

    fun removeAttribute(
        player: ServerPlayerEntity,
        attribute: RegistryEntry<EntityAttribute>,
        id: Identifier,
    ) {
        player.getAttributeInstance(attribute)?.removeModifier(id)
    }

    /** 应用一个"本局永久"的状态效果 (用超长持续时间模拟, 对局结束时由 onRemove 清理) */
    fun permanentEffect(
        player: ServerPlayerEntity,
        effect: RegistryEntry<StatusEffect>,
        amplifier: Int = 0,
        ambient: Boolean = true,
    ) {
        player.addStatusEffect(
            StatusEffectInstance(effect, StatusEffectInstance.INFINITE, amplifier, ambient, false, true)
        )
    }

    fun removeEffect(player: ServerPlayerEntity, effect: RegistryEntry<StatusEffect>) {
        player.removeStatusEffect(effect)
    }

    /** 强制同步属性/效果到客户端 (部分属性变更需要显式同步) */
    fun syncAttributes(player: ServerPlayerEntity) {
        // forceUpdate 由 add/removeModifier 自动触发; 保留此方法以便后续扩展
        player.sendAbilitiesUpdate()
    }

    /** 给玩家物品 (背包放不下时掉落) */
    fun giveStack(player: ServerPlayerEntity, stack: ItemStack) {
        player.giveItemStack(stack)
    }

    /** 给物品附魔 (自动把 RegistryKey 解析为 RegistryEntry; 附魔在 1.21+ 是数据驱动注册表) */
    fun enchant(player: ServerPlayerEntity, stack: ItemStack, key: RegistryKey<Enchantment>, level: Int) {
        val registry = player.server?.registryManager?.getOrThrow(RegistryKeys.ENCHANTMENT) ?: return
        val entry = registry.getEntry(key.value).orElse(null) ?: return
        stack.addEnchantment(entry, level)
    }
}
