package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftInputPollMixin {
	@Inject(
		method = "run",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;pollEvents()V",
			shift = At.Shift.AFTER
		)
	)
	private void kohsInventoryTweaks$afterCompleteGlfwInputPoll(final CallbackInfo callbackInfo) {
		SuperFastInventoryController.afterInputPoll((Minecraft) (Object) this);
	}
}
