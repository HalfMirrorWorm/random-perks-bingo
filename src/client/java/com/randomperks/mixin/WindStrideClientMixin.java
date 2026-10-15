package com.randomperks.mixin;

import com.randomperks.client.WindStrideClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "风之步伐"词条 (客户端侧): 与客户端自己的疾跑状态切换同步, 本地即时挂/摘加成。
 * (本文件位于 client 源集 —— 需要引用客户端专用类 ClientPlayerEntity;
 * 包名保持 com.randomperks.mixin, 以便 mixins.randomperks.json 的 client 列表按包前缀解析)
 */
@Mixin(LivingEntity.class)
public class WindStrideClientMixin {

    @Inject(method = "setSprinting(Z)V", at = @At("TAIL"))
    private void randomperks$windStrideClient(boolean sprinting, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity player)) return;
        WindStrideClient.INSTANCE.update(player, sprinting);
    }
}
