package com.randomperks.client.net

import com.randomperks.client.GlowMarkState
import com.randomperks.client.JockeyClientState
import com.randomperks.client.MorphClientState
import com.randomperks.client.screen.PerkCatalogScreen
import com.randomperks.client.screen.PerkChoiceScreen
import com.randomperks.client.screen.PerkListScreen
import com.randomperks.client.screen.StealScreen
import com.randomperks.client.screen.TargetPickScreen
import com.randomperks.game.ClientPerkFlags
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
import com.randomperks.network.ActionTargetPickC2S
import com.randomperks.network.ActionTargetsS2C
import com.randomperks.network.JockeyControlC2S
import com.randomperks.network.JockeyControlS2C
import com.randomperks.network.MorphS2C
import com.randomperks.network.StealOpenS2C
import com.randomperks.network.StealPickC2S
import com.randomperks.network.StealTargetPickC2S
import com.randomperks.network.StealTargetsS2C
import com.randomperks.network.WaterBucketTotemS2C
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.text.Text
import java.util.UUID

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
                // 同步给 main 源集的镜像 —— mixin 那边引用不到 client 源集的类
                ClientPerkFlags.ownedPerkIds = ownedPerkIds
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(StealTargetsS2C.ID) { payload, context ->
            context.client().execute {
                context.client().setScreen(
                    TargetPickScreen(
                        "random-perks-bingo.screen.steal_target",
                        "random-perks-bingo.steal_target.hint",
                        payload.targets,
                    ) { uuid -> pickStealTarget(uuid) },
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ActionTargetsS2C.ID) { payload, context ->
            context.client().execute {
                val (titleKey, hintKey) = when (payload.purpose) {
                    "nuke" -> "random-perks-bingo.screen.nuke_target" to "random-perks-bingo.nuke_target.hint"
                    "puny" -> "random-perks-bingo.screen.puny_target" to "random-perks-bingo.puny_target.hint"
                    "poisoner" -> "random-perks-bingo.screen.poisoner_target" to "random-perks-bingo.poisoner_target.hint"
                    else -> "random-perks-bingo.screen.lightning_target" to "random-perks-bingo.lightning_target.hint"
                }
                context.client().setScreen(
                    TargetPickScreen(titleKey, hintKey, payload.targets) { uuid ->
                        pickActionTarget(payload.purpose, uuid)
                    },
                )
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

        // "变群友": 某个实体进入变猪状态 (渲染与尺寸伪装全在客户端)
        ClientPlayNetworking.registerGlobalReceiver(MorphS2C.ID) { payload, context ->
            context.client().execute {
                MorphClientState.accept(payload.entityId, payload.durationMillis)
            }
        }

        // "Jockey": 被骑者收到骑乘者的输入, 交给 JockeyInputMixin / JockeyCameraMixin 生效
        ClientPlayNetworking.registerGlobalReceiver(JockeyControlS2C.ID) { payload, context ->
            context.client().execute {
                JockeyClientState.accept(payload.input, payload.yaw, payload.pitch)
            }
        }

        // "Jockey": 骑乘者每 tick 上报自己的输入 (只有骑在别的玩家身上时才发)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            if (player.vehicle !is PlayerEntity) return@register
            if (!ClientPlayNetworking.canSend(JockeyControlC2S.ID)) return@register

            ClientPlayNetworking.send(
                JockeyControlC2S(player.input.playerInput, player.yaw, player.pitch),
            )
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

    /** 在目标选择器里选中一名玩家 */
    fun pickStealTarget(uuid: UUID) {
        if (!ClientPlayNetworking.canSend(StealTargetPickC2S.ID)) return
        ClientPlayNetworking.send(StealTargetPickC2S(uuid))
    }

    /** "选一名玩家后立即生效"类词条: 上报选中的玩家 (purpose 区分是哪个词条) */
    fun pickActionTarget(purpose: String, uuid: UUID) {
        if (!ClientPlayNetworking.canSend(ActionTargetPickC2S.ID)) return
        ClientPlayNetworking.send(ActionTargetPickC2S(purpose, uuid))
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
        JockeyClientState.clear()
        MorphClientState.clear()
    }
}
