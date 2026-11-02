package com.randomperks.game

import com.randomperks.network.MorphS2C
import com.randomperks.network.PerkNetworking
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.animal.pig.PigSoundVariants

/**
 * "变群友" (蓝 · 丰饶) 的服务端状态维护。
 *
 * 词条效果是**永久**的: 只要玩家持有词条, 他就是一只猪 (0.9 格高, 可以钻 1 格高的缝)。
 * 所以这里不做"触发/计时", 而是持续把"谁该是猪"对账到 [MorphState]:
 *
 *  - 每秒对账一次 (获得词条、对局重置、重生换了实体 id 都能自动跟上);
 *  - 状态变化时 `calculateDimensions()` 重算碰撞箱 —— 改 getBaseDimensions 的返回值
 *    不会自动更新已缓存的 boundingBox;
 *  - 变化广播给所有客户端 (外观是客户端渲染的, 别人也得知道你是猪)。
 *
 * 状态以**玩家 UUID** 为键 (见 [MorphState]): 服务端重生会换实体 id,
 * 客户端本地玩家的实体 id 却保持不变, 用 id 会让重生后的视角重置。
 */
object PigMorphService {

    const val PERK_ID = "harvest_pig_morph"

    fun register() {
        // 每秒对账 (20 tick = 1 秒)
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % 20 == 0) reconcile(server)
        }

        // 重生后重新对账一次 (UUID 不变, 但新实体的碰撞箱需要立即刷新)
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ -> apply(newPlayer) }

        // 新玩家加入: 先把自己该看到的变形状态发过去
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            apply(handler.player)
            syncTo(handler.player)
        }

        // 玩家离开: 清掉他的 UUID (客户端也收到 false)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            val uuid = handler.player.uuid
            if (MorphState.isMorphed(uuid)) {
                MorphState.setMorphed(uuid, false)
                broadcast(server, MorphS2C(uuid, false))
            }
        }
    }

    /** 立即按当前词条状态刷新某位玩家 (获得词条 / 重生时调用) */
    fun refresh(player: ServerPlayer) = apply(player)

    private fun reconcile(server: MinecraftServer) {
        val online = server.playerList.players
        for (player in online) apply(player)

        // 清掉已经不在线的玩家 UUID
        val currentIds = online.mapTo(HashSet()) { it.uuid }
        for (uuid in MorphState.players()) {
            if (uuid !in currentIds) {
                MorphState.setMorphed(uuid, false)
                broadcast(server, MorphS2C(uuid, false))
            }
        }
    }

    private fun apply(player: ServerPlayer) {
        val wanted = PerkStateManager.get(player.uuid)?.hasPerk(PERK_ID) == true
        val changed = MorphState.isMorphed(player.uuid) != wanted

        if (changed) {
            MorphState.setMorphed(player.uuid, wanted)
            broadcast(player.level().getServer(), MorphS2C(player.uuid, wanted))

            // 变形的那一瞬间"哼"一声
            if (wanted) {
                val oink = player.level().registryAccess()
                    .lookupOrThrow(Registries.PIG_SOUND_VARIANT)
                    .getValueOrThrow(PigSoundVariants.CLASSIC)
                    .adultSounds()
                    .ambientSound()
                    .value()
                player.level().playSound(
                    null,
                    player.x, player.y, player.z,
                    oink,
                    SoundSource.NEUTRAL,
                    1.0f,
                    0.9f + player.random.nextFloat() * 0.2f,
                )
            }
        }

        // ★ 即使 UUID 状态没变也要刷新碰撞箱: 重生会创建新的实体, 它的 boundingBox
        //   是新算的; 不调用的话新实体仍是普通玩家尺寸 (钻不过 1 格缝)。
        player.refreshDimensions()
    }

    /** 把"每位在线玩家是否是猪"完整发给某个客户端 (加入服务器时用) */
    private fun syncTo(player: ServerPlayer) {
        val server = player.level().getServer()
        for (other in server.playerList.players) {
            PerkNetworking.sendMorph(player, MorphS2C(other.uuid, MorphState.isMorphed(other.uuid)))
        }
    }

    private fun broadcast(server: MinecraftServer?, payload: MorphS2C) {
        server ?: return
        for (target in server.playerList.players) {
            PerkNetworking.sendMorph(target, payload)
        }
    }
}
