package com.randomperks.mixin;

import com.randomperks.game.TemperingAnvilService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "千锤百炼" 的一次性铁砧：开始下落时把放置点的标记转移到下落实体上。
 *
 * <p>铁砧继承 {@link net.minecraft.world.level.block.FallingBlock}，下落时原位置由
 * {@code setBlockAndUpdate} 换成空气 —— 不走 {@code setPlacedBy}，也不走 {@code getDrops}。
 * 不转移的话旧坐标留下一个永远消耗不掉的标记，落地后的铁砧按原版 12% 概率碎裂。
 */
@Mixin(AnvilBlock.class)
public class TemperingAnvilFallMixin {

    @Inject(method = "falling", at = @At("TAIL"))
    private void randomperks$carryMarkerToFallingAnvil(FallingBlockEntity entity, CallbackInfo ci) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.getBlockState().is(Blocks.DAMAGED_ANVIL)) return;

        BlockPos pos = entity.blockPosition();
        if (!TemperingAnvilService.INSTANCE.consume(level, pos)) return;
        TemperingAnvilService.INSTANCE.markFalling(entity);
    }
}
