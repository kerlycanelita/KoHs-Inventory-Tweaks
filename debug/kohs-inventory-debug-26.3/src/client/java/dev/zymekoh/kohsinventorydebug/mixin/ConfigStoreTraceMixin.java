package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs configuration persistence boundaries without modifying the configuration. */
@Mixin(
	targets = "dev.zymekoh.kohsinventorytweaks.config.ConfigStore",
	priority = 3000,
	remap = false
)
abstract class ConfigStoreTraceMixin {
	@Inject(method = "load", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$loadHead(final CallbackInfo callbackInfo) {
		DebugCollector.onConfigEvent("load", false);
	}

	@Inject(method = "load", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$loadReturn(final CallbackInfo callbackInfo) {
		DebugCollector.onConfigEvent("load", true);
	}

	@Inject(method = "save", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$saveHead(final CallbackInfo callbackInfo) {
		DebugCollector.onConfigEvent("save", false);
	}

	@Inject(method = "save", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$saveReturn(final CallbackInfo callbackInfo) {
		DebugCollector.onConfigEvent("save", true);
	}

	@Inject(method = "replaceAndSave", at = @At("HEAD"), remap = false)
	private static void kohsInventoryDebug$replaceHead(final CallbackInfo callbackInfo) {
		DebugCollector.onConfigEvent("replaceAndSave", false);
	}

	@Inject(method = "replaceAndSave", at = @At("RETURN"), remap = false)
	private static void kohsInventoryDebug$replaceReturn(final CallbackInfo callbackInfo) {
		DebugCollector.onConfigEvent("replaceAndSave", true);
	}
}
