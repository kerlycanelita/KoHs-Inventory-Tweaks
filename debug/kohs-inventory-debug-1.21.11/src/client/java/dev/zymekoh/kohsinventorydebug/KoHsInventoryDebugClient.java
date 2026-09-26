package dev.zymekoh.kohsinventorydebug;

import net.fabricmc.api.ClientModInitializer;

public final class KoHsInventoryDebugClient implements ClientModInitializer {
	public static final String MOD_ID = "kohs_inventory_debug";

	@Override
	public void onInitializeClient() {
		DebugCollector.start();
		// Recording overlay: over the HUD while no screen is open, over the screen once one is.
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
			net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "recording_overlay"),
			(graphics, delta) -> RecordingOverlay.draw(graphics, false));
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register(
				(current, graphics, mouseX, mouseY, delta) -> RecordingOverlay.draw(graphics, true)));
	}
}
