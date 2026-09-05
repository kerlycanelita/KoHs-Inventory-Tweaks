package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
	@Shadow public float flip;
	@Shadow public float oFlip;
	@Shadow public float flipT;
	@Shadow public float flipA;
	@Shadow public float open;
	@Shadow public float oOpen;

	@Inject(method = "tickBook", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$freezeBook(final CallbackInfo callbackInfo) {
		if (!InventoryAnimationController.suppressAllInventoryAnimations()) {
			return;
		}
		this.flip = 0.0F;
		this.oFlip = 0.0F;
		this.flipT = 0.0F;
		this.flipA = 0.0F;
		this.open = 0.0F;
		this.oOpen = 0.0F;
		callbackInfo.cancel();
	}
}
