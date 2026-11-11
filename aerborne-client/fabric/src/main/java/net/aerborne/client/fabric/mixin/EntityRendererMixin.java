package net.aerborne.client.fabric.mixin;

import net.aerborne.client.fabric.AerborneNameplates;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives creatures the server lists an Aerborne nameplate: their name tag, with a health bar on the line beneath. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"))
    private void aerborne$nameplate(Entity entity, EntityRenderState state, float partial, CallbackInfo info) {
        Component[] lines = AerborneNameplates.lines(entity, state.distanceToCameraSq);
        if (lines == null) return;
        state.nameTag = lines[0];
        state.scoreText = lines[1];
        if (state.nameTagAttachment == null)
            state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partial));
    }
}
