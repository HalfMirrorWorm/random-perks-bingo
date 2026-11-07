package com.randomperks.mixin;

import com.randomperks.client.render.PigMorphRenderer;
import com.randomperks.game.MorphState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" —— 处于变猪状态的玩家改用 {@link PigMorphRenderer} 渲染。
 *
 * 只换渲染器, 不碰实体本身: 位置、动画、名字标签、碰撞箱提示等仍然走原版流程。
 *
 * 只处理**玩家**: 词条效果是"自己变猪", 不会有别的实体被标记;
 * 而且 {@link PigMorphRenderer} 是 PlayerEntityRenderer 的子类, 只能接玩家实体
 * (它的 updateRenderState 会强转 AbstractClientPlayerEntity)。
 */
@Mixin(EntityRenderDispatcher.class)
public class MorphRendererMixin {

    // 必须写完整描述符: getRenderer 有两个重载 (收实体 / 收渲染状态), 只写方法名会同时匹配,
    // 另一个的签名对不上 → 注入失败 → defaultRequire=1 直接崩启动
    @Inject(
            method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomperks$pigRenderer(Entity entity, CallbackInfoReturnable<EntityRenderer> cir) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (!MorphState.isMorphed(player.getUUID())) return;

        PlayerSkin skin = player.getSkin();
        boolean slim = skin != null && skin.model() == PlayerModelType.SLIM;

        PigMorphRenderer renderer = PigMorphRenderer.get(slim);
        if (renderer != null) {
            cir.setReturnValue(renderer);
        }
    }

    /**
     * 26.3 的实际模型提交走 {@code EntityRenderDispatcher.submit}，它按
     * {@link AvatarRenderState} 从原版玩家渲染器表里取渲染器，不再看实体。
     * 这里同样按实体 id 找回玩家 UUID，变猪时返回猪渲染器。
     */
    @Inject(
            method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomperks$pigRendererState(AvatarRenderState state, CallbackInfoReturnable<AvatarRenderer> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Entity entity = client.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;
        if (!MorphState.isMorphed(player.getUUID())) return;

        boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
        PigMorphRenderer renderer = PigMorphRenderer.get(slim);
        if (renderer != null) {
            cir.setReturnValue(renderer);
        }
    }
}
