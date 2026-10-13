package com.roguebingo.client

import com.roguebingo.client.net.ClientPerkNetworking
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

object Keybinds {

    private const val CATEGORY = "category.rogue-bingo"

    lateinit var openChoices: KeyBinding
        private set

    lateinit var openPerkList: KeyBinding
        private set

    lateinit var openCatalog: KeyBinding
        private set

    fun register() {
        openChoices = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.rogue-bingo.open_choices",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY,
            )
        )

        openPerkList = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.rogue-bingo.open_perk_list",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY,
            )
        )

        openCatalog = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.rogue-bingo.open_catalog",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
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
