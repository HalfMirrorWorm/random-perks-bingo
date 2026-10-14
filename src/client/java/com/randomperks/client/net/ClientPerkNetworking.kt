package com.randomperks.client.net

import com.randomperks.client.screen.PerkCatalogScreen
import com.randomperks.client.screen.PerkChoiceScreen
import com.randomperks.client.screen.PerkListScreen
import com.randomperks.network.PerkCatalogRequestC2S
import com.randomperks.network.PerkCatalogS2C
import com.randomperks.network.PerkListRequestC2S
import com.randomperks.network.PerkListS2C
import com.randomperks.network.PerkNotifyS2C
import com.randomperks.network.PerkOfferS2C
import com.randomperks.network.PerkOptionData
import com.randomperks.network.PerkPickC2S
import com.randomperks.network.PerkRequestC2S
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.text.Text

/**
 * 客户端网络处理与状态。
 */
object ClientPerkNetworking {

    /** 当前待选机会数 (由服务端同步) */
    var pendingChoices: Int = 0
        private set

    /** 当前正在展示的三选一候选 */
    var currentOptions: List<PerkOptionData>? = null
        private set

    fun registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(PerkNotifyS2C.ID) { payload, context ->
            context.client().execute {
                pendingChoices = payload.pendingCount
                if (payload.pendingCount > 0) {
                    context.client().player?.sendMessage(
                        Text.translatable("random-perks-bingo.message.choices_available", payload.pendingCount),
                        false,
                    )
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PerkOfferS2C.ID) { payload, context ->
            context.client().execute {
                currentOptions = payload.options
                context.client().setScreen(PerkChoiceScreen(payload.options))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PerkListS2C.ID) { payload, context ->
            context.client().execute {
                context.client().setScreen(PerkListScreen(payload.pendingCount, payload.perks))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PerkCatalogS2C.ID) { payload, context ->
            context.client().execute {
                context.client().setScreen(PerkCatalogScreen(payload.ownedIds.toSet(), payload.allPerks))
            }
        }
    }

    /** 请求服务端生成并下发三选一 */
    fun requestOffer() {
        if (pendingChoices <= 0) return
        if (!ClientPlayNetworking.canSend(PerkRequestC2S.ID)) return
        ClientPlayNetworking.send(PerkRequestC2S())
    }

    /** 请求已获得词条列表 */
    fun requestPerkList() {
        if (!ClientPlayNetworking.canSend(PerkListRequestC2S.ID)) return
        ClientPlayNetworking.send(PerkListRequestC2S())
    }

    /** 请求词条百科 */
    fun requestCatalog() {
        if (!ClientPlayNetworking.canSend(PerkCatalogRequestC2S.ID)) return
        ClientPlayNetworking.send(PerkCatalogRequestC2S())
    }

    /** 提交选择 */
    fun pick(index: Int) {
        currentOptions = null
        if (!ClientPlayNetworking.canSend(PerkPickC2S.ID)) return
        ClientPlayNetworking.send(PerkPickC2S(index))
    }

    /** 界面被关闭 (未选择) */
    fun clearOptions() {
        currentOptions = null
    }

    fun reset() {
        pendingChoices = 0
        currentOptions = null
    }
}
