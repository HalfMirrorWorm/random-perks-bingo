package com.randomperks.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.Box;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code ServerPlayNetworkHandler.isEntityNotCollidingWithBlocks} (private)。
 *
 * <p>"灵魂出鞘"需要 "放行处于灵魂状态的玩家, 其余人走原版检查", 所以必须能调用原方法。
 */
@Mixin(ServerPlayNetworkHandler.class)
public interface SoulOutValidationInvoker {

    @Invoker("isEntityNotCollidingWithBlocks")
    boolean randomperks$invokeIsEntityNotCollidingWithBlocks(
            WorldView world,
            Entity entity,
            Box box,
            double newX,
            double newY,
            double newZ
    );
}
