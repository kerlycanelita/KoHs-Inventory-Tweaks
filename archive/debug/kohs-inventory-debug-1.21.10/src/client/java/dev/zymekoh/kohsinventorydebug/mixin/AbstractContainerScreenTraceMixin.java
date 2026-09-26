package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Container initialization, key and slot-action timing. */
@Mixin(value = AbstractContainerScreen.class, priority = 3000)
abstract class AbstractContainerScreenTraceMixin {
	@Unique
	private Slot kohsInventoryDebug$slot;

	@Inject(method = "init", at = @At("RETURN"))
	private void kohsInventoryDebug$init(final CallbackInfo callbackInfo) {
		DebugCollector.onContainerInitialized((AbstractContainerScreen<?>) (Object) this);
	}

	@Inject(method = "slotClicked", at = @At("HEAD"))
	private void kohsInventoryDebug$slotHead(
		final Slot slot,
		final int slotId,
		final int button,
		final ClickType input,
		final CallbackInfo callbackInfo
	) {
		this.kohsInventoryDebug$slot = slot;
		DebugCollector.onContainerSlotAction((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, input, false);
	}

	@Inject(method = "slotClicked", at = @At("RETURN"))
	private void kohsInventoryDebug$slotReturn(
		final Slot slot,
		final int slotId,
		final int button,
		final ClickType input,
		final CallbackInfo callbackInfo
	) {
		DebugCollector.onContainerSlotAction((AbstractContainerScreen<?>) (Object) this, this.kohsInventoryDebug$slot, slotId, button, input, true);
		this.kohsInventoryDebug$slot = null;
	}

	@Inject(method = "keyPressed", at = @At("HEAD"))
	private void kohsInventoryDebug$keyHead(final KeyEvent event, final CallbackInfoReturnable<Boolean> callbackInfo) {
		DebugCollector.onContainerKey((AbstractContainerScreen<?>) (Object) this, event, false, false);
	}

	@Inject(method = "keyPressed", at = @At("RETURN"))
	private void kohsInventoryDebug$keyReturn(final KeyEvent event, final CallbackInfoReturnable<Boolean> callbackInfo) {
		DebugCollector.onContainerKey((AbstractContainerScreen<?>) (Object) this, event, true, callbackInfo.getReturnValueZ());
	}
}
