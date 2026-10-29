package com.randomperks.client.net

import com.randomperks.client.BigSnowballState
import com.randomperks.client.GlowMarkState
import com.randomperks.client.JockeyClientState
import com.randomperks.client.SoulCameraClient
import com.randomperks.game.ModVersionService
import com.randomperks.game.MorphState
import com.randomperks.client.screen.EquipmentPickScreen
import com.randomperks.client.screen.ItemSelectScreen
import com.randomperks.client.screen.PerkCatalogScreen
import com.randomperks.client.screen.PerkChoiceScreen
import com.randomperks.client.screen.PerkListScreen
import com.randomperks.client.screen.StealScreen
import com.randomperks.client.screen.TargetPickScreen
import com.randomperks.game.ClientPerkFlags
import com.randomperks.network.GlowMarkS2C
import com.randomperks.network.ItemSelectC2S
import com.randomperks.network.ItemSelectOpenS2C
import com.randomperks.network.PerkAbilitySyncS2C
import com.randomperks.network.PerkActivateC2S
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
import com.randomperks.network.BigSnowballS2C
import com.randomperks.network.DualTargetPickC2S
import com.randomperks.network.DualTargetsS2C
import com.randomperks.network.EquipmentPickC2S
import com.randomperks.network.EquipmentPickOpenS2C
import com.randomperks.network.JockeyBreakFreeC2S
import com.randomperks.network.JockeyControlC2S
import com.randomperks.network.JockeyControlS2C
import com.randomperks.network.MorphS2C
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.network.StealOpenS2C
import com.randomperks.network.StealPickC2S
import com.randomperks.network.StealTargetPickC2S
import com.randomperks.network.StealTargetsS2C
import com.randomperks.network.SoulOutS2C
import com.randomperks.network.WaterBucketTotemS2C
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.text.Text
import java.util.UUID
import java.util.concurrent.CompletableFuture

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
                context.client().setScreen(PerkListScreen(payload.pendingCount, payload.players))
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

        // "灵魂出窍": 开启/关闭灵魂相机 (身体留在原地)
        ClientPlayNetworking.registerGlobalReceiver(SoulOutS2C.ID) { payload, context ->
            context.client().execute {
                if (payload.active) {
                    SoulCameraClient.start()
                    // 灵魂状态下"有界面时不能移动": 如果还停在词条列表, 自动关掉,
                    // 否则 60 秒会白白流失 (与其他贮藏词条点击后立即进入使用流程一致)
                    if (context.client().currentScreen is PerkListScreen) {
                        context.client().setScreen(null)
                    }
                } else {
                    SoulCameraClient.stop()
                }
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
                    "phantoms" -> "random-perks-bingo.screen.phantoms_target" to "random-perks-bingo.phantoms_target.hint"
                    "nuke" -> "random-perks-bingo.screen.nuke_target" to "random-perks-bingo.nuke_target.hint"
                    "puny" -> "random-perks-bingo.screen.puny_target" to "random-perks-bingo.puny_target.hint"
                    "poisoner" -> "random-perks-bingo.screen.poisoner_target" to "random-perks-bingo.poisoner_target.hint"
                    "remote_transfer" -> "random-perks-bingo.screen.remote_transfer_target" to "random-perks-bingo.remote_transfer_target.hint"
                    "hodgepodge" -> "random-perks-bingo.screen.hodgepodge_target" to "random-perks-bingo.hodgepodge_target.hint"
                    "swap" -> "random-perks-bingo.screen.swap_target" to "random-perks-bingo.swap_target.hint"
                    else -> "random-perks-bingo.screen.lightning_target" to "random-perks-bingo.lightning_target.hint"
                }
                context.client().setScreen(
                    TargetPickScreen(titleKey, hintKey, payload.targets) { uuid ->
                        pickActionTarget(payload.purpose, uuid)
                    },
                )
            }
        }

        // "快速附魔": 打开装备选择器 (候选固定为 4 个护甲槽 + 副手, 客户端自己读装备来显示)
        ClientPlayNetworking.registerGlobalReceiver(EquipmentPickOpenS2C.ID) { payload, context ->
            context.client().execute {
                val (titleKey, hintKey) = when (payload.purpose) {
                    else -> "random-perks-bingo.screen.quick_enchant" to "random-perks-bingo.quick_enchant.hint"
                }
                context.client().setScreen(
                    EquipmentPickScreen(titleKey, hintKey) { key ->
                        ClientPlayNetworking.send(EquipmentPickC2S(payload.purpose, key))
                    },
                )
            }
        }

        // "易位": 连弹两次玩家选择器 (第二次自动排除第一个), 两次都选完才一次性回传
        ClientPlayNetworking.registerGlobalReceiver(DualTargetsS2C.ID) { payload, context ->
            context.client().execute {
                openDualTargetPicker(context.client(), payload.purpose, payload.targets, null)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(StealOpenS2C.ID) { payload, context ->
            context.client().execute {
                context.client().setScreen(StealScreen(payload.targetName, payload.items))
            }
        }

        // "远程传输": 打开自己的多格物品选择器
        ClientPlayNetworking.registerGlobalReceiver(ItemSelectOpenS2C.ID) { payload, context ->
            context.client().execute {
                context.client().setScreen(
                    ItemSelectScreen(payload.items, payload.count) { slots ->
                        sendItemSelect(payload.purpose, slots)
                    },
                )
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

        // 登录阶段: 响应服务端的版本查询 (走 LOGIN 阶段而不是 PLAY, 见 ModVersionService)
        ClientLoginNetworking.registerGlobalReceiver(ModVersionService.CHANNEL) { _, _, _, _ ->
            val buf = PacketByteBufs.create()
            buf.writeString(ModVersionService.currentVersion())
            CompletableFuture.completedFuture(buf)
        }

        // "打雪仗": 某个投掷物是变大的雪球 (commandTag 不同步, 只能靠这个包)
        ClientPlayNetworking.registerGlobalReceiver(BigSnowballS2C.ID) { payload, context ->
            context.client().execute {
                BigSnowballState.accept(payload.entityId)
            }
        }

        // "变群友": 某个玩家进入 / 退出变猪状态
        ClientPlayNetworking.registerGlobalReceiver(MorphS2C.ID) { payload, context ->
            context.client().execute {
                // 用 UUID 记录: 客户端重生会复用本地玩家的实体 id, 而服务端会换新 id
                MorphState.setMorphed(payload.playerUuid, payload.morphed)
                // 碰撞箱要主动重算 —— 改 getBaseDimensions 的返回值不会更新已缓存的 boundingBox
                context.client().world?.getPlayers()
                    ?.firstOrNull { it.uuid == payload.playerUuid }
                    ?.calculateDimensions()
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

        // "Jockey": 被骑者按 WASD 任意键累计 30 次 → 请求挣脱。
        // 读的是**原始按键绑定**: 被骑者的 playerInput 已经被 JockeyInputMixin
        // 覆盖成骑乘者的了, 从 input 里读不到他自己的 WASD。
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val options = client.options
            val brokeFree = JockeyClientState.noteMovementKeys(
                options.forwardKey.isPressed,
                options.backKey.isPressed,
                options.leftKey.isPressed,
                options.rightKey.isPressed,
            )
            if (!brokeFree) return@register
            if (!ClientPlayNetworking.canSend(JockeyBreakFreeC2S.ID)) return@register
            ClientPlayNetworking.send(JockeyBreakFreeC2S())
        }

        // 断线时清理客户端状态 —— 否则"灵魂出窍"的相机可能卡在离体状态
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            reset()
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

    /** 在 V 界面点击一个未使用的一次性词条, 请求服务端打开它的使用流程 */
    fun activatePerk(perkId: String) {
        if (!ClientPlayNetworking.canSend(PerkActivateC2S.ID)) return
        ClientPlayNetworking.send(PerkActivateC2S(perkId))
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

    /** 提交多格物品选择 */
    fun sendItemSelect(purpose: String, slots: List<Int>) {
        if (!ClientPlayNetworking.canSend(ItemSelectC2S.ID)) return
        ClientPlayNetworking.send(ItemSelectC2S(purpose, slots))
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

    /**
     * "易位" 的两次选择。
     *
     * 用 closeOnPick = false: 第一次选完直接 setScreen 换到第二次的弹窗, 由 setScreen
     * 负责移除旧屏。不能靠 client.execute 延迟 —— MinecraftClient.execute 在**主线程会
     * 立即执行**, 紧接着 TargetPickScreen 的 close() 会把新屏幕一起清掉 (第二个选择器
     * 因此从来没出现过)。
     */
    private fun openDualTargetPicker(
        client: MinecraftClient,
        purpose: String,
        targets: List<PlayerTargetEntry>,
        first: UUID?,
    ) {
        val remaining = if (first == null) targets else targets.filter { it.uuid != first }
        if (remaining.isEmpty()) return

        val titleKey =
            if (first == null) "random-perks-bingo.screen.swap_first"
            else "random-perks-bingo.screen.swap_second"
        val hintKey =
            if (first == null) "random-perks-bingo.swap_first.hint"
            else "random-perks-bingo.swap_second.hint"

        client.setScreen(
            TargetPickScreen(titleKey, hintKey, remaining, closeOnPick = false) { uuid ->
                if (first == null) {
                    // 选中第一个 → 直接换成第二个选择器
                    openDualTargetPicker(client, purpose, targets, uuid)
                } else {
                    client.setScreen(null)
                    ClientPlayNetworking.send(DualTargetPickC2S(purpose, first, uuid))
                }
            },
        )
    }

    fun reset() {
        pendingChoices = 0
        currentOptions = null
        SoulCameraClient.stop()
        JockeyClientState.clear()
        MorphState.clear()
        BigSnowballState.clear()
    }
}
