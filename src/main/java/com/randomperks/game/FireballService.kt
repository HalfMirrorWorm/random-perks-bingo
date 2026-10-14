package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.projectile.FireballEntity
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * "烈焰喷射"词条: 手持火焰弹右键 → 像恶魂一样发射火球。
 */
object FireballService {

    private const val PERK_ID = "hunter_fireball"

    fun register() {
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (!player.mainHandStack.isOf(Items.FIRE_CHARGE)) return@register ActionResult.PASS

            if (launch(player)) ActionResult.SUCCESS else ActionResult.PASS
        }
    }

    private fun launch(player: ServerPlayerEntity): Boolean {
        if (!PerkSession.isEnabled()) return false
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        val world = player.world as? ServerWorld ?: return false
        val direction = player.rotationVector

        val fireball = FireballEntity(world, player, direction.multiply(1.0), 1)
        fireball.setPosition(player.x + direction.x, player.eyeY - 0.2, player.z + direction.z)
        world.spawnEntity(fireball)

        // 消耗一枚火焰弹 (创造模式不消耗)
        if (!player.isCreative) {
            player.mainHandStack.decrement(1)
        }

        player.playSoundToPlayer(SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.2f)
        return true
    }
}
