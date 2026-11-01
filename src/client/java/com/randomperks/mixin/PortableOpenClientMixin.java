package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "磁力升级" / "便携工作台" 的**客户端预测抑制**。
 *
 * <p>这两个词条原本只在服务端拦 (UseBlockCallback 返回 SUCCESS, 不放置方块)。
 * 但客户端在发包之前会先在 {@code interactBlockInternal} 里跑一遍原版物品使用
 * (BlockItem.place → 本地 setBlockState + 放置音效), 服务端不放置后原版的
 * sequenced 机制再把本地方块回滚 —— 表现就是"先放置一下, 然后被取消"。
 *
 * <p>所以这里在 {@code interactBlockInternal} 的 HEAD 直接返回: 不预放置、不放音效,
 * 但 {@code interactBlock} 里的发包逻辑不受影响 (packet 在同一个 lambda 里构造),
 * 服务端照样会收到 PlayerInteractBlockC2SPacket, 由 UseBlockCallback 打开界面。
 *
 * <p>潜行时不拦 —— 保留原版"放置方块"的行为 (与服务端一致)。
 */
@Mixin(ClientPlayerInteractionManager.class)
public class PortableOpenClientMixin {

    private static final String MAGNET_PERK = "seeker_magnet_upgrade";
    private static final String CRAFTING_PERK = "artisan_portable_crafting";

    @Inject(method = "interactBlockInternal", at = @At("HEAD"), cancellable = true)
    private void randomperks$skipLocalPlacement(ClientPlayerEntity player, Hand hand,
                                                BlockHitResult hitResult,
                                                CallbackInfoReturnable<ActionResult> cir) {
        if (hand != Hand.MAIN_HAND) return;
        if (player.isSneaking()) return;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return;
        if (!randomperks$opensInsteadOfPlacing(stack)) return;

        cir.setReturnValue(ActionResult.SUCCESS);
    }

    /** 这件物品在这名玩家手里是"打开界面"而不是"放置方块" */
    private static boolean randomperks$opensInsteadOfPlacing(ItemStack stack) {
        if (stack.isOf(Items.ENDER_CHEST)) {
            return ClientPerkFlags.has(MAGNET_PERK);
        }
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            return ClientPerkFlags.has(MAGNET_PERK);
        }
        if (stack.isOf(Items.CRAFTING_TABLE)) {
            return ClientPerkFlags.has(CRAFTING_PERK);
        }
        return false;
    }
}
