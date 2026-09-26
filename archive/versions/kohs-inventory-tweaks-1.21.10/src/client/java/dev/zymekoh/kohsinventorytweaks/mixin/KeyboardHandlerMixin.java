package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$ignoreInventoryAutoRepeat(
		final long windowHandle, final int action, final KeyEvent event, final CallbackInfo callbackInfo
	) {
		if (SuperFastInventoryController.suppressInventoryKeyRepeat(this.minecraft, windowHandle, action, event)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "keyPress", at = @At("TAIL"))
	private void kohsInventoryTweaks$openInventoryWithoutTickDelay(
		final long handle,
		final int action,
		final KeyEvent event,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.onKeyboardEvent(this.minecraft, handle, action, event);
	}
}
