package com.randomperks.mixin;

import com.randomperks.game.TemperingAnvilService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "千锤百炼" 的一次性铁砧: 记录放置坐标; 未使用就被挖掉时把标记转移到掉落物上。
 */
@Mixin(Block.class)
public class TemperingAnvilBlockMixin {

    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void randomperks$markPlacedAnvil(
            Level world,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack itemStack,
            CallbackInfo ci
    ) {
        if (!(world instanceof ServerLevel serverWorld)) return;
        if (!state.is(Blocks.DAMAGED_ANVIL)) return;
        if (!TemperingAnvilService.INSTANCE.isMarked(itemStack)) return;

        TemperingAnvilService.INSTANCE.markPlaced(serverWorld, pos);
    }

    @Inject(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private static void randomperks$preserveAnvilMarker(
            BlockState state,
            ServerLevel world,
            BlockPos pos,
            BlockEntity blockEntity,
            Entity entity,
            ItemInstance tool,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        if (!state.is(Blocks.DAMAGED_ANVIL)) return;
        if (!TemperingAnvilService.INSTANCE.consume(world, pos)) return;

        for (ItemStack drop : cir.getReturnValue()) {
            if (drop.is(Items.DAMAGED_ANVIL)) {
                TemperingAnvilService.INSTANCE.markItem(drop);
                return;
            }
        }
    }
}
