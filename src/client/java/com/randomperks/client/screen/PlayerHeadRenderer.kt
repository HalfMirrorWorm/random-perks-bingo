package com.randomperks.client.screen

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import java.util.UUID

/**
 * 在选择器/玩家 tab 里绘制玩家头像 (皮肤面部 8×8)。
 * 找不到玩家列表条目时返回 false, 调用方保留原有的文字位置。
 */
object PlayerHeadRenderer {

    const val SIZE = 8

    fun draw(context: DrawContext, uuid: UUID?, x: Int, y: Int): Boolean {
        if (uuid == null) return false
        val entry = MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(uuid)
            ?: return false
        return drawEntry(context, entry.skinTextures.texture(), x, y)
    }

    fun drawByName(context: DrawContext, name: String, x: Int, y: Int): Boolean {
        val handler = MinecraftClient.getInstance().networkHandler ?: return false
        val entry = handler.playerList.firstOrNull { it.profile.name == name } ?: return false
        return drawEntry(context, entry.skinTextures.texture(), x, y)
    }

    private fun drawEntry(context: DrawContext, texture: net.minecraft.util.Identifier, x: Int, y: Int): Boolean {
        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x, y,
            8f, 8f,
            SIZE, SIZE,
            64, 64,
        )
        return true
    }
}
