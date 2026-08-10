package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void kohsInventoryTweaks$prepareCursor(final @Nullable Screen screen, final CallbackInfo callbackInfo) {
		CursorLandingController.onScreenRequested(screen);
		SuperFastInventoryController.onScreenRequested(screen);
	}
}
