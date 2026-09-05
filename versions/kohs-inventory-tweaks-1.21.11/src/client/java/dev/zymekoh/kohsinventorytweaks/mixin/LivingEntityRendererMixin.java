package dev.zymekoh.kohsinventorytweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.kohsinventorytweaks.render.VisiblePlayerGlowController;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("HEAD")
	)
	private void kohsInventoryTweaks$visiblePlayerLight(
		final LivingEntityRenderState state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState cameraRenderState,
		final CallbackInfo callbackInfo
	) {
		if (state instanceof AvatarRenderState avatar) {
			state.lightCoords = VisiblePlayerGlowController.light(avatar, state.lightCoords);
		}
	}

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
