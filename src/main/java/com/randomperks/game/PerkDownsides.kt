package com.randomperks.game

import com.randomperks.perk.PerkContext
import com.randomperks.perk.content.ArtisanPerks
import com.randomperks.perk.content.DelverPerks
import com.randomperks.state.PlayerPerkState
import net.minecraft.server.level.ServerPlayer

/**
 * 把"净化"的抑制状态同步到各个词条的实际效果上。
 *
 * <p>状态类/属性类代价在选中或取消抑制时立刻重算; 行为类代价
 * (不横扫 / 陆地不能疾跑) 由对应 mixin 动态读取抑制状态。
 */
object PerkDownsides {

    fun refresh(player: ServerPlayer, state: PlayerPerkState) {
        if (state.hasPerk("artisan_nya_axe")) {
            ArtisanPerks.NyaAxe.refresh(player, state)
        }
        if (state.hasPerk("seeker_lay_flat")) {
            LayFlatService.refreshSuppression(player)
        }
        if (state.hasPerk("delver_collector")) {
            DelverPerks.Collector.refresh(player, state)
        }
        if (state.hasPerk("delver_gluttonous_snake")) {
            DelverPerks.GluttonousSnake.refresh(PerkContext(player, state))
        }
    }
}
