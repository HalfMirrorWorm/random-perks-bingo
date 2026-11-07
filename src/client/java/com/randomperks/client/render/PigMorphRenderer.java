package com.randomperks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "变猪" 的渲染伪装 —— 把一个**玩家**画成猪 (猪头位置换成他自己的皮肤头部)。
 *
 * <p>这个类必须继承 {@link AvatarRenderer}, 不能直接继承 EntityRenderer:
 * 第一人称手部渲染会把玩家渲染器强制转型成 {@code AvatarRenderer}。
 *
 * <p>实体本身完全不动 (服务端不改它的持久状态), 这里只做两件事:
 * <ol>
 *   <li>用猪的模型/纹理渲染它;</li>
 *   <li>把猪头藏起来, 在同一个位置渲染玩家自己皮肤的头 + 帽子层 + 头盔。</li>
 * </ol>
 *
 * <p>尺寸/视角的伪装在 MorphDimensionsMixin (碰撞箱) 与 MorphEyeHeightMixin (眼睛高度)。
 */
public class PigMorphRenderer extends AvatarRenderer<AbstractClientPlayer> {

    /** 原版猪的默认 (温带) 纹理。
     *  26.3 把文件从 1.21.8 的 temperate_pig.png 改名成了 pig_temperate.png;
     *  路径写错会直接显示成紫黑格子的缺失纹理。 */
    private static final Identifier PIG_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/pig/pig_temperate.png");

    private static final Logger LOGGER = LoggerFactory.getLogger("random-perks-bingo/client");

    private static EntityRendererProvider.Context capturedContext;

    /** 宽 / 细手臂两个实例 —— 第一人称手部渲染会用到玩家模型的臂宽 */
    private static PigMorphRenderer wideInstance;
    private static PigMorphRenderer slimInstance;
    private static boolean warnedNoContext;

    /** 纯外观问题只记一次日志, 免得每帧刷屏 */
    private static boolean warnedHeadFailure;
    private static boolean warnedRenderFailure;

    private final PigModel pigModel;
    private final ModelPart pigHead;
    private final PlayerModel headModel;

    /**
     * 玩家头 + 帽子 + 头盔的渲染状态。
     *
     * 头盔不是自己画的: 交给原版的 {@link HumanoidArmorLayer}, 它才认得护甲纹理、
     * 皮革染色、附魔光效和自定义 ArmorRenderer。唯一要喂给它的是"头上的装备"。
     */
    private final AvatarRenderState armorState = new AvatarRenderState();

    private final HumanoidArmorLayer<AvatarRenderState, PlayerModel,
            HumanoidModel<AvatarRenderState>> armorRenderer;

    private final PigRenderState pigState = new PigRenderState();

    private PigMorphRenderer(EntityRendererProvider.Context context, boolean slimArms) {
        super(context, slimArms);

        ModelPart pigRoot = context.bakeLayer(ModelLayers.PIG);
        this.pigModel = new PigModel(pigRoot);
        this.pigHead = pigRoot.getChild("head");

        this.headModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);

