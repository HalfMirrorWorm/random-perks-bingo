package com.randomperks.client.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import java.util.OptionalDouble

/**
 * "透视之眼"词条的客户端渲染:
 * 给玩家 [RADIUS] 格内的矿石画**按种类着色的发光轮廓线** ——
 *  - 每种矿石一个颜色 (煤灰 / 铁米 / 铜橙 / 金黄 / 红石红 / 青金蓝 / 钻青 / 绿宝绿...)
 *  - 相接的同种矿石合并轮廓: 只画外沿棱, 剔除相邻方块之间的共享棱 (体素轮廓规则)
 *
 * 线渲染方式与原版调试线一致 (RenderPipelines.LINES 同源管线), 得到**屏幕恒宽**的清晰细线
 * (远近一样清楚); 自定义管线关掉深度测试 → 无视遮挡、穿墙可见。
 */
object XrayRenderer {

    private const val PERK_ID = "delver_xray_vision"
    private const val RADIUS = 20
    private const val RESCAN_INTERVAL_TICKS = 20

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

    /**
     * 穿墙线框: 原版 LINES 管线 (rendertype_lines) 的同源副本,
     * 只把深度测试改为 NO_DEPTH_TEST (无视遮挡) —— 1.21.6+ 渲染系统里管线的深度设置
     * 只能通过自定义 RenderPipeline 达成 (旧的全局深度开关已被移除)。
     */
    private val XRAY_LINE_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
        .withLocation("pipeline/randomperks_xray_ore_lines")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .build()

    /** 与 RenderLayer.getLines() 相同的层参数, 使用上面的穿墙管线 */
    private val XRAY_LINES: RenderLayer = RenderLayer.of(
        "randomperks_xray_ore_lines", 1536, XRAY_LINE_PIPELINE,
        RenderLayer.MultiPhaseParameters.builder()
            .lineWidth(RenderPhase.LineWidth(OptionalDouble.empty()))
            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
            .target(RenderPhase.ITEM_ENTITY_TARGET)
            .build(false),
    )

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
        // 自定义管线注册进全局表 (与原版管线同等待遇: 资源重载后仍可被正确重建)
        RenderPipelines.register(XRAY_LINE_PIPELINE)
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
        val buffer = immediate.getBuffer(XRAY_LINES)

        val camera = context.camera()
        val camX = camera.pos.x
        val camY = camera.pos.y
        val camZ = camera.pos.z
        val radius2 = (RADIUS * RADIUS).toDouble()

        for ((kind, segments) in outlineSegments) {
            if (segments.isEmpty()) continue
            val rgb = ORE_COLORS[kind] ?: DEFAULT_COLOR
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            for (seg in segments) {
                val p = seg.pos
                val ox = p.x + 0.5 - camX
                val oy = p.y + 0.5 - camY
                val oz = p.z + 0.5 - camZ
                if (ox * ox + oy * oy + oz * oz > radius2) continue
                emitSegment(buffer, seg, ox.toFloat(), oy.toFloat(), oz.toFloat(), r, g, b)
            }
        }
        // 无需手动 draw: 本层内容由世界渲染收尾统一刷出
    }

    /** 画一条棱 (两个顶点); normal 传"线方向"(与原版 VertexRendering.drawOutline 一致) */
    private fun emitSegment(
        buffer: VertexConsumer, seg: Segment,
        cx: Float, cy: Float, cz: Float, r: Int, g: Int, b: Int,
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

        // 线方向向量 (轴对齐线段, 本身就是单位向量) —— 照抄原版轮廓线的 normal 语义
        val nx = end[0] - start[0]
        val ny = end[1] - start[1]
        val nz = end[2] - start[2]

        buffer.vertex(cx + (start[0] - 0.5f), cy + (start[1] - 0.5f), cz + (start[2] - 0.5f))
            .color(r, g, b, 255)
            .normal(nx, ny, nz)
        buffer.vertex(cx + (end[0] - 0.5f), cy + (end[1] - 0.5f), cz + (end[2] - 0.5f))
            .color(r, g, b, 255)
            .normal(nx, ny, nz)
    }
}
