package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
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
	@Inject(method = "render", at = @At("HEAD"))
	private void kohsInventoryTweaks$beginContainerScale(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callbackInfo
	) {
		Screen screen = (Screen) (Object) this;
		float scale = (float) InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		float centerX = screen.width * 0.5F;
		float centerY = screen.height * 0.5F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void kohsInventoryTweaks$endContainerScale(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callbackInfo
	) {
		graphics.pose().popMatrix();
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int kohsInventoryTweaks$transformContainerMouseX(final int mouseX) {
		Screen screen = (Screen) (Object) this;
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale));
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int kohsInventoryTweaks$transformContainerMouseY(final int mouseY) {
		Screen screen = (Screen) (Object) this;
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale));
	}

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double kohsInventoryTweaks$transformClickedMouseX(final double mouseX) {
		if ((Object) this instanceof InventoryScreen) {
			return mouseX;
		}
		Screen screen = (Screen) (Object) this;
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale);
	}

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double kohsInventoryTweaks$transformClickedMouseY(final double mouseY) {
		if ((Object) this instanceof InventoryScreen) {
			return mouseY;
		}
		Screen screen = (Screen) (Object) this;
		double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale);
	}

	@ModifyVariable(method = {"mouseDragged", "mouseReleased"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double kohsInventoryTweaks$transformActionMouseX(final double mouseX) {
		Screen screen = (Screen) (Object) this;
		double scale = (Object) this instanceof InventoryScreen inventoryScreen
			? InventoryGuiScaler.appliedScale(inventoryScreen, ConfigStore.get())
			: InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale);
	}

	@ModifyVariable(method = {"mouseDragged", "mouseReleased"}, at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double kohsInventoryTweaks$transformActionMouseY(final double mouseY) {
		Screen screen = (Screen) (Object) this;
		double scale = (Object) this instanceof InventoryScreen inventoryScreen
			? InventoryGuiScaler.appliedScale(inventoryScreen, ConfigStore.get())
			: InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale);
	}

	@ModifyVariable(method = "mouseDragged", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private double kohsInventoryTweaks$transformDragDeltaX(final double deltaX) {
		return deltaX / this.kohsInventoryTweaks$interactionScale();
	}

	@ModifyVariable(method = "mouseDragged", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private double kohsInventoryTweaks$transformDragDeltaY(final double deltaY) {
		return deltaY / this.kohsInventoryTweaks$interactionScale();
	}

	private double kohsInventoryTweaks$interactionScale() {
		Screen screen = (Screen) (Object) this;
		return (Object) this instanceof InventoryScreen inventoryScreen
			? InventoryGuiScaler.appliedScale(inventoryScreen, ConfigStore.get())
			: InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
	}

	@Inject(
		method = "renderBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
			shift = At.Shift.BEFORE
		)
	)
	private void kohsInventoryTweaks$beginScaledInventoryBackground(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callbackInfo
	) {
		Screen screen = (Screen) (Object) this;
		float scale = (float) ((Object) this instanceof InventoryScreen inventoryScreen
			? InventoryGuiScaler.appliedScale(inventoryScreen, ConfigStore.get())
			: InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get()));
		float centerX = screen.width * 0.5F;
		float centerY = screen.height * 0.5F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
	}

	@Inject(
		method = "renderBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
			shift = At.Shift.AFTER
		)
	)
	private void kohsInventoryTweaks$endScaledInventoryBackground(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callbackInfo
	) {
		graphics.pose().popMatrix();
	}

	@Inject(method = "renderSnapbackItem", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$removeTransientGhostCopy(final CallbackInfo callbackInfo) {
		if (InventoryAnimationController.suppressAllInventoryAnimations()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void kohsInventoryTweaks$placeCursorAfterLayout(final CallbackInfo callbackInfo) {
		CursorLandingController.onContainerScreenInitialized(Minecraft.getInstance(), (Screen) (Object) this);
	}

	@Inject(method = "renderSlot", at = @At("HEAD"))
	private void kohsInventoryTweaks$drawItemHighlightBackground(
		final GuiGraphics graphics,
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

	@Inject(method = "renderSlot", at = @At("RETURN"))
	private void kohsInventoryTweaks$drawItemHighlightBorder(
		final GuiGraphics graphics,
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
		InventoryAnimationController.endInventoryItem();
	}

	@Inject(
		method = "renderContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphics;)V",
			shift = At.Shift.BEFORE
		)
	)
	private void kohsInventoryTweaks$updateHighlightedHover(
		final GuiGraphics graphics,
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
