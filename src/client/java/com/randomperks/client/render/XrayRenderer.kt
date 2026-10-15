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
 * "透视之眼"词条的客户端渲染:
 * 给玩家 [RADIUS] 格内的矿石画**按种类着色的发光轮廓线** ——
 *  - 每种矿石一个颜色 (煤灰 / 铁米 / 铜橙 / 金黄 / 红石红 / 青金蓝 / 钻青 / 绿宝绿...)
 *  - 相接的同种矿石合并轮廓: 只画外沿棱, 剔除相邻方块之间的共享棱 (体素轮廓规则)
 *
 * 沿用原版"实体发光"机制 (OutlineVertexConsumerProvider + getOutline 渲染层):
 * 纯色、无视深度、穿过方块可见; 仅持有该词条的客户端上渲染, 天然"仅自己可见"。
 */
object XrayRenderer {

    private const val PERK_ID = "delver_xray_vision"
    private const val RADIUS = 20
    private const val RESCAN_INTERVAL_TICKS = 20

    /** 轮廓线"半径" (格): 0.04 → 线宽约 0.08 格 */
    private const val EDGE_HALF = 0.04f

    /** outline 渲染层用的纹理 (只要求 alpha 全 1; 原版纯白贴图) */
    private val OUTLINE_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png")

    /** 矿石种类判定 (原版标签优先; modded 矿石按方块 id 兜底) */
    private val ORE_KIND_TAGS = listOf(
        BlockTags.COAL_ORES to "coal",
        BlockTags.IRON_ORES to "iron",
        BlockTags.COPPER_ORES to "copper",
        BlockTags.GOLD_ORES to "gold",
        BlockTags.REDSTONE_ORES to "redstone",
        BlockTags.LAPIS_ORES to "lapis",
        BlockTags.DIAMOND_ORES to "diamond",
        BlockTags.EMERALD_ORES to "emerald",
    )

    /** 各矿石种类的轮廓颜色 (0xRRGGBB); 未列出的类别用白色 */
    private val ORE_COLORS = mapOf(
        "coal" to 0x606060,
        "iron" to 0xD8AF93,
        "copper" to 0xE77C56,
        "gold" to 0xFCE94B,
        "redstone" to 0xFF3030,
        "lapis" to 0x3A6FD8,
        "diamond" to 0x33EBD0,
        "emerald" to 0x35D94F,
        "debris" to 0x8F6248,
    )
    private const val DEFAULT_COLOR = 0xFFFFFF

    /** 垂直于各轴的另两条轴 (用于棱的角位置/邻接判定) */
    private val U_AXIS = intArrayOf(1, 0, 0) // X→Y, Y→X, Z→X
    private val V_AXIS = intArrayOf(2, 2, 1) // X→Z, Y→Z, Z→Y

    /** 一次扫描的结果: 矿石种类 → 该组可见轮廓段 */
    private var outlineSegments: Map<String, List<Segment>> = emptyMap()
    private var tickCounter = 0

    /** 一条可见棱段: 方块位置 + 沿哪个轴 + 两个垂直轴上的角位置 (0/1) */
    private class Segment(val pos: BlockPos, val axis: Int, val sa: Int, val sb: Int)

    private fun oreKind(state: BlockState): String? {
        for ((tag, kind) in ORE_KIND_TAGS) if (state.isIn(tag)) return kind
        val path = Registries.BLOCK.getId(state.block).path
        if (path == "ancient_debris") return "debris"
        if (path.contains("ore")) return "id:$path" // modded: 同名矿石合并, 颜色用默认白
        return null
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
        WorldRenderEvents.AFTER_TRANSLUCENT.register { context -> render(context) }
    }

    /** 每秒扫描一次: 按种类收集矿石 → 计算并缓存各组可见轮廓段 */
    private fun tick(client: MinecraftClient) {
        tickCounter++
        if (tickCounter % RESCAN_INTERVAL_TICKS != 0) return

        val player = client.player
        val world = client.world
        if (player == null || world == null || !ClientPerkNetworking.hasPerk(PERK_ID)) {
            outlineSegments = emptyMap()
            return
        }

        val center = player.blockPos
        val groups = mutableMapOf<String, MutableSet<BlockPos>>()
        for (dx in -RADIUS..RADIUS) {
            for (dy in -RADIUS..RADIUS) {
                for (dz in -RADIUS..RADIUS) {
                    val pos = center.add(dx, dy, dz)
                    val kind = oreKind(world.getBlockState(pos)) ?: continue
                    groups.getOrPut(kind) { HashSet() }.add(pos.toImmutable())
                }
            }
        }

        outlineSegments = groups.mapValues { (_, positions) -> computeSegments(positions) }
    }

