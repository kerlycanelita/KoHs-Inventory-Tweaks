package dev.zymekoh.kohsinventorydebug.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the Vanilla logical key queue without consuming or changing it. */
@Mixin(value = KeyMapping.class, priority = 3000)
abstract class KeyMappingTraceMixin {
	@Inject(method = "click", at = @At("RETURN"))
	private static void kohsInventoryDebug$click(final InputConstants.Key key, final CallbackInfo callbackInfo) {
		DebugCollector.onKeyClick(key);
	}

	@Inject(method = "set", at = @At("RETURN"))
	private static void kohsInventoryDebug$set(final InputConstants.Key key, final boolean state, final CallbackInfo callbackInfo) {
		DebugCollector.onKeyState(key, state);
	}

	@Inject(method = "consumeClick", at = @At("RETURN"))
	private void kohsInventoryDebug$consume(final CallbackInfoReturnable<Boolean> callbackInfo) {
		DebugCollector.onKeyConsumed((KeyMapping) (Object) this, callbackInfo.getReturnValueZ());
	}
}
