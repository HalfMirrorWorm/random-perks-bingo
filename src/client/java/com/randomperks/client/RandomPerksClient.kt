package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.client.screen.PerkCatalogScreen
import com.randomperks.client.screen.PerkChoiceScreen
import com.randomperks.client.screen.PerkListScreen
import com.randomperks.network.PerkOptionData
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkRarity
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import org.slf4j.LoggerFactory

object RandomPerksClient : ClientModInitializer {

    val logger = LoggerFactory.getLogger("random-perks-bingo/client")!!

    override fun onInitializeClient() {
        logger.info("[RandomPerks] client initializing")

        ClientPerkNetworking.registerClient()
        Keybinds.register()
        AbilityHandler.register()
        com.randomperks.client.render.XrayRenderer.register()

        // UI 渲染自测 (开发用): 环境变量 randomperks_UITEST=true 时
        // 启动后自动打开两个界面并各渲染约 2 秒, 用于验证渲染代码不崩溃
        if (System.getenv("randomperks_UITEST") == "true") {
            logger.info("[RandomPerks] UI self-test ENABLED")
            var tick = 0

            val choiceOptions = listOf(
                PerkOptionData("swift_speed", PerkRarity.BLUE.ordinal, PerkArchetype.SWIFT.ordinal, ItemStack(Items.FEATHER)),
                PerkOptionData("hunter_soul_harvest", PerkRarity.GOLD.ordinal, PerkArchetype.HUNTER.ordinal, ItemStack(Items.NETHER_STAR)),
                PerkOptionData("delver_haste", PerkRarity.PURPLE.ordinal, PerkArchetype.DELVER.ordinal, ItemStack(Items.DIAMOND_PICKAXE)),
            )
            val listPerks = listOf(
                PerkOptionData("swift_speed", PerkRarity.BLUE.ordinal, PerkArchetype.SWIFT.ordinal, ItemStack(Items.FEATHER)),
                PerkOptionData("swift_tailwind", PerkRarity.PURPLE.ordinal, PerkArchetype.SWIFT.ordinal, ItemStack(Items.PHANTOM_MEMBRANE)),
                PerkOptionData("swift_wind_stride", PerkRarity.PURPLE.ordinal, PerkArchetype.SWIFT.ordinal, ItemStack(Items.SUGAR)),
                PerkOptionData("delver_haste", PerkRarity.PURPLE.ordinal, PerkArchetype.DELVER.ordinal, ItemStack(Items.DIAMOND_PICKAXE)),
                PerkOptionData("delver_night_vision", PerkRarity.BLUE.ordinal, PerkArchetype.DELVER.ordinal, ItemStack(Items.GOLDEN_HELMET)),
                PerkOptionData("delver_vein_scent", PerkRarity.PURPLE.ordinal, PerkArchetype.DELVER.ordinal, ItemStack(Items.RAW_IRON)),
                PerkOptionData("harvest_bounty", PerkRarity.PURPLE.ordinal, PerkArchetype.HARVEST.ordinal, ItemStack(Items.CARROT)),
                PerkOptionData("harvest_timber", PerkRarity.PURPLE.ordinal, PerkArchetype.HARVEST.ordinal, ItemStack(Items.OAK_LOG)),
                PerkOptionData("harvest_gardener", PerkRarity.BLUE.ordinal, PerkArchetype.HARVEST.ordinal, ItemStack(Items.WHEAT)),
                PerkOptionData("hunter_soul_harvest", PerkRarity.GOLD.ordinal, PerkArchetype.HUNTER.ordinal, ItemStack(Items.NETHER_STAR)),
                PerkOptionData("hunter_vampire", PerkRarity.BLUE.ordinal, PerkArchetype.HUNTER.ordinal, ItemStack(Items.RED_DYE)),
                PerkOptionData("hunter_strength", PerkRarity.BLUE.ordinal, PerkArchetype.HUNTER.ordinal, ItemStack(Items.IRON_SWORD)),
                PerkOptionData("seeker_scout", PerkRarity.BLUE.ordinal, PerkArchetype.SEEKER.ordinal, ItemStack(Items.SPYGLASS)),
                PerkOptionData("seeker_ender_supply", PerkRarity.PURPLE.ordinal, PerkArchetype.SEEKER.ordinal, ItemStack(Items.ENDER_PEARL)),
                PerkOptionData("artisan_supply", PerkRarity.BLUE.ordinal, PerkArchetype.ARTISAN.ordinal, ItemStack(Items.BREAD)),
                PerkOptionData("artisan_enchanter", PerkRarity.PURPLE.ordinal, PerkArchetype.ARTISAN.ordinal, ItemStack(Items.ENCHANTED_BOOK)),
            )

            ClientTickEvents.END_CLIENT_TICK.register { client ->
                tick++
                when (tick) {
                    100 -> {
                        client.setScreen(PerkChoiceScreen(choiceOptions))
                        logger.info("[RandomPerks] UI self-test: perk choice screen opened")
                    }
                    160 -> {
                        // 注意: 1.21.7 的 saveScreenshot 只有 3 个参数 (自动生成文件名)
                        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                            client.runDirectory,
                            client.framebuffer,
                        ) { text -> logger.info("[RandomPerks] screenshot(choice): {}", text.string) }
                    }
                    220 -> {
                        client.setScreen(
                            PerkListScreen(
                                3,
                                listOf(
                                    com.randomperks.network.PlayerPerkList(
                                        java.util.UUID.randomUUID(),
                                        "Test Player",
                                        listPerks,
                                    ),
                                ),
                            ),
                        )
                        logger.info("[RandomPerks] UI self-test: perk list screen opened")
                    }
                    280 -> {
                        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                            client.runDirectory,
                            client.framebuffer,
                        ) { text -> logger.info("[RandomPerks] screenshot(list): {}", text.string) }
                    }
                    340 -> {
                        val owned = listPerks.take(6).map { it.id }.toSet()
                        client.setScreen(PerkCatalogScreen(owned, listPerks))
                        logger.info("[RandomPerks] UI self-test: perk catalog screen opened")
                    }
                    400 -> {
                        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                            client.runDirectory,
                            client.framebuffer,
                        ) { text -> logger.info("[RandomPerks] screenshot(catalog): {}", text.string) }
                    }
                    460 -> {
                        client.setScreen(null)
                        logger.info("[RandomPerks] UI self-test: PASSED (all screens rendered without crash)")
                    }
                }
            }
        }
    }
}
