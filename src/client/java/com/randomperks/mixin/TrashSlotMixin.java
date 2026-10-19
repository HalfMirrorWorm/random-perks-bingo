package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "磁力升级" 附带的**垃圾桶栏位**（只在玩家背包界面里出现，需要持有该词条）—— 只负责绘制。
 *
 * <p><b>为什么不真的加一个 Slot</b>: {@code ScreenHandler} 的槽位索引要求客户端与服务端
 * 严格一一对应 —— 只在一端多塞一个槽位，点击时索引就会越界，服务端直接踢人。现成的垃圾桶
 * mod（TrashSlot 等）走的也是同一条路: 槽位只是**画面**，真正的动作只有"把这个物品销毁"，
 * 由客户端上报、服务端执行。
 *
 * <p><b>绘制注入 InventoryScreen</b>: InventoryScreen 自己 override 了 render, 注入到
 * HandledScreen 的 render 不会在 TAIL 跑到 (它被覆盖了)。
 *
 * <p><b>点击在 {@link TrashSlotClickMixin} 里</b>: mouseClicked 是 Inherited 方法
 * (声明在 HandledScreen, InventoryScreen 没有覆盖)。Loom 的 mixin 重映射只查目标类
 * **自己声明**的成员, 写成 {@code @Mixin(InventoryScreen.class)} + method="mouseClicked"
 * 会匹配不到 intermediary 名, 运行时注入失败。所以点击必须挂在 HandledScreen 上。
 *
 * <p>x / y 通过 HandledScreen 上的 Accessor 读取。
 */
@Mixin(InventoryScreen.class)
public class TrashSlotMixin {

    static final String PERK_ID = "seeker_magnet_upgrade";

    /** 格子边长 */
    static final int SIZE = 18;

    /** 相对 GUI 左上角的位置 (背包界面里 crafting 输出下方、主背包上方那块空白) */
    static final int OFFSET_X = 138;
    static final int OFFSET_Y = 62;

    /** 格子里的图标 (岩浆桶 = 销毁) */
    static final ItemStack ICON = new ItemStack(Items.LAVA_BUCKET);

    @Inject(method = "render", at = @At("TAIL"))
    private void randomperks$renderTrash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!ClientPerkFlags.has(PERK_ID)) return;

        int x = ((HandledScreenAccessor) this).randomperks$getX() + OFFSET_X;
        int y = ((HandledScreenAccessor) this).randomperks$getY() + OFFSET_Y;

        // 凹槽式背景: 主体 + 左上两道深色边
        context.fill(x, y, x + SIZE, y + SIZE, 0xFF8B8B8B);
        context.fill(x, y, x + SIZE, y + 1, 0xFF373737);
        context.fill(x, y, x + 1, y + SIZE, 0xFF373737);
        context.drawItem(ICON, x + 1, y + 1);
    }
}
