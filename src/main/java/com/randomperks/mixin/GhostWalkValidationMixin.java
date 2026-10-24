package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "幽灵漫步"的服务端放行。
 *
 * <p>客户端与本 mod 的 {@link PhaseWalkMixin} 会把水平移动按 noClip 处理, 玩家因此能进入
 * 方块内部; 但服务端在 {@code ServerPlayNetworkHandler.onPlayerMove} 里还有一道
 * "移动校验": 只要玩家移动后的碰撞箱进入了一个**之前没接触过的方块**
 * ({@code isEntityNotCollidingWithBlocks} 返回 true), 且不是旁观/创造模式,
 * 就会走 {@code requestTeleport} 把玩家送回移动前的位置 —— 表现出来就是
 * "刚穿进墙里就被弹出来"。因为位置差通常很小, 这一步还不会打印 "moved wrongly" 日志。
 *
 * <p>这里对持有该词条的玩家把这项检查视为 "没有撞到方块", 让服务端接受穿墙后的坐标。
 * 正常的方块碰撞仍由客户端与 {@link PhaseWalkMixin} 的垂直/水平拆分负责。
 */
@Mixin(ServerPlayNetworkHandler.class)
public class GhostWalkValidationMixin {

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
    private boolean randomperks$allowGhostWalkThroughBlocks(
            WorldView world,
            Entity entity,
            Box box,
            double newX,
            double newY,
            double newZ
    ) {
        if (entity instanceof ServerPlayerEntity player
                && PerkBoostQuery.INSTANCE.hasGhostWalk(player)) {
            return false;
        }
        // 没有词条: 走原版逻辑
        return ((GhostWalkValidationInvoker) (Object) this)
                .randomperks$invokeIsEntityNotCollidingWithBlocks(world, entity, box, newX, newY, newZ);
    }
}
