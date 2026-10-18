package com.randomperks.game

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.TntEntity
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * "TNT 投手" (紫 · 猎杀) —— 手持 TNT 右键时改为**抛射**一枚点燃的 TNT, 而不是放置方块。
 *
 * 右键方块和右键空气走的是两条路径 (useOnBlock / use), 所以两个事件都要拦:
 * 前者返回 SUCCESS 阻止原版的放置, 后者返回 SUCCESS 阻止原版的手部动画后空转。
 */
object TntThrowService {

    /** 引信: 80 tick = 4 秒 (与原版放置的 TNT 一致) */
    private const val FUSE_TICKS = 80

    /** 抛出速度 */
    private const val SPEED = 1.2

    fun register() {
        // 右键方块: 阻止放置, 改为抛射
        UseBlockCallback.EVENT.register { player, _, hand, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (!canThrow(player)) return@register ActionResult.PASS

            throwTnt(player)
            ActionResult.SUCCESS
        }

        // 右键空气 (1.21.2+ 的 UseItemCallback 直接返回 ActionResult)
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (!canThrow(player)) return@register ActionResult.PASS

            throwTnt(player)
            ActionResult.SUCCESS
        }
    }

    private fun canThrow(player: ServerPlayerEntity): Boolean =
        PerkBoostQuery.hasTntThrow(player) && player.getStackInHand(Hand.MAIN_HAND).isOf(Items.TNT)

    private fun throwTnt(player: ServerPlayerEntity) {
        val world = player.world as? ServerWorld ?: return

        // 消耗一个 TNT (创造模式不消耗)
        if (!player.isCreative) {
            player.getStackInHand(Hand.MAIN_HAND).decrement(1)
        }

        val tnt = TntEntity(
            world,
            player.x,
            player.getEyeY() - 0.1,
            player.z,
            player,
        )
        tnt.setFuse(FUSE_TICKS)
        tnt.setVelocity(player.rotationVector.multiply(SPEED))
        world.spawnEntity(tnt)
    }
}
