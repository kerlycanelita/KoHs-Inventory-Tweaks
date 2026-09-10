package dev.zymekoh.kohsinventorydebug;

import net.fabricmc.api.ClientModInitializer;

public final class KoHsInventoryDebugClient implements ClientModInitializer {
	public static final String MOD_ID = "kohs_inventory_debug";

	@Override
	public void onInitializeClient() {
		DebugCollector.start();
	}
}
