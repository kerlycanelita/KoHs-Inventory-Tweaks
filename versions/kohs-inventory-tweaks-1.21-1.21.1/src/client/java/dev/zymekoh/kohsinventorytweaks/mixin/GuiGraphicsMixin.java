package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
	@Inject(
		method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void kohsInventoryTweaks$customizeDetachedSlot(
		final ResourceLocation sprite,
		final int x,
		final int y,
		final int width,
		final int height,
		final CallbackInfo callbackInfo
	) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)
			|| !InventoryTextureManager.hasColorCustomization(config)
			|| !sprite.getPath().equals("container/slot")) {
			return;
		}
		drawSlotGrid((GuiGraphics) (Object) this, x, y, width, height, config);
		callbackInfo.cancel();
	}

	@Inject(
		method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void kohsInventoryTweaks$removeInventoryProgressAnimations(
		final ResourceLocation sprite,
		final int textureWidth,
		final int textureHeight,
		final int sourceX,
		final int sourceY,
		final int x,
		final int y,
		final int width,
		final int height,
		final CallbackInfo callbackInfo
	) {
		if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) {
			return;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		String path = sprite.getPath();
		if (path.equals("container/horse/chest_slots")
			&& InventoryTextureManager.hasColorCustomization(config)) {
			drawSlotGrid((GuiGraphics) (Object) this, x, y, width, height, config);
			callbackInfo.cancel();
			return;
		}
		if (InventoryAnimationController.suppressAllInventoryAnimations() && (path.endsWith("/lit_progress")
			|| path.endsWith("/burn_progress")
			|| path.equals("container/brewing_stand/brew_progress")
			|| path.equals("container/brewing_stand/bubbles"))) {
			callbackInfo.cancel();
		}
	}

	private static void drawSlotGrid(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final InventoryTweaksConfig config
	) {
		for (int slotY = 0; slotY < height; slotY += 18) {
			for (int slotX = 0; slotX < width; slotX += 18) {
				int right = x + Math.min(width, slotX + 18);
				int bottom = y + Math.min(height, slotY + 18);
				graphics.fill(
					x + slotX,
					y + slotY,
					right,
					bottom,
					tint(0xFF6B477F, config.frameColor, config.frameOpacity)
				);
				if (right - x - slotX > 2 && bottom - y - slotY > 2) {
					graphics.fill(
						x + slotX + 1,
						y + slotY + 1,
						right - 1,
						bottom - 1,
						tint(0xFF17101F, config.slotColor, config.slotOpacity)
					);
				}
			}
		}
	}

	private static int tint(final int base, final int color, final int opacity) {
		int alpha = (base >>> 24) * Math.max(0, Math.min(255, opacity)) / 255;
		int red = (base >> 16 & 0xFF) * (color >> 16 & 0xFF) / 255;
		int green = (base >> 8 & 0xFF) * (color >> 8 & 0xFF) / 255;
		int blue = (base & 0xFF) * (color & 0xFF) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	@ModifyVariable(
		method = "blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private ResourceLocation kohsInventoryTweaks$customizeContainerSurface(final ResourceLocation original) {
		if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) {
			return original;
		}
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		return InventoryTextureManager.screenTextureFor(
			ConfigStore.get(),
			original,
			screen,
			accessor.kohsInventoryTweaks$getImageWidth(),
			accessor.kohsInventoryTweaks$getImageHeight()
		);
	}
}
