package net.aerborne.client.fabric.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos") int aerborne$left();
    @Accessor("topPos") int aerborne$top();
    @Accessor("imageWidth") int aerborne$width();
    @Accessor("imageHeight") int aerborne$height();
    @Accessor("titleLabelX") int aerborne$titleX();
    @Accessor("titleLabelY") int aerborne$titleY();
}
