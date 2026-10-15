package com.randomperks.game

import com.randomperks.mixin.LivingEntityJumpVelocityInvoker
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

    /** "每段离地只允许一次"标记 (落地/重生时复位) */
    private const val DOUBLE_JUMP_USED_KEY = "double_jump_used"

    /**
     * 处理客户端发来的二段跳请求。
     * 服务端验证: 持有词条 / 在空中 / 未滑翔 / 未骑乘 / 本段离地未用过。
     */
    fun handleDoubleJump(player: ServerPlayerEntity) {
        if (!PerkSession.isEnabled()) return
        if (player.isOnGround) {
            clearDoubleJumpUsed(player)
            return
        }
        if (player.isGliding) return      // 滑翔中不可用 (堵住"无限飞行"路径)
        if (player.getVehicle() != null) return  // 骑乘中不可用 (空格属于载具)

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(DOUBLE_JUMP_PERK_ID)) return
        if (state.memory[DOUBLE_JUMP_USED_KEY] == "1") return  // 每段离地只允许一次

        state.memory[DOUBLE_JUMP_USED_KEY] = "1"

        // 真正的"再跳一次": 在当前垂直速度上**叠加**一次与自身跳跃能力相同的冲量 ——
        // 起跳后立刻按会跳得更高, 下落中按则先抵消下坠; 冲量随跳跃提升等加成自动变化
        // (读取的是玩家真实的 getJumpVelocity(), 而不是固定数值)
        val impulse = (player as LivingEntityJumpVelocityInvoker).invokeGetJumpVelocity()
        val velocity = player.velocity
        player.velocity = Vec3d(velocity.x, velocity.y + impulse, velocity.z)
        player.velocityModified = true

        player.playSoundToPlayer(
            SoundEvents.ENTITY_BAT_TAKEOFF,
            SoundCategory.PLAYERS,
            0.7f,
            1.4f,
        )
    }

    /** 复位"每段离地一次"标记 (落地由 PlayerLandingMixin 调用; 重生由词条 onRespawn 调用) */
    fun clearDoubleJumpUsed(player: ServerPlayerEntity) {
        PerkStateManager.get(player.uuid)?.memory?.remove(DOUBLE_JUMP_USED_KEY)
    }
}
