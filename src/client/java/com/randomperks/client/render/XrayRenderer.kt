package com.randomperks.client.render

import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexRendering
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos

/**
 * "透视之眼"词条的客户端渲染: 给玩家 20 格内的矿石画**发光描边轮廓**
 * (白色细边, 类似生物发光的轮廓感; 使用无深度测试的 debug 渲染层 → 可穿透方块看到)。
 * 仅在持有该词条的客户端上渲染 —— 天然"仅自己可见"。
 */
object XrayRenderer {

    private const val PERK_ID = "delver_xray_vision"
    private const val RADIUS = 20
    private const val RESCAN_INTERVAL_TICKS = 20

    /** 描边粗细 (方块比例) */
    private const val EDGE_THICKNESS = 0.03

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
        val consumers = context.consumers() ?: return
        val matrices = context.matrixStack() ?: return
        val camera = context.camera()

        matrices.push()
        matrices.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z)

        val buffer = consumers.getBuffer(RenderLayer.getDebugFilledBox())
        for (pos in orePositions) {
            drawGlowOutline(matrices, buffer, pos)
        }

        matrices.pop()
    }

    /** 用 12 条细"板条"拼出立方体轮廓 —— 白色发光描边的观感, 可穿墙 */
    private fun drawGlowOutline(matrices: MatrixStack, buffer: VertexConsumer, pos: BlockPos) {
        val t = EDGE_THICKNESS
        val x0 = pos.x.toDouble()
        val y0 = pos.y.toDouble()
        val z0 = pos.z.toDouble()
        val x1 = x0 + 1.0
        val y1 = y0 + 1.0
        val z1 = z0 + 1.0
        val r = 1.0f
        val g = 1.0f
        val b = 1.0f
        val a = 0.9f

        // 沿 X 方向的 4 条边
        for (dy in doubleArrayOf(y0, y1)) {
            for (dz in doubleArrayOf(z0, z1)) {
                VertexRendering.drawBox(matrices, buffer, x0, dy - t, dz - t, x1, dy + t, dz + t, r, g, b, a)
            }
        }
        // 沿 Y 方向的 4 条边
        for (dx in doubleArrayOf(x0, x1)) {
            for (dz in doubleArrayOf(z0, z1)) {
                VertexRendering.drawBox(matrices, buffer, dx - t, y0, dz - t, dx + t, y1, dz + t, r, g, b, a)
            }
        }
        // 沿 Z 方向的 4 条边
        for (dx in doubleArrayOf(x0, x1)) {
            for (dy in doubleArrayOf(y0, y1)) {
                VertexRendering.drawBox(matrices, buffer, dx - t, dy - t, z0, dx + t, dy + t, z1, r, g, b, a)
            }
        }
    }
}
