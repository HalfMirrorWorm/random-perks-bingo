package com.randomperks.mixin;

import com.randomperks.game.PerkWorldEventService;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "饱食度回满"事件 (当前 Fabric API 没有饥饿值变化事件, 直接注入原版 HungerManager.update)。
 *
 * update 是原版每 tick 结算饥饿值的地方, 其中"饱和回血"分支用的"满饱食度"判定就是 foodLevel >= 20。
 * 这里沿用同一标准, 只抓"上一 tick 不满 → 这一 tick 满"的那一次跳变:
 * 每 tick 都在看 (不会漏), 且只在跳变时发 (不会重复触发)。
 * 相比"每秒轮询 + 标志位", 既没有 1 秒的采样盲区, 也不占用词条状态表。
 */
@Mixin(HungerManager.class)
public class HungerRefillMixin {

    @Shadow
    private int foodLevel;

    /** 本次 update 之前的饱食度 (只在同一次调用内比较, 不持久化) */
    @Unique
    private int randomperks$prevFoodLevel;

    @Inject(method = "update(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void randomperks$captureFoodLevel(ServerPlayerEntity player, CallbackInfo ci) {
        this.randomperks$prevFoodLevel = this.foodLevel;
    }

    @Inject(method = "update(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("TAIL"))
    private void randomperks$onFoodLevelRefilled(ServerPlayerEntity player, CallbackInfo ci) {
        if (this.randomperks$prevFoodLevel >= 20) return;   // 本来就满 —— 不是"回满"
        if (this.foodLevel < 20) return;                    // 加了但还没到满
        PerkWorldEventService.INSTANCE.dispatchHungerRefilled(player);
    }
}
