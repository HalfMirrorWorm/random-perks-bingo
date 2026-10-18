package com.randomperks.mixin;

import com.randomperks.client.JockeyClientState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Jockey" 被骑者一侧: 在键盘输入结算完之后, 用骑乘者的输入覆盖掉本地输入。
 *
 * 只改 {@link Input} 的两个状态字段就够了 —— {@code ClientPlayerEntity.tickMovementInput}
 * 正是从 playerInput / movementVector 读前进、横移与跳跃, 之后的移动、碰撞、位置上报
 * 全部走原版流程, 所以被骑者不会出现"瞬移"或反作弊告警。
 */
@Mixin(KeyboardInput.class)
public abstract class JockeyInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void randomperks$applyJockeyInput(CallbackInfo ci) {
        PlayerInput forced = JockeyClientState.forcedInput();
        if (forced == null) return;

        this.playerInput = forced;
        this.movementVector = new Vec2f(
                randomperks$axis(forced.left(), forced.right()),
                randomperks$axis(forced.forward(), forced.backward()));
    }

    /** 与原版 KeyboardInput.getMovementMultiplier 同式 (那边是 private static, 换个名字避开冲突) */
    private static float randomperks$axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }
}
