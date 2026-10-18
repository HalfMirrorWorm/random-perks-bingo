package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "Jockey": 让持词条的玩家能把**其他玩家**当载具骑上去。
 *
 * <p>原版有两道闸拦着"骑玩家", 缺一不可:
 *
 * <ol>
 *   <li>{@code RideCommand.executeMount} 里对玩家载具直接抛
 *       {@code commands.ride.mount.failure.cant_ride_players} —— 所以
 *       "1.20.2 的 /ride 就是这么干的"是错的, 原版命令根本不让你骑玩家;</li>
 *   <li>{@code Entity.startRiding(Entity, boolean)} 的第二道判断
 *       {@code !this.getWorld().isClient() && !entity.type.isSaveable()} ——
 *       {@code EntityType.PLAYER} 是用 {@code .disableSaving()} 注册的 (saveable = false),
 *       于是服务端**永远**在这里返回 false。</li>
 * </ol>
 *
 * <p>要命的是第二道闸排在 {@code force} 判定**之前**:
 *
 * <pre>
 *   if (entity == this.vehicle) return false;
 *   else if (!entity.couldAcceptPassenger()) return false;
 *   else if (!isClient &amp;&amp; !entity.type.isSaveable()) return false;   // ← 这里, force 管不着
 *   else { ... if (force || ...) { 真正挂上去 } }
 * </pre>
 *
 * 所以 {@code startRiding(target, true)} 必然失败, JockeyService 原来就栽在这。
 *
 * <p>这里只放行"骑乘者是持 Jockey 的玩家 + 载具类型是玩家"这一种组合, 其余实体保持原判。
 */
@Mixin(Entity.class)
public class JockeyRideMixin {

    @Redirect(
            method = "startRiding(Lnet/minecraft/entity/Entity;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityType;isSaveable()Z"))
    private boolean randomperks$allowPlayerVehicle(EntityType<?> type) {
        if (type == EntityType.PLAYER) {
            Entity self = (Entity) (Object) this;
            if (self instanceof ServerPlayerEntity rider && PerkBoostQuery.INSTANCE.hasJockey(rider)) {
                return true;
            }
        }
        return type.isSaveable();
    }
}
