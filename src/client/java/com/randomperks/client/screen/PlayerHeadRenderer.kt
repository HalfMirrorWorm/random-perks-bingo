package com.randomperks.client.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.UUID

/**
 * 在选择器/玩家 tab 里绘制玩家头像 (皮肤面部 8×8)。
 * 找不到玩家列表条目时返回 false, 调用方保留原有的文字位置。
 */
object PlayerHeadRenderer {

    const val SIZE = 8

    fun draw(context: GuiGraphicsExtractor, uuid: UUID?, x: Int, y: Int): Boolean {
        if (uuid == null) return false
        val entry = Minecraft.getInstance().connection?.getPlayerInfo(uuid)
            ?: return false
        return drawEntry(context, entry.skin.body().texturePath(), x, y)
    }

    fun drawByName(context: GuiGraphicsExtractor, name: String, x: Int, y: Int): Boolean {
        val handler = Minecraft.getInstance().connection ?: return false
        val entry = handler.onlinePlayers.firstOrNull { it.profile.name == name } ?: return false
        return drawEntry(context, entry.skin.body().texturePath(), x, y)
    }

    private fun drawEntry(context: GuiGraphicsExtractor, texture: net.minecraft.resources.Identifier, x: Int, y: Int): Boolean {
        context.blit(
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
