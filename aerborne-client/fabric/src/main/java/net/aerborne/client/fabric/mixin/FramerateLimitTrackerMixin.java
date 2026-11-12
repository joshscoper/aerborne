package net.aerborne.client.fabric.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.aerborne.client.fabric.ClientState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the game window is open but not focused (alt-tabbed, or on a second screen), draw at most 30 frames a second.
 * Vanilla already slows down when minimised or idle; this covers the case in between and saves the GPU for whatever
 * the player switched to. Switch it off with "Throttle when unfocused" in the companion settings.
 */
@Mixin(FramerateLimitTracker.class)
abstract class FramerateLimitTrackerMixin {
    private static final int UNFOCUSED_LIMIT = 30;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void aerborne$unfocused(CallbackInfoReturnable<Integer> info) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.preferences.throttleUnfocused || mc.isWindowActive() || mc.level == null) return;
        info.setReturnValue(Math.min(info.getReturnValueI(), UNFOCUSED_LIMIT));
    }
}
