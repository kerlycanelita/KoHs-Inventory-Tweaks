package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
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

	@Invoker("slotClicked")
	void kohsInventoryTweaks$invokeSlotClicked(Slot slot, int slotId, int buttonNum, ClickType clickType);
}
