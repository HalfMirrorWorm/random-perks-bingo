package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d

/**
 * 需要客户端按键配合的主动能力 (通过自定义 C2S 包触发)。
 */
object PerkAbilityService {

    private const val DOUBLE_JUMP_PERK_ID = "swift_double_jump"

    /** 二段跳冷却 (tick), 防止包连发刷跳跃 */
    private const val DOUBLE_JUMP_COOLDOWN = 10L

    /** 二段跳初速度 (普通跳跃为 0.42) */
    private const val DOUBLE_JUMP_VELOCITY = 0.55

    /**
     * 处理客户端发来的二段跳请求。
     * 服务端验证: 持有词条 / 在空中 / 冷却已过。
     */
    fun handleDoubleJump(player: ServerPlayerEntity) {
        if (!PerkSession.isEnabled()) return
        if (player.isOnGround) return

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(DOUBLE_JUMP_PERK_ID)) return

        val server = player.server ?: return
        val now = server.ticks.toLong()
        if (now < (state.cooldowns["double_jump"] ?: 0L)) return
        state.cooldowns["double_jump"] = now + DOUBLE_JUMP_COOLDOWN

        val velocity = player.velocity
        player.velocity = Vec3d(velocity.x, DOUBLE_JUMP_VELOCITY, velocity.z)
        player.velocityModified = true

        player.playSoundToPlayer(
            SoundEvents.ENTITY_BAT_TAKEOFF,
            SoundCategory.PLAYERS,
            0.7f,
            1.4f,
        )
    }
}
