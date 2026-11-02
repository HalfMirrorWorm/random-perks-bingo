package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.UseAction;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "冰！" (蓝 · 疾风): 持有者可以**食用糖**, 吃完获得 15 秒速度 I。
 *
 * <h3>为什么不是给糖加个食物组件</h3>
 * 糖本身没有 {@code FOOD} / {@code CONSUMABLE} 组件, 而组件是挂在**物品/物品堆**上的,
 * 没有"只对某个玩家生效"的写法 —— 给糖加上组件等于让所有人都能吃糖。
 * 所以这里改成在 {@code ItemStack} 的 4 个消耗相关方法上按玩家放行:
 *
 * <ul>
 *   <li>{@code use(World, PlayerEntity, Hand)} —— 右键时的入口 (客户端预判 + 服务端结算都走这里),
 *       改为直接跑一份"糖"的消费定义 ({@link #randomperks$sugar()});</li>
 *   <li>{@code getMaxUseTime(LivingEntity)} —— 不返回 &gt;0 的话会被当成"瞬发使用",
 *       不会进入原版的进食倒计时;</li>
 *   <li>{@code finishUsing(World, LivingEntity)} —— 倒计时结束后由原版调用,
 *       这里走同一份定义的 {@code finishConsumption} (扣物品 / 收尾咀嚼 / 粒子 / 正面效果),
 *       再补上原版食物吃完时的那声打嗝;</li>
 *   <li>{@code usageTick(World, LivingEntity, int)} —— 进食中的咀嚼声和碎屑粒子。
 *       原版只在物品堆带有 {@code CONSUMABLE} 组件时才播, 糖没有该组件,
 *       所以要在这里按同一节奏调用 {@code spawnParticlesAndPlaySound};</li>
 *   <li>{@code getUseAction()} —— 只影响手臂姿势与进食动画 (该方法拿不到玩家, 但非持有者
 *       根本进不了"正在用糖"的状态, 所以无条件返回 EAT 是安全的)。</li>
 * </ul>
 *
 * <p>非持有者这四处全部原样返回, 与未安装本 mod 的行为完全一致。
 */
@Mixin(ItemStack.class)
public class EdibleSugarMixin {

    private static final String PERK_ID = "swift_edible_sugar";

    /** 懒初始化: 直接写在静态初始化里会早于注册表引导访问 StatusEffects */
    @Unique
    private static ConsumableComponent randomperks$sugarComponent;

    /** 糖的消费定义: 1.6 秒吃完 (与原版食物默认时长一致), 吃完给 15 秒速度 I */
    @Unique
    private static ConsumableComponent randomperks$sugar() {
        ConsumableComponent cached = randomperks$sugarComponent;
        if (cached == null) {
            cached = ConsumableComponent.builder()
                    .consumeSeconds(1.6F)
                    .useAction(UseAction.EAT)
                    .sound(SoundEvents.ENTITY_GENERIC_EAT)
                    .consumeParticles(true)
                    .consumeEffect(new ApplyEffectsConsumeEffect(
                            new StatusEffectInstance(StatusEffects.SPEED, 15 * 20, 0)))
                    .build();
            randomperks$sugarComponent = cached;
        }
        return cached;
    }

    @Unique
    private static boolean randomperks$hasPerk(PlayerEntity player) {
        if (player.getEntityWorld().isClient()) {
            return ClientPerkFlags.has(PERK_ID);
        }
        return player instanceof ServerPlayerEntity serverPlayer
                && PerkBoostQuery.INSTANCE.hasEdibleSugar(serverPlayer);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void randomperks$useSugar(World world, PlayerEntity user, Hand hand,
                                      CallbackInfoReturnable<ActionResult> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.isOf(Items.SUGAR)) return;
        if (!randomperks$hasPerk(user)) return;
        cir.setReturnValue(randomperks$sugar().consume(user, self, hand));
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void randomperks$sugarUseTime(LivingEntity user,
                                          CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.isOf(Items.SUGAR)) return;
        if (!(user instanceof PlayerEntity player) || !randomperks$hasPerk(player)) return;
        cir.setReturnValue(randomperks$sugar().getConsumeTicks());
    }

    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void randomperks$finishSugar(World world, LivingEntity user,
                                         CallbackInfoReturnable<ItemStack> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.isOf(Items.SUGAR)) return;
        if (!(user instanceof PlayerEntity player) || !randomperks$hasPerk(player)) return;
        ItemStack finished = randomperks$sugar().finishConsumption(world, user, self);
        // 原版食物的打嗝在 FoodComponent.onConsume 里, 糖没有食物组件, 这里按同样的音量和音高补上。
        // 只在服务端播: playSound 的 source 为 null 时会发给周围所有人, 吃的人自己也能听到。
        if (!world.isClient()) {
            Random random = user.getRandom();
            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ENTITY_PLAYER_BURP,
                    SoundCategory.PLAYERS,
                    0.5F,
                    MathHelper.nextBetween(random, 0.9F, 1.0F));
        }
        cir.setReturnValue(finished);
    }

    @Inject(method = "usageTick", at = @At("HEAD"))
    private void randomperks$sugarUsageTick(World world, LivingEntity user, int remainingUseTicks,
                                            CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.isOf(Items.SUGAR)) return;
        if (!(user instanceof PlayerEntity player) || !randomperks$hasPerk(player)) return;
        ConsumableComponent sugar = randomperks$sugar();
        if (sugar.shouldSpawnParticlesAndPlaySounds(remainingUseTicks)) {
            sugar.spawnParticlesAndPlaySound(user.getRandom(), user, self, 5);
        }
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void randomperks$sugarUseAction(CallbackInfoReturnable<UseAction> cir) {
        if (((ItemStack) (Object) this).isOf(Items.SUGAR)) {
            cir.setReturnValue(UseAction.EAT);
        }
    }
}
