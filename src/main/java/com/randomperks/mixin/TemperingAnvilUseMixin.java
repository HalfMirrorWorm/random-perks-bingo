package com.randomperks.mixin;

import com.randomperks.game.TemperingAnvilService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "千锤百炼" 的一次性铁砧: 第一次取走产物后强制把铁砧打碎 (原版损坏铁砧只有 12% 概率碎裂)。
 */
@Mixin(AnvilMenu.class)
public class TemperingAnvilUseMixin {

    @Inject(method = "onTake", at = @At("TAIL"))
    private void randomperks$breakOneUseAnvil(Player player, ItemStack stack, CallbackInfo ci) {
        ContainerLevelAccess context = ((ForgingScreenHandlerAccessor) (Object) this).getContext();
        context.execute((world, pos) -> {
            if (!(world instanceof ServerLevel serverWorld)) return;
            if (!TemperingAnvilService.INSTANCE.consume(serverWorld, pos)) return;
            if (!serverWorld.getBlockState(pos).is(Blocks.DAMAGED_ANVIL)) return;

            serverWorld.removeBlock(pos, false);
            serverWorld.levelEvent(1029, pos, 0);
        });
    }
}
