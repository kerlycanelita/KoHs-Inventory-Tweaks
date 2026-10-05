package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.FeatureLab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times the slot overlays for the overlay-perf lab. Pseudo, so a build from before
 * the overlays existed still loads the companion for A/B runs.
 */
@Pseudo
@Mixin(targets = "dev.zymekoh.kohsinventorytweaks.render.SlotOverlays", remap = false)
abstract class KoHsSlotOverlayTimingMixin {
	@Inject(method = "drawSlot", at = @At("HEAD"), remap = false, require = 0)
	private static void kohsInventoryDebug$drawStart(final CallbackInfo callbackInfo) {
		FeatureLab.overlayStart();
	}

	@Inject(method = "drawSlot", at = @At("RETURN"), remap = false, require = 0)
	private static void kohsInventoryDebug$drawEnd(final CallbackInfo callbackInfo) {
		FeatureLab.overlayEnd();
	}

	@Inject(method = "beginFrame", at = @At("HEAD"), remap = false, require = 0)
	private static void kohsInventoryDebug$frame(final CallbackInfo callbackInfo) {
		FeatureLab.overlayFrame();
	}
}
