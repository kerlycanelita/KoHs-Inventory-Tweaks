package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientBundleTooltip.class)
public abstract class ClientBundleTooltipMixin {
	// This version draws the bundle slots through a private blit helper, and its
	// hover highlight through AbstractContainerScreen#renderSlotHighlight rather
	// than the front and back sprites of later versions. Redirecting the helper is
	// what reaches the slot, and there is no highlight sprite to let through.
	@Redirect(
		method = "blit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIII)V"
		)
	)
	private void kohsInventoryTweaks$customizeBundleSlot(
		final GuiGraphics graphics,
		final ResourceLocation sprite,
		final int x,
		final int y,
		final int blitOffset,
		final int width,
		final int height
	) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (!InventoryTextureManager.hasColorCustomization(config)) {
			graphics.blitSprite(sprite, x, y, blitOffset, width, height);
			return;
		}
		graphics.fill(x, y, x + width, y + height, tint(0xFF6B477F, config.frameColor, config.frameOpacity));
		graphics.fill(
			x + 3,
			y + 3,
			x + width - 3,
			y + height - 3,
			tint(0xFF17101F, config.slotColor, config.slotOpacity)
		);
	}

	private static int tint(final int base, final int color, final int opacity) {
		int alpha = (base >>> 24) * Math.max(0, Math.min(255, opacity)) / 255;
		int red = (base >> 16 & 0xFF) * (color >> 16 & 0xFF) / 255;
		int green = (base >> 8 & 0xFF) * (color >> 8 & 0xFF) / 255;
		int blue = (base & 0xFF) * (color & 0xFF) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}
}
