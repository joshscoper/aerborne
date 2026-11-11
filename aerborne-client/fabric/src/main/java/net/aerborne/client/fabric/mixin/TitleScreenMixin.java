package net.aerborne.client.fabric.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The title screen shows the Aerborne sky kingdom in place of Minecraft's turning panorama. The painting covers
 * the whole window at any shape: it is scaled to fill and its overflow is cropped evenly from both sides.
 * The wordmark itself is swapped by resource (textures/gui/title/minecraft.png), not here.
 */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("aerborne_client", "textures/gui/brand/title_background.png");
    private static final int TEXTURE_WIDTH = 1536, TEXTURE_HEIGHT = 1024;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
    private void aerborne$paintBackground(TitleScreen screen, GuiGraphicsExtractor graphics, float delta) {
        int width = screen.width, height = screen.height;
        // The part of the painting with the window's shape, centred.
        int regionWidth = TEXTURE_WIDTH, regionHeight = TEXTURE_HEIGHT;
        if ((long) width * TEXTURE_HEIGHT > (long) height * TEXTURE_WIDTH) regionHeight = Math.max(1, TEXTURE_WIDTH * height / width);
        else regionWidth = Math.max(1, TEXTURE_HEIGHT * width / height);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0,
                (TEXTURE_WIDTH - regionWidth) / 2F, (TEXTURE_HEIGHT - regionHeight) / 2F,
                width, height, regionWidth, regionHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
