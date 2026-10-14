package com.randomperks.client.render

import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexRendering
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * "透视之眼"词条的客户端渲染: 把玩家 20 格内的矿石画成发光盒
 * (使用 debug 渲染层, 无深度测试 → 可穿透方块看到, 即"矿透")。
 */
object XrayRenderer {

    private const val PERK_ID = "delver_xray_vision"
    private const val RADIUS = 20
    private const val RESCAN_INTERVAL_TICKS = 20

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
            VertexRendering.drawBox(
                matrices, buffer,
                Box(pos).contract(0.005),
                0.2f, 1.0f, 1.0f, 0.35f,
            )
        }

        matrices.pop()
    }
}
