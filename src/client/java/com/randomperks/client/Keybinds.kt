package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

object Keybinds {

    /** 1.21.9 起按键分类是 Identifier, 语言键是 key.category.<命名空间>.<路径>。 */
    private val CATEGORY = KeyBinding.Category.create(Identifier.of("random-perks-bingo", "main"))

    lateinit var openChoices: KeyBinding
        private set

    lateinit var openPerkList: KeyBinding
        private set

    lateinit var openCatalog: KeyBinding
        private set

    fun register() {
        openChoices = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.random-perks-bingo.open_choices",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY,
            )
        )

        openPerkList = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.random-perks-bingo.open_perk_list",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY,
            )
        )

        openCatalog = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.random-perks-bingo.open_catalog",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY,
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openChoices.wasPressed()) {
                if (client.currentScreen == null && ClientPerkNetworking.pendingChoices > 0) {
                    ClientPerkNetworking.requestOffer()
                }
            }
            while (openPerkList.wasPressed()) {
                if (client.currentScreen == null) {
                    ClientPerkNetworking.requestPerkList()
                }
            }
            while (openCatalog.wasPressed()) {
                if (client.currentScreen == null) {
                    ClientPerkNetworking.requestCatalog()
                }
            }
        }
    }
}
