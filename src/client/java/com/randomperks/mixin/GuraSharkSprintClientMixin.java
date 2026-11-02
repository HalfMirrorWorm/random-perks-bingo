package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "古拉鲨鱼": 从水里走上陆地时, 把残留的疾跑状态主动停掉。
 *
 * <p>原版只在"能开始疾跑"时调 setSprinting(true), 保持疾跑期间不会再调 ——
 * 玩家在水里疾跑上岸后, 标记仍然是 true, 光靠 GuraSharkSprintMixin 拦
 * setSprinting(true) 停不下来。这里放在 tickMovement 开头 (早于本 tick 的
 * 疾跑判定和实体移动), 清掉之后同 tick 内即使再次尝试开跑也会被拦下。
 */
@Mixin(LocalPlayer.class)
public class GuraSharkSprintClientMixin {

    private static final String PERK_ID = "swift_gura_shark";

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void randomperks$stopLandSprint(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!player.isSprinting()) return;
        if (player.isInWater() || player.getAbilities().flying) return;
        if (!ClientPerkFlags.has(PERK_ID)) return;
        player.setSprinting(false);
    }
}
