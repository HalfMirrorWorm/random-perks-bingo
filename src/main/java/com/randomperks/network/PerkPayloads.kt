package com.randomperks.network

import net.minecraft.world.item.ItemStack
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
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

private fun writeCards(buf: RegistryFriendlyByteBuf, cards: List<PerkOptionData>) {
    buf.writeVarInt(cards.size)
    for (card in cards) {
        buf.writeUtf(card.id)
        buf.writeVarInt(card.rarityOrdinal)
        buf.writeVarInt(card.archetypeOrdinal)
        ItemStack.STREAM_CODEC.encode(buf, card.icon)
        buf.writeBoolean(card.storable)
    }
}

private fun readCards(buf: RegistryFriendlyByteBuf): List<PerkOptionData> {
    val size = buf.readVarInt()
    val list = ArrayList<PerkOptionData>(size)
    repeat(size) {
        val id = buf.readUtf()
        val rarity = buf.readVarInt()
        val archetype = buf.readVarInt()
        val icon = ItemStack.STREAM_CODEC.decode(buf)
        val storable = buf.readBoolean()
        list.add(PerkOptionData(id, rarity, archetype, icon, storable))
    }
    return list
}

/** S2C: 通知玩家当前待选机会数量 (用于提示与 HUD 角标) */
class PerkNotifyS2C(val pendingCount: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkNotifyS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_notify"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkNotifyS2C> = StreamCodec.ofMember(
            { value, buf -> buf.writeVarInt(value.pendingCount) },
            { buf -> PerkNotifyS2C(buf.readVarInt()) },
        )
    }

    override fun type() = ID
}

/** S2C: 下发一次三选一的候选项 */
class PerkOfferS2C(val options: List<PerkOptionData>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkOfferS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_offer"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkOfferS2C> = StreamCodec.ofMember(
            { value, buf -> writeCards(buf, value.options) },
            { buf -> PerkOfferS2C(readCards(buf)) },
        )
    }

    override fun type() = ID
}

/** C2S: 玩家请求打开三选一 (拉取候选) */
class PerkRequestC2S : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkRequestC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkRequestC2S> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> PerkRequestC2S() },
        )
    }

    override fun type() = ID
}

/** C2S: 玩家选择了第 index 个候选项 */
class PerkPickC2S(val index: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkPickC2S> = StreamCodec.ofMember(
            { value, buf -> buf.writeVarInt(value.index) },
            { buf -> PerkPickC2S(buf.readVarInt()) },
        )
    }

    override fun type() = ID
}

/** C2S: 玩家请求查看已获得的词条列表 */
class PerkListRequestC2S : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkListRequestC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_list_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkListRequestC2S> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> PerkListRequestC2S() },
        )
    }

    override fun type() = ID
}

/** C2S: 玩家在 V 界面点击了一个待使用的一次性词条 */
class PerkActivateC2S(val perkId: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkActivateC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_activate"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkActivateC2S> = StreamCodec.ofMember(
            { value, buf -> buf.writeUtf(value.perkId) },
            { buf -> PerkActivateC2S(buf.readUtf()) },
        )
    }

    override fun type() = ID
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

private fun writePlayerLists(buf: RegistryFriendlyByteBuf, lists: List<PlayerPerkList>) {
    buf.writeVarInt(lists.size)
    for (list in lists) {
        buf.writeUUID(list.uuid)
        buf.writeUtf(list.name)
        writeCards(buf, list.perks)
        buf.writeVarInt(list.pendingPerkIds.size)
        for (id in list.pendingPerkIds) buf.writeUtf(id)
        buf.writeUtf(list.teamId)
        buf.writeUtf(list.teamName)
        buf.writeUtf(list.teamNameKey)
        buf.writeUtf(list.teamNameArg)
        buf.writeInt(list.teamColor)
    }
}

