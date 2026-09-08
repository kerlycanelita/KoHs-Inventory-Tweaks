package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void kohsInventoryTweaks$prepareCursor(final @Nullable Screen screen, final CallbackInfo callbackInfo) {
		CursorLandingController.onScreenRequested(screen);
	}

	/**
	 * Finalizes the placement once {@link Minecraft#setScreen(Screen)} has completed
	 * the whole synchronous opening transaction, instead of from {@code Screen#init}.
	 *
	 * <p>{@code init} also runs on every window resize and could re-arm the landing
	 * long after the opening; this point cannot, and it is still the same input
	 * event, so no tick, render or scheduled task is crossed.</p>
	 */
	@Inject(method = "setScreen", at = @At("RETURN"))
	private void kohsInventoryTweaks$finalizeCursor(final @Nullable Screen screen, final CallbackInfo callbackInfo) {
		CursorLandingController.onScreenOpened(Minecraft.getInstance(), screen);
	}
}
