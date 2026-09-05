package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.compat.MouseConflictNotificationController;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "render", at = @At("RETURN"))
	private void kohsInventoryTweaks$drawCompatibilityNotification(
		final GuiGraphics graphics,
		final DeltaTracker deltaTracker,
		final CallbackInfo callbackInfo
	) {
		MouseConflictNotificationController.draw(graphics);
	}

	@Inject(method = "renderSlot", at = @At("HEAD"))
	private void kohsInventoryTweaks$drawHotbarHighlightBackground(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final DeltaTracker deltaTracker,
		final Player player,
		final ItemStack stack,
		final int seed,
		final CallbackInfo callbackInfo
	) {
		ItemHighlighterController.drawHotbarSlot(graphics, x, y, stack, false);
	}

	@Inject(method = "renderSlot", at = @At("RETURN"))
	private void kohsInventoryTweaks$drawHotbarHighlightBorder(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final DeltaTracker deltaTracker,
		final Player player,
		final ItemStack stack,
		final int seed,
		final CallbackInfo callbackInfo
	) {
		ItemHighlighterController.drawHotbarSlot(graphics, x, y, stack, true);
	}
}
