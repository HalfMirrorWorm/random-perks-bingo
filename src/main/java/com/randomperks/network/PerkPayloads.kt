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
    /** 是否带"贮藏"标记 —— 可以在 V 界面点击执行的一次性词条 */
    val storable: Boolean = false,
)

private fun writeCards(buf: RegistryByteBuf, cards: List<PerkOptionData>) {
    buf.writeVarInt(cards.size)
    for (card in cards) {
        buf.writeString(card.id)
        buf.writeVarInt(card.rarityOrdinal)
        buf.writeVarInt(card.archetypeOrdinal)
        ItemStack.PACKET_CODEC.encode(buf, card.icon)
        buf.writeBoolean(card.storable)
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
        val storable = buf.readBoolean()
        list.add(PerkOptionData(id, rarity, archetype, icon, storable))
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

/** C2S: 玩家在 V 界面点击了一个待使用的一次性词条 */
class PerkActivateC2S(val perkId: String) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkActivateC2S>(Identifier.of("random-perks-bingo", "perk_activate"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkActivateC2S> = PacketCodec.of(
            { value, buf -> buf.writeString(value.perkId) },
            { buf -> PerkActivateC2S(buf.readString()) },
        )
    }

    override fun getId() = ID
}

/** 单个玩家的词条列表 (供 V 键界面的 tab 页使用) */
data class PlayerPerkList(
    val uuid: UUID,
    val name: String,
    val perks: List<PerkOptionData>,
    /** 已获得但还没使用的一次性词条 id (V 界面里闪烁, 点击触发) */
    val pendingPerkIds: List<String> = emptyList(),
    /** 所属队伍的内部 id (无队伍为空串) */
    val teamId: String = "",
    /** 队伍显示名 (无队伍为空串)。注意: 这是服务端已解析的文本, 通常固定为英文 */
    val teamName: String = "",
    /** 队伍显示名的翻译 key (如 bingo.team.red); 客户端据此按自己的语言显示 */
    val teamNameKey: String = "",
    /** 翻译 key 的唯一参数 (如临时队伍的编号; 无参数为空串) */
    val teamNameArg: String = "",
    /** 队伍颜色的 RGB 值 (无队伍时白色) */
    val teamColor: Int = 0xFFFFFF,
)

private fun writePlayerLists(buf: RegistryByteBuf, lists: List<PlayerPerkList>) {
    buf.writeVarInt(lists.size)
    for (list in lists) {
        buf.writeUuid(list.uuid)
        buf.writeString(list.name)
        writeCards(buf, list.perks)
        buf.writeVarInt(list.pendingPerkIds.size)
        for (id in list.pendingPerkIds) buf.writeString(id)
        buf.writeString(list.teamId)
        buf.writeString(list.teamName)
        buf.writeString(list.teamNameKey)
        buf.writeString(list.teamNameArg)
        buf.writeInt(list.teamColor)
    }
}

private fun readPlayerLists(buf: RegistryByteBuf): List<PlayerPerkList> {
    val size = buf.readVarInt()
    val result = ArrayList<PlayerPerkList>(size)
    repeat(size) {
        val uuid = buf.readUuid()
        val name = buf.readString()
        val perks = readCards(buf)
        val pendingSize = buf.readVarInt()
        val pending = ArrayList<String>(pendingSize)
        repeat(pendingSize) { pending.add(buf.readString()) }
        val teamId = buf.readString()
        val teamName = buf.readString()
        val teamNameKey = buf.readString()
        val teamNameArg = buf.readString()
        val teamColor = buf.readInt()
        result.add(
            PlayerPerkList(uuid, name, perks, pending, teamId, teamName, teamNameKey, teamNameArg, teamColor)
        )
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

/** S2C: "灵魂出鞘"状态 (客户端据此启用穿墙移动) */
class SoulOutS2C(val active: Boolean) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SoulOutS2C>(Identifier.of("random-perks-bingo", "soul_out"))
        val CODEC: PacketCodec<RegistryByteBuf, SoulOutS2C> = PacketCodec.of(
            { value, buf -> buf.writeBoolean(value.active) },
            { buf -> SoulOutS2C(buf.readBoolean()) },
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

/** S2C: 让客户端从自己的背包里挑选若干格物品 (远程传输等) */
class ItemSelectOpenS2C(
    val purpose: String,
    val items: List<ItemStack>,
    val count: Int,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ItemSelectOpenS2C>(Identifier.of("random-perks-bingo", "item_select_open"))
        val CODEC: PacketCodec<RegistryByteBuf, ItemSelectOpenS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                buf.writeVarInt(value.count)
                buf.writeVarInt(value.items.size)
                for (item in value.items) {
                    buf.writeBoolean(!item.isEmpty)
                    if (!item.isEmpty) ItemStack.PACKET_CODEC.encode(buf, item)
                }
            },
            { buf ->
                val purpose = buf.readString()
                val count = buf.readVarInt()
                val size = buf.readVarInt().coerceIn(0, 64)
                val items = ArrayList<ItemStack>(size)
                repeat(size) {
                    items.add(if (buf.readBoolean()) ItemStack.PACKET_CODEC.decode(buf) else ItemStack.EMPTY)
                }
                ItemSelectOpenS2C(purpose, items, count)
            },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端提交选中的物品格 (purpose 区分词条) */
class ItemSelectC2S(
    val purpose: String,
    val slots: List<Int>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ItemSelectC2S>(Identifier.of("random-perks-bingo", "item_select"))
        val CODEC: PacketCodec<RegistryByteBuf, ItemSelectC2S> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                buf.writeVarInt(value.slots.size)
                for (slot in value.slots) buf.writeVarInt(slot)
            },
            { buf ->
                val purpose = buf.readString()
                val size = buf.readVarInt().coerceIn(0, 64)
                val slots = ArrayList<Int>(size)
                repeat(size) { slots.add(buf.readVarInt()) }
                ItemSelectC2S(purpose, slots)
            },
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
 * S2C: 下发"需要选**两名**玩家"类词条的候选列表。
 *
 * 客户端会连续弹两次 [com.randomperks.client.screen.TargetPickScreen] (第二次自动排除第一次选中的),
 * 两次都选完才回一个 [DualTargetPickC2S]。
 */
class DualTargetsS2C(
    val purpose: String,
    val targets: List<PlayerTargetEntry>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DualTargetsS2C>(Identifier.of("random-perks-bingo", "dual_targets"))
        val CODEC: PacketCodec<RegistryByteBuf, DualTargetsS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                writeTargets(buf, value.targets)
            },
            { buf -> DualTargetsS2C(buf.readString(), readTargets(buf)) },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端在两次选择器里分别选中了两名玩家 (first 是先选的) */
class DualTargetPickC2S(
    val purpose: String,
    val first: UUID,
    val second: UUID,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DualTargetPickC2S>(Identifier.of("random-perks-bingo", "dual_target_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, DualTargetPickC2S> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                buf.writeUuid(value.first)
                buf.writeUuid(value.second)
            },
            { buf -> DualTargetPickC2S(buf.readString(), buf.readUuid(), buf.readUuid()) },
        )
    }

    override fun getId() = ID
}

/**
 * S2C: 打开**装备选择器** (候选固定为 4 个护甲槽 + 副手)。
 *
 * 不下发候选列表 —— 这 5 个槽位是固定的, 客户端自己读自己的装备来显示就够了。
 * purpose 供服务端在回传时分派到具体词条。
 */
class EquipmentPickOpenS2C(val purpose: String) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<EquipmentPickOpenS2C>(Identifier.of("random-perks-bingo", "equipment_pick_open"))
        val CODEC: PacketCodec<RegistryByteBuf, EquipmentPickOpenS2C> = PacketCodec.of(
            { value, buf -> buf.writeString(value.purpose) },
            { buf -> EquipmentPickOpenS2C(buf.readString()) },
        )
    }

    override fun getId() = ID
}

/** C2S: 客户端在装备选择器里选中了某个槽位 (slot 用 EquipmentSlot.getName(); 服务端会校验) */
class EquipmentPickC2S(
    val purpose: String,
    val slot: String,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<EquipmentPickC2S>(Identifier.of("random-perks-bingo", "equipment_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, EquipmentPickC2S> = PacketCodec.of(
            { value, buf ->
                buf.writeString(value.purpose)
                buf.writeString(value.slot)
            },
            { buf -> EquipmentPickC2S(buf.readString(), buf.readString()) },
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
 * C2S: "Jockey" **被骑者**连按 WASD 达到 30 次, 请求脱离骑乘。
 *
 * 被骑者的键盘输入在客户端就被骑乘者的输入覆盖掉了, 所以服务端看不到他按 WASD ——
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

/** S2C: 同步某个玩家**是否**处于"变猪"状态 ("变群友", 外观、尺寸、视角伪装全部由客户端完成) */
class MorphS2C(
    val playerUuid: UUID,
    val morphed: Boolean,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<MorphS2C>(Identifier.of("random-perks-bingo", "morph"))
        val CODEC: PacketCodec<RegistryByteBuf, MorphS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeUuid(value.playerUuid)
                buf.writeBoolean(value.morphed)
            },
            { buf -> MorphS2C(buf.readUuid(), buf.readBoolean()) },
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

/** C2S: 客户端请求冲刺 (服务端验证词条与状态) */
class DashC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DashC2S>(Identifier.of("random-perks-bingo", "dash"))
        val CODEC: PacketCodec<RegistryByteBuf, DashC2S> = PacketCodec.of(
            { _, _ -> },
            { _ -> DashC2S() },
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
