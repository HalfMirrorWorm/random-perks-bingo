package com.randomperks.mixin.compass;

import com.randomperks.client.compat.CompassSearchCompat;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * "自然罗盘" 界面: 在它自己的名称过滤结果后面补上拼音命中的生物群系。
 *
 * <p>目标类是第三方 mod 的类 (不参与混淆), 所以 remap = false;
 * 没装 naturescompass 时由 {@link CompassMixinPlugin} 直接跳过本 mixin。
 */
@Mixin(targets = "com.chaosthedude.naturescompass.gui.NaturesCompassScreen", remap = false)
public abstract class NaturesCompassSearchMixin {

    @Shadow
    private List<?> allowedBiomes;

    @Shadow
    private World world;

    @Shadow
    private TextFieldWidget searchTextField;

    @Inject(method = "sortBiomes", at = @At("RETURN"))
    private void randomperks$addPinyinMatches(CallbackInfoReturnable<List<?>> cir) {
        CompassSearchCompat.INSTANCE.addBiomePinyinMatches(
                this.world, this.searchTextField, this.allowedBiomes, cir.getReturnValue());
    }
}