private fun readPlayerLists(buf: RegistryFriendlyByteBuf): List<PlayerPerkList> {
    val size = buf.readVarInt()
    val result = ArrayList<PlayerPerkList>(size)
    repeat(size) {
        val uuid = buf.readUUID()
        val name = buf.readUtf()
        val perks = readCards(buf)
        val pendingSize = buf.readVarInt()
        val pending = ArrayList<String>(pendingSize)
        repeat(pendingSize) { pending.add(buf.readUtf()) }
        val teamId = buf.readUtf()
        val teamName = buf.readUtf()
        val teamNameKey = buf.readUtf()
        val teamNameArg = buf.readUtf()
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
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkListS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_list"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkListS2C> = StreamCodec.ofMember(
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

    override fun type() = ID
}

/** S2C: 让客户端把指定实体显示为"发光标记" (仅发给同队玩家, 代替原版全局发光效果) */
class GlowMarkS2C(
    val entityIds: List<Int>,
    val durationMillis: Long,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<GlowMarkS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "glow_mark"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, GlowMarkS2C> = StreamCodec.ofMember(
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

    override fun type() = ID
}

/** S2C: 同步玩家当前拥有的词条 id 列表 (客户端据此启用能力: 矿透渲染 / 偷窃按键等) */
class PerkAbilitySyncS2C(val perkIds: List<String>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkAbilitySyncS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "ability_sync"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkAbilitySyncS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeVarInt(value.perkIds.size)
                for (id in value.perkIds) buf.writeUtf(id)
            },
            { buf ->
                val size = buf.readVarInt()
                val list = ArrayList<String>(size)
                repeat(size) { list.add(buf.readUtf()) }
                PerkAbilitySyncS2C(list)
            },
        )
    }

    override fun type() = ID
}

/** S2C: "灵魂出窍"状态 (客户端据此开启/关闭灵魂相机) */
class SoulOutS2C(val active: Boolean) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SoulOutS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "soul_out"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SoulOutS2C> = StreamCodec.ofMember(
            { value, buf -> buf.writeBoolean(value.active) },
            { buf -> SoulOutS2C(buf.readBoolean()) },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端请求打开偷窃界面 (需持有"顺手牵羊"词条) */
class StealRequestC2S : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StealRequestC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "steal_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, StealRequestC2S> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> StealRequestC2S() },
        )
    }

    override fun type() = ID
}

/** S2C: 下发偷窃目标的信息与背包内容 (36 格) */
class StealOpenS2C(
    val targetName: String,
    val items: List<ItemStack>,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StealOpenS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "steal_open"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, StealOpenS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.targetName)
                buf.writeVarInt(value.items.size)
                for (item in value.items) {
                    buf.writeBoolean(!item.isEmpty)
                    if (!item.isEmpty) ItemStack.STREAM_CODEC.encode(buf, item)
                }
            },
            { buf ->
                val name = buf.readUtf()
                val size = buf.readVarInt()
                val items = ArrayList<ItemStack>(size)
                repeat(size) {
                    items.add(if (buf.readBoolean()) ItemStack.STREAM_CODEC.decode(buf) else ItemStack.EMPTY)
                }
                StealOpenS2C(name, items)
            },
        )
    }

    override fun type() = ID
}

/** C2S: 偷取目标背包中第 index 格的物品 */
class StealPickC2S(val index: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StealPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "steal_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, StealPickC2S> = StreamCodec.ofMember(
            { value, buf -> buf.writeVarInt(value.index) },
            { buf -> StealPickC2S(buf.readVarInt()) },
        )
    }

    override fun type() = ID
}

/** S2C: 让客户端从自己的背包里挑选若干格物品 (远程传输等) */
class ItemSelectOpenS2C(
    val purpose: String,
    val items: List<ItemStack>,
    val count: Int,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ItemSelectOpenS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "item_select_open"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ItemSelectOpenS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                buf.writeVarInt(value.count)
                buf.writeVarInt(value.items.size)
                for (item in value.items) {
                    buf.writeBoolean(!item.isEmpty)
                    if (!item.isEmpty) ItemStack.STREAM_CODEC.encode(buf, item)
                }
            },
            { buf ->
                val purpose = buf.readUtf()
                val count = buf.readVarInt()
                val size = buf.readVarInt().coerceIn(0, 64)
                val items = ArrayList<ItemStack>(size)
                repeat(size) {
                    items.add(if (buf.readBoolean()) ItemStack.STREAM_CODEC.decode(buf) else ItemStack.EMPTY)
                }
                ItemSelectOpenS2C(purpose, items, count)
            },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端提交选中的物品格 (purpose 区分词条) */
class ItemSelectC2S(
    val purpose: String,
    val slots: List<Int>,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ItemSelectC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "item_select"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ItemSelectC2S> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                buf.writeVarInt(value.slots.size)
                for (slot in value.slots) buf.writeVarInt(slot)
            },
            { buf ->
                val purpose = buf.readUtf()
                val size = buf.readVarInt().coerceIn(0, 64)
                val slots = ArrayList<Int>(size)
                repeat(size) { slots.add(buf.readVarInt()) }
                ItemSelectC2S(purpose, slots)
            },
        )
    }

    override fun type() = ID
}

