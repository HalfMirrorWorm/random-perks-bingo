package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "村庄英雄": 你的交易不会让村民缺货。
 *
 * <p>原版 {@code TradeOffer.uses} 达到 {@code maxUses} 后该交易项会被标成缺货
 * (界面上是红色的 X), 直到村民补货。这里在每次交易结算之后把使用次数清零,
 * 交易项就永远是"有货"状态。
 *
 * <p>{@link MerchantResultSlot#onTake} 在客户端也会执行 (界面预测), 所以两边都清零 ——
 * 只清服务端的话, 客户端界面上的库存会一路扣到缺货, 和服务端对不上。
 * 客户端没有服务端词条状态, 用 {@link ClientPerkFlags} 的本地镜像判断。
 */
@Mixin(MerchantResultSlot.class)
public abstract class VillageHeroTradeMixin {

    @Shadow
    @Final
    private MerchantContainer slots;

    @Inject(method = "onTake", at = @At("TAIL"))
    private void randomperks$keepTradeStock(Player player, ItemStack stack, CallbackInfo ci) {
        boolean hasPerk;
        if (player.level().isClientSide()) {
            hasPerk = ClientPerkFlags.has("seeker_village_hero");
        } else {
            hasPerk = player instanceof ServerPlayer serverPlayer
                && PerkBoostQuery.INSTANCE.hasVillageHero(serverPlayer);
        }
        if (!hasPerk) return;

        MerchantOffer offer = this.slots.getActiveOffer();
        if (offer != null) {
            offer.resetUses();
        }
    }
}
