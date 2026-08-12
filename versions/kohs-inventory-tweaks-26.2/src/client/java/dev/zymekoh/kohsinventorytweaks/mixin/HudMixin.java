package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void kohsInventoryTweaks$drawHotbarHighlightBackground(
		final GuiGraphicsExtractor graphics,
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

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void kohsInventoryTweaks$drawHotbarHighlightBorder(
		final GuiGraphicsExtractor graphics,
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
