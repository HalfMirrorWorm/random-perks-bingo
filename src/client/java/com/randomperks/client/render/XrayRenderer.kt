package com.randomperks.client.render

import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.OutlineVertexConsumerProvider
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * "透视之眼"词条的客户端渲染: 给玩家 [RADIUS] 格内的矿石画**发光框**
 * (每个矿石一个立体线框, 12 条棱)。
 *
 * 沿用原版"实体发光效果"的同一套机制 (OutlineVertexConsumerProvider + getOutline 渲染层):
 * 纯色、无视深度、穿过方块可见; 仅在持有该词条的客户端上渲染 —— 天然"仅自己可见"。
 * 线框画法参考成熟社区实现 (Shimmer / Spelunker)。
 */
object XrayRenderer {

    private const val PERK_ID = "delver_xray_vision"
    private const val RADIUS = 20
    private const val RESCAN_INTERVAL_TICKS = 20

    /** 线框棱的"半径" (格): 0.04 → 线宽约 0.08 格 */
    private const val EDGE_HALF = 0.04f

    /** 渐隐起点 (格): 以内全尺寸, 到 [RADIUS] 线性缩没 */
    private const val FADE_NEAR = 14.0

    /** outline 渲染层用的纹理 (只要求 alpha 全 1; 原版纯白贴图) */
    private val OUTLINE_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png")

    /** 原版各矿石标签 (没有总标签, 逐一列出; modded 矿石靠名称兜底) */
    private val ORE_TAGS = listOf(
        BlockTags.COAL_ORES,
        BlockTags.IRON_ORES,
        BlockTags.COPPER_ORES,
        BlockTags.GOLD_ORES,
        BlockTags.REDSTONE_ORES,
        BlockTags.LAPIS_ORES,
        BlockTags.DIAMOND_ORES,
        BlockTags.EMERALD_ORES,
    )

