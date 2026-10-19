package com.randomperks.network

import net.minecraft.item.ItemStack
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.PlayerInput
import java.util.UUID
/** 一个词条卡片的数据 (服务端 → 客户端 展示用; 同时用于"三选一候选"与"已获得列表") */
data class PerkOptionData(
    val id: String,
    val rarityOrdinal: Int,
    val archetypeOrdinal: Int,
    val icon: ItemStack,
)

private fun writeCards(buf: RegistryByteBuf, cards: List<PerkOptionData>) {
    buf.writeVarInt(cards.size)
    for (card in cards) {
        buf.writeString(card.id)
        buf.writeVarInt(card.rarityOrdinal)
        buf.writeVarInt(card.archetypeOrdinal)
        ItemStack.PACKET_CODEC.encode(buf, card.icon)
    }
}

private fun readCards(buf: RegistryByteBuf): List<PerkOptionData> {
    val size = buf.readVarInt()
    val list = ArrayList<PerkOptionData>(size)
    repeat(size) {
        val id = buf.readString()
        val rarity = buf.readVarInt()
        val archetype = buf.readVarInt()
        val icon = ItemStack.PACKET_CODEC.decode(buf)
        list.add(PerkOptionData(id, rarity, archetype, icon))
    }
    return list
}

/** S2C: 通知玩家当前待选机会数量 (用于提示与 HUD 角标) */
class PerkNotifyS2C(val pendingCount: Int) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkNotifyS2C>(Identifier.of("random-perks-bingo", "perk_notify"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkNotifyS2C> = PacketCodec.of(
            { value, buf -> buf.writeVarInt(value.pendingCount) },
            { buf -> PerkNotifyS2C(buf.readVarInt()) },
        )
    }

    override fun getId() = ID
}

/** S2C: 下发一次三选一的候选项 */
class PerkOfferS2C(val options: List<PerkOptionData>) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkOfferS2C>(Identifier.of("random-perks-bingo", "perk_offer"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkOfferS2C> = PacketCodec.of(
            { value, buf -> writeCards(buf, value.options) },
            { buf -> PerkOfferS2C(readCards(buf)) },
        )
    }

    override fun getId() = ID
}

/** C2S: 玩家请求打开三选一 (拉取候选) */
class PerkRequestC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkRequestC2S>(Identifier.of("random-perks-bingo", "perk_request"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkRequestC2S> = PacketCodec.of(
            { _, _ -> },
            { _ -> PerkRequestC2S() },
        )
    }

    override fun getId() = ID
}

/** C2S: 玩家选择了第 index 个候选项 */
class PerkPickC2S(val index: Int) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkPickC2S>(Identifier.of("random-perks-bingo", "perk_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkPickC2S> = PacketCodec.of(
            { value, buf -> buf.writeVarInt(value.index) },
            { buf -> PerkPickC2S(buf.readVarInt()) },
        )
    }

    override fun getId() = ID
}

/** C2S: 玩家请求查看已获得的词条列表 */
class PerkListRequestC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkListRequestC2S>(Identifier.of("random-perks-bingo", "perk_list_request"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkListRequestC2S> = PacketCodec.of(
            { _, _ -> },
            { _ -> PerkListRequestC2S() },
        )
    }

    override fun getId() = ID
}

/** 单个玩家的词条列表 (供 V 键界面的 tab 页使用) */
data class PlayerPerkList(
    val uuid: UUID,
    val name: String,
    val perks: List<PerkOptionData>,
)

private fun writePlayerLists(buf: RegistryByteBuf, lists: List<PlayerPerkList>) {
    buf.writeVarInt(lists.size)
    for (list in lists) {
        buf.writeUuid(list.uuid)
        buf.writeString(list.name)
        writeCards(buf, list.perks)
    }
}

private fun readPlayerLists(buf: RegistryByteBuf): List<PlayerPerkList> {
    val size = buf.readVarInt()
    val result = ArrayList<PlayerPerkList>(size)
    repeat(size) {
        result.add(PlayerPerkList(buf.readUuid(), buf.readString(), readCards(buf)))
    }
    return result
}

