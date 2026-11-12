package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.ClientState;
import net.aerborne.client.fabric.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The chat input sits in the Aerborne inset frame instead of a flat dark strip. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), require = 0)
    private void aerborne$input(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int colour) {
        if (!ClientState.preferences.styledChat) { graphics.fill(x1, y1, x2, y2, colour); return; }
        Ui.inset(graphics, x1 - 1, y1 - 3, x2 - x1 + 2, y2 - y1 + 5);
    }
}