/** 可选中的玩家目标 (uuid 供服务端校验, name 供界面显示) —— 顺手牵羊、引雷术等共用 */
data class PlayerTargetEntry(val uuid: UUID, val name: String)

/** 目标列表的公共编解码 (所有"选一名玩家"类词条共用同一套格式) */
private fun writeTargets(buf: RegistryFriendlyByteBuf, targets: List<PlayerTargetEntry>) {
    buf.writeVarInt(targets.size)
    for (t in targets) {
        buf.writeUUID(t.uuid)
        buf.writeUtf(t.name)
    }
}

private fun readTargets(buf: RegistryFriendlyByteBuf): List<PlayerTargetEntry> {
    val size = buf.readVarInt()
    val list = ArrayList<PlayerTargetEntry>(size)
    repeat(size) { list.add(PlayerTargetEntry(buf.readUUID(), buf.readUtf())) }
    return list
}

/** S2C: 下发"顺手牵羊"的候选目标列表 —— 客户端据此打开玩家选择器 */
class StealTargetsS2C(val targets: List<PlayerTargetEntry>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StealTargetsS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "steal_targets"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, StealTargetsS2C> = StreamCodec.ofMember(
            { value, buf -> writeTargets(buf, value.targets) },
            { buf -> StealTargetsS2C(readTargets(buf)) },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端在玩家选择器中选中了某个目标 */
class StealTargetPickC2S(val uuid: UUID) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StealTargetPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "steal_target_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, StealTargetPickC2S> = StreamCodec.ofMember(
            { value, buf -> buf.writeUUID(value.uuid) },
            { buf -> StealTargetPickC2S(buf.readUUID()) },
        )
    }

    override fun type() = ID
}

/** S2C: 下发"选一名玩家后立即生效"类词条的候选列表 (purpose 区分是哪个词条) */
class ActionTargetsS2C(
    val purpose: String,
    val targets: List<PlayerTargetEntry>,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ActionTargetsS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "action_targets"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ActionTargetsS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                writeTargets(buf, value.targets)
            },
            { buf ->
                val purpose = buf.readUtf()
                ActionTargetsS2C(purpose, readTargets(buf))
            },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端在目标选择器里选中了某个玩家 (带回 purpose 供服务端分派) */
class ActionTargetPickC2S(
    val purpose: String,
    val uuid: UUID,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ActionTargetPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "action_target_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ActionTargetPickC2S> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                buf.writeUUID(value.uuid)
            },
            { buf -> ActionTargetPickC2S(buf.readUtf(), buf.readUUID()) },
        )
    }

    override fun type() = ID
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
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DualTargetsS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "dual_targets"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DualTargetsS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                writeTargets(buf, value.targets)
            },
            { buf -> DualTargetsS2C(buf.readUtf(), readTargets(buf)) },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端在两次选择器里分别选中了两名玩家 (first 是先选的) */
class DualTargetPickC2S(
    val purpose: String,
    val first: UUID,
    val second: UUID,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DualTargetPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "dual_target_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DualTargetPickC2S> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                buf.writeUUID(value.first)
                buf.writeUUID(value.second)
            },
            { buf -> DualTargetPickC2S(buf.readUtf(), buf.readUUID(), buf.readUUID()) },
        )
    }

    override fun type() = ID
}

/**
 * S2C: 打开**装备选择器** (候选固定为 4 个护甲槽 + 副手)。
 *
 * 不下发候选列表 —— 这 5 个槽位是固定的, 客户端自己读自己的装备来显示就够了。
 * purpose 供服务端在回传时分派到具体词条。
 */
class EquipmentPickOpenS2C(val purpose: String) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<EquipmentPickOpenS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "equipment_pick_open"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, EquipmentPickOpenS2C> = StreamCodec.ofMember(
            { value, buf -> buf.writeUtf(value.purpose) },
            { buf -> EquipmentPickOpenS2C(buf.readUtf()) },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端在装备选择器里选中了某个槽位 (slot 用 EquipmentSlot.getName(); 服务端会校验) */
class EquipmentPickC2S(
    val purpose: String,
    val slot: String,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<EquipmentPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "equipment_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, EquipmentPickC2S> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUtf(value.purpose)
                buf.writeUtf(value.slot)
            },
            { buf -> EquipmentPickC2S(buf.readUtf(), buf.readUtf()) },
        )
    }

    override fun type() = ID
}


