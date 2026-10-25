package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "天启日" (神话 · 猎杀)。
 *
 * <p>新规则:
 * <ul>
 *   <li>效果持续 20 分钟, 被敌方玩家击杀时立即结束;</li>
 *   <li>期间被持有者击杀的前 4 名玩家, 强制掉落**背包位**物品;</li>
 *   <li>持有者自己只有被敌方玩家击杀时才会强制掉落背包位物品 (随后效果结束);</li>
 *   <li>"背包位" = 主背包 27 格 (快捷栏 0~8 与护甲/副手不掉);</li>
 *   <li>仍然只在 keepInventory 开启时干预 (关闭时原版本来就会掉落)。</li>
 * </ul>
 *
 * <p>注入 {@code onDeath} 而不是 dropInventory: 致命一击的伤害源只存在于 onDeath 入参里
 * (原版 {@code damage()} 在调用 onDeath **之后**才写 lastDamageSource)。
 */
@Mixin(ServerPlayerEntity.class)
public class DoomsdayMixin {

    /** 背包位: 主背包 27 格 (跳过快捷栏 0~8) */
    private static final int BACKPACK_START = 9;
    private static final int BACKPACK_END = 36;

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void randomperks$onDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (self.isSpectator()) return;

        ServerPlayerEntity killer = source.getAttacker() instanceof ServerPlayerEntity player
                && player != self ? player : null;
        boolean enemyKill = killer != null && !isSameTeam(self, killer);
        boolean keepInventory = self.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);

        // 自己持有天启日: 只有被敌方玩家击杀才掉落, 且这次死亡结束效果
        if (PerkBoostQuery.INSTANCE.hasDoomsdayEffect(self)) {
            if (enemyKill) {
                if (keepInventory) dropBackpack(self);
                PerkBoostQuery.INSTANCE.endDoomsday(self);
            }
            return;
        }

        // 被仍处于效果中的天启日持有者击杀
        if (enemyKill && keepInventory
                && PerkBoostQuery.INSTANCE.hasDoomsdayEffect(killer)
                && PerkBoostQuery.INSTANCE.consumeDoomsdayKill(killer)) {
            dropBackpack(self);
        }
    }

    /** 强制掉落主背包 27 格; 消失诅咒物品按原版规则直接消失 */
    private static void dropBackpack(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = BACKPACK_START; slot < BACKPACK_END; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;

            if (EnchantmentHelper.hasAnyEnchantmentsWith(
                    stack, EnchantmentEffectComponentTypes.PREVENT_EQUIPMENT_DROP)) {
                inventory.setStack(slot, ItemStack.EMPTY);
                continue;
            }

            player.dropItem(stack, true, false);
            inventory.setStack(slot, ItemStack.EMPTY);
        }
    }

    private static boolean isSameTeam(ServerPlayerEntity a, ServerPlayerEntity b) {
        Team teamA = a.getScoreboardTeam();
        Team teamB = b.getScoreboardTeam();
        if (teamA == null || teamB == null) return teamA == teamB;
        return teamA.getName().equals(teamB.getName());
    }
}
