package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.UseEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "边走边用" (客户端): 使用物品时不再减速。
 *
 * <p>26.3 的使用物品减速在客户端 {@code LocalPlayer} 里:
 * <ul>
 *   <li>{@code modifyInput} 在 {@code isUsingItem() && !isPassenger()} 时把输入向量乘
 *       {@code itemUseSpeedMultiplier()} (默认 0.2);</li>
 *   <li>{@code isSlowDueToUsingItem()} 还会在 {@code canStartSprinting()} 里禁止疾跑。</li>
 * </ul>
 * 持有该词条时把倍率换成 1、并让 {@code isSlowDueToUsingItem} 返回 false;
 * 潜行、0.98 输入缩放、方形移动修正等其他原版逻辑保持不变。
 */
@Mixin(LocalPlayer.class)
public abstract class UseItemSpeedClientMixin {

    private static final String PERK_ID = "swift_use_on_move";

    @Redirect(
            method = "modifyInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F"
            )
    )
    private static float randomperks$noUseItemSlowdown(LocalPlayer self) {
        if (ClientPerkFlags.has(PERK_ID)) return 1.0f;
        // 与原版 itemUseSpeedMultiplier() 等价: 读取当前使用物品的 UseEffects
        return self.getUseItem()
                .getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT)
                .speedMultiplier();
    }

    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void randomperks$allowSprintWhileUsing(CallbackInfoReturnable<Boolean> cir) {
        if (ClientPerkFlags.has(PERK_ID)) cir.setReturnValue(false);
    }
}
