package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.mixin.LivingEntityJumpVelocityInvoker
import com.randomperks.state.PerkStateManager
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
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
        if (state.memory[DOUBLE_JUMP_USED_KEY] == "1") {  // 每段离地只允许一次
            RandomPerksMod.logger.info(
                "[RandomPerks] double jump rejected for {} — already used this airtime", player.name.string,
            )
            return
        }

        state.memory[DOUBLE_JUMP_USED_KEY] = "1"

        // 真正的"再跳一次": 把垂直速度**置为**一次与自身跳跃能力相同的冲量 (置位而非叠加) ——
        // 任何时机按下都得到一致的标准跳跃高度 (与当前下落速度无关), 水平速度保持不变;
        // 冲量随跳跃提升等加成自动变化 (读取的是玩家真实的 getJumpVelocity(), 而不是固定数值)
        val impulse = (player as LivingEntityJumpVelocityInvoker).invokeGetJumpVelocity()
        val velocity = player.velocity
        player.velocity = Vec3d(velocity.x, impulse.toDouble(), velocity.z)
        player.velocityModified = true

        RandomPerksMod.logger.info(
            "[RandomPerks] double jump applied for {} (impulse {}, velY {} → {})",
            player.name.string, impulse, velocity.y, player.velocity.y,
        )

        // 反馈 (参考社区通行做法: 升空音效 + 跳跃位置喷一小团云):
        // 音效 = 烟花升空的"咻"; 粒子 = 脚下 cloud burst (服务端生成 → 附近玩家都能看到)
        player.playSoundToPlayer(
            SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
            SoundCategory.PLAYERS,
            0.6f,
            1.2f,
        )
        (player.world as? ServerWorld)?.spawnParticles(
            ParticleTypes.CLOUD,
            player.x, player.y + 0.05, player.z,
            10, 0.25, 0.05, 0.25, 0.01,
        )
    }

    /** 复位"每段离地一次"标记 (落地由 PlayerLandingMixin 调用; 重生由词条 onRespawn 调用) */
    fun clearDoubleJumpUsed(player: ServerPlayerEntity) {
        PerkStateManager.get(player.uuid)?.memory?.remove(DOUBLE_JUMP_USED_KEY)
    }
}
