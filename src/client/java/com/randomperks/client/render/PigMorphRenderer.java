package com.randomperks.client.render;

import com.randomperks.game.MorphState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
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
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "变猪" 的渲染伪装 —— 把一个**玩家**画成猪 (猪头位置换成他自己的皮肤头部)。
 *
 * <p>★ 这个类**必须继承 {@link PlayerEntityRenderer}**, 不能直接继承 EntityRenderer:
 * 原版 {@code HeldItemRenderer} 在第一人称渲染手时会把
 * {@code EntityRenderDispatcher.getRenderer(player)} 强制转型成 {@code PlayerEntityRenderer}
 * —— 返回一个不是它的子类的渲染器会直接 ClassCastException 把客户端打崩
 * (0.0.9 的崩溃就是这么来的)。
 *
 * <p>实体本身完全不动 (服务端不改它的持久状态), 这里只做两件事:
 * <ol>
 *   <li>用猪的模型/纹理渲染它;</li>
 *   <li>把猪头藏起来, 在同一个位置渲染玩家自己皮肤的头 + 帽子层 + 头盔。</li>
 * </ol>
 *
 * <p>尺寸/视角的伪装在 MorphDimensionsMixin (碰撞箱) 与 MorphEyeHeightMixin (眼睛高度)。
 */
public class PigMorphRenderer extends PlayerEntityRenderer {

    /** 原版猪的默认 (温带) 纹理 */
    private static final Identifier PIG_TEXTURE =
            Identifier.ofVanilla("textures/entity/pig/temperate_pig.png");

    private static final Logger LOGGER = LoggerFactory.getLogger("random-perks-bingo/client");

    private static EntityRendererFactory.Context capturedContext;

    /** 宽 / 细手臂两个实例 —— 第一人称手部渲染会用到玩家模型的臂宽 */
    private static PigMorphRenderer wideInstance;
    private static PigMorphRenderer slimInstance;

    /** 纯外观问题只记一次日志, 免得每帧刷屏 */
    private static boolean warnedHeadFailure;
    private static boolean warnedRenderFailure;

    private final PigEntityModel pigModel;
    private final ModelPart pigHead;
    private final PlayerEntityModel headModel;

    /**
     * 玩家头 + 帽子 + 头盔的渲染状态。
     *
     * 头盔不是自己画的: 交给原版的 {@link ArmorFeatureRenderer}, 它才认得护甲纹理、
     * 皮革染色、附魔光效和自定义 ArmorRenderer。唯一要喂给它的是"头上的装备"。
     */
    private final PlayerEntityRenderState armorState = new PlayerEntityRenderState();

    private final ArmorFeatureRenderer<PlayerEntityRenderState, PlayerEntityModel,
            BipedEntityModel<PlayerEntityRenderState>> armorRenderer;

    private final PigEntityRenderState pigState = new PigEntityRenderState();

    private PigMorphRenderer(EntityRendererFactory.Context context, boolean slimArms) {
        super(context, slimArms);

        ModelPart pigRoot = context.getPart(EntityModelLayers.PIG);
        this.pigModel = new PigEntityModel(pigRoot);
        this.pigHead = pigRoot.getChild("head");

        this.headModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);

        // 头盔渲染器的"上下文模型"指向**我们自己的** headModel, 而不是原版玩家渲染器的那个。
        // 这样 renderArmor 内部 copyStateTo(armorModel) 时抄到的是我们已经对齐到猪头的姿势,
        // 头盔才会跟着猪头走。
        this.armorRenderer = new ArmorFeatureRenderer<>(
                new HeadFeatureContext(this.headModel),
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                context.getEquipmentRenderer());

