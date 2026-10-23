package com.randomperks.game

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "幽灵漫步" —— 水平方向穿墙移动的判定。
 *
 * 客户端读词条能力镜像 (由 PerkAbilitySyncS2C 同步), 服务端读玩家词条状态;
 * 两边都返回 true 时, PhaseWalkMixin 才会拆分水平/垂直移动。
 */
object PhaseWalkService {

    const val PERK_ID = "swift_ghost_walk"

    fun canPhase(player: PlayerEntity): Boolean =
        if (player.world.isClient) {
            ClientPerkFlags.has(PERK_ID)
        } else {
            (player as? ServerPlayerEntity)?.let { PerkBoostQuery.hasGhostWalk(it) } == true
        }
}