/** S2C: 下发**所有在线玩家**的词条列表 (附请求者自己的待选数) */
class PerkListS2C(
    val pendingCount: Int,
    val players: List<PlayerPerkList>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkListS2C>(Identifier.of("random-perks-bingo", "perk_list"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkListS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.pendingCount)
                writePlayerLists(buf, value.players)
            },
            { buf ->
                val pending = buf.readVarInt()
                PerkListS2C(pending, readPlayerLists(buf))
            },
        )
    }

    override fun getId() = ID
}

/** S2C: 让客户端把指定实体显示为"发光标记" (仅发给同队玩家, 代替原版全局发光效果) */
class GlowMarkS2C(
    val entityIds: List<Int>,
    val durationMillis: Long,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<GlowMarkS2C>(Identifier.of("random-perks-bingo", "glow_mark"))
        val CODEC: PacketCodec<RegistryByteBuf, GlowMarkS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.entityIds.size)
                for (id in value.entityIds) buf.writeVarInt(id)
                buf.writeLong(value.durationMillis)
            },
            { buf ->
                val size = buf.readVarInt()
                val ids = ArrayList<Int>(size)
                repeat(size) { ids.add(buf.readVarInt()) }
                GlowMarkS2C(ids, buf.readLong())
            },
        )
    }

    override fun getId() = ID
}

/** S2C: 同步玩家当前拥有的词条 id 列表 (客户端据此启用能力: 矿透渲染 / 偷窃按键等) */
class PerkAbilitySyncS2C(val perkIds: List<String>) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkAbilitySyncS2C>(Identifier.of("random-perks-bingo", "ability_sync"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkAbilitySyncS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.perkIds.size)
                for (id in value.perkIds) buf.writeString(id)
            },
            { buf ->
                val size = buf.readVarInt()
                val list = ArrayList<String>(size)
                repeat(size) { list.add(buf.readString()) }
                PerkAbilitySyncS2C(list)
            },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端请求打开偷窃界面 (需持有"顺手牵羊"词条) */
class StealRequestC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StealRequestC2S>(Identifier.of("random-perks-bingo", "steal_request"))
        val CODEC: PacketCodec<RegistryByteBuf, StealRequestC2S> = PacketCodec.of(
            { _, _ -> },
            { _ -> StealRequestC2S() },
        )
    }

    override fun getId() = ID
}

/** S2C: 下发偷窃目标的信息与背包内容 (36 格) */
class StealOpenS2C(
    val targetName: String,
    val items: List<ItemStack>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StealOpenS2C>(Identifier.of("random-perks-bingo", "steal_open"))
        val CODEC: PacketCodec<RegistryByteBuf, StealOpenS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.targetName)
                buf.writeVarInt(value.items.size)
                for (item in value.items) {
                    buf.writeBoolean(!item.isEmpty)
                    if (!item.isEmpty) ItemStack.PACKET_CODEC.encode(buf, item)
                }
            },
            { buf ->
                val name = buf.readString()
                val size = buf.readVarInt()
                val items = ArrayList<ItemStack>(size)
                repeat(size) {
                    items.add(if (buf.readBoolean()) ItemStack.PACKET_CODEC.decode(buf) else ItemStack.EMPTY)
                }
                StealOpenS2C(name, items)
            },
        )
    }

    override fun getId() = ID
}

/** C2S: 偷取目标背包中第 index 格的物品 */
class StealPickC2S(val index: Int) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StealPickC2S>(Identifier.of("random-perks-bingo", "steal_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, StealPickC2S> = PacketCodec.of(
            { value, buf -> buf.writeVarInt(value.index) },
            { buf -> StealPickC2S(buf.readVarInt()) },
        )
    }

    override fun getId() = ID
}

/** 可选中的玩家目标 (uuid 供服务端校验, name 供界面显示) —— 顺手牵羊、引雷术等共用 */
data class PlayerTargetEntry(val uuid: UUID, val name: String)

/** 目标列表的公共编解码 (所有"选一名玩家"类词条共用同一套格式) */
private fun writeTargets(buf: RegistryByteBuf, targets: List<PlayerTargetEntry>) {
    buf.writeVarInt(targets.size)
    for (t in targets) {
        buf.writeUuid(t.uuid)
        buf.writeString(t.name)
    }
}

private fun readTargets(buf: RegistryByteBuf): List<PlayerTargetEntry> {
    val size = buf.readVarInt()
    val list = ArrayList<PlayerTargetEntry>(size)
    repeat(size) { list.add(PlayerTargetEntry(buf.readUuid(), buf.readString())) }
    return list
}