        // 只画头盔: 其余三个槽位保持空, renderArmor 遇到空槽会直接返回
        this.armorState.equippedHeadStack = ItemStack.EMPTY;
        this.armorState.equippedChestStack = ItemStack.EMPTY;
        this.armorState.equippedLegsStack = ItemStack.EMPTY;
        this.armorState.equippedFeetStack = ItemStack.EMPTY;
    }

    /** 由 RendererContextMixin 在 Context 构造完成后调用 */
    public static void captureContext(EntityRendererFactory.Context context) {
        capturedContext = context;
        // 资源重载会重建模型, 旧实例作废
        wideInstance = null;
        slimInstance = null;
    }

    /** 全局单例 (按手臂模型区分; Context 还没捕获到时返回 null) */
    public static PigMorphRenderer get(boolean slimArms) {
        if (capturedContext == null) return null;

        if (slimArms) {
            if (slimInstance == null) slimInstance = new PigMorphRenderer(capturedContext, true);
            return slimInstance;
        }
        if (wideInstance == null) wideInstance = new PigMorphRenderer(capturedContext, false);
        return wideInstance;
    }

    @Override
    public void render(PlayerEntityRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        if (!MorphState.isMorphed(state.id)) {
            super.render(state, matrices, vertexConsumers, light);
            return;
        }

        boolean renderedAsPig = false;
        try {
            renderAsPig(state, matrices, vertexConsumers, light);
            renderedAsPig = true;
        } catch (Throwable t) {
            // 纯外观问题不该拖垮整个客户端 —— 失败就退回原版玩家模型
            if (!warnedRenderFailure) {
                warnedRenderFailure = true;
                LOGGER.error("[RandomPerks] pig morph: render failed, falling back to player model", t);
            }
            super.render(state, matrices, vertexConsumers, light);
        }

        // 名牌: 原版是 EntityRenderer.render 在模型画完后顺手画的, 而我们绕过了它
        // (super.render 会把整个玩家模型也画出来), 所以这里单独补一次。
        // 此时 matrices 已经回到"实体原点", 和原版画名牌时的坐标系一致。
        if (renderedAsPig && state.displayName != null) {
            super.renderLabelIfPresent(state, state.displayName, matrices, vertexConsumers, light);
        }
    }

    private void renderAsPig(PlayerEntityRenderState state, MatrixStack matrices,
                             VertexConsumerProvider vertexConsumers, int light) {
        SkinTextures skin = state.skinTextures;

        matrices.push();
        try {
            // 与原版 LivingEntityRenderer.render 完全一致的三步 (baseScale → 朝向 → y 翻转 + 下移)。
            // 少任何一步都会导致猪朝向不对 / 悬空。
            float baseScale = state.baseScale;
            matrices.scale(baseScale, baseScale, baseScale);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - state.bodyYaw));
            matrices.scale(-1.0f, -1.0f, 1.0f);
            matrices.translate(0.0, -1.501, 0.0);

            PigEntityRenderState pigState = this.pigState;
            pigState.bodyYaw = state.bodyYaw;
            pigState.relativeHeadYaw = state.relativeHeadYaw;
            pigState.pitch = state.pitch;
            pigState.age = state.age;
            pigState.limbSwingAnimationProgress = state.limbSwingAnimationProgress;
            pigState.limbSwingAmplitude = state.limbSwingAmplitude;
            pigState.baby = false;

            VertexConsumer pigBuffer =
                    vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PIG_TEXTURE));

            // 猪身: 要换头时把猪头藏起来, 免得和玩家头穿模
            this.pigHead.visible = skin == null;
            this.pigModel.setAngles(pigState);
            this.pigModel.render(matrices, pigBuffer, light, OverlayTexture.DEFAULT_UV);

            if (skin != null) {
                try {
                    renderPlayerHead(state, matrices, vertexConsumers, light, skin);
                } catch (Throwable t) {
                    // 头画崩了就退回猪头, 至少还是只完整的猪
                    if (!warnedHeadFailure) {
                        warnedHeadFailure = true;
                        LOGGER.error("[RandomPerks] pig morph: player-head render failed, falling back to pig head", t);
                    }
                    this.pigHead.visible = true;
                    this.pigHead.render(matrices, pigBuffer, light, OverlayTexture.DEFAULT_UV);
                }
            }

            this.pigHead.visible = true;
        } finally {
            matrices.pop();
        }
    }

    private void renderPlayerHead(PlayerEntityRenderState state, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, SkinTextures skin) {
        // 先让 PlayerEntityModel 正常摆姿势。
        // ★ 3D Skin Layers 之类的模组会在 setAngles 的 TAIL 处把外层皮肤 (hat 等)
        //   替换成 3D mesh; 不调用 setAngles 的话它们不会注入, 外层皮肤会一直按
        //   原版平面模型渲染 —— 这就是"变群友不兼容 3D 皮肤层"的原因。
        this.headModel.setAngles(state);

        ModelPart head = this.headModel.head;

        // 套用**猪头**的轴心与朝向 —— 直接渲染的话玩家头会停在玩家模型的高度上。
        head.copyTransform(this.pigHead);
        head.pitch = state.pitch * 0.017453292f;
        head.yaw = state.relativeHeadYaw * 0.017453292f;

        // 帽子层 (皮肤的第二层, 比如帽子/头发) 跟着头走
        this.headModel.hat.copyTransform(head);
        this.headModel.hat.visible = state.hatVisible;

        matrices.push();
        try {
            // 猪头立方体相对轴心是 y ∈ [-4, 4] (中心 0); 玩家头立方体是 y ∈ [-8, 0] (中心 -4)。
            // 平移 4 模型单位 (= 0.25 格) 让两者的中心重合。
            matrices.translate(0.0f, 0.25f, 0.0f);

            VertexConsumer headBuffer =
                    vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin.texture()));
            head.render(matrices, headBuffer, light, OverlayTexture.DEFAULT_UV);
            this.headModel.hat.render(matrices, headBuffer, light, OverlayTexture.DEFAULT_UV);

            // 头盔: 交给原版 ArmorFeatureRenderer。它的上下文模型就是上面这个 headModel,
            // 所以头盔会自动跟着猪头的位置和朝向。armorState 里只有头盔槽有东西。
            this.armorState.equippedHeadStack = state.equippedHeadStack;
            if (!state.equippedHeadStack.isEmpty()) {
                this.armorRenderer.render(matrices, vertexConsumers, light, this.armorState, 0.0f, 0.0f);
            }
        } finally {
            matrices.pop();
        }
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
