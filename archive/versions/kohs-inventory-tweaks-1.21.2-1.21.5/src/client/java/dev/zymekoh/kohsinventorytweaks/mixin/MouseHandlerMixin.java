package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Applies one bounded final release coordinate when compatibility policy keeps
// Cursor Landing available; unrelated foreign mouse hooks remain untouched.
@Mixin(value = MouseHandler.class, priority = 2000)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@ModifyArgs(
		method = "releaseMouse",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"
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
			target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V",
			shift = At.Shift.AFTER
		)
	)
	private void kohsInventoryTweaks$commitVisibleCursorPosition(final CallbackInfo callbackInfo) {
		CursorLandingController.onMouseReleased(this.minecraft);
	}

	@Inject(method = "grabMouse", at = @At("HEAD"))
	private void kohsInventoryTweaks$allowGameplayCursorCentering(final CallbackInfo callbackInfo) {
		CursorLandingController.onMouseGrabRequested();
	}

	@Inject(method = "onPress", at = @At("TAIL"))
	private void kohsInventoryTweaks$openMouseBoundInventoryWithoutTickDelay(
		final long handle,
		final int button,
		final int action,
		final int modifiers,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.onMouseButton(this.minecraft, handle, button, action);
	}
}
