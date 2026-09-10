package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes raw mouse callbacks and capture transitions without owning the cursor. */
@Mixin(value = MouseHandler.class, priority = 3000)
abstract class MouseHandlerTraceMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void kohsInventoryDebug$button(
		final long windowHandle,
		final MouseButtonInfo info,
		final int action,
		final CallbackInfo callbackInfo
	) {
		if (windowHandle == this.minecraft.getWindow().handle()) {
			DebugCollector.onMouseButton(this.minecraft, info, action);
		}
	}

	@Inject(method = "onMove", at = @At("HEAD"))
	private void kohsInventoryDebug$move(
		final long windowHandle,
		final double x,
		final double y,
		final CallbackInfo callbackInfo
	) {
		if (windowHandle == this.minecraft.getWindow().handle()) {
			DebugCollector.onMouseMove(this.minecraft, x, y);
		}
	}

	@Inject(method = "grabMouse", at = @At("HEAD"))
	private void kohsInventoryDebug$grabHead(final CallbackInfo callbackInfo) {
		DebugCollector.onMouseGrab(this.minecraft, true, false);
	}

	@Inject(method = "grabMouse", at = @At("RETURN"))
	private void kohsInventoryDebug$grabReturn(final CallbackInfo callbackInfo) {
		DebugCollector.onMouseGrab(this.minecraft, true, true);
	}

	@Inject(method = "releaseMouse", at = @At("HEAD"))
	private void kohsInventoryDebug$releaseHead(final CallbackInfo callbackInfo) {
		DebugCollector.onMouseGrab(this.minecraft, false, false);
	}

	@Inject(method = "releaseMouse", at = @At("RETURN"))
	private void kohsInventoryDebug$releaseReturn(final CallbackInfo callbackInfo) {
		DebugCollector.onMouseGrab(this.minecraft, false, true);
	}
}
