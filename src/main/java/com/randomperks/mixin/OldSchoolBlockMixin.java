package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "老玩家" 的剑格挡 —— 1.9 战斗更新以前的右键举剑。
 *
 * 注入的是 {@link Item} 的**默认实现**: 剑 (SwordItem) 没有 override 这三个方法, 所以会走到;
 * 其他自己实现了 use 的物品走自己的逻辑, 不受影响。
 *
 * 三件事:
 * <ul>
 *   <li>{@code use} —— 持有词条的玩家右键剑时返回 consume, 进入"使用中"状态;</li>
 *   <li>{@code getMaxUseTime} —— 给足时长 (72000 tick), 松手才结束;</li>
 *   <li>{@code getUseAction} —— BLOCK: 第三人称会摆格挡姿势, {@code PlayerEntity.isBlocking()}
 *       也为 true。这个不按词条判断 —— 没进"使用中"状态的玩家根本走不到它。</li>
 * </ul>
 * 减伤在 LivingEntityDamageMixin 里 (受到的伤害减半)。
 */
@Mixin(Item.class)
public class OldSchoolBlockMixin {

    private static final int BLOCK_USE_TICKS = 72000;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void randomperks$startSwordBlock(World world, PlayerEntity user, Hand hand,
                                             CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (!stack.isIn(ItemTags.SWORDS)) return;
        if (!hasVeteran(user)) return;

        // CONSUME = 使用成功但不挥手臂 —— 右键举剑不该有挥手动作
        cir.setReturnValue(ActionResult.CONSUME);
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void randomperks$swordBlockDuration(ItemStack stack, LivingEntity user,
                                                CallbackInfoReturnable<Integer> cir) {
        if (stack.isIn(ItemTags.SWORDS)) {
            cir.setReturnValue(BLOCK_USE_TICKS);
        }
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void randomperks$swordBlockAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (stack.isIn(ItemTags.SWORDS)) {
            cir.setReturnValue(UseAction.BLOCK);
        }
    }

    /** 服务端问词条状态, 客户端问同步下来的标记 (客户端要本地预测"开始使用") */
    private static boolean hasVeteran(PlayerEntity user) {
        if (user.getWorld().isClient()) {
            return ClientPerkFlags.has("hunter_veteran");
        }
        return user instanceof ServerPlayerEntity player
                && PerkBoostQuery.INSTANCE.hasOldSchoolCombat(player);
    }
}
