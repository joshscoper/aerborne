package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.AerborneMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a plugin menu: filler panes are not drawn, highlighted or given a tooltip; navigation items show as icon
 * buttons; and the title is drawn by AerborneMenus instead of the vanilla labels.
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    private boolean aerborne$filler(Slot slot) {
        return slot != null && AerborneMenus.skinned(this) && AerborneMenus.filler(slot.getItem());
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void aerborne$slot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
        if (!AerborneMenus.skinned(this)) return;
        if (AerborneMenus.filler(slot.getItem())) { info.cancel(); return; }
        if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) {
            var icon = AerborneMenus.navigation(slot.getItem());
            if (icon != null) { AerborneMenus.navigationButton(graphics, icon, slot.x, slot.y); info.cancel(); }
        }
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void aerborne$labels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo info) {
        if (AerborneMenus.skinned(this)) info.cancel();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void aerborne$highlight(GuiGraphicsExtractor graphics, CallbackInfo info) {
        if (aerborne$filler(hoveredSlot)) info.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void aerborne$tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo info) {
        if (aerborne$filler(hoveredSlot)) info.cancel();
    }
}
