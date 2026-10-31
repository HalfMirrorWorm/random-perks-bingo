package com.randomperks.mixin.compass;

import com.randomperks.client.compat.CompassSearchCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * "结构罗盘" 界面: 在它自己的名称过滤结果后面补上拼音命中的结构。
 *
 * <p>搜索框字段 (searchTextField) 是它自己的 TextFieldWidget 子类, 无法用 @Shadow 声明,
 * 由 {@link CompassSearchCompat} 反射读取。
 */
@Mixin(targets = "com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen", remap = false)
public abstract class ExplorersCompassSearchMixin {

    @Shadow
    private List<?> allowedStructureIDs;

    @Inject(method = "sortStructures", at = @At("RETURN"))
    private void randomperks$addPinyinMatches(CallbackInfoReturnable<List<?>> cir) {
        CompassSearchCompat.INSTANCE.addStructurePinyinMatches(
                this, this.allowedStructureIDs, cir.getReturnValue());
    }
}
