package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Raw Input Buffer's native thread from completing an in-flight
 * camera-centering operation after Minecraft has already opened a GUI.
 */
@Pseudo
@Mixin(targets = "walksy.rawinput.RawInputHandler", remap = false)
public abstract class RawInputBufferMixin {
	@Inject(method = "centerSystemCursor", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void kohsInventoryTweaks$keepGuiCursorStable(final CallbackInfo callbackInfo) {
		if (CursorLandingController.shouldSuppressNativeCursorCentering()) {
			callbackInfo.cancel();
		}
	}
}

