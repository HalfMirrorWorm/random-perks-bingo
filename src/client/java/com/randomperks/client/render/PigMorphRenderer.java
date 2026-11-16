package com.randomperks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
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
    private static boolean warnedRenderFailure;

    private final PigModel pigModel;
    private final ModelPart pigHead;

    /**
     * 每个玩家实体一份独立的玩家头模型。
     *
     * <p>26.3 的模型提交是延迟执行的: submitModelPart 只保存 ModelPart 引用, 真正渲染时
     * 才读取它的位置/旋转/visible。如果所有变猪玩家共用同一个 headModel, 一帧里后提交的
     * 玩家会覆盖前一个人的头部姿势, 导致多个变猪玩家时头部位置/朝向错乱。这里按实体 id
     * 缓存独立的模型实例, 让每次提交读到自己那份姿势。
     */
    private final EntityRendererProvider.Context context;
    private final Map<Integer, PlayerModel> headModels = new HashMap<>();

    private PigMorphRenderer(EntityRendererProvider.Context context, boolean slimArms) {
        super(context, slimArms);

        this.context = context;
        ModelPart pigRoot = context.bakeLayer(ModelLayers.PIG);
        this.pigModel = new PigModel(pigRoot);
        this.pigHead = pigRoot.getChild("head");
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
        // 皮肤还没加载时没有玩家头可画, 退回普通玩家模型 (至少不会出现无头猪)。
        if (state.skin == null) {
            super.submit(state, matrices, collector, camera);
            return;
        }

        boolean renderedAsPig = false;
        try {
            renderAsPig(state, matrices, collector, state.skin);
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

    private void renderAsPig(AvatarRenderState state, PoseStack matrices,
                             SubmitNodeCollector collector, PlayerSkin skin) {
        matrices.pushPose();
        try {
            float baseScale = state.scale;
            matrices.scale(baseScale, baseScale, baseScale);
            matrices.rotateDegrees(Axis.YP, 180.0f - state.bodyRot);
            matrices.scale(-1.0f, -1.0f, 1.0f);
            matrices.translate(0.0, -1.501, 0.0);

            PigRenderState pigState = new PigRenderState();
            pigState.bodyRot = state.bodyRot;
            pigState.yRot = state.yRot;
            pigState.xRot = state.xRot;
            pigState.ageInTicks = state.ageInTicks;
            pigState.walkAnimationPos = state.walkAnimationPos;
            pigState.walkAnimationSpeed = state.walkAnimationSpeed;
            pigState.isBaby = false;

            this.pigModel.setupAnim(pigState);
            // ★ 26.3 的模型提交是延迟执行的: submitModel 只存模型引用, 真正渲染时
            // 才读取 ModelPart.visible。这里必须在提交前隐藏猪头, 且**不能**在方法结束前
            // 恢复 visible —— 否则猪头 (包括 26.3 新增的鼻子方块) 会在本帧稍后重新出现,
            // 盖住玩家头。猪头隐藏后不再需要恢复; 玩家头由 renderPlayerHead 单独画在
            // 猪头原来的轴心上。
            this.pigHead.visible = false;
            collector.submitModel(
                    this.pigModel,
                    pigState,
                    matrices,
                    RenderTypes.entityCutout(PIG_TEXTURE),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor);

            // 猪头已经完全不渲染, 在它原来的位置画玩家自己的头。
            renderPlayerHead(state, matrices, collector, skin);
        } finally {
            matrices.popPose();
        }
    }

    private void renderPlayerHead(AvatarRenderState state, PoseStack matrices,
                                  SubmitNodeCollector collector, PlayerSkin skin) {
        PlayerModel headModel = this.headModels.computeIfAbsent(
                state.id,
                id -> new PlayerModel(this.context.bakeLayer(ModelLayers.PLAYER), false));
        ModelPart head = headModel.head;

        head.loadPose(this.pigHead.storePose());
        head.xRot = state.xRot * 0.017453292f;
        head.yRot = state.yRot * 0.017453292f;
        headModel.hat.visible = state.showHat;

        matrices.pushPose();
        try {
            // 猪头: 轴心 z=-6, 立方体 z ∈ [-8, 0] (中心 -4)
            // 玩家头: 立方体 z ∈ [-4, 4] (中心 0) —— 需要 Y +4、Z -4 模型单位。
            //
            // 关键: 这个偏移必须跟着头的旋转一起转。如果直接 translate(0, 0.25, -0.25),
            // 只有头朝正前方时两个立方体中心才重合; 玩家一转头/抬头, 玩家头就会相对
            // 猪头漂移。这里用 head 的 ZYX 旋转把局部偏移转到父空间, 与 ModelPart
            // translateAndRotate 的旋转顺序一致。
            Quaternionf headRotation = new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot);
            Vector3f headOffset = headRotation.transform(new Vector3f(0.0f, 0.25f, -0.25f));
            matrices.translate(headOffset.x, headOffset.y, headOffset.z);
            collector.submitModelPart(
                    head,
                    matrices,
                    RenderTypes.entityTranslucent(skin.body().texturePath()),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    null,
                    -1,
                    state.outlineColor);
        } finally {
            matrices.popPose();
        }
    }
}
