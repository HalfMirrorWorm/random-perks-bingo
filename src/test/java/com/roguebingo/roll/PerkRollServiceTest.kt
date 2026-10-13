package com.roguebingo.roll

import com.roguebingo.perk.PerkArchetype
import com.roguebingo.perk.PerkDefinition
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.perk.PerkRarity
import com.roguebingo.state.PlayerPerkState
import net.minecraft.item.ItemStack
import java.util.UUID
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 抽取逻辑的纯逻辑单元测试 (不触碰 MC 运行时)。
 */
class PerkRollServiceTest {

    /** 测试用哑元词条 —— icon() 不应被这些测试调用 */
    private abstract class DummyPerk(
        override val id: String,
        override val rarity: PerkRarity,
        override val archetype: PerkArchetype,
    ) : PerkDefinition {
        override fun icon(): ItemStack = throw UnsupportedOperationException("not used in tests")
    }

    private object BlueA : DummyPerk("blue_a", PerkRarity.BLUE, PerkArchetype.SWIFT)
    private object BlueB : DummyPerk("blue_b", PerkRarity.BLUE, PerkArchetype.DELVER)
    private object BlueC : DummyPerk("blue_c", PerkRarity.BLUE, PerkArchetype.HARVEST)
    private object BlueD : DummyPerk("blue_d", PerkRarity.BLUE, PerkArchetype.HUNTER)
    private object PurpleA : DummyPerk("purple_a", PerkRarity.PURPLE, PerkArchetype.SEEKER)
    private object GoldA : DummyPerk("gold_a", PerkRarity.GOLD, PerkArchetype.ARTISAN)

    private val allPerks = listOf(BlueA, BlueB, BlueC, BlueD, PurpleA, GoldA)

    @BeforeTest
    fun setup() {
        PerkRegistry.clear()
        allPerks.forEach { PerkRegistry.register(it) }
    }

    private fun newState() = PlayerPerkState(UUID.randomUUID())

    @Test
    fun `returns exactly 3 distinct options when pool is large enough`() {
        val options = PerkRollService.rollOptions(newState(), 3)
        assertEquals(3, options.size)
        assertEquals(3, options.map { it.id }.toSet().size, "options must be distinct")
    }

    @Test
    fun `excludes owned perks`() {
        val state = newState()
        state.ownedPerks.add(BlueA)
        state.ownedPerks.add(PurpleA)

        repeat(50) {
            val options = PerkRollService.rollOptions(state, 3, Random(it))
            assertTrue(options.none { it.id == BlueA.id || it.id == PurpleA.id })
        }
    }

    @Test
    fun `degrades gracefully when pool is smaller than requested`() {
        val state = newState()
        // 只留 2 个可用
        allPerks.drop(2).forEach { state.ownedPerks.add(it) }

        val options = PerkRollService.rollOptions(state, 3, Random(1))
        assertEquals(2, options.size)
    }

    @Test
    fun `returns empty when pool is exhausted`() {
        val state = newState()
        allPerks.forEach { state.ownedPerks.add(it) }

        val options = PerkRollService.rollOptions(state, 3)
        assertTrue(options.isEmpty())
    }

    @Test
    fun `rarity distribution roughly follows configured weights`() {
        val random = Random(42)
        val samples = 60_000
        val counts = mutableMapOf<PerkRarity, Int>()
        repeat(samples) {
            val r = PerkRollService.rollRarity(random)
            counts[r] = (counts[r] ?: 0) + 1
        }

        val total = PerkRarity.entries.sumOf { it.weight }.toDouble()
        for (rarity in PerkRarity.entries) {
            val expected = rarity.weight / total
            val actual = (counts[rarity] ?: 0) / samples.toDouble()
            assertTrue(
                kotlin.math.abs(expected - actual) < 0.02,
                "rarity $rarity expected≈$expected actual=$actual",
            )
        }
    }

    @Test
    fun `owned perk state resets fully`() {
        val state = newState()
        state.ownedPerks.add(BlueA)
        state.pendingOffers.add(listOf("blue_a", "blue_b", "blue_c"))
        state.cooldowns["x"] = 1L

        state.reset()

        assertEquals(0, state.pendingChoices)
        assertTrue(state.ownedPerks.isEmpty())
        assertTrue(state.cooldowns.isEmpty())
        assertEquals(null, state.currentOffer())
    }

    @Test
    fun `consuming an offer keeps the remaining queue in order`() {
        val state = newState()
        state.pendingOffers.add(listOf("a"))
        state.pendingOffers.add(listOf("b"))

        assertEquals(2, state.pendingChoices)
        assertEquals(listOf("a"), state.currentOffer())

        state.consumeOffer()
        assertEquals(1, state.pendingChoices)
        assertEquals(listOf("b"), state.currentOffer())
    }

    @Test
    fun `each player rolls independently`() {
        val a = newState()
        val b = newState()
        a.ownedPerks.add(BlueA)

        // A 的候选不应包含自己已有的; B 的候选不受 A 影响
        repeat(30) {
            val optionsA = PerkRollService.rollOptions(a, 3, Random(it))
            assertTrue(optionsA.none { it.id == BlueA.id })
        }
        val optionsB = PerkRollService.rollOptions(b, 3, Random(1))
        assertEquals(3, optionsB.size)
    }
}
