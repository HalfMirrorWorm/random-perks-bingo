package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "破壁人": 让基岩在"手持能挖黑曜石的镐"时通过 {@code Player.hasCorrectToolForDrops}.
 *
 * <p>原版基岩没有任何正确工具, 即使 {@link BedrockBreakMixin} 让破坏进度能走,
 * {@code ServerPlayerGameMode.destroyBlock} 仍会因为 {@code hasCorrectToolForDrops} 为 false
 * 而不调用 {@code playerDestroy}, 结果就是"能挖掉但不掉东西"。这里把工具门槛对齐黑曜石,
 * 后续由 {@link WallbreakerDropMixin} 补上基岩掉落。
 */
@Mixin(Player.class)
public class WallbreakerHarvestMixin {

    private static final String PERK_ID = "delver_wallbreaker";

    @Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void randomperks$bedrockHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (!state.is(Blocks.BEDROCK)) return;

        Player self = (Player) (Object) this;
        boolean hasPerk;
        if (self.level().isClientSide()) {
            hasPerk = ClientPerkFlags.has(PERK_ID);
        } else {
            hasPerk = self instanceof ServerPlayer serverPlayer
                && PerkBoostQuery.INSTANCE.hasWallbreaker(serverPlayer);
        }
        if (!hasPerk) return;

        // 与黑曜石完全相同的工具等级: 钻石/下界合金镐或等效模组镐
        if (!self.getMainHandItem().isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return;

        cir.setReturnValue(true);
    }
}
