package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame, tick and screen lifecycle instrumentation. */
@Mixin(value = Minecraft.class, priority = 3000)
abstract class MinecraftTraceMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void kohsInventoryDebug$frameStart(final boolean advanceGameTime, final CallbackInfo callbackInfo) {
		DebugCollector.onFrameStart();
	}

	@Inject(method = "runTick", at = @At("RETURN"))
	private void kohsInventoryDebug$frameEnd(final boolean advanceGameTime, final CallbackInfo callbackInfo) {
		DebugCollector.onFrameEnd((Minecraft) (Object) this);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void kohsInventoryDebug$tickStart(final CallbackInfo callbackInfo) {
		DebugCollector.onTickStart();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void kohsInventoryDebug$tickEnd(final CallbackInfo callbackInfo) {
		DebugCollector.onTickEnd((Minecraft) (Object) this);
	}

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void kohsInventoryDebug$screenHead(final Screen screen, final CallbackInfo callbackInfo) {
		DebugCollector.onSetScreen((Minecraft) (Object) this, screen, false);
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void kohsInventoryDebug$screenReturn(final Screen screen, final CallbackInfo callbackInfo) {
		DebugCollector.onSetScreen((Minecraft) (Object) this, screen, true);
	}
}
