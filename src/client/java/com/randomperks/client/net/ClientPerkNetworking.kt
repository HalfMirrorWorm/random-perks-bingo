package com.randomperks.client.net

import com.randomperks.client.GlowMarkState
import com.randomperks.client.screen.PerkCatalogScreen
import com.randomperks.client.screen.PerkChoiceScreen
import com.randomperks.client.screen.PerkListScreen
import com.randomperks.client.screen.StealScreen
import com.randomperks.network.GlowMarkS2C
import com.randomperks.network.PerkAbilitySyncS2C
import com.randomperks.network.PerkCatalogRequestC2S
import com.randomperks.network.PerkCatalogS2C
import com.randomperks.network.PerkListRequestC2S
import com.randomperks.network.PerkListS2C
import com.randomperks.network.PerkNotifyS2C
import com.randomperks.network.PerkOfferS2C
import com.randomperks.network.PerkOptionData
import com.randomperks.network.PerkPickC2S
import com.randomperks.network.PerkRequestC2S
import com.randomperks.network.StealOpenS2C
import com.randomperks.network.StealPickC2S
import com.randomperks.network.StealRequestC2S
import com.randomperks.network.WaterBucketTotemS2C
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.text.Text

/**
 * 客户端网络处理与状态。
 */
object ClientPerkNetworking {

    /** 当前待选机会数 (由服务端同步) */
    var pendingChoices: Int = 0
        private set

    /** 当前拥有的词条 id 集合 (由服务端同步, 供客户端能力使用: 矿透渲染/偷窃按键等) */
    var ownedPerkIds: Set<String> = emptySet()
        private set

    fun hasPerk(id: String): Boolean = id in ownedPerkIds

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

        ClientPlayNetworking.registerGlobalReceiver(PerkAbilitySyncS2C.ID) { payload, context ->
            context.client().execute {
                ownedPerkIds = payload.perkIds.toSet()
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(StealOpenS2C.ID) { payload, context ->
            context.client().execute {
                context.client().setScreen(StealScreen(payload.targetName, payload.items))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(GlowMarkS2C.ID) { payload, context ->
            context.client().execute {
                GlowMarkState.mark(payload.entityIds, payload.durationMillis)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(WaterBucketTotemS2C.ID) { _, context ->
            context.client().execute {
                // "落地，水"救命的演出: 与图腾一致的上浮旋转动画, 物品换成水桶
                context.client().gameRenderer.showFloatingItem(ItemStack(Items.WATER_BUCKET))
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

    /** 请求打开偷窃界面 (需持有"顺手牵羊"词条) */
    fun requestSteal() {
        if (!ClientPlayNetworking.canSend(StealRequestC2S.ID)) return
        ClientPlayNetworking.send(StealRequestC2S())
    }

    /** 选择偷取第 index 格的物品 */
    fun pickSteal(index: Int) {
        if (!ClientPlayNetworking.canSend(StealPickC2S.ID)) return
        ClientPlayNetworking.send(StealPickC2S(index))
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
