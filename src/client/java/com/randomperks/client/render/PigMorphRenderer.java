package com.randomperks.client.render;

import com.randomperks.mixin.RenderDispatcherAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PigEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PigEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.PigVariant;
import net.minecraft.entity.passive.PigVariants;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * "变群友" 的渲染伪装 —— 把一个实体画成猪。
 *
 * 实体本身完全不动 (服务端一个字节都没改), 这里只做两件事:
 * <ol>
 *   <li>用猪的模型渲染它 (猪的变体/纹理直接借用原版 {@link PigEntityRenderer});</li>
 *   <li>如果它是玩家, 把猪头藏起来、在同一个位置渲染他自己皮肤的头。</li>
 * </ol>
 * 尺寸/视角的伪装在 MorphSizeMixin 里 (改 {@code Entity.getDimensions})。
 */
public class PigMorphRenderer extends EntityRenderer<Entity, PigEntityRenderState> {

    /** 借不到原版猪渲染器时的兜底纹理 */
    private static final Identifier FALLBACK_TEXTURE = Identifier.ofVanilla("textures/entity/pig/pig.png");

    private static EntityRendererFactory.Context capturedContext;
    private static PigMorphRenderer instance;

    private final PigEntityModel model;
    private final PlayerEntityModel headModel;
    private final ModelPart pigHead;

    public PigMorphRenderer(EntityRendererFactory.Context context) {
        super(context);
        ModelPart root = context.getPart(EntityModelLayers.PIG);
        this.model = new PigEntityModel(root);
        this.pigHead = root.getChild("head");
        this.headModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);
    }

    /** 由 RendererContextMixin 在 Context 构造完成后调用 */
    public static void captureContext(EntityRendererFactory.Context context) {
        capturedContext = context;
        // 资源重载会重建模型, 旧实例作废
        instance = null;
    }

    /** 全局单例 (延迟创建; Context 还没捕获到时返回 null) */
    public static PigMorphRenderer get() {
        if (instance == null && capturedContext != null) {
            instance = new PigMorphRenderer(capturedContext);
        }
        return instance;
    }

    @Override
    public PigEntityRenderState createRenderState() {
        return new PigEntityRenderState();
    }

    @Override
    public void updateRenderState(Entity entity, PigEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);

        // 变体决定模型与纹理 —— 固定用默认变体
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            Registry<PigVariant> registry =
                    client.world.getRegistryManager().getOrThrow(RegistryKeys.PIG_VARIANT);
            state.variant = registry.getOrThrow(PigVariants.DEFAULT).value();
        }
        state.baby = false;

        if (entity instanceof LivingEntity living) {
            state.bodyYaw = living.getBodyYaw();
            state.relativeHeadYaw = living.getHeadYaw() - living.getBodyYaw();
            state.pitch = living.getPitch();
            state.limbSwingAnimationProgress = living.limbAnimator.getAnimationProgress(tickDelta);
            state.limbSwingAmplitude = living.limbAnimator.getAmplitude(tickDelta);
        } else {
            state.bodyYaw = entity.getYaw();
            state.relativeHeadYaw = 0.0f;
            state.pitch = entity.getPitch();
            state.limbSwingAnimationProgress = 0.0f;
            state.limbSwingAmplitude = 0.0f;
        }

        // 玩家 → 猪头换成他自己的皮肤
        SkinTextures skin = entity instanceof AbstractClientPlayerEntity player ? player.getSkinTextures() : null;
        ((MorphSkinHolder) (Object) state).randomperks$setSkin(skin);
    }

    @Override
    public void render(PigEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        SkinTextures skin = ((MorphSkinHolder) (Object) state).randomperks$getSkin();

        matrices.push();
        matrices.scale(-1.0f, -1.0f, 1.0f);
        matrices.translate(0.0, -1.501, 0.0);

        // 猪身: 要换头时把猪头藏起来, 免得和玩家头穿模
        this.pigHead.visible = skin == null;
        this.model.setAngles(state);
        VertexConsumer pigBuffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(pigTexture(state)));
        this.model.render(matrices, pigBuffer, light, OverlayTexture.DEFAULT_UV);
        this.pigHead.visible = true;

        // 玩家头 (跟猪头同一个位置, 朝向跟随身体)
        if (skin != null) {
            ModelPart head = this.headModel.head;
            head.pitch = state.pitch * 0.017453292f;
            head.yaw = state.relativeHeadYaw * 0.017453292f;
            VertexConsumer headBuffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin.texture()));
            head.render(matrices, headBuffer, light, OverlayTexture.DEFAULT_UV);
        }

        matrices.pop();
    }

    /** 猪的纹理 —— 直接问原版猪渲染器要 (它按 state.variant 取, 省得自己拼路径) */
    private Identifier pigTexture(PigEntityRenderState state) {
        Map<EntityType<?>, EntityRenderer<?, ?>> renderers =
                ((RenderDispatcherAccessor) this.dispatcher).randomperks$getRenderers();
        EntityRenderer<?, ?> renderer = renderers.get(EntityType.PIG);
        if (renderer instanceof PigEntityRenderer pigRenderer) {
            return pigRenderer.getTexture(state);
        }
        return FALLBACK_TEXTURE;
    }
}
