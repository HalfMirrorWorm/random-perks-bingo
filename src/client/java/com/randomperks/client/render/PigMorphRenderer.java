package com.randomperks.client.render;

import com.randomperks.mixin.RenderDispatcherAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PigEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.PigVariant;
import net.minecraft.entity.passive.PigVariants;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * "变群友" 的渲染伪装 —— 把一个实体画成猪。
 *
 * 实体本身完全不动 (服务端不改它的任何持久状态), 这里只做三件事:
 * <ol>
 *   <li>用猪的模型渲染它 (猪的变体/纹理直接借用原版 {@link PigEntityRenderer});</li>
 *   <li>如果它是玩家, 把猪头藏起来、在同一个位置渲染他自己皮肤的头 —— 连帽子层和
 *       头上的护甲一起 (参考 sparktraits 的 PigPlayerRenderer);</li>
 *   <li>尺寸/视角的伪装在 MorphDimensionsMixin (碰撞箱) 与 MorphEyeHeightMixin (眼睛高度)。</li>
 * </ol>
 */
public class PigMorphRenderer extends EntityRenderer<Entity, PigEntityRenderState> {

    /** 借不到原版猪渲染器时的兜底纹理 */
    private static final Identifier FALLBACK_TEXTURE = Identifier.ofVanilla("textures/entity/pig/pig.png");

    private static EntityRendererFactory.Context capturedContext;
    private static PigMorphRenderer instance;

    private static final Logger LOGGER = LoggerFactory.getLogger("random-perks-bingo/client");

    /** 玩家头渲染失败只记一次日志, 免得每帧刷屏 */
    private static boolean warnedHeadFailure;

    /** 猪变体注册表查询失败同样只记一次 */
    private static boolean warnedVariantFailure;

    private final PigEntityModel model;
    private final PlayerEntityModel headModel;
    private final ModelPart pigHead;

    /**
     * 玩家头 + 帽子 + 头盔的渲染状态。
     *
     * 头盔不是自己画的: 交给原版的 {@link ArmorFeatureRenderer}, 它才认得护甲纹理、
     * 皮革染色、附魔光效和自定义 ArmorRenderer。唯一要喂给它的是"头上的装备"。
     */
    private final PlayerEntityRenderState playerState = new PlayerEntityRenderState();

    /**
     * 这一帧要渲染的玩家有没有开"帽子层"。
     *
     * 原版是靠 PlayerEntityModel.setAngles 设 hat.visible 的, 而我们绕过了 setAngles
     * (头的姿势是手抄猪头的), 所以得自己记一下, 否则没开帽子层的皮肤也会多画一层。
     */
    private boolean hatVisible = true;
    private final ArmorFeatureRenderer<PlayerEntityRenderState, PlayerEntityModel,
            BipedEntityModel<PlayerEntityRenderState>> armorRenderer;

    public PigMorphRenderer(EntityRendererFactory.Context context) {
        super(context);
        ModelPart root = context.getPart(EntityModelLayers.PIG);
        this.model = new PigEntityModel(root);
        this.pigHead = root.getChild("head");
        this.headModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);

        // 头盔渲染器的"上下文模型"指向**我们自己的** headModel, 而不是原版玩家渲染器的那个。
        // 这样 renderArmor 内部 copyStateTo(armorModel) 时抄到的是我们已经对齐到猪头的姿势,
        // 头盔才会跟着猪头走 (原版那个模型的头还停在玩家自己的高度上)。
        this.armorRenderer = new ArmorFeatureRenderer<>(
                new HeadFeatureContext(this.headModel),
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                context.getEquipmentRenderer());

        // 只画头盔: 其余三个槽位保持空, renderArmor 遇到空槽会直接返回
        this.playerState.equippedHeadStack = ItemStack.EMPTY;
        this.playerState.equippedChestStack = ItemStack.EMPTY;
        this.playerState.equippedLegsStack = ItemStack.EMPTY;
        this.playerState.equippedFeetStack = ItemStack.EMPTY;
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
            try {
                Registry<PigVariant> registry =
                        client.world.getRegistryManager().getOrThrow(RegistryKeys.PIG_VARIANT);
                state.variant = registry.getOrThrow(PigVariants.DEFAULT).value();
            } catch (Exception e) {
                // 注册表缺失 / 异常时保留上一次的 variant (PigEntityRenderer.getTexture 对 null 有兜底),
                // 不要因为一个外观细节把整个客户端带崩
                if (!warnedVariantFailure) {
                    warnedVariantFailure = true;
                    LOGGER.error("[RandomPerks] pig morph: pig variant lookup failed", e);
                }
            }
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

