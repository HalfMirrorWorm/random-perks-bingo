package com.randomperks.network

import net.minecraft.item.ItemStack
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

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

/** S2C: 下发玩家已获得的词条列表 (附当前待选数) */
class PerkListS2C(
    val pendingCount: Int,
    val perks: List<PerkOptionData>,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkListS2C>(Identifier.of("random-perks-bingo", "perk_list"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkListS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.pendingCount)
                writeCards(buf, value.perks)
            },
            { buf ->
                val pending = buf.readVarInt()
                PerkListS2C(pending, readCards(buf))
            },
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
