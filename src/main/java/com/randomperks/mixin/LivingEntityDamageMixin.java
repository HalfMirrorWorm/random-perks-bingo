package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 玩家伤害的若干词条修正。
 *
 * 三个效果写在同一个 handler 里: 它们都作用在 {@code damage} 的 amount 参数上,
 * 分成多个 {@code @ModifyVariable} 去改同一个参数容易互相打架, 顺序也不直观。
 *
 * <ul>
 *   <li>"猎人直觉" —— **对怪物 (Monster)** 造成的伤害 +25%, 对玩家/其他目标不加成</li>
 *   <li>"恶魔契约" —— 造成的伤害翻倍, 受到的**任何**伤害也翻倍</li>
 *   <li>"老玩家" —— 右键举剑格挡时, 受到的伤害减半</li>
 *   <li>"细嚼慢咽" —— 正在进食食物时, 受到的伤害 -75%</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float randomperks$modifyDamage(float amount, ServerLevel world, DamageSource source) {
        Entity attacker = source.getEntity();
        // 荆棘/刺猬反弹的伤害不吃攻击者侧的增伤, 保证反弹比例就是实际受到伤害的比例
        boolean thorns = source.is(DamageTypes.THORNS);

        // 攻击者侧的修正: "恶魔契约" 造成伤害翻倍
        // (在受害者这一侧也能看到 attacker, 所以不用另开一个注入点)
        if (!thorns && attacker instanceof ServerPlayer attackerPlayer
                && PerkBoostQuery.INSTANCE.hasDemonPact(attackerPlayer)) {
            amount *= 2.0f;
        }

        // "良子冲击": 饱食度满 (饥饿值 20) 时造成伤害 +50%
        if (!thorns && attacker instanceof ServerPlayer attackerPlayer
                && PerkBoostQuery.INSTANCE.hasSatiationStrike(attackerPlayer)
                && attackerPlayer.getFoodData().getFoodLevel() >= 20) {
            amount *= 1.5f;
        }

        // "猎人直觉": 只对怪物 (Monster) 生效 —— 打玩家 / 其他目标不加成
        if (!thorns && attacker instanceof ServerPlayer attackerPlayer
                && (Object) this instanceof Enemy
                && PerkBoostQuery.INSTANCE.hasHunterInstinct(attackerPlayer)) {
            amount *= 1.25f;
        }

        if (!((Object) this instanceof ServerPlayer player)) return amount;

        // "恶魔契约": 受到任何伤害翻倍 (含摔落/岩浆等环境伤害)
        if (PerkBoostQuery.INSTANCE.hasDemonPact(player)) {
            amount *= 2.0f;
        }

        // "老玩家": 剑格挡 —— 右键举剑 (1.9 以前的手感) 时受到的**攻击**伤害减半。
        // "使用中 + 手里是剑" 只有 OldSchoolBlockMixin 放过的那条路径能达成, 所以不必再查持有状态。
        // 只挡有攻击者的伤害: 摔落/岩浆/溺水这类环境伤害和格挡无关, 不应该一起减半。
        if (PerkBoostQuery.INSTANCE.hasOldSchoolCombat(player)
                && player.isUsingItem()
                && player.getUseItem().is(ItemTags.SWORDS)
                && source.getEntity() != null) {
            amount *= 0.5f;
        }

        // "细嚼慢咽": 正在进食 (使用带 FOOD 组件的物品) 时, 受到的伤害 -75%
        if (PerkBoostQuery.INSTANCE.hasSlowChewing(player)
                && player.isUsingItem()
                && player.getUseItem().get(DataComponents.FOOD) != null) {
            amount *= 0.25f;
        }

        return amount;
    }
}