/** S2C: 下发"顺手牵羊"的候选目标列表 —— 客户端据此打开玩家选择器 */
class StealTargetsS2C(val targets: List<PlayerTargetEntry>) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StealTargetsS2C>(Identifier.of("random-perks-bingo", "steal_targets"))
        val CODEC: PacketCodec<RegistryByteBuf, StealTargetsS2C> = PacketCodec.of(
            { value, buf -> writeTargets(buf, value.targets) },
            { buf -> StealTargetsS2C(readTargets(buf)) },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端在玩家选择器中选中了某个目标 */
class StealTargetPickC2S(val uuid: UUID) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StealTargetPickC2S>(Identifier.of("random-perks-bingo", "steal_target_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, StealTargetPickC2S> = PacketCodec.of(
            { value, buf -> buf.writeUuid(value.uuid) },
            { buf -> StealTargetPickC2S(buf.readUuid()) },
        )
    }

    override fun getId() = ID
}

/** S2C: 下发"选一名玩家后立即生效"类词条的候选列表 (purpose 区分是哪个词条) */
class ActionTargetsS2C(
    val purpose: String,
    val targets: List<PlayerTargetEntry>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ActionTargetsS2C>(Identifier.of("random-perks-bingo", "action_targets"))
        val CODEC: PacketCodec<RegistryByteBuf, ActionTargetsS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                writeTargets(buf, value.targets)
            },
            { buf ->
                val purpose = buf.readString()
                ActionTargetsS2C(purpose, readTargets(buf))
            },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端在目标选择器里选中了某个玩家 (带回 purpose 供服务端分派) */
class ActionTargetPickC2S(
    val purpose: String,
    val uuid: UUID,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ActionTargetPickC2S>(Identifier.of("random-perks-bingo", "action_target_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, ActionTargetPickC2S> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                buf.writeUuid(value.uuid)
            },
            { buf -> ActionTargetPickC2S(buf.readString(), buf.readUuid()) },
        )
    }

    override fun getId() = ID
}

/**
 * C2S: "Jockey" 骑乘者每 tick 上报自己的输入。
 *
 * 服务端把它转给被骑的玩家 (见 [JockeyControlS2C]) —— 玩家客户端的位置是权威的,
 * 所以"控制别人移动"只能由对方客户端来执行, 服务端转发是最短的一条链路。
 */
class JockeyControlC2S(
    val input: PlayerInput,
    val yaw: Float,
    val pitch: Float,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<JockeyControlC2S>(Identifier.of("random-perks-bingo", "jockey_control"))
        val CODEC: PacketCodec<RegistryByteBuf, JockeyControlC2S> = PacketCodec.of(
            { value, buf ->
                PlayerInput.PACKET_CODEC.encode(buf, value.input)
                buf.writeFloat(value.yaw)
                buf.writeFloat(value.pitch)
            },
            { buf ->
                JockeyControlC2S(PlayerInput.PACKET_CODEC.decode(buf), buf.readFloat(), buf.readFloat())
            },
        )
    }

    override fun getId() = ID
}

/**
 * C2S: "Jockey" **被骑者**连按 Shift 达到次数, 请求脱离骑乘。
 *
 * 被骑者的键盘输入在客户端就被骑乘者的输入覆盖掉了, 所以服务端看不到他按 Shift ——
 * 只能在客户端侧数按键次数, 到这里才上报。
 */
class JockeyBreakFreeC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<JockeyBreakFreeC2S>(Identifier.of("random-perks-bingo", "jockey_break_free"))
        val CODEC: PacketCodec<RegistryByteBuf, JockeyBreakFreeC2S> = PacketCodec.of(
            { _, _ -> },
            { JockeyBreakFreeC2S() },
        )
    }

    override fun getId() = ID
}

/**
 * C2S: "磁力升级" 附带的垃圾桶 —— 玩家把**光标上**的物品丢进垃圾桶, 请求服务端销毁。
 *
 * 垃圾桶是**客户端绘制**的, 不是真的容器槽位: ScreenHandler 的槽位索引要求客户端/服务端
 * 严格一一对应, 只在一端多塞一个槽位, 点击时就会索引越界被踢。现成的垃圾桶 mod 也是这套
 * 思路 —— 画面自己画, 只把"销毁这个物品"的动作发给服务端。
 */
