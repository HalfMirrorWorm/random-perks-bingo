package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.util.Prediction;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "天启日" (红 · 猎杀)。
 *
 * <p>新规则:
 * <ul>
 *   <li>效果持续 20 分钟, 被敌方玩家击杀时立即结束;</li>
 *   <li>期间被持有者击杀的前 4 名**敌方玩家**, 强制掉落**背包位**物品 (队友不掉、不计数);</li>
 *   <li>持有者自己只有被敌方玩家击杀时才会强制掉落背包位物品 (随后效果结束);</li>
 *   <li>"背包位" = 主背包 27 格 (快捷栏 0~8 与护甲/副手不掉);</li>
 *   <li>仍然只在 keepInventory 开启时干预 (关闭时原版本来就会掉落)。</li>
 * </ul>
 *
 * <p>注入 {@code onDeath} 而不是 dropInventory: 致命一击的伤害源只存在于 onDeath 入参里
 * (原版 {@code damage()} 在调用 onDeath **之后**才写 lastDamageSource)。
 */
@Mixin(ServerPlayer.class)
public class DoomsdayMixin {

    /** 背包位: 主背包 27 格 (跳过快捷栏 0~8) */
    private static final int BACKPACK_START = 9;
    private static final int BACKPACK_END = 36;

    @Inject(method = "die", at = @At("HEAD"))
    private void randomperks$onDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isSpectator()) return;

        ServerPlayer killer = source.getEntity() instanceof ServerPlayer player
                && player != self ? player : null;
        boolean enemyKill = killer != null && !isSameTeam(self, killer);
        boolean keepInventory = self.level().getGameRules().get(GameRules.KEEP_INVENTORY);

        // 自己持有天启日: 只有被敌方玩家击杀才掉落, 且这次死亡结束效果
        if (PerkBoostQuery.INSTANCE.hasDoomsdayEffect(self)) {
            if (enemyKill) {
                if (keepInventory) dropBackpack(self);
                PerkBoostQuery.INSTANCE.endDoomsday(self);
            }
            return;
        }

        // 被仍处于效果中的天启日持有者击杀: 只对**敌方玩家**生效,
        // 击杀队友不掉背包、也不计入 4 次
        if (enemyKill && keepInventory
                && PerkBoostQuery.INSTANCE.hasDoomsdayEffect(killer)
                && PerkBoostQuery.INSTANCE.consumeDoomsdayKill(killer)) {
            dropBackpack(self);
        }
    }

    /** 强制掉落主背包 27 格; 消失诅咒物品按原版规则直接消失 */
    private static void dropBackpack(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = BACKPACK_START; slot < BACKPACK_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            if (EnchantmentHelper.has(
                    stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                inventory.setItem(slot, ItemStack.EMPTY);
                continue;
            }

            player.drop(stack, true, Prediction.SERVER_ONLY);
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    /** 只有**双方都在同一支队伍**时才算队友; 没有队伍时视为人人都是敌人
     *  (以前两边都为 null 时返回 true, 无队伍的服务器上"被敌方击杀"永远不会触发) */
    private static boolean isSameTeam(ServerPlayer a, ServerPlayer b) {
        PlayerTeam teamA = a.getTeam();
        PlayerTeam teamB = b.getTeam();
        if (teamA == null || teamB == null) return false;
        return teamA.getName().equals(teamB.getName());
    }
}