        // 头盔渲染器的"上下文模型"指向**我们自己的** headModel, 而不是原版玩家渲染器的那个。
        // 这样 renderArmor 内部 copyStateTo(armorModel) 时抄到的是我们已经对齐到猪头的姿势,
        // 头盔才会跟着猪头走。
        ArmorModelSet<HumanoidModel<AvatarRenderState>> armorModels = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR,
                context.getModelSet(),
                part -> new HumanoidModel<>(part));
        this.armorRenderer = new HumanoidArmorLayer<>(
                new HeadFeatureContext(this.headModel),
                armorModels,
                context.getEquipmentRenderer());

        // 只画头盔: 其余三个槽位保持空, renderArmor 遇到空槽会直接返回
        this.armorState.headEquipment = ItemStack.EMPTY;
        this.armorState.chestEquipment = ItemStack.EMPTY;
        this.armorState.legsEquipment = ItemStack.EMPTY;
        this.armorState.feetEquipment = ItemStack.EMPTY;
    }

    /** 由 RendererContextMixin 在 Context 构造完成后调用 */
    public static void captureContext(EntityRendererProvider.Context context) {
        capturedContext = context;
        // 资源重载会重建模型, 旧实例作废
        wideInstance = null;
        slimInstance = null;
    }

    /** 全局单例 (按手臂模型区分; Context 还没捕获到时返回 null) */
    public static PigMorphRenderer get(boolean slimArms) {
        if (capturedContext == null) {
            if (!warnedNoContext) {
                warnedNoContext = true;
                LOGGER.warn("[RandomPerks] pig morph: renderer context not captured");
            }
            return null;
        }

        if (slimArms) {
            if (slimInstance == null) slimInstance = new PigMorphRenderer(capturedContext, true);
            return slimInstance;
        }
        if (wideInstance == null) wideInstance = new PigMorphRenderer(capturedContext, false);
        return wideInstance;
    }

    @Override
    public void submit(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        // 这个渲染器只会被 MorphRendererMixin 交给"变猪中"的玩家
        // (它按 UUID 判断), 所以这里直接按猪渲染即可。
        boolean renderedAsPig = false;
        try {
            renderAsPig(state, matrices, collector);
            renderedAsPig = true;
        } catch (Throwable t) {
            if (!warnedRenderFailure) {
                warnedRenderFailure = true;
                LOGGER.error("[RandomPerks] pig morph: render failed, falling back to player model", t);
            }
            super.submit(state, matrices, collector, camera);
        }

        // 名牌由 EntityRenderer.submit 在模型之后提交。这里绕开了它, 单独补一次。
        if (renderedAsPig) {
            super.submitNameDisplay(state, matrices, collector, camera);
        }
    }

    private void renderAsPig(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector collector) {
        PlayerSkin skin = state.skin;

        matrices.pushPose();
        try {
            float baseScale = state.scale;
            matrices.scale(baseScale, baseScale, baseScale);
            matrices.rotateDegrees(Axis.YP, 180.0f - state.bodyRot);
            matrices.scale(-1.0f, -1.0f, 1.0f);
            matrices.translate(0.0, -1.501, 0.0);

            PigRenderState pigState = this.pigState;
            pigState.bodyRot = state.bodyRot;
            pigState.yRot = state.yRot;
            pigState.xRot = state.xRot;
            pigState.ageInTicks = state.ageInTicks;
            pigState.walkAnimationPos = state.walkAnimationPos;
            pigState.walkAnimationSpeed = state.walkAnimationSpeed;
            pigState.isBaby = false;

            this.pigModel.setupAnim(pigState);
            this.pigHead.visible = skin == null;
            collector.submitModel(
                    this.pigModel,
                    pigState,
                    matrices,
                    RenderTypes.entityCutout(PIG_TEXTURE),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor);

            if (skin != null) {
                try {
                    renderPlayerHead(state, matrices, collector, skin);
                } catch (Throwable t) {
                    if (!warnedHeadFailure) {
                        warnedHeadFailure = true;
                        LOGGER.error("[RandomPerks] pig morph: player-head render failed, falling back to pig head", t);
                    }
                    this.pigHead.visible = true;
                    collector.submitModelPart(
                            this.pigHead,
                            matrices,
                            RenderTypes.entityCutout(PIG_TEXTURE),
                            state.lightCoords,
                            OverlayTexture.NO_OVERLAY,
                            null);
                }
            }

            this.pigHead.visible = true;
        } finally {
            matrices.popPose();
        }
    }

    private void renderPlayerHead(AvatarRenderState state, PoseStack matrices,
                                  SubmitNodeCollector collector, PlayerSkin skin) {
        ModelPart head = this.headModel.head;

        head.loadPose(this.pigHead.storePose());
        head.xRot = state.xRot * 0.017453292f;
        head.yRot = state.yRot * 0.017453292f;
        this.headModel.hat.visible = state.showHat;

        matrices.pushPose();
        try {
            matrices.translate(0.0f, 0.25f, 0.0f);
            collector.submitModelPart(
                    head,
                    matrices,
                    RenderTypes.entityTranslucent(skin.body().texturePath()),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    null,
                    -1,
                    state.outlineColor);

            this.armorState.headEquipment = state.headEquipment;
            if (!state.headEquipment.isEmpty()) {
                this.armorRenderer.submit(matrices, collector, state.lightCoords, this.armorState, 0.0f, 0.0f);
            }
        } finally {
            matrices.popPose();
        }
    }

    /**
     * 喂给 {@link HumanoidArmorLayer} 的上下文: 模型用我们的 headModel。
     *
     * 1.21.7 的 FeatureRendererContext 只剩 getModel() —— 纹理由各自的渲染器自己解决
     * (头盔的纹理归 EquipmentRenderer 按护甲材质决定), 所以这里没有纹理回调。
     */
    private record HeadFeatureContext(PlayerModel model)
            implements RenderLayerParent<AvatarRenderState, PlayerModel> {

        @Override
        public PlayerModel getModel() {
            return this.model;
        }
    }
}
