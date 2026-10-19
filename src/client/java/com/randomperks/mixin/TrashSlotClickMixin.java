package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.network.TrashItemC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "磁力升级" 垃圾桶栏位的**点击**处理（绘制见 {@link TrashSlotMixin}）。
 *
 * <p>必须挂在 {@link HandledScreen} 上: {@code mouseClicked} 声明在 HandledScreen,
 * InventoryScreen 并没有覆盖它。Loom 的 mixin 重映射只查目标类自己声明的成员,
 * 若写成 {@code @Mixin(InventoryScreen.class)} + {@code method = "mouseClicked"},
 * 注解值不会被重映射成 intermediary 名, 运行时注入会失败。这里用 instanceof 限流,
 * 效果等价于只对背包界面生效。
 */
@Mixin(HandledScreen.class)
public class TrashSlotClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void randomperks$trashClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (!((Object) this instanceof InventoryScreen)) return;
        if (!ClientPerkFlags.has(TrashSlotMixin.PERK_ID)) return;

        int x = ((HandledScreenAccessor) this).randomperks$getX() + TrashSlotMixin.OFFSET_X;
        int y = ((HandledScreenAccessor) this).randomperks$getY() + TrashSlotMixin.OFFSET_Y;
        if (mouseX < x || mouseX >= x + TrashSlotMixin.SIZE || mouseY < y || mouseY >= y + TrashSlotMixin.SIZE) return;

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
