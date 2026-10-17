package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.tag.ItemTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * 供 Java mixin 查询词条状态的轻量桥接 (避免 Java 侧直接操作 Kotlin 状态对象)。
 */
object PerkBoostQuery {

    /** 玩家是否持有"贪婪之手" (受到其他生物 2 倍伤害) */
    fun hasFragileToMobs(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_greedy_hands") == true

    /** 玩家是否持有"小鸟胃" (进食恢复翻倍) */
    fun hasEatBoost(player: PlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_gourmet") == true

    /** 玩家是否持有"救生桶" (摔落伤害时副手水桶当图腾) */
    fun hasFallBucket(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("swift_life_bucket") == true

    /** 玩家是否持有"哈基米" (对苦力怕等效于猫, 由 CatAuraMixin 调用) */
    fun hasCatAura(player: PlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_cat_aura") == true

    /** 玩家是否持有"地下视野" (bingo 夜视管制器放行用, 由 NightVisionControllerMixin 调用) */
    fun hasNightVisionPerk(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("delver_night_vision") == true

    // ---- "风之步伐": 疾跑加成 (与原版疾跑修饰符同机制, 由 WindStrideMixin 在疾跑状态切换时调用) ----

    private val WIND_STRIDE_MOD = Identifier.of("random-perks-bingo", "wind_stride_sprint")
    private const val WIND_STRIDE_BONUS = 0.2
    private const val WIND_STRIDE_PERK = "swift_wind_stride"

    /** 疾跑状态变化时: 持有词条 → 挂/摘 +20% 疾跑加成 (与原版 +30% 疾跑加成同桶相加 → 疾跑 1.5 倍) */
    fun updateWindStride(player: ServerPlayerEntity, sprinting: Boolean) {
        if (PerkStateManager.get(player.uuid)?.hasPerk(WIND_STRIDE_PERK) != true) return
        if (sprinting) applyWindStride(player) else clearWindStride(player)
    }

    /** 挂上疾跑加成 (临时修饰符: 与效果类修饰符一样不随存档保存; 获得词条时正在疾跑也会调用) */
    fun applyWindStride(player: ServerPlayerEntity) {
        val instance = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) ?: return
        instance.removeModifier(WIND_STRIDE_MOD)
        instance.addTemporaryModifier(
            EntityAttributeModifier(WIND_STRIDE_MOD, WIND_STRIDE_BONUS, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        )
    }

    /** 清除疾跑加成 */
    fun clearWindStride(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)?.removeModifier(WIND_STRIDE_MOD)
    }

    // ---- "透视之眼"的代价: 35% 不掉落 (由 XrayDropSuppressMixin 在 Block.dropStacks 拦截) ----

    private const val XRAY_PERK = "delver_xray_vision"

    /** 记录"该玩家本 tick 的挖块掉落被透视惩罚抑制"的 tick (供矿脉嗅觉等联动词条查询) */
    const val XRAY_SUPPRESS_TICK_KEY = "xray_suppress_tick"

    /** 用镐破坏方块时是否应抑制本次掉落生成 (持有词条 + 手持镐 + 35%) */
    fun shouldSuppressXrayDrop(player: ServerPlayerEntity, tool: ItemStack): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(XRAY_PERK)) return false
        if (!tool.isIn(ItemTags.PICKAXES)) return false
        if (player.random.nextFloat() >= 0.35f) return false
        // 抑制生效: 留下本 tick 的标记 —— "矿脉嗅觉"等额外掉落会据此一并抑制, 保证
        // 额外份同样被 X-Ray 惩罚 (不生成式下唯一能让联动成立的方式)
        state.memory[XRAY_SUPPRESS_TICK_KEY] = (player.server?.ticks ?: 0).toString()
        return true
    }

    // ---- "喵人斧": 一击必杀 (由 NyaAxeMixin 在 LivingEntity.damage 拦截) ----

    private const val NYA_AXE_PERK = "hunter_nya_axe"

    /** 斧头上的 NBT 标记: 只有词条发放的那一把才有 (玩家用铁砧改名伪造不出来) */
    private const val NYA_AXE_TAG = "randomperks_nya_axe"

    /** 必杀伤害 —— 远高于任何原版生物的血量 (监守者也才 500) */
    private const val LETHAL_DAMAGE = 10_000.0f

    /** 给这把斧头打上"秒人斧"标记 */
    fun markNyaAxe(stack: ItemStack) {
        stack.set(
            DataComponentTypes.CUSTOM_DATA,
            NbtComponent.of(NbtCompound().apply { putBoolean(NYA_AXE_TAG, true) }),
        )
    }

    /** 注: 1.21 的 NbtCompound.getBoolean 返回 Optional<Boolean>, 不是裸 boolean */
    private fun isNyaAxe(stack: ItemStack): Boolean =
        stack.get(DataComponentTypes.CUSTOM_DATA)
            ?.copyNbt()
            ?.getBoolean(NYA_AXE_TAG)
            ?.orElse(false) == true

    /**
     * 攻击者手持"秒人斧"时, 把这一击的伤害抬到必杀值, 并**消耗掉斧头** (只能用 1 次)。
     * 不满足条件时原样返回 amount。
     *
     * 注: 消耗发生在这里 (damage 的 HEAD), 所以万一这一刀被目标的无敌帧挡下也不会退回斧头 ——
     * 换来的是"消耗"与"伤害"同一处判定, 不会出现抬了伤害却没消耗的漏洞。
     */
    fun applyNyaAxe(attacker: ServerPlayerEntity, amount: Float): Float {
        val state = PerkStateManager.get(attacker.uuid) ?: return amount
        if (!state.hasPerk(NYA_AXE_PERK)) return amount

        val stack = attacker.mainHandStack
        if (!isNyaAxe(stack)) return amount

        // 消耗: 斧头不可堆叠, 直接扣掉整把
        stack.decrement(1)
        attacker.sendMessage(Text.translatable("random-perks-bingo.message.nya_axe_used"), true)
        return LETHAL_DAMAGE
    }

    // ---- "大胃袋": 满饱食度也能吃 (由 BigStomachMixin 在 ConsumableComponent.canConsume 拦截) ----

    /** 玩家是否持有"大胃袋" */
    fun hasBigStomach(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_big_stomach") == true

    // ---- "老玩家": 剑的攻击冷却始终蓄满 (由 OldSchoolCombatMixin 在 getAttackCooldownProgress 拦截) ----

    /** 玩家是否持有"老玩家" */
    fun hasOldSchoolCombat(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_veteran") == true
}
