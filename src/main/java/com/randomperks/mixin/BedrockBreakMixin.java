package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "破壁人" —— 手持能挖黑曜石的镐时, 基岩可以像黑曜石一样挖掉。
 *
 * <p>基岩的 {@code getDestroySpeed} 是 -1, 原版 {@code getDestroyProgress} 遇到 -1 直接返回 0,
 * 破坏进度永远不动。这里在 HEAD 拦下: 对基岩改用**黑曜石**的硬度与工具速度重新算一遍,
 * 于是挖掘手感与黑曜石完全一致 (钻石/下界合金镐约 10 秒一块)。
 *
 * <p>门槛也与黑曜石一致: 手里的工具必须能挖黑曜石 (即钻石/下界合金镐), 否则不生效。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BedrockBreakMixin {

    private static final String PERK_ID = "delver_wallbreaker";

    @Inject(
            method = "getDestroyProgress",
            at = @At("HEAD"),
            cancellable = true
    )
    private void randomperks$breakBedrock(Player player, BlockGetter level, BlockPos pos,
                                          CallbackInfoReturnable<Float> cir) {
        BlockState self = (BlockState) (Object) this;
        if (!self.is(Blocks.BEDROCK)) return;

        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        // 挖不动黑曜石就挖不动基岩 (与原版对黑曜石的等级要求一致)
        if (!player.getMainHandItem().isCorrectToolForDrops(obsidian)) return;

        boolean hasPerk;
        if (player.level().isClientSide()) {
            hasPerk = ClientPerkFlags.has(PERK_ID);
        } else {
            hasPerk = player instanceof ServerPlayer serverPlayer
                    && PerkBoostQuery.INSTANCE.hasWallbreaker(serverPlayer);
        }
        if (!hasPerk) return;

        // 用"挖黑曜石"的速度与硬度来算进度: 工具加速、效率、急迫等都自然生效
        float speed = player.getDestroySpeed(obsidian);
        float hardness = obsidian.getDestroySpeed(level, pos);
        if (hardness <= 0.0f) return;

        cir.setReturnValue(speed / hardness / 30.0F);
    }
}