/** S2C: 通知客户端某个投掷物是"大雪球" (渲染时放大) */
class BigSnowballS2C(val entityId: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<BigSnowballS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "big_snowball"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BigSnowballS2C> = StreamCodec.ofMember(
            { value, buf -> buf.writeVarInt(value.entityId) },
            { buf -> BigSnowballS2C(buf.readVarInt()) },
        )
    }

    override fun type() = ID
}

/** S2C: 同步某个玩家**是否**处于"变猪"状态 ("变群友", 外观、尺寸、视角伪装全部由客户端完成) */
class MorphS2C(
    val playerUuid: UUID,
    val morphed: Boolean,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<MorphS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "morph"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MorphS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeUUID(value.playerUuid)
                buf.writeBoolean(value.morphed)
            },
            { buf -> MorphS2C(buf.readUUID(), buf.readBoolean()) },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端请求二段跳 (服务端验证词条与状态) */
class DoubleJumpC2S : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DoubleJumpC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "double_jump"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DoubleJumpC2S> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> DoubleJumpC2S() },
        )
    }

    override fun type() = ID
}

/** C2S: 客户端请求冲刺 (服务端验证词条与状态) */
class DashC2S : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DashC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "dash"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, DashC2S> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> DashC2S() },
        )
    }

    override fun type() = ID
}

/** S2C: "落地，水"触发 —— 客户端播放"水桶上浮旋转"演出 (参照图腾动画, 物品换成水桶) */
class WaterBucketTotemS2C : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<WaterBucketTotemS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "water_bucket_totem"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WaterBucketTotemS2C> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> WaterBucketTotemS2C() },
        )
    }

    override fun type() = ID
}

/** C2S: 玩家请求打开词条百科 (全部词条 + 已收集标记) */
class PerkCatalogRequestC2S : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkCatalogRequestC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_catalog_request"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkCatalogRequestC2S> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> PerkCatalogRequestC2S() },
        )
    }

    override fun type() = ID
}

/** S2C: 词条百科数据 —— 全部词条定义 + 玩家已收集的 id 集合 */
class PerkCatalogS2C(
    val ownedIds: List<String>,
    val allPerks: List<PerkOptionData>,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PerkCatalogS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "perk_catalog"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PerkCatalogS2C> = StreamCodec.ofMember(
            { value, buf ->
                buf.writeVarInt(value.ownedIds.size)
                for (id in value.ownedIds) buf.writeUtf(id)
                writeCards(buf, value.allPerks)
            },
            { buf ->
                val size = buf.readVarInt()
                val owned = ArrayList<String>(size)
                repeat(size) { owned.add(buf.readUtf()) }
                PerkCatalogS2C(owned, readCards(buf))
            },
        )
    }

    override fun type() = ID
}

/** S2C: 打开天气选择器 (词条「世界一直下雨」) */
class WeatherSelectS2C : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<WeatherSelectS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "weather_select"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WeatherSelectS2C> = StreamCodec.ofMember(
            { _, _ -> },
            { _ -> WeatherSelectS2C() },
        )
    }

    override fun type() = ID
}

/** C2S: 玩家选定的天气 (0 晴 / 1 雨 / 2 雷雨) */
class WeatherPickC2S(val weather: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<WeatherPickC2S>(Identifier.fromNamespaceAndPath("random-perks-bingo", "weather_pick"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WeatherPickC2S> = StreamCodec.ofMember(
            { value, buf -> buf.writeVarInt(value.weather) },
            { buf -> WeatherPickC2S(buf.readVarInt()) },
        )
    }

    override fun type() = ID
}
/** S2C: 上局回顾数据 (整包 JSON, 由 RecapService 生成) */
class RecapOpenS2C(val json: String) : CustomPacketPayload {
    companion object {
        /** JSON 上限: 一局最多几百条事件, 留足余量 */
        private const val MAX_JSON_LENGTH = 1 shl 20

        val ID = CustomPacketPayload.Type<RecapOpenS2C>(Identifier.fromNamespaceAndPath("random-perks-bingo", "recap_open"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RecapOpenS2C> = StreamCodec.ofMember(
            { value, buf -> buf.writeUtf(value.json, MAX_JSON_LENGTH) },
            { buf -> RecapOpenS2C(buf.readUtf(MAX_JSON_LENGTH)) },
        )
    }

    override fun type() = ID
}