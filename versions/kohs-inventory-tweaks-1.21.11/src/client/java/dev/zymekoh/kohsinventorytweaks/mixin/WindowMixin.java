package dev.zymekoh.kohsinventorytweaks.mixin;

import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
	@Inject(method = "updateDisplay", at = @At("RETURN"))
	private void kohsInventoryTweaks$afterCompleteGlfwInputPoll(
		final TracyFrameCapture frameCapture,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.afterInputPoll(Minecraft.getInstance());
	}
}
