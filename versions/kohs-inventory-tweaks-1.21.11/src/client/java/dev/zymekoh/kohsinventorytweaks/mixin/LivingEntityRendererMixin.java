package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.VisiblePlayerGlowController;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
	private void kohsInventoryTweaks$visiblePlayerTint(
		final LivingEntityRenderState state,
		final CallbackInfoReturnable<Integer> callbackInfo
	) {
		if (state instanceof AvatarRenderState avatar) {
			callbackInfo.setReturnValue(VisiblePlayerGlowController.tint(avatar, callbackInfo.getReturnValue()));
		}
	}
}
