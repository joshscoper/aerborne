package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.ClientState;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chat lines sit on translucent Aerborne navy instead of black, at the same opacity the player chose. */
@Mixin(ChatComponent.class)
abstract class ChatStyleMixin {
    private static int aerborne$navy(float alpha) {
        return ClientState.preferences.styledChat ? ARGB.color(Math.round(alpha * 255), 0x14213A) : ARGB.black(alpha);
    }

    @Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;black(F)I"), require = 0)
    private int aerborne$panel(float alpha) { return aerborne$navy(alpha); }

    @Redirect(method = "lambda$extractRenderState$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;black(F)I"), require = 0)
    private static int aerborne$line(float alpha) { return aerborne$navy(alpha); }
}
