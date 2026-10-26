package com.randomperks.mixin;

import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出窍": 灵魂状态下把相机从身体上解绑, 使用 [SoulCameraClient] 维护的坐标与视角。
 * 身体实体仍然留在原地 (其他玩家看到的也是站着的身体)。
 *
 * <p>必须调用 {@code setPos} / {@code setRotation} 而不是直接改字段 ——
 * 相机朝向的四元数、水平/垂直平面都是在 setRotation 里一起算好的。
 */
@Mixin(Camera.class)
public abstract class SoulCameraMixin {

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    private boolean thirdPerson;

    @Inject(method = "update", at = @At("TAIL"))
    private void randomperks$applySoulCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                             boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!SoulCameraClient.active) return;

        SoulCameraClient.INSTANCE.updatePerFrame();

        this.setPos(SoulCameraClient.pos);
        this.setRotation(SoulCameraClient.yaw, SoulCameraClient.pitch);
        // 让 WorldRenderer 把"相机实体"(也就是留在原地的身体)照常渲染出来;
        // 第一人称手部由 SoulHandMixin 另外取消 (灵魂没有手)
        this.thirdPerson = true;
    }
}
