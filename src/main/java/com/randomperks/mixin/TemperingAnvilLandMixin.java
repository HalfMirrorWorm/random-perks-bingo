package com.randomperks.mixin;

import com.randomperks.game.TemperingAnvilService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "千锤百炼" 的一次性铁砧：落地时把下落实体上的标记转到落点坐标。
 *
 * <p>落地流程是 {@code setBlockAndUpdate(blockPosition(), blockState)} 之后 {@code discard()}，
 * 所以在 tick 结尾检查"已被移除 + 带标记 + 落点确实是损坏的铁砧"即可。
 */
@Mixin(FallingBlockEntity.class)
public class TemperingAnvilLandMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void randomperks$markLandedAnvil(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) return;
        if (!self.isRemoved()) return;
        if (!TemperingAnvilService.INSTANCE.isMarkedFalling(self)) return;

        BlockPos pos = self.blockPosition();
        if (!level.getBlockState(pos).is(Blocks.DAMAGED_ANVIL)) return;
        TemperingAnvilService.INSTANCE.markPlaced(level, pos);
    }
}
