package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.AerborneMenus;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The drop key does nothing while a locked hotbar slot is selected. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void aerborne$lockedDrop(boolean all, CallbackInfoReturnable<Boolean> info) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (AerborneMenus.lockedIndex(self.getInventory().getSelectedSlot())) {
            self.sendOverlayMessage(net.minecraft.network.chat.Component.literal("That slot is locked. Press L over it in your inventory to unlock."));
            info.setReturnValue(false);
        }
    }
}
