package com.randomperks.effect

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.ItemStack
import net.minecraft.tags.ItemTags
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos

/**
 * 词条效果的工具函数 (属性 / 状态效果 / 物品)。
 */
object PerkEffects {

    /** 应用/更新一个属性修饰符 (同 id 的旧修饰符会被替换) */
    fun setAttribute(
        player: ServerPlayer,
        attribute: Holder<Attribute>,
        id: Identifier,
        value: Double,
        operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_VALUE,
    ) {
        val instance = player.getAttribute(attribute) ?: return
        instance.removeModifier(id)
        instance.addPermanentModifier(AttributeModifier(id, value, operation))
    }

    fun removeAttribute(
        player: ServerPlayer,
        attribute: Holder<Attribute>,
        id: Identifier,
    ) {
        player.getAttribute(attribute)?.removeModifier(id)
    }

    /** 应用一个"本局永久"的状态效果 (用超长持续时间模拟, 对局结束时由 onRemove 清理) */
    fun permanentEffect(
        player: ServerPlayer,
        effect: Holder<MobEffect>,
        amplifier: Int = 0,
        ambient: Boolean = true,
    ) {
        player.addEffect(
            MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, ambient, false, true)
        )
    }

    fun removeEffect(player: ServerPlayer, effect: Holder<MobEffect>) {
        player.removeEffect(effect)
    }

    /** 强制同步属性/效果到客户端 (部分属性变更需要显式同步) */
    fun syncAttributes(player: ServerPlayer) {
        // forceUpdate 由 add/removeModifier 自动触发; 保留此方法以便后续扩展
        player.onUpdateAbilities()
    }

    /** 给玩家物品 (背包放不下时**掉在脚下**)。
     *  ★ 必须用 offerOrDrop: PlayerEntity.giveItemStack 内部只是 inventory.insertStack,
     *  塞不下的部分会留在传入对象里被静默丢弃 —— 原版"给不下就掉"的 API 是 PlayerInventory.offerOrDrop。 */
    fun giveStack(player: ServerPlayer, stack: ItemStack) {
        player.inventory.placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY)
    }

    /** 生成一份"该方块的真实掉落"作为掉落物 (自动含时运/精准采集结果; 标准拾取延迟)。
     *  供"矿脉嗅觉 / 老矿工 / 樵夫之手"等"额外掉落"类词条复用。 */
    fun spawnBlockDrops(
        player: ServerPlayer,
        world: ServerLevel,
        pos: BlockPos,
        blockState: BlockState,
        copies: Int = 1,
    ) {
        // 只结算一次掉落表 (含时运/精准采集); copies > 1 时生成同一份结果的多个副本
        val drops = Block.getDrops(blockState, world, pos, null, player, player.mainHandItem)
        for (drop in drops) {
            repeat(copies) {
                val entity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, drop.copy())
                entity.setDefaultPickUpDelay()
                world.addFreshEntity(entity)
            }
        }
    }

    /** 给物品附魔 (自动把 RegistryKey 解析为 RegistryEntry; 附魔在 1.21+ 是数据驱动注册表) */
    fun enchant(player: ServerPlayer, stack: ItemStack, key: ResourceKey<Enchantment>, level: Int) {
        val registry = player.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT) ?: return
        val entry = registry.get(key.identifier()).orElse(null) ?: return
        stack.enchant(entry, level)
    }

    /**
     * 玩家是否**没有穿着任何护甲**。
     *
     * 按原版护甲物品标签判断 (HEAD_ARMOR / CHEST_ARMOR / LEG_ARMOR / FOOT_ARMOR)，
     * 而不是护甲值 —— 后者会把"皇帝新衣"自己加的 ARMOR 修饰符算进去，形成循环。
     * 南瓜、生物头颅、鞘翅都不在护甲标签里，不算穿甲；铜甲、海龟帽照常算。
     */
    fun hasNoArmor(player: ServerPlayer): Boolean {
        for ((slot, tag) in ARMOR_SLOTS) {
            if (player.getItemBySlot(slot).`is`(tag)) return false
        }
        return true
    }

    private val ARMOR_SLOTS = listOf(
        EquipmentSlot.HEAD to ItemTags.HEAD_ARMOR,
        EquipmentSlot.CHEST to ItemTags.CHEST_ARMOR,
        EquipmentSlot.LEGS to ItemTags.LEG_ARMOR,
        EquipmentSlot.FEET to ItemTags.FOOT_ARMOR,
    )
}
