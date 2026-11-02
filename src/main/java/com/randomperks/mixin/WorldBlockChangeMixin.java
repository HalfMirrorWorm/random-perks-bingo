package com.randomperks.mixin;

import com.randomperks.game.RewindService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「逆转时间」的地形变更日志。
 *
 * <p>服务端的方块变化 (放置/破坏/爆炸/活塞/流体/作物/火焰......) 最终都会调用
 * {@link Level#setBlock(BlockPos, BlockState, int, int)}。这里只记录每个坐标
 * **第一次**被写入前的旧状态, 回溯时把它写回去即可。
 *
 * <p>刻意不记录区块生成: {@code ChunkRegion} 走的是自己的 setBlockState,
 * 不经过本方法, 所以新生成的地形不会被当成"玩家改动"。
 */
@Mixin(Level.class)
public abstract class WorldBlockChangeMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void randomperks$recordBlockChange(
            BlockPos pos,
            BlockState state,
            int flags,
            int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!((Object) this instanceof ServerLevel world)) return;
        if (!RewindService.INSTANCE.shouldRecordBlockChange()) return;

        BlockState previous = world.getBlockState(pos);
        if (previous != state) {
            RewindService.INSTANCE.recordBlockChange(world, pos, previous);
        }
    }
}
