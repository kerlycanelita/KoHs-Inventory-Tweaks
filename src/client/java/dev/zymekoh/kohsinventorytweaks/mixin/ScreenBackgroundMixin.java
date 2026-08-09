package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	private static final int VANILLA_BOTTOM_ALPHA = 208;
	private static final int VANILLA_TOP_ALPHA = 192;

	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$customizeInventoryBackdrop(
		final GuiGraphicsExtractor graphics,
		final CallbackInfo callbackInfo
	) {
		if (!((Object) this instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		int opacity = Math.max(0, Math.min(255, ConfigStore.get().inventoryBackdropOpacity));
		int topAlpha = Math.min(255, opacity * VANILLA_TOP_ALPHA / VANILLA_BOTTOM_ALPHA);
		graphics.fillGradient(
			0,
			0,
			screen.width,
			screen.height,
			topAlpha << 24 | 0x101010,
			opacity << 24 | 0x101010
		);
		callbackInfo.cancel();
	}
}
