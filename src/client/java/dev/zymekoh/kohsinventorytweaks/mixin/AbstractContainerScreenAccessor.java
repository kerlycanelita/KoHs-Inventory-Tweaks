package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int kohsInventoryTweaks$getLeftPos();

	@Accessor("topPos")
	int kohsInventoryTweaks$getTopPos();

	@Accessor("imageWidth")
	int kohsInventoryTweaks$getImageWidth();

	@Accessor("imageHeight")
	int kohsInventoryTweaks$getImageHeight();

	@Accessor("hoveredSlot")
	@Nullable Slot kohsInventoryTweaks$getHoveredSlot();

	@Accessor("hoveredSlot")
	void kohsInventoryTweaks$setHoveredSlot(@Nullable Slot slot);

	@Invoker("getHoveredSlot")
	@Nullable Slot kohsInventoryTweaks$findHoveredSlot(double x, double y);

	@Invoker("onStopHovering")
	void kohsInventoryTweaks$stopHovering(Slot slot);
}
