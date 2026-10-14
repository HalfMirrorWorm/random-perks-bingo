package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 供 Java mixin 查询词条状态的轻量桥接 (避免 Java 侧直接操作 Kotlin 状态对象)。
 */
object PerkBoostQuery {

    /** 玩家是否持有"贪婪之手" (受到其他生物 2 倍伤害) */
    fun hasFragileToMobs(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_greedy_hands") == true
}
