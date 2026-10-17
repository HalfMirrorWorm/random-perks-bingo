package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "老玩家": 手持剑时攻击冷却**始终视为已蓄满** —— 1.9 战斗更新前的手感 (连点即满伤害)。
 *
 * 注入 PlayerEntity.getAttackCooldownProgress: 这个值决定伤害缩放 (0.2 + f²×0.8)、
 * 横扫判定 (f > 0.9) 与暴击判定。恒返回 1.0 后, 每一击都按满伤害结算。
 *
 * 服务端决定实际伤害; 客户端决定准星/冷却条显示, 所以两边都要放行。
 */
@Mixin(PlayerEntity.class)
public class OldSchoolCombatMixin {

    private static final String VETERAN_PERK = "hunter_veteran";

    @Inject(method = "getAttackCooldownProgress", at = @At("HEAD"), cancellable = true)
    private void randomperks$alwaysCharged(float baseTime, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!self.getMainHandStack().isIn(ItemTags.SWORDS)) return;

        if (self.getWorld().isClient()) {
            if (ClientPerkFlags.has(VETERAN_PERK)) cir.setReturnValue(1.0F);
            return;
        }
        if (self instanceof ServerPlayerEntity player && PerkBoostQuery.INSTANCE.hasOldSchoolCombat(player)) {
            cir.setReturnValue(1.0F);
        }
    }
}
