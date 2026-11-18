package com.randomperks.mixin;

import com.randomperks.game.NukeExplosionService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * "核电轻而易举": 召唤出的闪电苦力怕爆炸后, 把被炸成空气的方块填成随机矿石。
 *
 * <p>26.3 的爆炸实现是 {@link ServerExplosion}: {@code explode()} 先算爆炸位置列表,
 * 再破坏方块 / 点火。这里在 {@code calculateExplodedPositions} 的 RETURN 缓存列表,
 * 在 {@code explode} 的 TAIL 按列表补矿 —— 只补真正的空气格, 抗爆方块/火/流体都不动。
 *
 * <p>只对带 {@link NukeExplosionService#CREEPER_TAG} 的苦力怕生效, 普通苦力怕不受影响。
 */
@Mixin(ServerExplosion.class)
public abstract class NukeExplosionMixin {

    @Unique
    private List<BlockPos> randomperks$explodedPositions;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
    private void randomperks$capturePositions(CallbackInfoReturnable<List<BlockPos>> cir) {
        this.randomperks$explodedPositions = cir.getReturnValue();
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void randomperks$fillOres(CallbackInfo ci) {
        ServerExplosion self = (ServerExplosion) (Object) this;
        Entity source = self.getDirectSourceEntity();
        if (!NukeExplosionService.INSTANCE.isMarked(source)) return;

        List<BlockPos> positions = this.randomperks$explodedPositions;
        if (positions == null || positions.isEmpty()) return;
        NukeExplosionService.INSTANCE.fillOres(self.level(), positions);
    }
}
