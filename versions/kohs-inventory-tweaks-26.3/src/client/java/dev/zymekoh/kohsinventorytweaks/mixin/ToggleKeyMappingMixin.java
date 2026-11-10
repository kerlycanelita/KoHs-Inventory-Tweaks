package dev.zymekoh.kohsinventorytweaks.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A toggled sprint or sneak comes back after a screen closes when it is bound to a mouse button
 * too, as it does for a keyboard key (MC-308595).
 */
@Mixin(ToggleKeyMapping.class)
public abstract class ToggleKeyMappingMixin {
	@Shadow private boolean releasedByScreenWhenDown;
	@Shadow @Final private boolean shouldRestore;
	@Shadow @Final private BooleanSupplier needsToggle;

	@Inject(method = "shouldRestoreStateOnScreenClosed", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$restoreMouseToggles(final CallbackInfoReturnable<Boolean> callbackInfo) {
		InputConstants.Key key = ((KeyMappingAccessor) (Object) this).kohsInventoryTweaks$getKey();
		if (key.getType() == InputConstants.Type.MOUSE && this.shouldRestore && this.needsToggle.getAsBoolean()
			&& this.releasedByScreenWhenDown) {
			this.releasedByScreenWhenDown = false;
			callbackInfo.setReturnValue(true);
		}
	}
}
