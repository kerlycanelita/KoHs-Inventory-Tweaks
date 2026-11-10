package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records only gameplay mappings relevant to inventory diagnosis, never typed text. */
@Mixin(value = KeyboardHandler.class, priority = 3000)
abstract class KeyboardHandlerTraceMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"))
	private void kohsInventoryDebug$key(
		final long windowHandle,
		final int action,
		final KeyEvent event,
		final CallbackInfo callbackInfo
	) {
		if (windowHandle == this.minecraft.getWindow().handle()) {
			DebugCollector.onPhysicalKey(this.minecraft, action, event);
		}
	}

	@Inject(method = "keyPress", at = @At("RETURN"))
	private void kohsInventoryDebug$keyReturn(
		final long windowHandle, final int action, final KeyEvent event, final CallbackInfo callbackInfo
	) {
		if (windowHandle == this.minecraft.getWindow().handle()) {
			DebugCollector.onKeyboardReturn(this.minecraft, action, event);
		}
	}
}
