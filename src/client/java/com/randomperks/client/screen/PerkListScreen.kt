package com.randomperks.client.screen

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.network.PerkOptionData
import com.randomperks.network.PlayerPerkList
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 词条列表 (按键 V 打开)。
 *
 * 顶部是 tab 栏 —— **每个在线玩家一个 tab**, 打开时默认选中自己;
 * 下面复用原来的两列卡片网格, 显示当前选中玩家的词条。
 */
class PerkListScreen(
    private val pendingCount: Int,
    private val players: List<PlayerPerkList>,
) : Screen(Text.translatable("random-perks-bingo.screen.my_perks")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLUMNS = 2
        const val COL_GAP = 8
        const val CARD_HEIGHT = 26
        const val ROW_GAP = 4
        const val LIST_BOTTOM_MARGIN = 26
        const val MAX_WIDTH = 460

        const val TAB_TOP = 44
        const val TAB_HEIGHT = 14
        const val TAB_GAP = 4
        const val TAB_PAD = 6
        const val TAB_LINE_GAP = 3
    }

    private data class TabBox(val index: Int, val x: Int, val y: Int, val width: Int)
    private data class TeamTab(val groupIndex: Int, val x: Int, val y: Int, val width: Int)
    private data class TeamGroup(
        val id: String,
        val name: Text,
        val color: Int,
        val players: List<Int>,
    )

    private var selected = -1
    private var selectedTeam = 0
    private var groups: List<TeamGroup> = emptyList()
    private var scrollOffset = 0
    private var maxScroll = 0
    private var hovered = -1

    private val sortState = PerkSortState()
    private var sortBoxes: List<Pair<PerkSortMode, IntArray>> = emptyList()

    /** 本帧每张卡片的点击区域 (含当前排序/滚动后的位置) */
    private val cardBoxes = mutableListOf<Pair<PerkOptionData, IntArray>>()

    private var listLeft = 0
    private var listWidth = 0
    private var cardWidth = 0
    private var listTop = TAB_TOP

    private val tabs = mutableListOf<TabBox>()
    private val teamTabs = mutableListOf<TeamTab>()

    private fun selfUuid() = MinecraftClient.getInstance().player?.uuid

    override fun init() {
        listWidth = (width - 60).coerceAtMost(MAX_WIDTH)
        listLeft = (width - listWidth) / 2
        cardWidth = (listWidth - COL_GAP) / COLUMNS
        scrollOffset = 0

        // 只有第一次打开 (或选择失效) 时才定位到自己; 窗口 resize 保留当前选择
        if (selected !in players.indices) {
            val self = selfUuid()
            selected = players.indexOfFirst { it.uuid == self }.coerceAtLeast(0)
        }

        buildGroups()
        val groupOfSelected = groups.indexOfFirst { selected in it.players }
        if (selectedTeam !in groups.indices ||
            (groupOfSelected >= 0 && selectedTeam != groupOfSelected)
        ) {
            selectedTeam = groupOfSelected.coerceAtLeast(0)
        }

        buildTabs()
    }

    /** 按队伍把玩家分组; 无队伍单独一组 */
    private fun buildGroups() {
        val order = LinkedHashMap<String, MutableList<Int>>()
        for ((index, player) in players.withIndex()) {
            order.getOrPut(player.teamId) { mutableListOf() }.add(index)
        }

        groups = order.map { (id, members) ->
            val first = players[members.first()]
            val name = when {
                id.isEmpty() -> Text.translatable("random-perks-bingo.list.no_team")
                first.teamName.isNotEmpty() -> Text.literal(first.teamName)
                else -> Text.literal(id)
            }
            val color = if (id.isEmpty()) {
                0xFFAAAAAA.toInt()
            } else {
                0xFF000000.toInt() or (first.teamColor and 0xFFFFFF)
            }
            TeamGroup(id, name, color, members)
        }
    }

    /** 上行队伍父 tab; 下行只显示当前队伍成员的名字 tab */
    private fun buildTabs() {
        teamTabs.clear()
        tabs.clear()
        if (players.isEmpty() || groups.isEmpty()) {
            listTop = TAB_TOP
            return
        }
        if (selectedTeam !in groups.indices) selectedTeam = 0

        var x = listLeft
        var y = TAB_TOP
        for ((groupIndex, group) in groups.withIndex()) {
            val tabWidth = textRenderer.getWidth(group.name) + TAB_PAD * 2
            if (x > listLeft && x + tabWidth > listLeft + listWidth) {
                x = listLeft
                y += TAB_HEIGHT + TAB_LINE_GAP
            }
            teamTabs.add(TeamTab(groupIndex, x, y, tabWidth))
            x += tabWidth + TAB_GAP
        }

        var childY = y + TAB_HEIGHT + TAB_LINE_GAP
        var childBottom = childY
        val group = groups[selectedTeam]
        x = listLeft
        for (playerIndex in group.players) {
            val player = players[playerIndex]
            val tabWidth = textRenderer.getWidth(player.name) + TAB_PAD * 2
            if (x > listLeft && x + tabWidth > listLeft + listWidth) {
                x = listLeft
                childY += TAB_HEIGHT + TAB_LINE_GAP
            }
            tabs.add(TabBox(playerIndex, x, childY, tabWidth))
            childBottom = childY + TAB_HEIGHT
            x += tabWidth + TAB_GAP
        }
        listTop = childBottom + 8
    }

    private fun listBottom() = height - LIST_BOTTOM_MARGIN

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 同 PerkChoiceScreen: 不调 renderBackground, 避免 1.21.6+ 的二次 blur 崩溃
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        val current = players.getOrNull(selected)
        val self = selfUuid()

        // 右上角的排序切换 (默认 / 名称↑↓ / 等级↑↓)
        sortBoxes = drawPerkSortButtons(context, textRenderer, width - 8, 6, sortState, mouseX, mouseY)

        // 标题: 看自己 → "我的词条"; 看别人 → "XXX 的词条"
        val viewingOther = current != null && current.uuid != self
        val title = if (viewingOther) {
            Text.translatable("random-perks-bingo.screen.player_perks", current!!.name)
        } else {
            Text.translatable("random-perks-bingo.screen.my_perks")
        }
        context.drawCenteredTextWithShadow(
            textRenderer,
            title,
            width / 2, 16,
            if (viewingOther) TeamColorUtil.forPlayer(current!!.uuid) else ScreenStyle.TEXT_NAME,
        )

        // 待选数只对自己有意义
        if (current != null && current.uuid == self) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("random-perks-bingo.list.pending", pendingCount),
                width / 2, 30,
                if (pendingCount > 0) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_DIM,
            )
        }

        // 队伍父 tab (名称始终使用队伍颜色)
        for (tab in teamTabs) {
            val group = groups[tab.groupIndex]
            val active = tab.groupIndex == selectedTeam
            val hover = mouseX in tab.x..(tab.x + tab.width) && mouseY in tab.y..(tab.y + TAB_HEIGHT)
            val background = when {
                active -> ScreenStyle.CARD_BG_HOVER
                hover -> ScreenStyle.CARD_BG
                else -> 0x40000000
            }
            context.fill(tab.x, tab.y, tab.x + tab.width, tab.y + TAB_HEIGHT, background)
            if (active) {
                context.fill(
                    tab.x, tab.y + TAB_HEIGHT - 2,
                    tab.x + tab.width, tab.y + TAB_HEIGHT,
                    group.color,
                )
            }
            context.drawCenteredTextWithShadow(
                textRenderer,
                group.name,
                tab.x + tab.width / 2,
                tab.y + 3,
                group.color,
            )
        }

        // 玩家子 tab (名称同样使用队伍颜色)
        val teamColor = groups.getOrNull(selectedTeam)?.color ?: ScreenStyle.TEXT_NAME
        for (tab in tabs) {
            val active = tab.index == selected
            val hover = mouseX in tab.x..(tab.x + tab.width) && mouseY in tab.y..(tab.y + TAB_HEIGHT)
            val background = when {
                active -> ScreenStyle.CARD_BG_HOVER
                hover -> ScreenStyle.CARD_BG
                else -> 0x40000000
            }
            context.fill(tab.x, tab.y, tab.x + tab.width, tab.y + TAB_HEIGHT, background)
            if (active) {
                context.fill(
                    tab.x, tab.y + TAB_HEIGHT - 2,
                    tab.x + tab.width, tab.y + TAB_HEIGHT,
                    teamColor,
                )
            }
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(players[tab.index].name),
                tab.x + tab.width / 2,
                tab.y + 3,
                teamColor,
            )
        }

        val perks = sortState.sort(current?.perks.orEmpty())
        cardBoxes.clear()
        if (perks.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("random-perks-bingo.list.empty"),
                width / 2, listTop + 12, ScreenStyle.TEXT_DIM,
            )
            super.render(context, mouseX, mouseY, delta)
            return
        }

        // 计算滚动范围
        val bottom = listBottom()
        val listHeight = bottom - listTop
        val rows = (perks.size + COLUMNS - 1) / COLUMNS
        val rowHeight = CARD_HEIGHT + ROW_GAP
        val contentHeight = rows * rowHeight
        maxScroll = (contentHeight - listHeight + ROW_GAP).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        hovered = -1

        // 列表区域 (裁剪, 防止滚动时卡片画出界)
        context.enableScissor(listLeft - 4, listTop, listLeft + listWidth + 4, bottom)

        // 只有自己的 tab 显示"点击使用"状态 —— 别人的词条不能由你触发
        val pendingIds = if (current != null && current.uuid == self) {
            current.pendingPerkIds.toSet()
        } else {
            emptySet()
        }

        for ((index, perk) in perks.withIndex()) {
            val row = index / COLUMNS
            val col = index % COLUMNS
            val x = listLeft + col * (cardWidth + COL_GAP)
            val y = listTop + row * rowHeight - scrollOffset

            val isHovered = mouseX in x..(x + cardWidth) && mouseY in y..(y + CARD_HEIGHT)
            if (isHovered) hovered = index

            cardBoxes.add(perk to intArrayOf(x, y, cardWidth, CARD_HEIGHT))
            drawRow(context, perk, x, y, isHovered, pending = perk.id in pendingIds)
        }

        context.disableScissor()

        // 滚动指示条
        if (maxScroll > 0) {
            val barX = listLeft + listWidth + 8
            val barHeight = (listHeight * listHeight / contentHeight).coerceAtLeast(12)
            val barY = listTop + (listHeight - barHeight) * scrollOffset / maxScroll
            context.fill(barX, listTop, barX + 2, bottom, ScreenStyle.DIVIDER)
            context.fill(barX - 1, barY, barX + 3, barY + barHeight, 0x90FFFFFF.toInt())
        }

        // 悬停时底部显示描述
        if (hovered in perks.indices) {
            val perk = perks[hovered]
            val lines = textRenderer.wrapLines(Text.translatable("random-perks-bingo.perk.${perk.id}.desc"), width - 100)
            var textY = height - 14 - lines.size * 10
            context.fill(30, textY - 5, width - 30, height - 6, 0xC0000000.toInt())
            for (line in lines) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, textY, ScreenStyle.TEXT_DESC)
                textY += 10
            }
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawRow(
        context: DrawContext,
        perk: PerkOptionData,
        x: Int,
        y: Int,
        isHovered: Boolean,
        pending: Boolean,
    ) {
        val displayColor = ScreenStyle.rarityDisplayColor(perk.rarityOrdinal)
        val archetype = ScreenStyle.archetype(perk.archetypeOrdinal)

        // 卡片背景 + 左侧品质条
        context.fill(x, y, x + cardWidth, y + CARD_HEIGHT, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)
        context.fill(x, y, x + 3, y + CARD_HEIGHT, displayColor)
        if (isHovered) {
            context.fill(x + 3, y, x + cardWidth, y + 1, ScreenStyle.BORDER_HOVER)
            context.fill(x + 3, y + CARD_HEIGHT - 1, x + cardWidth, y + CARD_HEIGHT, ScreenStyle.BORDER_HOVER)
            context.fill(x + cardWidth - 1, y, x + cardWidth, y + CARD_HEIGHT, ScreenStyle.BORDER_HOVER)
        }

        // 一次性词条还没用: 整张卡闪烁绿边, 提示"点我使用"
        if (pending && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            val glow = ScreenStyle.TEXT_HIGHLIGHT
            context.fill(x, y, x + cardWidth, y + 1, glow)
            context.fill(x, y + CARD_HEIGHT - 1, x + cardWidth, y + CARD_HEIGHT, glow)
            context.fill(x, y, x + 1, y + CARD_HEIGHT, glow)
            context.fill(x + cardWidth - 1, y, x + cardWidth, y + CARD_HEIGHT, glow)
        }
        if (pending) {
            val hint = Text.translatable("random-perks-bingo.list.click_to_use")
            context.drawTextWithShadow(
                textRenderer, hint,
                x + cardWidth - 6 - textRenderer.getWidth(hint), y + 15,
                ScreenStyle.TEXT_HIGHLIGHT,
            )
        }

        // 图标 (16x16, 垂直居中)
        context.drawItem(perk.icon, x + 8, y + 5)

        // 名称 (品质色) + 流派 (灰色)
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.perk.${perk.id}.name"),
            x + 30, y + 5, displayColor,
        )
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.archetype.${archetype.id}"),
            x + 30, y + 15, ScreenStyle.TEXT_DIM,
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // 排序切换
        for ((mode, box) in sortBoxes) {
            if (mouseX in box[0].toDouble()..(box[0] + box[2]).toDouble() &&
                mouseY in box[1].toDouble()..(box[1] + box[3]).toDouble()
            ) {
                if (sortState.click(mode)) scrollOffset = 0
                return true
            }
        }

        // 点队伍父 tab 切换队伍 (默认选中该队的自己, 否则第一个队员)
        for (tab in teamTabs) {
            if (mouseX in tab.x.toDouble()..(tab.x + tab.width).toDouble() &&
                mouseY in tab.y.toDouble()..(tab.y + TAB_HEIGHT).toDouble()
            ) {
                if (selectedTeam != tab.groupIndex) {
                    selectedTeam = tab.groupIndex
                    val group = groups.getOrNull(selectedTeam)
                    val self = selfUuid()
                    selected = group?.players?.firstOrNull { players[it].uuid == self }
                        ?: group?.players?.firstOrNull()
                        ?: -1
                    scrollOffset = 0
                    buildTabs()
                }
                return true
            }
        }

        // 点玩家子 tab 切换玩家
        for (tab in tabs) {
            if (mouseX in tab.x.toDouble()..(tab.x + tab.width).toDouble() &&
                mouseY in tab.y.toDouble()..(tab.y + TAB_HEIGHT).toDouble()
            ) {
                if (tab.index != selected) {
                    selected = tab.index
                    scrollOffset = 0
                }
                return true
            }
        }

        // 点击自己的一次性词条 → 触发使用流程
        val current = players.getOrNull(selected)
        if (button == 0 && current != null && current.uuid == selfUuid() &&
            mouseX >= listLeft && mouseX <= listLeft + listWidth &&
            mouseY >= listTop && mouseY <= listBottom()
        ) {
            for ((perk, box) in cardBoxes) {
                if (mouseX in box[0].toDouble()..(box[0] + box[2]).toDouble() &&
                    mouseY in box[1].toDouble()..(box[1] + box[3]).toDouble()
                ) {
                    if (perk.id in current.pendingPerkIds) {
                        ClientPerkNetworking.activatePerk(perk.id)
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (maxScroll > 0) {
            scrollOffset = (scrollOffset - (verticalAmount * 16).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun shouldPause(): Boolean = false
}
