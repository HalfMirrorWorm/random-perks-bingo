package com.randomperks.game

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

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
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (!canThrow(player)) return@register InteractionResult.PASS

            throwTnt(player)
            InteractionResult.SUCCESS
        }

        // 右键空气 (1.21.2+ 的 UseItemCallback 直接返回 ActionResult)
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (!canThrow(player)) return@register InteractionResult.PASS

            throwTnt(player)
            InteractionResult.SUCCESS
        }
    }

    private fun canThrow(player: ServerPlayer): Boolean =
        PerkBoostQuery.hasTntThrow(player) && player.getItemInHand(InteractionHand.MAIN_HAND).`is`(Items.TNT)

    private fun throwTnt(player: ServerPlayer) {
        val world = player.level() as? ServerLevel ?: return

        // 消耗一个 TNT (创造模式不消耗)
        if (!player.isCreative) {
            player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1)
        }

        val tnt = PrimedTnt(
            world,
            player.x,
            player.getEyeY() - 0.1,
            player.z,
            player,
        )
        tnt.setFuse(FUSE_TICKS)
        tnt.setDeltaMovement(player.lookAngle.scale(SPEED))
        world.addFreshEntity(tnt)
    }
}
