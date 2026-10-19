package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.network.TrashItemC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "磁力升级" 附带的**垃圾桶栏位**（只在玩家背包界面里出现，需要持有该词条）。
 *
 * <p><b>为什么不真的加一个 Slot</b>: {@code ScreenHandler} 的槽位索引要求客户端与服务端
 * 严格一一对应 —— 只在一端多塞一个槽位，点击时索引就会越界，服务端直接踢人。现成的垃圾桶
 * mod（TrashSlot 等）走的也是同一条路: 槽位只是**画面**，真正的动作只有"把这个物品销毁"，
 * 由客户端上报、服务端执行。所以这里:
 *
 * <ul>
 *   <li>render: 在背包界面固定位置画一个 18×18 的格子</li>
 *   <li>mouseClicked: 光标上拿着物品时点它 → 发包 + 本地清光标（服务端随后同步确认）</li>
 * </ul>
 *
 * <p><b>注入目标是 InventoryScreen 而不是 HandledScreen</b>: InventoryScreen 自己
 * override 了 render / mouseClicked, 注入到基类对它的实例完全不生效 (之前垃圾桶
 * 从来不显示就是这个原因)。x / y 仍然通过 HandledScreen 上的 Accessor 读取。
 */
@Mixin(InventoryScreen.class)
public class TrashSlotMixin {

    private static final String PERK_ID = "seeker_magnet_upgrade";

    /** 格子边长 */
    private static final int SIZE = 18;

    /** 相对 GUI 左上角的位置 (背包界面里 crafting 输出下方、主背包上方那块空白) */
    private static final int OFFSET_X = 138;
    private static final int OFFSET_Y = 62;

    /** 格子里的图标 (岩浆桶 = 销毁) */
    private static final ItemStack ICON = new ItemStack(Items.LAVA_BUCKET);

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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void randomperks$trashClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (!ClientPerkFlags.has(PERK_ID)) return;

        int x = ((HandledScreenAccessor) this).randomperks$getX() + OFFSET_X;
        int y = ((HandledScreenAccessor) this).randomperks$getY() + OFFSET_Y;
        if (mouseX < x || mouseX >= x + SIZE || mouseY < y || mouseY >= y + SIZE) return;

        ScreenHandler handler = ((HandledScreen<?>) (Object) this).getScreenHandler();
        if (handler.getCursorStack().isEmpty()) return;

        // 用实例的 getId() 取通道 (companion 里的 ID 从 Java 侧是 private 的)
        TrashItemC2S payload = new TrashItemC2S();
        if (ClientPlayNetworking.canSend(payload.getId())) {
            ClientPlayNetworking.send(payload);
        }
        // 本地先清掉, 免得等回包时物品还粘在光标上
        handler.setCursorStack(ItemStack.EMPTY);
        cir.setReturnValue(true);
    }
}
