package com.randomperks.mixin;

import com.randomperks.game.TemperingAnvilService;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "千锤百炼" 的一次性铁砧: 第一次取走产物后强制把铁砧打碎 (原版损坏铁砧只有 12% 概率碎裂)。
 */
@Mixin(AnvilScreenHandler.class)
public class TemperingAnvilUseMixin {

    @Inject(method = "onTakeOutput", at = @At("TAIL"))
    private void randomperks$breakOneUseAnvil(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        ScreenHandlerContext context = ((ForgingScreenHandlerAccessor) (Object) this).getContext();
        context.run((world, pos) -> {
            if (!(world instanceof ServerWorld serverWorld)) return;
            if (!TemperingAnvilService.INSTANCE.consume(serverWorld, pos)) return;
            if (!serverWorld.getBlockState(pos).isOf(Blocks.DAMAGED_ANVIL)) return;

            serverWorld.removeBlock(pos, false);
            serverWorld.syncWorldEvent(1029, pos, 0);
        });
    }
}
