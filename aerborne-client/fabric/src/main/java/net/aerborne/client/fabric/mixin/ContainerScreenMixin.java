package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.AerborneMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A plugin menu is drawn on the Aerborne panel instead of the chest art. */
@Mixin(ContainerScreen.class)
abstract class ContainerScreenMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void aerborne$panel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (!AerborneMenus.skinned(this)) return;
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        // Keep the world dimmed behind the menu, as the vanilla screen does.
        graphics.fill(0, 0, ((ContainerScreen) (Object) this).width, ((ContainerScreen) (Object) this).height, 0x90000000);
        AerborneMenus.background(graphics, (ContainerScreen) (Object) this, screen.aerborne$left(), screen.aerborne$top(),
                screen.aerborne$width(), screen.aerborne$height());
        info.cancel();
    }
}
