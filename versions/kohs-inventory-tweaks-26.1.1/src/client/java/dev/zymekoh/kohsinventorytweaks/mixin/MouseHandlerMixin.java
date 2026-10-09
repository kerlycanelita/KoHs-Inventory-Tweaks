package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import dev.zymekoh.kohsinventorytweaks.inventory.InputFence;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Owns only local cursor release and the optional early construction of the
// ordinary player-inventory screen. Inventory actions remain Vanilla-owned.
@Mixin(value = MouseHandler.class, priority = 2000)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@Inject(
		method = "releaseMouse",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"
		)
	)
	private void kohsInventoryTweaks$showPointerBeforeLanding(final CallbackInfo callbackInfo) {
		CursorLandingController.beforeMouseRelease(this.minecraft);
	}

	@ModifyArgs(
		method = "releaseMouse",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"
		)
	)
	private void kohsInventoryTweaks$replaceVanillaCenter(final Args args) {
		double[] target = CursorLandingController.overrideReleasePosition(this.minecraft);
		if (target == null) {
			return;
		}
		this.xpos = target[0];
		this.ypos = target[1];
		args.set(2, target[0]);
		args.set(3, target[1]);
	}

	@Inject(
		method = "releaseMouse",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V",
			shift = At.Shift.AFTER
		)
	)
	private void kohsInventoryTweaks$checkRestoredPointer(final CallbackInfo callbackInfo) {
		CursorLandingController.afterMouseRelease(this.minecraft, this.xpos, this.ypos);
	}

	// An inventory opened ahead of its tick: what is done in it waits for that tick.
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$keepEarlyButton(
		final long windowHandle, final MouseButtonInfo buttonInfo, final int action, final CallbackInfo callbackInfo
	) {
		if (InputFence.holdButton(this.minecraft, windowHandle, buttonInfo, action)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$keepEarlyScroll(
		final long windowHandle, final double horizontal, final double vertical, final CallbackInfo callbackInfo
	) {
		if (InputFence.holdScroll(this.minecraft, windowHandle, horizontal, vertical)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("TAIL"))
	private void kohsInventoryTweaks$openLocalInventoryOnMousePress(
		final long windowHandle,
		final MouseButtonInfo buttonInfo,
		final int action,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.onMouseButton(this.minecraft, windowHandle, buttonInfo, action);
	}

}
