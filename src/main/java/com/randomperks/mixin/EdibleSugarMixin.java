package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
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
    private static Consumable randomperks$sugarComponent;

    /** 糖的消费定义: 1.6 秒吃完 (与原版食物默认时长一致), 吃完给 15 秒速度 I */
    @Unique
    private static Consumable randomperks$sugar() {
        Consumable cached = randomperks$sugarComponent;
        if (cached == null) {
            cached = Consumable.builder()
                    .consumeSeconds(1.6F)
                    .animation(ItemUseAnimation.EAT)
                    .sound(SoundEvents.GENERIC_EAT)
                    .hasConsumeParticles(true)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(
                            new MobEffectInstance(MobEffects.SPEED, 15 * 20, 0)))
                    .build();
            randomperks$sugarComponent = cached;
        }
        return cached;
    }

    @Unique
    private static boolean randomperks$hasPerk(Player player) {
        if (player.level().isClientSide()) {
            return ClientPerkFlags.has(PERK_ID);
        }
        return player instanceof ServerPlayer serverPlayer
                && PerkBoostQuery.INSTANCE.hasEdibleSugar(serverPlayer);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void randomperks$useSugar(Level world, Player user, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.SUGAR)) return;
        if (!randomperks$hasPerk(user)) return;
        cir.setReturnValue(randomperks$sugar().startConsuming(user, self, hand));
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void randomperks$sugarUseTime(LivingEntity user,
                                          CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.SUGAR)) return;
        if (!(user instanceof Player player) || !randomperks$hasPerk(player)) return;
        cir.setReturnValue(randomperks$sugar().consumeTicks());
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void randomperks$finishSugar(Level world, LivingEntity user,
                                         CallbackInfoReturnable<ItemStack> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.SUGAR)) return;
        if (!(user instanceof Player player) || !randomperks$hasPerk(player)) return;
        ItemStack finished = randomperks$sugar().onConsume(world, user, self);
        // 原版食物的打嗝在 FoodComponent.onConsume 里, 糖没有食物组件, 这里按同样的音量和音高补上。
        // 只在服务端播: playSound 的 source 为 null 时会发给周围所有人, 吃的人自己也能听到。
        if (!world.isClientSide()) {
            RandomSource random = user.getRandom();
            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.PLAYER_BURP,
                    SoundSource.PLAYERS,
                    0.5F,
                    Mth.randomBetween(random, 0.9F, 1.0F));
        }
        cir.setReturnValue(finished);
    }

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void randomperks$sugarUsageTick(Level world, LivingEntity user, int remainingUseTicks,
                                            CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.SUGAR)) return;
        if (!(user instanceof Player player) || !randomperks$hasPerk(player)) return;
        Consumable sugar = randomperks$sugar();
        if (sugar.shouldEmitParticlesAndSounds(remainingUseTicks)) {
            sugar.emitParticlesAndSounds(user.getRandom(), user, self, 5);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void randomperks$sugarUseAction(CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (((ItemStack) (Object) this).is(Items.SUGAR)) {
            cir.setReturnValue(ItemUseAnimation.EAT);
        }
    }
}