    /** 对一组同种矿石计算外沿轮廓段 (相接方块之间的共享棱在这里被剔除) */
    private fun computeSegments(positions: Set<BlockPos>): List<Segment> {
        val out = mutableListOf<Segment>()
        val probe = BlockPos.Mutable()
        for (p in positions) {
            for (axis in 0..2) {
                val u = U_AXIS[axis]
                val v = V_AXIS[axis]
                for (sa in 0..1) {
                    for (sb in 0..1) {
                        if (isEdgeVisible(positions, probe, p, u, v, sa, sb)) {
                            out.add(Segment(p, axis, sa, sb))
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * 体素轮廓规则: 一条棱周围环绕 4 个象限方块 (垂直于棱的两条轴上取 {-1,0} x {-1,0} 平移)。
     * 同种方块数 1 或 3 → 画 (边界/凹角); 2 → 对角相接画、并排相接不画; 0 或 4 → 不画。
     */
    private fun isEdgeVisible(
        positions: Set<BlockPos>, probe: BlockPos.Mutable, p: BlockPos,
        u: Int, v: Int, sa: Int, sb: Int,
    ): Boolean {
        fun occupied(uo: Int, vo: Int): Boolean {
            val x = p.x + (if (u == 0) uo else 0) + (if (v == 0) vo else 0)
            val y = p.y + (if (u == 1) uo else 0) + (if (v == 1) vo else 0)
            val z = p.z + (if (u == 2) uo else 0) + (if (v == 2) vo else 0)
            probe.set(x, y, z)
            return positions.contains(probe)
        }
        val a0b0 = occupied(sa - 1, sb - 1)
        val a0b1 = occupied(sa - 1, sb)
        val a1b0 = occupied(sa, sb - 1)
        val a1b1 = occupied(sa, sb)
        val n = (if (a0b0) 1 else 0) + (if (a0b1) 1 else 0) + (if (a1b0) 1 else 0) + (if (a1b1) 1 else 0)
        return when (n) {
            1, 3 -> true
            2 -> (a0b0 && a1b1) || (a0b1 && a1b0) // 对角相接 → 画 (两个独立轮廓都经过这条棱)
            else -> false
        }
    }

    private fun render(context: WorldRenderContext) {
        if (outlineSegments.isEmpty()) return
        val immediate = context.consumers() as? VertexConsumerProvider.Immediate ?: return

        val outline = OutlineVertexConsumerProvider(immediate)
        val layer = RenderLayer.getOutline(OUTLINE_TEXTURE)
        val camera = context.camera()
        val camX = camera.pos.x
        val camY = camera.pos.y
        val camZ = camera.pos.z
        val radius2 = (RADIUS * RADIUS).toDouble()

        for ((kind, segments) in outlineSegments) {
            if (segments.isEmpty()) continue
            val rgb = ORE_COLORS[kind] ?: DEFAULT_COLOR
            // setColor 影响其后写入的顶点 → 每个种类一批颜色
            outline.setColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, 255)
            val buffer = outline.getBuffer(layer)
            for (seg in segments) {
                val p = seg.pos
                val ox = p.x + 0.5 - camX
                val oy = p.y + 0.5 - camY
                val oz = p.z + 0.5 - camZ
                if (ox * ox + oy * oy + oz * oz > radius2) continue
                emitSegment(buffer, seg, ox.toFloat(), oy.toFloat(), oz.toFloat())
            }
        }

        outline.draw()
    }

    /** 渲染一条棱段: 两个互相垂直的细面片组成十字 (从任意角度至少一面可见) */
    private fun emitSegment(
        buffer: VertexConsumer, seg: Segment,
        cx: Float, cy: Float, cz: Float,
    ) {
        val axis = seg.axis
        val u = U_AXIS[axis]
        val v = V_AXIS[axis]

        // 棱段两端的局部坐标 (0..1): axis 轴从 0 到 1, 垂直两轴取角 (sa, sb)
        val start = FloatArray(3)
        start[axis] = 0f
        start[u] = seg.sa.toFloat()
        start[v] = seg.sb.toFloat()
        val end = floatArrayOf(start[0], start[1], start[2])
        end[axis] = 1f

        val x0 = cx + (start[0] - 0.5f)
        val y0 = cy + (start[1] - 0.5f)
        val z0 = cz + (start[2] - 0.5f)
        val x1 = cx + (end[0] - 0.5f)
        val y1 = cy + (end[1] - 0.5f)
        val z1 = cz + (end[2] - 0.5f)

        emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, u)
        emitQuad(buffer, cx, cy, cz, x0, y0, z0, x1, y1, z1, v)
    }

    /** 一条棱沿线轴向 (由 [thicknessAxis] 指定) 扩展 ±EDGE_HALF 形成的四边形面片 */
    private fun emitQuad(
        buffer: VertexConsumer, cx: Float, cy: Float, cz: Float,
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        thicknessAxis: Int,
    ) {
        val ox = if (thicknessAxis == 0) EDGE_HALF else 0f
        val oy = if (thicknessAxis == 1) EDGE_HALF else 0f
        val oz = if (thicknessAxis == 2) EDGE_HALF else 0f
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
