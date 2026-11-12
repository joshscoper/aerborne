package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.AerborneMenus;
import net.aerborne.client.fabric.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every container screen: rarity colour behind items, the item lock (L over a slot; locked slots cannot be
 * clicked, swapped into or thrown), and the search box that dims what does not match. In a plugin menu also:
 * filler panes are not drawn, highlighted or given a tooltip; navigation items show as icon buttons; and the
 * title is drawn by AerborneMenus instead of the vanilla labels.
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow protected int titleLabelX, titleLabelY, inventoryLabelX, inventoryLabelY;
    @Shadow @org.spongepowered.asm.mixin.Final protected net.minecraft.network.chat.Component playerInventoryTitle;

    private boolean aerborne$filler(Slot slot) {
        return slot != null && AerborneMenus.skinned(this) && AerborneMenus.filler(slot.getItem());
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void aerborne$slot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
        if (AerborneMenus.skinned(this)) {
            if (AerborneMenus.filler(slot.getItem())) { info.cancel(); return; }
            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                var icon = AerborneMenus.navigation(slot.getItem());
                if (icon != null) { AerborneMenus.navigationButton(graphics, icon, slot.x, slot.y); info.cancel(); return; }
            }
        }
        if (ClientState.preferences.rarity) {
            String rarity = AerborneMenus.rarityName(slot.getItem());
            if (rarity != null) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x30000000 | AerborneMenus.rarityColour(slot.getItem()));
                graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.aerborne.client.fabric.Ui.rarity(rarity),
                        slot.x - 1, slot.y - 1, 18, 18);
            }
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void aerborne$slotAfter(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
        if (AerborneMenus.hiddenBySearch(this, slot.getItem())) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xC00D1A2B);
        if (AerborneMenus.locked(slot)) AerborneMenus.lockBadge(graphics, slot.x, slot.y);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void aerborne$labels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo info) {
        if (AerborneMenus.skinned(this)) { info.cancel(); return; }
        // The restyled container art is dark, so its labels are drawn light: the title in gold, the bag in mist.
        var font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.text(font, ((net.minecraft.client.gui.screens.Screen) (Object) this).getTitle(), titleLabelX, titleLabelY, 0xFFF4C76B, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFACBBD0, false);
        info.cancel();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void aerborne$highlight(GuiGraphicsExtractor graphics, CallbackInfo info) {
        if (aerborne$filler(hoveredSlot)) info.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void aerborne$tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo info) {
        if (aerborne$filler(hoveredSlot)) info.cancel();
    }

    /** A locked slot ignores clicks, and nothing can be swapped into or out of a locked hotbar slot. */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void aerborne$lock(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo info) {
        if (AerborneMenus.locked(slot) || input == ContainerInput.SWAP && AerborneMenus.lockedIndex(button)) info.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void aerborne$keys(KeyEvent event, CallbackInfoReturnable<Boolean> info) {
        var search = AerborneMenus.search(this);
        if (search != null && search.isFocused()) {
            // Typing in the search box must not close the screen or trigger hotbar keys.
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); info.setReturnValue(true); return; }
            search.keyPressed(event);
            info.setReturnValue(true);
            return;
        }
        if (event.key() == GLFW.GLFW_KEY_L && AerborneMenus.lockIndex(hoveredSlot) >= 0) {
            AerborneMenus.toggleLock(hoveredSlot);
            info.setReturnValue(true);
        }
    }
}
