package com.randomperks.mixin;

import com.randomperks.game.BoneMealFlowerService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "骨粉撒花"词条: 骨粉催草成功后, 交给 [BoneMealFlowerService] 额外长出随机花。
 */
@Mixin(BoneMealItem.class)
public class BoneMealFlowerMixin {

    @Inject(method = "useOn", at = @At("RETURN"))
    private void randomperks$boneMealFlowers(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (result == null || !result.consumesAction()) return;

        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(context.getLevel() instanceof ServerLevel serverWorld)) return;

        BoneMealFlowerService.INSTANCE.handleUse(serverPlayer, serverWorld, context.getClickedPos());
    }
}
