package net.aerborne.client.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The survival inventory draws its own "Crafting" label; on the restyled dark art it is drawn in gold. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void aerborne$labels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo info) {
        var screen = (AbstractContainerScreenAccessor) this;
        graphics.text(Minecraft.getInstance().font, ((InventoryScreen) (Object) this).getTitle(),
                screen.aerborne$titleX(), screen.aerborne$titleY(), 0xFFF4C76B, false);
        info.cancel();
    }
}
