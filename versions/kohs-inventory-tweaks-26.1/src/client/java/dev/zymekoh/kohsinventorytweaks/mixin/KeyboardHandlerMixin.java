package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.InputFence;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Applied late so its HEAD hook runs first: a key the fence keeps is not seen twice by others.
@Mixin(value = KeyboardHandler.class, priority = 2000)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$ignoreInventoryAutoRepeat(
		final long windowHandle, final int action, final KeyEvent event, final CallbackInfo callbackInfo
	) {
		// An inventory opened ahead of its tick: what is done in it waits for that tick.
		if (InputFence.holdKey(this.minecraft, windowHandle, action, event)
			|| SuperFastInventoryController.suppressInventoryKeyRepeat(this.minecraft, windowHandle, action, event)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$keepEarlyTyping(
		final long windowHandle, final CharacterEvent event, final CallbackInfo callbackInfo
	) {
		if (InputFence.holdChar(this.minecraft, windowHandle, event)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "keyPress", at = @At("TAIL"))
	private void kohsInventoryTweaks$openLocalInventoryOnPhysicalPress(
		final long windowHandle,
		final int action,
		final KeyEvent event,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.onKeyboardEvent(this.minecraft, windowHandle, action, event);
	}
}
