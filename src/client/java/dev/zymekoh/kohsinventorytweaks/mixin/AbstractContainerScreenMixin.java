package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import dev.zymekoh.kohsinventorytweaks.render.AccessibilityRenderController;
import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void kohsInventoryTweaks$beginContainerScale(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		Screen screen = (Screen) (Object) this;
		if (screen instanceof InventoryScreen) {
			// InventoryScreen owns one scale around its recipe-book render path.
			// Applying the generic container transform as its super methods run would
			// square the scale and desynchronize rendered slots from pointer input.
			return;
		}
		float scale = (float) InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		InventoryGuiScaler.beginScaledSurface(graphics, screen.width * 0.5F, screen.height * 0.5F, scale);
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void kohsInventoryTweaks$endContainerScale(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		if ((Object) this instanceof InventoryScreen) {
			return;
		}
		InventoryGuiScaler.endScaledSurface(graphics);
	}

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int kohsInventoryTweaks$transformContainerMouseX(final int mouseX) {
		Screen screen = (Screen) (Object) this;
		if (screen instanceof InventoryScreen) {
			return mouseX;
		}
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale));
	}

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int kohsInventoryTweaks$transformContainerMouseY(final int mouseY) {
		Screen screen = (Screen) (Object) this;
		if (screen instanceof InventoryScreen) {
			return mouseY;
		}
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale));
	}

	@ModifyVariable(method = {"mouseClicked", "mouseDragged", "mouseReleased"}, at = @At("HEAD"), argsOnly = true)
	private MouseButtonEvent kohsInventoryTweaks$transformContainerMouseEvent(final MouseButtonEvent event) {
		Screen screen = (Screen) (Object) this;
		if (screen instanceof InventoryScreen) {
			// AbstractRecipeBookScreenMixin transforms clicks/drags once and
			// InventoryScreenMixin transforms releases once. The base method receives
			// that already-normalized event through invokespecial.
			return event;
		}
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryEvent(event, screen.width, screen.height, scale);
	}

	@Inject(method = "extractSnapbackItem", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$removeTransientGhostCopy(final CallbackInfo callbackInfo) {
		if (InventoryAnimationController.suppressAllInventoryAnimations()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void kohsInventoryTweaks$drawItemHighlightBackground(
		final GuiGraphicsExtractor graphics,
		final Slot slot,
		final int mouseX,
		final int mouseY,
		final CallbackInfo callbackInfo
	) {
		InventoryAnimationController.beginInventoryItem();
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
		InventoryAnimationController.endInventoryItem();
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
