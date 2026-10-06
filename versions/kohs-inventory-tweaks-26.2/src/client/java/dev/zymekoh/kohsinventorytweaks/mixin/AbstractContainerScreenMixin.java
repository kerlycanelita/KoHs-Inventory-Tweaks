package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import dev.zymekoh.kohsinventorytweaks.render.AccessibilityRenderController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2 has no snapback extraction, so there is no item-return motion to reduce here. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void kohsInventoryTweaks$drawItemHighlightBackground(
		final GuiGraphicsExtractor graphics,
		final Slot slot,
		final int mouseX,
		final int mouseY,
		final CallbackInfo callbackInfo
	) {
		ItemHighlighterController.drawContainerSlot(
			graphics,
			(AbstractContainerScreen<?>) (Object) this,
			slot,
			false
		);
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void kohsInventoryTweaks$drawItemHighlightBorder(
		final GuiGraphicsExtractor graphics,
		final Slot slot,
		final int mouseX,
		final int mouseY,
		final CallbackInfo callbackInfo
	) {
		ItemHighlighterController.drawContainerSlot(
			graphics,
			(AbstractContainerScreen<?>) (Object) this,
			slot,
			true
		);
		AccessibilityRenderController.drawFocusedSlot(
			graphics,
			(AbstractContainerScreen<?>) (Object) this,
			slot
		);
	}

	@Inject(
		method = "extractContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void kohsInventoryTweaks$updateHighlightedHover(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		ItemHighlighterController.updateDynamicHover((AbstractContainerScreen<?>) (Object) this);
	}

	@Inject(method = "removed", at = @At("RETURN"))
	private void kohsInventoryTweaks$clearHighlightedHover(final CallbackInfo callbackInfo) {
		ItemHighlighterController.onContainerClosed();
	}
}
