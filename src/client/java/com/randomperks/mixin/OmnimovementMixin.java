package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "全向移动": 八个方向都能疾跑。
 *
 * <p>原版疾跑状态是**客户端权威**的 (服务端只在收到 ClientCommandC2SPacket 时跟着
 * setSprinting), 而客户端三个判定里唯一限制方向的就是 {@code input.hasForwardMovement()}:
 *
 * <pre>
 *   canStartSprinting()      → !isSprinting() &amp;&amp; input.hasForwardMovement() &amp;&amp; canSprint() &amp;&amp; ...
 *   shouldStopSprinting()    → ... || !input.hasForwardMovement() || ...
 *   shouldStopSwimSprinting()→ ... || !input.hasForwardMovement() &amp;&amp; ... || ...
 * </pre>
 *
 * 也就是说 W/A/S/D 本身同速, 卡住的只是"只有前进才能进入/保持疾跑"。
 *
 * <p>持词条时把这三处的判断换成"任意方向有输入"(等价于原版私有方法 hasMovementInput),
 * 于是侧移、后退、四个斜向都能跑起来; 松开所有方向键时仍然照常停下。
 */
@Mixin(ClientPlayerEntity.class)
public class OmnimovementMixin {

    private static final String PERK_ID = "swift_omnimovement";

    @Redirect(
            method = "canStartSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean randomperks$startInAnyDirection(Input input) {
        return randomperks$hasMovement(input);
    }

    @Redirect(
            method = "shouldStopSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean randomperks$keepInAnyDirection(Input input) {
        return randomperks$hasMovement(input);
    }

    @Redirect(
            method = "shouldStopSwimSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean randomperks$keepInAnyDirectionSwim(Input input) {
        return randomperks$hasMovement(input);
    }

    /** 持词条: 任意方向有输入就算"在移动"; 否则保持原版"只有前进算移动"。 */
    private static boolean randomperks$hasMovement(Input input) {
        if (!ClientPerkFlags.has(PERK_ID)) return input.hasForwardMovement();
        return input.getMovementInput().lengthSquared() > 0.0F;
    }
}
