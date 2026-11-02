package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

/**
 * "烈焰喷射"词条: 手持火焰弹右键 → 像恶魂一样发射火球。
 */
object FireballService {

    private const val PERK_ID = "summon_fireball"

    /** 给"烈焰喷射"打出的火球打的标记 —— 用于禁止被攻击反弹 (见 FireballNoDeflectMixin) */
    const val FIREBALL_TAG = "randomperks_fireball"

    fun register() {
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (!player.mainHandItem.`is`(Items.FIRE_CHARGE)) return@register InteractionResult.PASS

            if (launch(player)) InteractionResult.SUCCESS else InteractionResult.PASS
        }
    }

    private fun launch(player: ServerPlayer): Boolean {
        if (!PerkSession.isEnabled()) return false
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        val world = player.level() as? ServerLevel ?: return false
        val direction = player.lookAngle

        val fireball = LargeFireball(world, player, direction.scale(1.0), 1)
        fireball.setPos(player.x + direction.x, player.eyeY - 0.2, player.z + direction.z)
        fireball.addTag(FIREBALL_TAG)
        world.addFreshEntity(fireball)

        // 消耗一枚火焰弹 (创造模式不消耗)
        if (!player.isCreative) {
            player.mainHandItem.shrink(1)
        }

        player.level().playSound(
            null, player.x, player.y, player.z,
            SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
        return true
    }
}
