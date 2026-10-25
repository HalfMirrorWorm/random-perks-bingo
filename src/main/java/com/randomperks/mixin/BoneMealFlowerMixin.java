package com.randomperks.mixin;

import com.randomperks.game.BoneMealFlowerService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "骨粉撒花"词条: 骨粉催草成功后, 交给 [BoneMealFlowerService] 额外长出随机花。
 */
@Mixin(BoneMealItem.class)
public class BoneMealFlowerMixin {

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void randomperks$boneMealFlowers(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = cir.getReturnValue();
        if (result == null || !result.isAccepted()) return;

        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (!(context.getWorld() instanceof ServerWorld serverWorld)) return;

        BoneMealFlowerService.INSTANCE.handleUse(serverPlayer, serverWorld, context.getBlockPos());
    }
}
