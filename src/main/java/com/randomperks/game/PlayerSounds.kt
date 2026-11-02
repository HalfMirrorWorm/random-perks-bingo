package com.randomperks.game

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent

/**
 * 1.21.11 删掉了 ServerPlayerEntity.playSoundToPlayer。
 * 1.21.10 的实现就是只给这名玩家发一个 PlaySoundS2CPacket, 这里照原样补上,
 * 词条反馈音仍然只有本人听得到。
 */
fun ServerPlayerEntity.playSoundToPlayer(sound: SoundEvent, category: SoundCategory, volume: Float, pitch: Float) {
    networkHandler.sendPacket(
        PlaySoundS2CPacket(
            Registries.SOUND_EVENT.getEntry(sound),
            category,
            x,
            y,
            z,
            volume,
            pitch,
            random.nextLong(),
        ),
    )
}
