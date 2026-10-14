package com.randomperks.game

import com.randomperks.network.PerkNetworking
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 团队可见的"发光标记" (代替原版全局发光的 GLOWING 状态效果)。
 *
 * 服务端把被标记实体的 id 只推送给**同队玩家**的客户端;
 * 同队客户端通过 EntityGlowMixin 在本地把这些实体渲染为发光轮廓 —— 敌方看不到。
 */
object GlowMarkService {

    /** 玩家的同队在线玩家 (含自己); 无队伍时仅自己 */
    fun teamViewers(player: ServerPlayerEntity): List<ServerPlayerEntity> {
        val server = player.server ?: return listOf(player)
        val team = player.scoreboardTeam?.name ?: return listOf(player)
        return server.playerManager.playerList.filter {
            it.uuid == player.uuid || it.scoreboardTeam?.name == team
        }
    }

    /** 让观察者们的客户端把 [entity] 显示为发光 (durationMillis 毫秒后自动消失) */
    fun markFor(viewers: List<ServerPlayerEntity>, entity: Entity, durationMillis: Long) {
        if (viewers.isEmpty()) return
        val ids = listOf(entity.id)
        viewers.forEach { PerkNetworking.sendGlowMark(it, ids, durationMillis) }
    }
}
