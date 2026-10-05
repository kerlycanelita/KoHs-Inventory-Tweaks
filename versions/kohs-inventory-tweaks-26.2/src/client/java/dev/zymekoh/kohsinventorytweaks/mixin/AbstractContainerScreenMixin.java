package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.TotemGuard;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import dev.zymekoh.kohsinventorytweaks.render.AccessibilityRenderController;
import dev.zymekoh.kohsinventorytweaks.render.SlotOverlays;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
		SlotOverlays.drawSlot(graphics, (AbstractContainerScreen<?>) (Object) this, slot);
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
		SlotOverlays.beginFrame((AbstractContainerScreen<?>) (Object) this);
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$steadyTooltip(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final CallbackInfo callbackInfo
	) {
		if (SlotOverlays.holdsTooltip()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
	private void kohsInventoryTweaks$appendInventoryTotal(
		final ItemStack stack,
		final CallbackInfoReturnable<List<Component>> callbackInfo
	) {
		Component total = SlotOverlays.inventoryTotal(stack);
		if (total != null) {
			List<Component> lines = new ArrayList<>(callbackInfo.getReturnValue());
			lines.add(total);
			callbackInfo.setReturnValue(lines);
		}
	}

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$protectTotems(
		final Slot slot,
		final int slotId,
		final int button,
		final ContainerInput input,
		final CallbackInfo callbackInfo
	) {
		if (TotemGuard.blocks((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, input)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "removed", at = @At("RETURN"))
	private void kohsInventoryTweaks$clearHighlightedHover(final CallbackInfo callbackInfo) {
		ItemHighlighterController.onContainerClosed();
	}
}
