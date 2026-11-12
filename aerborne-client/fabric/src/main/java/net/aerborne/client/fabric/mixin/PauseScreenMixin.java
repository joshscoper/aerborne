package net.aerborne.client.fabric.mixin;

import java.net.URI;
import net.aerborne.client.fabric.AerborneMenuScreen;
import net.aerborne.client.fabric.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Game Menu in Aerborne colours: the logo in the Aerborne frame and only what a player needs. Return to Game,
 * Options, the Discord and the website (each through the usual "open this link?" prompt) and Quit, which leaves
 * exactly as the vanilla button does (offering to send a drafted report first). The F3+Esc pause without a menu is
 * left alone.
 */
@Mixin(PauseScreen.class)
abstract class PauseScreenMixin extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("aerborne_client", "textures/gui/brand/logo.png");
    private static final URI DISCORD = URI.create("https://discord.gg/AUvpKyemCj"), WEBSITE = URI.create("https://aerborne.net");
    private static final int PANEL_WIDTH = 240, PANEL_HEIGHT = 206, BUTTON = 200;

    @Shadow @Final private boolean showPauseMenu;

    private PauseScreenMixin(Component title) { super(title); }

    private int aerborne$left() { return (width - PANEL_WIDTH) / 2; }
    private int aerborne$top() { return (height - PANEL_HEIGHT) / 2; }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void aerborne$menu(CallbackInfo info) {
        if (!showPauseMenu) return;
        info.cancel();
        Screen self = this;
        int x = width / 2 - BUTTON / 2, y = aerborne$top() + 76;
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y, BUTTON, 20, Component.translatable("menu.returnToGame"), null, 0, false,
                b -> { minecraft.gui.setScreen(null); minecraft.mouseHandler.grabMouse(); }));
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y + 24, BUTTON, 20, Component.translatable("menu.options"), null, 0, false,
                b -> minecraft.gui.setScreen(new OptionsScreen(self, minecraft.options, true))));
        int half = (BUTTON - 4) / 2;
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y + 48, half, 20, Component.literal("Discord"), Ui.control("envelope"), 14, false,
                ConfirmLinkScreen.confirmLink(self, DISCORD)));
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(x + half + 4, y + 48, half, 20, Component.literal("Website"), Ui.control("pin"), 14, false,
                ConfirmLinkScreen.confirmLink(self, WEBSITE)));
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y + 80, BUTTON, 20, Component.literal(minecraft.isLocalServer() ? "Save and Quit" : "Quit"),
                Ui.control("close"), 14, false, b -> {
                    b.active = false;
                    minecraft.getReportingContext().draftReportHandled(minecraft, self, () -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
                }));
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void aerborne$frame(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (!showPauseMenu) return;
        int left = aerborne$left(), top = aerborne$top();
        Ui.window(g, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        // The logo, 1024 by 267, at 200 wide.
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, width / 2 - 100, top + 18, 0, 0, 200, 52, 1024, 267, 1024, 267);
        g.fill(left + 24, top + 182, left + PANEL_WIDTH - 24, top + 183, 0x60F4C76B);
        String note = "Paused · the world carries on around you";
        if (minecraft.isLocalServer()) note = "Paused";
        g.text(font, note, width / 2 - font.width(note) / 2, top + 188, Ui.MUTED, false);
    }
}
