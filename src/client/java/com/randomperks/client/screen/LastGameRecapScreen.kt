package com.randomperks.client.screen

import com.randomperks.perk.PerkRegistry
import com.randomperks.recap.RecapCapture
import com.randomperks.recap.RecapData
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import java.util.Locale

/**
 * 「上局回顾」界面 —— 由大厅菜单里的按钮触发, 数据由服务端整包下发。
 *
 * 三个标签页:
 * 1. **词条选取**: 按玩家分组, 按时间列出每个人选走的词条;
 * 2. **首次完成排名**: 按"第一次拿下格子"的时间排序;
 * 3. **完成时间线**: 全部拿下格子的记录, 按时间顺序。
 */
class LastGameRecapScreen(
    private val data: RecapData,
) : Screen(Component.translatable("random-perks-bingo.recap.title")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val ROW_HEIGHT = 11
        const val CONTENT_TOP = 58
        const val CONTENT_BOTTOM_MARGIN = 26
        const val TAB_WIDTH = 108
        const val TAB_GAP = 4
        val TAB_KEYS = listOf("picks", "rank", "timeline")
    }

    private data class Row(val text: String, val color: Int, val bold: Boolean = false)

    private var tab = 0
    private var scroll = 0
    private var rows: List<Row> = emptyList()

    private val teamsById by lazy { data.teams.associateBy { it.id } }

    override fun init() {
        rows = buildRows()
        scroll = 0

        val total = TAB_KEYS.size * TAB_WIDTH + (TAB_KEYS.size - 1) * TAB_GAP
        var x = (width - total) / 2
        TAB_KEYS.forEachIndexed { index, key ->
            val button = Button.builder(Component.translatable("random-perks-bingo.recap.tab.$key")) {
                setTab(index)
            }
                .bounds(x, 32, TAB_WIDTH, 20)
                .build()
            button.active = index != tab
            addRenderableWidget(button)
            x += TAB_WIDTH + TAB_GAP
        }
    }

    private fun setTab(newTab: Int) {
        if (newTab == tab) return
        tab = newTab
        if (minecraft != null) {
            clearWidgets()
            init()
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)
        super.extractRenderState(context, mouseX, mouseY, delta)

        context.centeredText(font, title, width / 2, 14, 0xFFFFFFFF.toInt())

        if (data.isEmpty) {
            context.centeredText(
                font,
                Component.translatable("random-perks-bingo.recap.empty"),
                width / 2, height / 2, 0xFFAAAAAA.toInt(),
            )
            return
        }

        val bottom = height - CONTENT_BOTTOM_MARGIN
        var y = CONTENT_TOP
        for (index in scroll until rows.size) {
            if (y + ROW_HEIGHT > bottom) break
            val row = rows[index]
            if (row.text.isNotEmpty()) {
                val text = font.plainSubstrByWidth(row.text, width - 48)
                context.text(font, text, 24, y, row.color, row.bold)
            }
            y += ROW_HEIGHT
        }

        // 底部提示 + 滚动位置
        val hint = if (rows.size * ROW_HEIGHT > bottom - CONTENT_TOP) {
            Component.translatable("random-perks-bingo.recap.hint.scroll", scroll + 1, rows.size)
        } else {
            Component.translatable("random-perks-bingo.recap.hint")
        }
        context.centeredText(font, hint, width / 2, height - 18, 0xFF888888.toInt())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val visible = ((height - CONTENT_BOTTOM_MARGIN - CONTENT_TOP) / ROW_HEIGHT).coerceAtLeast(1)
        val maxScroll = (rows.size - visible).coerceAtLeast(0)
        if (maxScroll == 0) return false
        scroll = (scroll - verticalAmount.toInt() * 2).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen(): Boolean = false

    // ------------------------------------------------------------------
    // rows
    // ------------------------------------------------------------------

    private fun buildRows(): List<Row> = when (tab) {
        0 -> pickRows()
        1 -> rankRows()
        else -> timelineRows()
    }

    private fun pickRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val byPlayer = data.picks.groupBy { it.playerUuid }
        if (byPlayer.isEmpty()) {
            rows += Row(Component.translatable("random-perks-bingo.recap.empty.picks").string, 0xFFAAAAAA.toInt())
            return rows
        }

        val players = byPlayer.values.map { it.first() }
            .sortedBy { it.playerName.lowercase(Locale.ROOT) }

        for (player in players) {
            val team = teamsById[player.teamId]
            rows += Row(
                Component.translatable(
                    "random-perks-bingo.recap.player_header",
                    player.playerName,
                    team?.name ?: player.teamId,
                ).string,
                team?.color ?: 0xFFFFFFFF.toInt(),
                bold = true,
            )

            for (pick in byPlayer.getValue(player.playerUuid).sortedBy { it.seconds }) {
                val perkName = Component.translatable("random-perks-bingo.perk.${pick.perkId}.name").string
                val rarity = PerkRegistry.byId(pick.perkId)?.rarity
                    ?.let { Component.translatable("random-perks-bingo.rarity.${it.name.lowercase()}").string }
                    ?: "?"
                rows += Row("    ${formatTime(pick.seconds)}  $perkName ($rarity)", 0xFFDDDDDD.toInt())
            }
            rows += Row("", 0)
        }
        return rows
    }

    private fun rankRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val firstCaptures = LinkedHashMap<String, RecapCapture>()
        for (capture in data.captures.sortedBy { it.seconds }) {
            if (capture.playerUuid.isEmpty()) continue
            firstCaptures.putIfAbsent(capture.playerUuid, capture)
        }
        if (firstCaptures.isEmpty()) {
            rows += Row(Component.translatable("random-perks-bingo.recap.empty.captures").string, 0xFFAAAAAA.toInt())
            return rows
        }

        val totals = data.captures.groupingBy { it.playerUuid }.eachCount()
        var index = 1
        for ((uuid, capture) in firstCaptures) {
            val team = teamsById[capture.teamId]
            rows += Row(
                Component.translatable(
                    "random-perks-bingo.recap.rank_row",
                    index,
                    capture.playerName,
                    team?.name ?: capture.teamId,
                    formatTime(capture.seconds),
                    totals[uuid] ?: 0,
                ).string,
                team?.color ?: 0xFFFFFFFF.toInt(),
                bold = index <= 3,
            )
            index++
        }
        return rows
    }

    private fun timelineRows(): List<Row> {
        val rows = mutableListOf<Row>()
        if (data.captures.isEmpty()) {
            rows += Row(Component.translatable("random-perks-bingo.recap.empty.captures").string, 0xFFAAAAAA.toInt())
            return rows
        }
        for (capture in data.captures.sortedBy { it.seconds }) {
            val team = teamsById[capture.teamId]
            val player = capture.playerName.ifEmpty { "?" }
            rows += Row(
                Component.translatable(
                    "random-perks-bingo.recap.timeline_row",
                    formatTime(capture.seconds),
                    team?.name ?: capture.teamId,
                    player,
                    capture.objective,
                ).string,
                team?.color ?: 0xFFFFFFFF.toInt(),
            )
        }
        return rows
    }

    private fun formatTime(seconds: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
