package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$removeAnimatedInventoryFoil(
		final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if (InventoryAnimationController.suppressAnimatedFoil()) {
			callbackInfo.setReturnValue(false);
		}
	}
}

