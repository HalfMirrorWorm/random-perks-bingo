package com.randomperks.game

import com.randomperks.network.PerkNetworking
import com.randomperks.perk.PerkContext
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text

/**
 * "灵魂出窍" (金 · 疾风 · 贮藏) 的服务端逻辑。
 *
 * 参考 FreeCam / 灵魂出窍类 mod 的形态: **身体留在原地**, 灵魂 (视角) 飞出去。
 * - 服务端只记录开始/结束时间; 玩家的实体始终留在原地, 因此身体仍然会被攻击、
 *   会正常受重力影响 (悬空时激活要自己承担摔落风险);
 * - 客户端在灵魂状态下接管相机: 可以自由飞行、穿过方块 (纯客户端, 服务端不需要
 *   移动校验), 同时冻结身体的移动/攻击/使用物品输入;
 * - 60 秒后服务端发 SoulOutS2C(false), 客户端相机自动回到身体。
 */
object SoulOutService {

    const val PERK_ID = "delver_soul_out"

    /** 灵魂出窍持续时间: 60 秒 */
    private const val DURATION_TICKS = 60 * 20

    private const val KEY_END = "soul_out_end"

    /** 在 V 界面点击词条时触发 */
    fun activate(ctx: PerkContext) {
        val player = ctx.player
        val state = ctx.state

        if (state.isUsed(PERK_ID)) return
        if (isActive(player)) return

        val server = player.server ?: return
        val now = server.ticks.toLong()

        state.memory[KEY_END] = (now + DURATION_TICKS).toString()
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

    /** 每秒驱动一次: 时间到了就结束 (客户端相机随之归位) */
    fun tick(ctx: PerkContext) {
        val end = ctx.state.memory[KEY_END]?.toLongOrNull() ?: return
        val now = ctx.player.server?.ticks?.toLong() ?: return
        if (now >= end) {
            end(ctx.player, ctx.state)
        }
    }

    /** 玩家断线重连: 补回灵魂状态; 如果离线期间已经到期, 直接结束 */
    fun syncOnJoin(player: ServerPlayerEntity) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.memory.containsKey(KEY_END)) return

        if (isActive(player)) {
            PerkNetworking.sendSoulOut(player, true)
        } else {
            end(player, state)
        }
    }

    /** 对局重置 / 关闭时调用: 结束所有还在进行中的灵魂出窍 (必须在清空 memory 之前调用) */
    fun clear() {
        val server = ServerHolder.server ?: return
        for (player in server.playerManager.playerList) {
            val state = PerkStateManager.get(player.uuid) ?: continue
            if (state.memory.containsKey(KEY_END)) {
                end(player, state)
            }
        }
    }

    /** 该玩家此刻是否处于灵魂状态 */
    fun isSoul(player: ServerPlayerEntity): Boolean = isActive(player)

    private fun isActive(player: ServerPlayerEntity): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        val end = state.memory[KEY_END]?.toLongOrNull() ?: return false
        val now = player.server?.ticks?.toLong() ?: return false
        return now < end
    }

    fun end(player: ServerPlayerEntity, state: PlayerPerkState) {
        if (state.memory.remove(KEY_END) == null) return

        PerkNetworking.sendSoulOut(player, false)

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
