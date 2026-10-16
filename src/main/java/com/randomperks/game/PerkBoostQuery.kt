package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.tag.ItemTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * 供 Java mixin 查询词条状态的轻量桥接 (避免 Java 侧直接操作 Kotlin 状态对象)。
 */
object PerkBoostQuery {

    /** 玩家是否持有"贪婪之手" (受到其他生物 2 倍伤害) */
    fun hasFragileToMobs(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_greedy_hands") == true

    /** 玩家是否持有"美食家" (进食恢复 +25%) */
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

    // ---- "透视之眼"的代价: 50% 不掉落 (由 XrayDropSuppressMixin 在 Block.dropStacks 拦截) ----

    private const val XRAY_PERK = "delver_xray_vision"

    /** 记录"该玩家本 tick 的挖块掉落被透视惩罚抑制"的 tick (供矿脉嗅觉等联动词条查询) */
    const val XRAY_SUPPRESS_TICK_KEY = "xray_suppress_tick"

    /** 用镐破坏方块时是否应抑制本次掉落生成 (持有词条 + 手持镐 + 50%) */
    fun shouldSuppressXrayDrop(player: ServerPlayerEntity, tool: ItemStack): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(XRAY_PERK)) return false
        if (!tool.isIn(ItemTags.PICKAXES)) return false
        if (player.random.nextFloat() >= 0.5f) return false
        // 抑制生效: 留下本 tick 的标记 —— "矿脉嗅觉"等额外掉落会据此一并抑制, 保证
        // 额外份同样被 X-Ray 惩罚 (不生成式下唯一能让联动成立的方式)
        state.memory[XRAY_SUPPRESS_TICK_KEY] = (player.server?.ticks ?: 0).toString()
        return true
    }
}
