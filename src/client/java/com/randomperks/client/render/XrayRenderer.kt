package com.randomperks.client.render

import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OutlineVertexConsumerProvider
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos

/**
 * "透视之眼"词条的客户端渲染: 给玩家 20 格内的矿石画**发光剪影**
 * —— 用的是原版"实体发光效果"的同一套机制 (OutlineVertexConsumerProvider):
 * 方块模型被渲染成纯色剪影 + 描边层, 观感与生物发光完全一致, 且穿过方块可见。
 * 仅在持有该词条的客户端上渲染 —— 天然"仅自己可见"。
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
        val immediate = context.consumers() as? VertexConsumerProvider.Immediate ?: return
        val matrices = context.matrixStack() ?: return
        val camera = context.camera()
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return

        // 原版"生物发光"同款机制: 把方块模型渲染进 OutlineVertexConsumerProvider,
        // 得到纯色剪影 + 描边层 (穿墙可见)
        val outline = OutlineVertexConsumerProvider(immediate)
        outline.setColor(255, 255, 255, 255)

        matrices.push()
        matrices.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z)
        for (pos in orePositions) {
            val state = world.getBlockState(pos)
            matrices.push()
            matrices.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            client.blockRenderManager.renderBlockAsEntity(
                state, matrices, outline,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
            )
            matrices.pop()
        }
        matrices.pop()

        outline.draw()
    }
}
