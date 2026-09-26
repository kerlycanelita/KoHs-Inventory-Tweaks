package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
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

	@Inject(method = "keyPress", at = @At("TAIL"))
	private void kohsInventoryTweaks$openInventoryWithoutTickDelay(
		final long handle,
		final int keyCode,
		final int scanCode,
		final int action,
		final int modifiers,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.onKeyboardEvent(this.minecraft, handle, keyCode, scanCode, action);
	}
}
