package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.render.AnimatedItemPacer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enchanted and animated GUI items are redrawn at a steady pace instead of on every frame. See
 * {@link AnimatedItemPacer}.
 *
 * <p>1.21.11 keeps its item atlas in GuiRenderer, and an animated item already in it is drawn again
 * once a frame. Between two redraws it reads as still, so the copy already in the atlas is used.
 * The lambda is the one GuiRenderer#prepareItemElements runs for each item.</p>
 */
@Mixin(GuiRenderer.class)
public abstract class GuiItemAtlasMixin {
	@WrapOperation(method = "method_71055", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;isAnimated()Z", ordinal = 0))
	private boolean kohsInventoryTweaks$paceAnimatedItems(final TrackingItemStackRenderState state, final Operation<Boolean> original) {
		boolean animated = original.call(state);
		return animated && (!AnimatedItemPacer.active() || AnimatedItemPacer.redrawDue(state.getModelIdentity()));
	}

	@Inject(method = "incrementFrameNumber", at = @At("RETURN"))
	private void kohsInventoryTweaks$forgetUnseenItems(final CallbackInfo callbackInfo) {
		AnimatedItemPacer.endFrame();
	}
}
