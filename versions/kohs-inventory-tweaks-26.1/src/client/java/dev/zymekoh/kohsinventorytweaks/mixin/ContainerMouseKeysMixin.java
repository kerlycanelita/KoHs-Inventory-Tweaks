package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.ContainerKeys;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The inventory and drop keys bound to a mouse button work in containers, as their keyboard bindings do. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerMouseKeysMixin {
	@Inject(method = "mouseClicked", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;checkHotbarMouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;)V"),
		cancellable = true)
	private void kohsInventoryTweaks$inventoryAndDropButtons(
		final MouseButtonEvent event, final boolean doubleClick, final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if (ContainerKeys.mouseKeys((AbstractContainerScreen<?>) (Object) this, event)) {
			callbackInfo.setReturnValue(true);
		}
	}
}
