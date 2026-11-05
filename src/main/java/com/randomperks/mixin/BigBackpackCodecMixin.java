package com.randomperks.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "大背包" 的 4 倍堆叠需要背包里出现超过 99 的堆叠, 但原版 {@link ItemStack} 存档 codec 的
 * {@code count} 字段是 {@code ExtraCodecs.intRange(1, 99)} —— 超过 99 的堆叠能存在内存里,
 * 却写不进玩家存档 (重进时那一格读不出来)。
 *
 * <p>这里只把这**一个调用点**的上限放宽到 256, 其它地方的 {@code intRange(1, 99)} 不受影响。
 */
@Mixin(ItemStack.class)
public class BigBackpackCodecMixin {

    @Redirect(
        method = "lambda$static$1",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"
        )
    )
    private static Codec<Integer> randomperks$widenStackCount(int min, int max) {
        return ExtraCodecs.intRange(min, max == 99 ? 256 : max);
    }
}
