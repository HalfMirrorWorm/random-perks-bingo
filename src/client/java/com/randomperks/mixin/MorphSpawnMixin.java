package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 复活会新建 {@code ClientPlayerEntity}。构造时碰撞箱和 {@code standingEyeHeight}
 * 取的是玩家类型的默认值 (眼睛 1.62), 之后没有姿势变化就不会再算。
 * 变猪状态按 UUID 还在, 服务端认为没变化, 也不会再发 {@code MorphS2C}。
 * 相机读的是这份缓存, 所以复活后视角弹回玩家高度。
 *
 * 实体加入客户端世界时如果仍是猪, 立刻重算。别人复活刷出的新实体同样走这里。
 */
@Mixin(ClientWorld.class)
public class MorphSpawnMixin {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void randomperks$refreshPigDimensions(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        if (!MorphState.isMorphed(player.getUuid())) return;
        player.calculateDimensions();
    }
}
