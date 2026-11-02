package com.randomperks.mixin;

import com.randomperks.game.BedBombBehavior;
import com.randomperks.game.BedBombContext;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "无忧的安眠" (蓝 · 深掘): 在地狱 / 末地睡床时, 床的爆炸**范围翻倍、伤害 1.5 倍**。
 *
 * <p>26.1 / 26.2 的爆炸仍在 {@code useWithoutItem} 里:
 * {@code world.explode(..., 5.0F, true, BLOCK)}。这里把这次调用换掉:
 * <ul>
 *   <li>威力 5 → 10（爆炸半径 = 威力 × 2，正好翻倍）</li>
 *   <li>传入自定义的 {@link BedBombBehavior}，按原版 power 5 的公式算出伤害后再 ×1.5</li>
 * </ul>
 *
 * <p>是否生效在 {@code useWithoutItem} 的 HEAD 处判定并记到 {@link BedBombContext}，
 * 因为 explode 的 redirect 拿不到 player 参数。
 */
@Mixin(BedBlock.class)
public class BedBombMixin {

    /** 原版床爆炸威力 */
    private static final float VANILLA_POWER = 5.0F;

    @Inject(method = "useWithoutItem", at = @At("HEAD"))
    private void randomperks$markBedBomb(BlockState state, Level world, BlockPos pos, Player player,
                                         BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        boolean apply = !world.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && PerkBoostQuery.INSTANCE.hasCarefreeSleep(serverPlayer);
        BedBombContext.setActive(apply);
    }

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void randomperks$clearBedBomb(BlockState state, Level world, BlockPos pos, Player player,
                                          BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        BedBombContext.setActive(false);
    }

    @Redirect(
            method = "useWithoutItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;Lnet/minecraft/world/phys/Vec3;FZLnet/minecraft/world/level/Level$ExplosionInteraction;)V"
            )
    )
    private void randomperks$biggerBedBomb(Level world, Entity entity, DamageSource damageSource,
                                           ExplosionDamageCalculator behavior, Vec3 pos, float power,
                                           boolean createFire, Level.ExplosionInteraction sourceType) {
        if (!BedBombContext.getActive()) {
            world.explode(entity, damageSource, behavior, pos, power, createFire, sourceType);
            return;
        }
        // 威力 5 -> 10 (半径 = 威力 × 2, 正好翻倍); 伤害由 BedBombBehavior 按原版公式 ×1.5
        world.explode(entity, damageSource, new BedBombBehavior(), pos,
                VANILLA_POWER * 2.0F, createFire, sourceType);
    }
}
