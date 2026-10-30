package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
 *   <li>"Jockey" —— 骑在别人身上时, 受到来自**被骑者**的伤害 -75%</li>
 *   <li>"恶魔契约" —— 造成的伤害翻倍, 受到的**任何**伤害也翻倍</li>
 *   <li>"老玩家" —— 右键举剑格挡时, 受到的伤害减半</li>
 *   <li>"细嚼慢咽" —— 正在进食食物时, 受到的伤害 -75%</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float randomperks$modifyDamage(float amount, ServerWorld world, DamageSource source) {
        Entity attacker = source.getAttacker();
        // 荆棘/刺猬反弹的伤害不吃攻击者侧的增伤, 保证反弹比例就是实际受到伤害的比例
        boolean thorns = source.isOf(DamageTypes.THORNS);

        // 攻击者侧的修正: "恶魔契约" 造成伤害翻倍
        // (在受害者这一侧也能看到 attacker, 所以不用另开一个注入点)
        if (!thorns && attacker instanceof ServerPlayerEntity attackerPlayer
                && PerkBoostQuery.INSTANCE.hasDemonPact(attackerPlayer)) {
            amount *= 2.0f;
        }

        // "良子冲击": 饱食度满 (饥饿值 20) 时造成伤害 +50%
        if (!thorns && attacker instanceof ServerPlayerEntity attackerPlayer
                && PerkBoostQuery.INSTANCE.hasSatiationStrike(attackerPlayer)
                && attackerPlayer.getHungerManager().getFoodLevel() >= 20) {
            amount *= 1.5f;
        }

        // "猎人直觉": 只对怪物 (Monster) 生效 —— 打玩家 / 其他目标不加成
        if (!thorns && attacker instanceof ServerPlayerEntity attackerPlayer
                && (Object) this instanceof Monster
                && PerkBoostQuery.INSTANCE.hasHunterInstinct(attackerPlayer)) {
            amount *= 1.25f;
        }

        if (!((Object) this instanceof ServerPlayerEntity player)) return amount;

        // "Jockey": 只有"被自己骑着的那个玩家"打过来才减伤
        if (PerkBoostQuery.INSTANCE.hasJockey(player)
                && attacker instanceof ServerPlayerEntity
                && player.getVehicle() == attacker) {
            amount *= 0.25f;
        }

        // "恶魔契约": 受到任何伤害翻倍 (含摔落/岩浆等环境伤害)
        if (PerkBoostQuery.INSTANCE.hasDemonPact(player)) {
            amount *= 2.0f;
        }

        // "老玩家": 剑格挡 —— 右键举剑 (1.9 以前的手感) 时受到的伤害减半。
        // "使用中 + 手里是剑" 只有 OldSchoolBlockMixin 放过的那条路径能达成, 所以不必再查持有状态。
        if (PerkBoostQuery.INSTANCE.hasOldSchoolCombat(player)
                && player.isUsingItem()
                && player.getActiveItem().isIn(ItemTags.SWORDS)) {
            amount *= 0.5f;
        }

        // "细嚼慢咽": 正在进食 (使用带 FOOD 组件的物品) 时, 受到的伤害 -75%
        if (PerkBoostQuery.INSTANCE.hasSlowChewing(player)
                && player.isUsingItem()
                && player.getActiveItem().get(DataComponentTypes.FOOD) != null) {
            amount *= 0.25f;
        }

        return amount;
    }
}
