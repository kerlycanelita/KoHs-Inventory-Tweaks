package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DynamicAtlasAllocator.Slot.class)
public interface AtlasSlotAccessor {
	@Accessor("externalState")
	void kohsInventoryTweaks$setExternalState(DynamicAtlasAllocator.SlotState state);
}