    /** 单位立方体的 12 条棱 (每条 = x0,y0,z0, x1,y1,z1) */
    private val EDGES = arrayOf(
        // 底面 (y=0)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f),
        floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f),
        floatArrayOf(1f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 0f),
        // 顶面 (y=1)
        floatArrayOf(0f, 1f, 0f, 1f, 1f, 0f),
        floatArrayOf(1f, 1f, 0f, 1f, 1f, 1f),
        floatArrayOf(1f, 1f, 1f, 0f, 1f, 1f),
        floatArrayOf(0f, 1f, 1f, 0f, 1f, 0f),
        // 竖棱
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f),
        floatArrayOf(1f, 0f, 0f, 1f, 1f, 0f),
        floatArrayOf(1f, 0f, 1f, 1f, 1f, 1f),
        floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f),
    )

    private var orePositions: List<BlockPos> = emptyList()
    private var tickCounter = 0

    private fun isOre(state: BlockState): Boolean {
        if (ORE_TAGS.any { state.isIn(it) }) return true
        // 兜底: 方块 id 含 "ore" 或远古残骸 (兼容模组矿石)
        val path = Registries.BLOCK.getId(state.block).path
        return path.contains("ore") || path == "ancient_debris"
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
        WorldRenderEvents.AFTER_TRANSLUCENT.register { context -> render(context) }
    }

    /** 每秒扫描一次周围矿石并缓存位置 */
    private fun tick(client: MinecraftClient) {
        tickCounter++
        if (tickCounter % RESCAN_INTERVAL_TICKS != 0) return

        val player = client.player
        val world = client.world
        if (player == null || world == null || !ClientPerkNetworking.hasPerk(PERK_ID)) {
            orePositions = emptyList()
            return
        }

        val center = player.blockPos
        val found = mutableListOf<BlockPos>()
        for (dx in -RADIUS..RADIUS) {
            for (dy in -RADIUS..RADIUS) {
                for (dz in -RADIUS..RADIUS) {
                    val pos = center.add(dx, dy, dz)
                    if (isOre(world.getBlockState(pos))) {
                        found.add(pos.toImmutable())
                    }
                }
            }
        }
        orePositions = found
    }

    private fun render(context: WorldRenderContext) {
        if (orePositions.isEmpty()) return
        val immediate = context.consumers() as? VertexConsumerProvider.Immediate ?: return

        // 原版"发光"同款: 顶点被强制成 setColor 的纯色, 渲染进 getOutline 层 (无视深度, 穿墙)
        val outline = OutlineVertexConsumerProvider(immediate)
        outline.setColor(255, 255, 255, 255)
        val buffer = outline.getBuffer(RenderLayer.getOutline(OUTLINE_TEXTURE))

        val camera = context.camera()
        val camX = camera.pos.x
        val camY = camera.pos.y
        val camZ = camera.pos.z
        val far2 = (RADIUS * RADIUS).toDouble()
        val near2 = FADE_NEAR * FADE_NEAR

        for (pos in orePositions) {
            // 相对相机的中心偏移 (减完相机再转 Float, 避免大坐标精度损失)
            val ox = pos.x + 0.5 - camX
            val oy = pos.y + 0.5 - camY
            val oz = pos.z + 0.5 - camZ
            val d2 = ox * ox + oy * oy + oz * oz
            val cx = ox.toFloat()
            val cy = oy.toFloat()
            val cz = oz.toFloat()
            val fade = ((far2 - d2) / (far2 - near2)).coerceIn(0.0, 1.0)
            if (fade <= 0.0) continue
            drawWireframe(buffer, cx, cy, cz, fade.toFloat())
        }

        outline.draw()
    }

    /** 画一个以 (cx,cy,cz) 为方块中心的线框; [scale] 为距离渐隐的收缩系数 (线宽不缩) */
    private fun drawWireframe(buffer: VertexConsumer, cx: Float, cy: Float, cz: Float, scale: Float) {
        for (e in EDGES) {
            // 端点变换到"以方块中心为原点、缩放后"的局部坐标
            val x0 = (e[0] - 0.5f) * scale
            val y0 = (e[1] - 0.5f) * scale
            val z0 = (e[2] - 0.5f) * scale
            val x1 = (e[3] - 0.5f) * scale
            val y1 = (e[4] - 0.5f) * scale
            val z1 = (e[5] - 0.5f) * scale

            // 每条棱用两个互相垂直的"面片"组成十字 (从任意角度至少一面可见)
            if (e[0] != e[3]) { // 沿 X
                emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, 0f, EDGE_HALF, 0f)
                emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, 0f, 0f, EDGE_HALF)
            } else if (e[1] != e[4]) { // 沿 Y
                emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, EDGE_HALF, 0f, 0f)
                emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, 0f, 0f, EDGE_HALF)
            } else { // 沿 Z
                emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, EDGE_HALF, 0f, 0f)
                emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, 0f, EDGE_HALF, 0f)
            }
        }
    }

    /** 一条棱沿线轴向 (ax,ay,az) 扩展 ±EDGE_HALF 形成的四边形面片 */
    private fun emitQuad(
        buffer: VertexConsumer, cx: Float, cy: Float, cz: Float,
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        ax: Float, ay: Float, az: Float,
    ) {
        val ox = ax * EDGE_HALF
        val oy = ay * EDGE_HALF
        val oz = az * EDGE_HALF
        putVertex(buffer, cx, cy, cz, x0 - ox, y0 - oy, z0 - oz)
        putVertex(buffer, cx, cy, cz, x1 - ox, y1 - oy, z1 - oz)
        putVertex(buffer, cx, cy, cz, x1 + ox, y1 + oy, z1 + oz)
        putVertex(buffer, cx, cy, cz, x0 + ox, y0 + oy, z0 + oz)
    }

    private fun putVertex(
        buffer: VertexConsumer, cx: Float, cy: Float, cz: Float,
        x: Float, y: Float, z: Float,
    ) {
        // 颜色 -1 (白色) 会被 OutlineVertexConsumerProvider 强制替换; 光照/法线在该层无关紧要
        buffer.vertex(cx + x, cy + y, cz + z, -1, 0f, 0f, 0, 0, 0f, 1f, 0f)
    }
}
