package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.SlotTargeting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolves shortcut input against the latest delivered pointer, without waiting for a render. */
@Mixin(AbstractContainerScreen.class)
public abstract class InventorySlotInputMixin {
	@Inject(method = "keyPressed", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;checkHotbarKeyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
	private void kohsInventoryTweaks$refreshKeyboardTarget(final KeyEvent event, final CallbackInfoReturnable<Boolean> cir) {
		SlotTargeting.refreshFromPointer((AbstractContainerScreen<?>) (Object) this);
	}

	@Inject(method = "checkHotbarMouseClicked", at = @At("HEAD"))
	private void kohsInventoryTweaks$refreshMouseBindingTarget(final MouseButtonEvent event, final CallbackInfo ci) {
		// The inherited click path already converted this event to inventory coordinates.
		SlotTargeting.refresh((AbstractContainerScreen<?>) (Object) this, event.x(), event.y(), false);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"))
	private void kohsInventoryTweaks$refreshScrollTarget(final double x, final double y,
		final double horizontal, final double vertical, final CallbackInfoReturnable<Boolean> cir) {
		SlotTargeting.refresh((AbstractContainerScreen<?>) (Object) this, x, y, false);
	}
}
