package com.randomperks.mixin;

import com.randomperks.game.BedBombBehavior;
import com.randomperks.game.BedBombContext;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "无忧的安眠" (蓝 · 深掘): 在地狱 / 末地睡床时, 床的爆炸**范围翻倍、伤害 1.5 倍**。
 *
 * <p>原版逻辑在 {@link BedBlock#onUse}: 床不工作的维度里直接
 * {@code world.createExplosion(null, badRespawnPoint, null, pos, 5.0F, true, BLOCK)}。
 * 这里把这次调用换掉:
 * <ul>
 *   <li>威力 5 → 10（爆炸半径 = 威力 × 2，正好翻倍）</li>
 *   <li>传入自定义的 {@link BedBombBehavior}，按原版 power 5 的公式算出伤害后再 ×1.5</li>
 * </ul>
 *
 * <p>是否生效在 {@code onUse} 的 HEAD 处判定并记到 {@link BedBombContext}，
 * 因为 createExplosion 的 redirect 拿不到 onUse 的 player 参数。
 */
@Mixin(BedBlock.class)
public class BedBombMixin {

    /** 原版床爆炸威力 */
    private static final float VANILLA_POWER = 5.0F;

    @Inject(method = "onUse", at = @At("HEAD"))
    private void randomperks$markBedBomb(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                         BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        boolean apply = !world.isClient()
                && !world.getDimension().bedWorks()          // 只有"床会爆炸"的维度
                && player instanceof ServerPlayerEntity serverPlayer
                && PerkBoostQuery.INSTANCE.hasCarefreeSleep(serverPlayer);
        BedBombContext.setActive(apply);
    }

    @Inject(method = "onUse", at = @At("RETURN"))
    private void randomperks$clearBedBomb(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                          BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        BedBombContext.setActive(false);
    }

    @Redirect(
            method = "onUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;Lnet/minecraft/util/math/Vec3d;FZLnet/minecraft/world/World$ExplosionSourceType;)V"
            )
    )
    private void randomperks$biggerBedBomb(World world, Entity entity, DamageSource damageSource,
                                           ExplosionBehavior behavior, Vec3d pos, float power,
                                           boolean createFire, World.ExplosionSourceType sourceType) {
        if (!BedBombContext.getActive()) {
            world.createExplosion(entity, damageSource, behavior, pos, power, createFire, sourceType);
            return;
        }
        // 威力 5 -> 10 (半径 = 威力 × 2, 正好翻倍); 伤害由 BedBombBehavior 按原版公式 ×1.5
        world.createExplosion(entity, damageSource, new BedBombBehavior(), pos,
                VANILLA_POWER * 2.0F, createFire, sourceType);
    }
}
