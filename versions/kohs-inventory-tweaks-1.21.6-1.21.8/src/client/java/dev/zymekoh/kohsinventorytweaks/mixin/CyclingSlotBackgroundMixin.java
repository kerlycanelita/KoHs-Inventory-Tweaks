package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CyclingSlotBackground.class)
public abstract class CyclingSlotBackgroundMixin {
	@Shadow private int tick;
	@Shadow private int iconIndex;

	@Inject(method = "tick", at = @At("RETURN"))
	private void kohsInventoryTweaks$keepFirstSlotIcon(
		final List<ResourceLocation> icons,
		final CallbackInfo callbackInfo
	) {
		if (InventoryAnimationController.suppressAllInventoryAnimations()) {
			this.tick = 0;
			this.iconIndex = 0;
		}
	}
}

