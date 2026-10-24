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
 * <p>幽灵漫步需要 "放行持有词条的玩家, 其余人走原版检查", 所以必须能调用原方法。
 * 本项目没有 refmap, 直接用 @Shadow 引用目标类自身的成员虽然可行, 但这里用 @Invoker
 * 与其他 accessor 保持一致。
 */
@Mixin(ServerPlayNetworkHandler.class)
public interface GhostWalkValidationInvoker {

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
