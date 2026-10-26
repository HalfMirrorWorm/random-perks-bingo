package com.randomperks.game

import com.randomperks.network.PerkNetworking
import com.randomperks.perk.PerkContext
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text

/**
 * "灵魂出鞘" (金 · 疾跑 · 贮藏) 的服务端逻辑。
 *
 * 玩家在 V 界面点击使用后灵魂离体 **60 秒**:
 * - 可以飞行;
 * - 水平方向可以穿墙 (客户端 [com.randomperks.mixin.SoulOutMovementMixin] 拆分移动,
 *   服务端 [com.randomperks.mixin.SoulOutValidationMixin] 放行移动校验);
 * - 隐身, 并且免疫普通伤害 (无视无敌的伤害仍然生效);
 * - 结束时回到激活时身体所在的位置。
 *
 * 状态写在 [PlayerPerkState.memory] 里, 所以断线重连后仍然有效。
 */
object SoulOutService {

    const val PERK_ID = "swift_soul_out"

    /** 灵魂出鞘持续时间: 60 秒 */
    private const val DURATION_TICKS = 60 * 20

    private const val KEY_END = "soul_out_end"
    private const val KEY_X = "soul_out_x"
    private const val KEY_Y = "soul_out_y"
    private const val KEY_Z = "soul_out_z"
    private const val KEY_FLY_BEFORE = "soul_out_fly_before"

    /** 在 V 界面点击词条时触发 */
    fun activate(ctx: PerkContext) {
        val player = ctx.player
        val state = ctx.state

        if (state.isUsed(PERK_ID)) return
        if (isActive(player)) return

        val server = player.server ?: return
        val now = server.ticks.toLong()

        // 记住"身体"的位置, 结束时飞回去
        state.memory[KEY_END] = (now + DURATION_TICKS).toString()
        state.memory[KEY_X] = player.x.toString()
        state.memory[KEY_Y] = player.y.toString()
        state.memory[KEY_Z] = player.z.toString()
        state.memory[KEY_FLY_BEFORE] = if (player.abilities.allowFlying) "1" else "0"

        if (player.isGliding) player.stopGliding()
        player.stopRiding()

        player.abilities.allowFlying = true
        player.abilities.flying = true
        player.sendAbilitiesUpdate()

        // 灵魂不可见; 不显示粒子与图标, 避免暴露位置
        player.addStatusEffect(
            StatusEffectInstance(StatusEffects.INVISIBILITY, DURATION_TICKS, 0, false, false, false)
        )

        state.markUsed(PERK_ID)
        PerkNetworking.sendSoulOut(player, true)

        player.playSoundToPlayer(
            SoundEvents.PARTICLE_SOUL_ESCAPE.value(),
            SoundCategory.PLAYERS,
            0.9f,
            0.8f,
        )
        (player.world as? ServerWorld)?.spawnParticles(
            ParticleTypes.SOUL,
            player.x, player.y + 0.9, player.z,
            24, 0.35, 0.6, 0.35, 0.02,
        )
        player.sendMessage(Text.translatable("random-perks-bingo.message.soul_out_start"), false)
    }

    /** 每秒驱动一次: 时间到了就归位 */
    fun tick(ctx: PerkContext) {
        val end = ctx.state.memory[KEY_END]?.toLongOrNull() ?: return
        val now = ctx.player.server?.ticks?.toLong() ?: return
        if (now >= end) {
            end(ctx.player, ctx.state, teleportBack = true)
        }
    }

    /** 玩家断线重连: 补回灵魂状态; 如果离线期间已经到期, 立刻归位并收回能力 */
    fun syncOnJoin(player: ServerPlayerEntity) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.memory.containsKey(KEY_END)) return

        if (!isActive(player)) {
            // 离线期间到期: 玩家数据里 allowFlying 仍是 true, 必须在这里清掉
            end(player, state, teleportBack = true)
            return
        }

        PerkNetworking.sendSoulOut(player, true)
        player.abilities.allowFlying = true
        player.sendAbilitiesUpdate()
    }

    /** 对局重置 / 关闭时调用: 让所有还处于灵魂状态的玩家先归位 (必须在清空 memory 之前调用) */
    fun clear() {
        val server = ServerHolder.server ?: return
        for (player in server.playerManager.playerList) {
            val state = PerkStateManager.get(player.uuid) ?: continue
            if (state.memory.containsKey(KEY_END)) {
                end(player, state, teleportBack = true)
            }
        }
    }

    /** 灵魂状态下免疫普通伤害 (与图腾/复活甲一致, 无视无敌的伤害仍然生效) */
    fun blocksDamage(player: ServerPlayerEntity, source: DamageSource): Boolean {
        if (!isActive(player)) return false
        return !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
    }

    /** 该玩家此刻是否处于灵魂状态 (mixin 也会调用) */
    fun isSoul(player: ServerPlayerEntity): Boolean = isActive(player)

    private fun isActive(player: ServerPlayerEntity): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        val end = state.memory[KEY_END]?.toLongOrNull() ?: return false
        val now = player.server?.ticks?.toLong() ?: return false
        return now < end
    }

    fun end(player: ServerPlayerEntity, state: PlayerPerkState, teleportBack: Boolean) {
        if (state.memory.remove(KEY_END) == null) return

        val x = state.memory.remove(KEY_X)?.toDoubleOrNull()
        val y = state.memory.remove(KEY_Y)?.toDoubleOrNull()
        val z = state.memory.remove(KEY_Z)?.toDoubleOrNull()
        val flyBefore = state.memory.remove(KEY_FLY_BEFORE) == "1"

        // 只有本来就是"不能飞"的玩家才收回飞行能力 (创造/旁观不受影响)
        if (!flyBefore && !player.isCreative && !player.isSpectator) {
            player.abilities.allowFlying = false
            player.abilities.flying = false
        }
        player.sendAbilitiesUpdate()
        player.removeStatusEffect(StatusEffects.INVISIBILITY)

        PerkNetworking.sendSoulOut(player, false)

        if (teleportBack && x != null && y != null && z != null) {
            player.teleport(
                player.world,
                x, y, z,
                emptySet(),
                player.yaw,
                player.pitch,
                true,
            )
        }

        player.playSoundToPlayer(
            SoundEvents.PARTICLE_SOUL_ESCAPE.value(),
            SoundCategory.PLAYERS,
            0.7f,
            1.25f,
        )
        (player.world as? ServerWorld)?.spawnParticles(
            ParticleTypes.SOUL,
            player.x, player.y + 0.9, player.z,
            16, 0.3, 0.5, 0.3, 0.02,
        )
        player.sendMessage(Text.translatable("random-perks-bingo.message.soul_out_end"), false)
    }
}
