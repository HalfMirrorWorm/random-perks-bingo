package com.roguebingo.network

import net.minecraft.item.ItemStack
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/** 一个候选词条 (服务端 → 客户端 展示用) */
data class PerkOptionData(
    val id: String,
    val rarityOrdinal: Int,
    val icon: ItemStack,
)

/** S2C: 通知玩家当前待选机会数量 (用于提示与 HUD 角标) */
class PerkNotifyS2C(val pendingCount: Int) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkNotifyS2C>(Identifier.of("rogue-bingo", "perk_notify"))
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
        val ID = CustomPayload.Id<PerkOfferS2C>(Identifier.of("rogue-bingo", "perk_offer"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkOfferS2C> = PacketCodec.of(
            { value, buf ->
                buf.writeVarInt(value.options.size)
                for (option in value.options) {
                    buf.writeString(option.id)
                    buf.writeVarInt(option.rarityOrdinal)
                    ItemStack.PACKET_CODEC.encode(buf, option.icon)
                }
            },
            { buf ->
                val size = buf.readVarInt()
                val list = ArrayList<PerkOptionData>(size)
                repeat(size) {
                    val id = buf.readString()
                    val rarity = buf.readVarInt()
                    val icon = ItemStack.PACKET_CODEC.decode(buf)
                    list.add(PerkOptionData(id, rarity, icon))
                }
                PerkOfferS2C(list)
            },
        )
    }

    override fun getId() = ID
}

/** C2S: 玩家请求打开三选一 (拉取候选) */
class PerkRequestC2S : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PerkRequestC2S>(Identifier.of("rogue-bingo", "perk_request"))
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
        val ID = CustomPayload.Id<PerkPickC2S>(Identifier.of("rogue-bingo", "perk_pick"))
        val CODEC: PacketCodec<RegistryByteBuf, PerkPickC2S> = PacketCodec.of(
            { value, buf -> buf.writeVarInt(value.index) },
            { buf -> PerkPickC2S(buf.readVarInt()) },
        )
    }

    override fun getId() = ID
}
