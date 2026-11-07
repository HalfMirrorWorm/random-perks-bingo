package com.randomperks.mixin.compass;

import com.randomperks.client.compat.CompassSearchCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.world.level.Level;

/**
 * "自然罗盘" 界面: 在它自己的名称过滤结果后面补上拼音命中的生物群系。
 *
 * <p>目标类是第三方 mod 的类 (不参与混淆), 所以 remap = false;
 * 没装 naturescompass 时由 {@link CompassMixinPlugin} 直接跳过本 mixin。
 *
 * <p>2.5.1 (26.3) 把字段 `world` 改名为 `level`, 搜索框也从 `searchTextField`
 * 改成了 `searchBox`。搜索框是它自己的 `TransparentEditBox`, 这里不直接 shadow,
 * 交给 {@link CompassSearchCompat} 反射读取, 避免把第三方类变成编译依赖。
 */
@Mixin(targets = "com.chaosthedude.naturescompass.gui.NaturesCompassScreen", remap = false)
public abstract class NaturesCompassSearchMixin {

    @Shadow
    private List<?> allowedBiomes;

    @Shadow
    private Level level;

    @Inject(method = "sortBiomes", at = @At("RETURN"))
    private void randomperks$addPinyinMatches(CallbackInfoReturnable<List<?>> cir) {
        CompassSearchCompat.INSTANCE.addBiomePinyinMatches(
                this, this.level, this.allowedBiomes, cir.getReturnValue());
    }
}