class TrashItemC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<TrashItemC2S>(Identifier.of("random-perks-bingo", "trash_item"))
        val CODEC: PacketCodec<RegistryByteBuf, TrashItemC2S> = PacketCodec.of(
            { _, _ -> },
            { TrashItemC2S() },
        )
    }

    override fun getId() = ID
}

/** S2C: 把骑乘者的输入下发给被骑的玩家 (对方客户端据此接管自己的移动与朝向) */
class JockeyControlS2C(
    val input: PlayerInput,
    val yaw: Float,
    val pitch: Float,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<JockeyControlS2C>(Identifier.of("random-perks-bingo", "jockey_control_s2c"))
        val CODEC: PacketCodec<RegistryByteBuf, JockeyControlS2C> = PacketCodec.of(
            { value, buf ->
                PlayerInput.PACKET_CODEC.encode(buf, value.input)
                buf.writeFloat(value.yaw)
                buf.writeFloat(value.pitch)
            },
            { buf ->
                JockeyControlS2C(PlayerInput.PACKET_CODEC.decode(buf), buf.readFloat(), buf.readFloat())
            },
        )
    }

    override fun getId() = ID
}

/** S2C: 通知客户端某个投掷物是"大雪球" (渲染时放大) */
class BigSnowballS2C(val entityId: Int) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<BigSnowballS2C>(Identifier.of("random-perks-bingo", "big_snowball"))
        val CODEC: PacketCodec<RegistryByteBuf, BigSnowballS2C> = PacketCodec.of(
            { value, buf -> buf.writeVarInt(value.entityId) },
            { buf -> BigSnowballS2C(buf.readVarInt()) },
        )
    }

    override fun getId() = ID
}

/** S2C: 通知客户端某个实体进入"变猪"状态 (外观与尺寸伪装全部由客户端完成) */
class MorphS2C(
    val entityId: Int,
    val durationMillis: Long,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<MorphS2C>(Identifier.of("random-perks-bingo", "morph"))
        val CODEC: PacketCodec<RegistryByteBuf, MorphS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.entityId)
                buf.writeVarLong(value.durationMillis)
            },
            { buf -> MorphS2C(buf.readVarInt(), buf.readVarLong()) },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端请求二段跳 (服务端验证词条与状态) */
class DoubleJumpC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DoubleJumpC2S>(Identifier.of("random-perks-bingo", "double_jump"))
        val CODEC: PacketCodec<RegistryByteBuf, DoubleJumpC2S> = PacketCodec.of(
            { _, _ -> },
            { _ -> DoubleJumpC2S() },
        )
    }

    override fun getId() = ID
}

/** S2C: "落地，水"触发 —— 客户端播放"水桶上浮旋转"演出 (参照图腾动画, 物品换成水桶) */
class WaterBucketTotemS2C : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<WaterBucketTotemS2C>(Identifier.of("random-perks-bingo", "water_bucket_totem"))
        val CODEC: PacketCodec<RegistryByteBuf, WaterBucketTotemS2C> = PacketCodec.of(
            { _, _ -> },
            { _ -> WaterBucketTotemS2C() },
        )
    }

    override fun getId() = ID
}

/** C2S: 玩家请求打开词条百科 (全部词条 + 已收集标记) */
class PerkCatalogRequestC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkCatalogRequestC2S>(Identifier.of("random-perks-bingo", "perk_catalog_request"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkCatalogRequestC2S> = PacketCodec.of(
            { _, _ -> },
            { _ -> PerkCatalogRequestC2S() },
        )
    }

    override fun getId() = ID
}

/** S2C: 词条百科数据 —— 全部词条定义 + 玩家已收集的 id 集合 */
class PerkCatalogS2C(
    val ownedIds: List<String>,
    val allPerks: List<PerkOptionData>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkCatalogS2C>(Identifier.of("random-perks-bingo", "perk_catalog"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkCatalogS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.ownedIds.size)
                for (id in value.ownedIds) buf.writeString(id)
                writeCards(buf, value.allPerks)
            },
            { buf ->
                val size = buf.readVarInt()
                val owned = ArrayList<String>(size)
                repeat(size) { owned.add(buf.readString()) }
                PerkCatalogS2C(owned, readCards(buf))
            },
        )
    }

    override fun getId() = ID
}
