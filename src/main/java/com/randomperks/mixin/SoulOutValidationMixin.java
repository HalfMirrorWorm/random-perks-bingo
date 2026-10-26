package com.randomperks.mixin;

import com.randomperks.game.SoulOutService;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "灵魂出鞘"的服务端移动校验放行。
 *
 * <p>客户端会把水平移动按 noClip 处理, 玩家因此能进入方块内部; 但服务端
 * {@code onPlayerMove} 里还有一道 "移动后的碰撞箱进入了之前没接触过的方块" 校验,
 * 触发时会把玩家 requestTeleport 回移动前的位置 —— 表现就是 "刚穿进墙就被弹出来"。
 *
 * <p>这里对处于灵魂状态的玩家把这项检查视为 "没有撞到方块", 让服务端接受穿墙后的坐标。
 */
@Mixin(ServerPlayNetworkHandler.class)
public class SoulOutValidationMixin {

    @Redirect(
            method = "onPlayerMove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;"
                            + "isEntityNotCollidingWithBlocks("
                            + "Lnet/minecraft/world/WorldView;"
                            + "Lnet/minecraft/entity/Entity;"
                            + "Lnet/minecraft/util/math/Box;"
                            + "DDD)Z"
            )
    )
    private boolean randomperks$allowSoulOutThroughBlocks(
            // @Redirect 的目标方法是非静态方法, 处理器必须在参数 0 接收调用者实例
            ServerPlayNetworkHandler handler,
            WorldView world,
            Entity entity,
            Box box,
            double newX,
            double newY,
            double newZ
    ) {
        if (entity instanceof ServerPlayerEntity player
                && SoulOutService.INSTANCE.isSoul(player)) {
            return false;
        }
        return ((SoulOutValidationInvoker) (Object) handler)
                .randomperks$invokeIsEntityNotCollidingWithBlocks(world, entity, box, newX, newY, newZ);
    }
}
