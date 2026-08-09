package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
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

@Mixin(MouseHandler.class)
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

	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$recoverUnexpectedCenter(
		final long handle,
		final double x,
		final double y,
		final CallbackInfo callbackInfo
	) {
		if (handle == this.minecraft.getWindow().handle()
			&& CursorLandingController.recoverUnexpectedCenterEvent(this.minecraft, x, y)) {
			callbackInfo.cancel();
		}
	}
}
