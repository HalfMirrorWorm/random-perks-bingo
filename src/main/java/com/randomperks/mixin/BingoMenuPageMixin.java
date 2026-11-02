package com.randomperks.mixin;

import com.randomperks.recap.RecapService;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import me.jfenn.bingo.common.menu.ButtonKt;
import me.jfenn.bingo.common.menu.CommonKt;
import me.jfenn.bingo.common.menu.ConstantProperty;
import me.jfenn.bingo.common.menu.MenuComponent;
import me.jfenn.bingo.platform.IPlayerHandle;
import me.jfenn.bingo.platform.text.IText;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

/**
 * 把大厅「开始游戏」按钮上下对切。
 *
 * <p>上半仍是开始游戏, 下半是「上局回顾」, 外框还是原来那一块。
 * 中间留一条 0.05 的缝, 两块混凝土才不会贴成一个按钮。
 *
 * <p>bingo 的按钮坐标是底边, 方块从底边向上长。对切时下半留在原底边,
 * 上半的底边抬到缝的上面。必须在 {@code createRootPage$lambda$0} 里改,
 * 这个 lambda 才是真正登记开始按钮的地方。
 */
@Mixin(targets = "me.jfenn.bingo.common.menu.MenuPageKt", remap = false)
public class BingoMenuPageMixin {

    /** 两块按钮之间的缝, 避免贴在一起分不出点击区域 */
    private static final double SPLIT_GAP = 0.05;

    private static final String TILE_BUTTON =
            "Lme/jfenn/bingo/common/menu/ButtonKt;registerTileButton$default("
                    + "Lme/jfenn/bingo/common/menu/MenuComponent;"
                    + "Lorg/joml/Vector3d;"
                    + "Ljava/lang/String;"
                    + "Lme/jfenn/bingo/platform/text/IText;"
                    + "Lme/jfenn/bingo/common/menu/Property;"
                    + "F"
                    + "Ljava/util/List;"
                    + "Lme/jfenn/bingo/common/menu/Property;"
                    + "Lme/jfenn/bingo/common/menu/Property;"
                    + "Ljava/lang/String;"
                    + "Lme/jfenn/bingo/platform/IDisplayEntity$Brightness;"
                    + "DD"
                    + "Lkotlin/jvm/functions/Function1;"
                    + "Lkotlin/jvm/functions/Function1;"
                    + "ILjava/lang/Object;)V";

    /**
     * 2.15 的 {@code createRootPage$lambda$0} 里第一次 registerTileButton 是开始游戏
     * (StringKey.OptionsStart), 第二次才是「重置默认」。
     */
    @ModifyArgs(
            method = "createRootPage$lambda$0",
            at = @At(value = "INVOKE", target = TILE_BUTTON, ordinal = 0),
            remap = false
    )
    private static void randomperks$splitStartButton(Args args) {
        MenuComponent component = args.get(0);
        Vector3d position = args.get(1);
        double width = args.get(11);
        double height = args.get(12);
        if (component == null || position == null || height <= SPLIT_GAP) {
            return;
        }

        double half = (height - SPLIT_GAP) / 2.0;
        Vector3d bottom = new Vector3d(position);

        IText label = component.getText().translatable(
                "random-perks-bingo.recap.title", "上局回顾"
        );
        List<IText> tooltip = List.of(
                component.getText().translatable("random-perks-bingo.recap.title", "上局回顾")
                        .formatted(ChatFormatting.GREEN),
                component.getText().translatable(
                        "random-perks-bingo.recap.menu.tooltip",
                        "查看上一局的词条选取和队伍完成顺序"
                )
        );
        Function1<IPlayerHandle, Boolean> everyone = player -> Boolean.TRUE;
        Function1<IPlayerHandle, Unit> onClick = player -> {
            ServerPlayer serverPlayer = player.getPlayer();
            if (serverPlayer != null) {
                RecapService.INSTANCE.openFor(serverPlayer);
            }
            return Unit.INSTANCE;
        };

        // 下半: 底边不动
        ButtonKt.registerTileButton(
                component,
                bottom,
                null,
                label,
                new ConstantProperty<>(label),
                CommonKt.MENU_TEXT_SCALE,
                tooltip,
                new ConstantProperty<>(tooltip),
                new ConstantProperty<>(false),
                null,
                CommonKt.getMENU_BRIGHTNESS_ALT(),
                width,
                half,
                everyone,
                onClick
        );

        // 上半: 开始游戏, 底边抬到缝上面, 高度改成一半
        args.set(1, new Vector3d(bottom).add(0.0, half + SPLIT_GAP, 0.0));
        args.set(12, half);
    }
}
