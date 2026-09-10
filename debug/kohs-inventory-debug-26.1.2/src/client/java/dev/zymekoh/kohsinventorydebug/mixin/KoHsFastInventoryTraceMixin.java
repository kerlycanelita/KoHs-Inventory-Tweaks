package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import dev.zymekoh.kohsinventorydebug.CloseHotbarRegressionLab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct observation of KoHs Fast Inventory decisions. */
@Mixin(
	targets = "dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController",
	priority = 3000,
	remap = false
)
abstract class KoHsFastInventoryTraceMixin {
	@Inject(method = "onKeyboardEvent", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$keyHead(
		final Minecraft minecraft,
		final long windowHandle,
		final int action,
		final KeyEvent event,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onFastKeyboard(minecraft, action, event, false);
	}

	@Inject(method = "onKeyboardEvent", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$keyReturn(
		final Minecraft minecraft,
		final long windowHandle,
		final int action,
		final KeyEvent event,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onFastKeyboard(minecraft, action, event, true);
	}

	@Inject(method = "onMouseButton", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$mouseHead(
		final Minecraft minecraft,
		final long windowHandle,
		final MouseButtonInfo info,
		final int action,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onFastMouse(minecraft, info, action, false);
	}

	@Inject(method = "onMouseButton", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$mouseReturn(
		final Minecraft minecraft,
		final long windowHandle,
		final MouseButtonInfo info,
		final int action,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onFastMouse(minecraft, info, action, true);
	}

	@Inject(method = "afterInputPoll", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$batchHead(final Minecraft minecraft, final CallbackInfo callbackInfo) {
		CloseHotbarRegressionLab.drainPoll(minecraft);
		DebugCollector.onFastBatch(minecraft, false);
	}

	@Inject(method = "afterInputPoll", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$batchReturn(final Minecraft minecraft, final CallbackInfo callbackInfo) {
		DebugCollector.onFastBatch(minecraft, true);
	}
}
