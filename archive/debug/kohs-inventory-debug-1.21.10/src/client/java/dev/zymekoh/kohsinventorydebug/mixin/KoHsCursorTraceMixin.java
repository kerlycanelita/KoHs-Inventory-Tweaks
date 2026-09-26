package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct observation of every KoHs cursor placement stage. */
@Mixin(
	targets = "dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController",
	priority = 3000,
	remap = false
)
abstract class KoHsCursorTraceMixin {
	@Inject(method = "onScreenRequested", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$requestHead(final Screen screen, final CallbackInfo callbackInfo) {
		DebugCollector.onCursorRequested(screen, false);
	}

	@Inject(method = "onScreenRequested", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$requestReturn(final Screen screen, final CallbackInfo callbackInfo) {
		DebugCollector.onCursorRequested(screen, true);
	}

	@Inject(method = "overrideReleasePosition", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$releaseHead(
		final Minecraft minecraft,
		final CallbackInfoReturnable<double[]> callbackInfo
	) {
		DebugCollector.onCursorRelease(minecraft, false, null);
	}

	@Inject(method = "overrideReleasePosition", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$releaseReturn(
		final Minecraft minecraft,
		final CallbackInfoReturnable<double[]> callbackInfo
	) {
		DebugCollector.onCursorRelease(minecraft, true, callbackInfo.getReturnValue());
	}

	@Inject(method = "onContainerScreenInitialized", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$initHead(
		final Minecraft minecraft,
		final Screen screen,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onCursorContainerInit(minecraft, screen, false);
	}

	@Inject(method = "onContainerScreenInitialized", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$initReturn(
		final Minecraft minecraft,
		final Screen screen,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onCursorContainerInit(minecraft, screen, true);
	}

	@Inject(method = "warp", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$warpHead(
		final Minecraft minecraft,
		final double[] position,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onCursorWarp(minecraft, position, false);
	}

	@Inject(method = "warp", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$warpReturn(
		final Minecraft minecraft,
		final double[] position,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onCursorWarp(minecraft, position, true);
	}
}
