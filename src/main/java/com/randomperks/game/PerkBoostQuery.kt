package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity

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
}
