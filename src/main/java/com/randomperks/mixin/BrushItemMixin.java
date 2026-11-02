package com.randomperks.mixin;

import com.randomperks.game.WarmArchaeologyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "暖水考古": 让刷子也能刷珊瑚块, 并且和**刷可疑沙一样需要时间**。
 *
 * <p>原版 {@link BrushItem#onUseTick} 每 10 tick 触发一次"刷动"(播放音效 + 碎屑粒子),
 * 但只有方块带 {@code BrushableBlockEntity} 时才会真正结算。珊瑚块没有方块实体,
 * 所以在同一个节奏点上调用 [WarmArchaeologyService.handleBrushTick] 自己计数:
 * 刷满 10 次才变成失活珊瑚并掉落战利品。
 *
 * <p>音效与粒子完全交给原版 —— {@code usageTick} 对任何有破坏粒子的方块都会播放。
 */
@Mixin(BrushItem.class)
public class BrushItemMixin {

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void randomperks$coralBrush(Level world, LivingEntity user, ItemStack stack,
                                        int remainingUseTicks, CallbackInfo ci) {
        if (world.isClientSide()) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!(world instanceof ServerLevel serverWorld)) return;

        // 原版同款: 沿视线找玩家正在刷的方块
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, EntitySelector.CAN_BE_PICKED,
                player.blockInteractionRange());
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        // 与原版一致的刷动节奏: 每 10 tick 一次
        int i = ((Item) (Object) this).getUseDuration(stack, user) - remainingUseTicks + 1;
        if (i % 10 != 5) return;

        WarmArchaeologyService.INSTANCE.handleBrushTick(
                player, serverWorld, blockHit.getBlockPos(), stack);
    }
}