        // 玩家 → 猪头换成他自己的皮肤, 头上的护甲一并带过来
        SkinTextures skin = entity instanceof AbstractClientPlayerEntity player ? player.getSkinTextures() : null;
        ((MorphSkinHolder) (Object) state).randomperks$setSkin(skin);

        // ArmorFeatureRenderer 只看这几个槽位, 我们只填头盔, 其余留空 → 只画头盔
        this.playerState.equippedHeadStack = entity instanceof LivingEntity living
                ? living.getEquippedStack(EquipmentSlot.HEAD)
                : ItemStack.EMPTY;
        this.hatVisible = !(entity instanceof AbstractClientPlayerEntity player)
                || player.isPartVisible(PlayerModelPart.HAT);
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

        // 玩家头: 必须套用**猪头**的轴心与朝向。
        // 直接在当前矩阵下 render 的话, 玩家的 head 部件会停在玩家模型的头部高度
        // (局部 y ∈ [-8, 0] → 脚上方 1.0~1.5 格), 也就是"头悬在猪身上面"。
        if (skin != null) {
            ModelPart head = this.headModel.head;
            // 参考 sparktraits 的 PigPlayerRenderer#copyPigHeadTransform:
            // 先把猪头的**完整 transform** (轴心 / 缩放 / roll) 抄到玩家头上, 再只覆盖朝向。
            // copyTransform = xScale/yScale/zScale + pitch/yaw/roll + originX/Y/Z。
            head.copyTransform(this.pigHead);
            head.pitch = state.pitch * 0.017453292f;
            head.yaw = state.relativeHeadYaw * 0.017453292f;
            // 帽子层 (皮肤的第二层, 比如帽子/头发) 跟着头走
            this.headModel.hat.copyTransform(head);
            this.headModel.hat.visible = this.hatVisible;

            matrices.push();
            try {
                // 猪头立方体相对轴心是 y ∈ [-4, 4] (中心 0); 玩家头立方体是 y ∈ [-8, 0] (中心 -4)。
                // 下移 4 模型单位 = 0.25 格, 两者的中心就重合了 (参考实现没做这步, 头会偏高 0.25)。
                matrices.translate(0.0f, 0.25f, 0.0f);

                VertexConsumer headBuffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin.texture()));
                head.render(matrices, headBuffer, light, OverlayTexture.DEFAULT_UV);
                this.headModel.hat.render(matrices, headBuffer, light, OverlayTexture.DEFAULT_UV);

                // 头盔: 交给原版 ArmorFeatureRenderer。它的上下文模型就是上面这个 headModel,
                // 所以头盔会自动跟着猪头的位置和朝向。playerState 里只有头盔槽有东西。
                this.armorRenderer.render(matrices, vertexConsumers, light, this.playerState, 0.0f, 0.0f);
            } catch (Throwable t) {
                // 纯外观问题不该拖垮整个客户端 —— 失败就退回猪头, 并把原因记一次日志
                if (!warnedHeadFailure) {
                    warnedHeadFailure = true;
                    LOGGER.error("[RandomPerks] pig morph: player-head render failed, falling back to pig head", t);
                }
            } finally {
                matrices.pop();
            }
        }

        matrices.pop();

        // 基类 render 只负责拴绳, 保留原版行为
        super.render(state, matrices, vertexConsumers, light);
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

    /**
     * 喂给 {@link ArmorFeatureRenderer} 的上下文: 模型用我们的 headModel。
     *
     * 1.21.7 的 FeatureRendererContext 只剩 getModel() —— 纹理由各自的渲染器自己解决
     * (头盔的纹理归 EquipmentRenderer 按护甲材质决定), 所以这里没有纹理回调。
     */
    private record HeadFeatureContext(PlayerEntityModel model)
            implements FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> {

        @Override
        public PlayerEntityModel getModel() {
            return this.model;
        }
    }
}
