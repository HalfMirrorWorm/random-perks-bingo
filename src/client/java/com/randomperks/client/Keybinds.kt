package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.resources.Identifier

object Keybinds {

    /** GLFW 键码。26.3 的客户端编译路径不再暴露 lwjgl，直接用键值。 */
    private const val KEY_X = 88
    private const val KEY_V = 86
    private const val KEY_K = 75

    /**
     * 分类翻译键是 key.category.<命名空间>.<路径>，即 key.category.random-perks-bingo.main。
     */
    private val CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("random-perks-bingo", "main"),
    )

    lateinit var openChoices: KeyMapping
        private set

    lateinit var openPerkList: KeyMapping
        private set

    lateinit var openCatalog: KeyMapping
        private set

    fun register() {
        openChoices = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.random-perks-bingo.open_choices",
                InputConstants.Type.KEYSYM,
                KEY_X,
                CATEGORY,
            )
        )

        openPerkList = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.random-perks-bingo.open_perk_list",
                InputConstants.Type.KEYSYM,
                KEY_V,
                CATEGORY,
            )
        )

        openCatalog = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.random-perks-bingo.open_catalog",
                InputConstants.Type.KEYSYM,
                KEY_K,
                CATEGORY,
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openChoices.consumeClick()) {
                if (client.gui.screen() == null && ClientPerkNetworking.pendingChoices > 0) {
                    ClientPerkNetworking.requestOffer()
                }
            }
            while (openPerkList.consumeClick()) {
                if (client.gui.screen() == null) {
                    ClientPerkNetworking.requestPerkList()
                }
            }
            while (openCatalog.consumeClick()) {
                if (client.gui.screen() == null) {
                    ClientPerkNetworking.requestCatalog()
                }
            }
        }
    }
}
