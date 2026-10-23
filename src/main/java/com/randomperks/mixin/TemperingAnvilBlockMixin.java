package com.randomperks.mixin;

import com.randomperks.game.TemperingAnvilService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * "千锤百炼" 的一次性铁砧: 记录放置坐标; 未使用就被挖掉时把标记转移到掉落物上。
 */
@Mixin(Block.class)
public class TemperingAnvilBlockMixin {

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void randomperks$markPlacedAnvil(
            World world,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack itemStack,
            CallbackInfo ci
    ) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!state.isOf(Blocks.DAMAGED_ANVIL)) return;
        if (!TemperingAnvilService.INSTANCE.isMarked(itemStack)) return;

        TemperingAnvilService.INSTANCE.markPlaced(serverWorld, pos);
    }

    @Inject(
            method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private static void randomperks$preserveAnvilMarker(
            BlockState state,
            ServerWorld world,
            BlockPos pos,
            BlockEntity blockEntity,
            Entity entity,
            ItemStack tool,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        if (!state.isOf(Blocks.DAMAGED_ANVIL)) return;
        if (!TemperingAnvilService.INSTANCE.consume(world, pos)) return;

        for (ItemStack drop : cir.getReturnValue()) {
            if (drop.isOf(Items.DAMAGED_ANVIL)) {
                TemperingAnvilService.INSTANCE.markItem(drop);
                return;
            }
        }
    }
}
