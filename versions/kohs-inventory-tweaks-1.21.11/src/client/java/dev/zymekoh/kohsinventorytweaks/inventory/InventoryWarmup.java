package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Pays the cost of the first inventory once the player is already in the world.
 *
 * <p>Nothing here is shown or made current: the screen is constructed and dropped,
 * so only class loading and static setup survive. {@code init} is never called, no
 * screen is set, and no inventory state is touched.</p>
 */
public final class InventoryWarmup {
	private static boolean warmed;
	private static long warmupMicros = -1L;

	private InventoryWarmup() {
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (minecraft == null || minecraft.level == null || minecraft.player == null) {
			warmed = false;
			return;
		}
		if (warmed) {
			return;
		}
		warmed = true;

		long started = System.nanoTime();
		try {
			InventoryTextureManager.textureFor(ConfigStore.get());
			new InventoryScreen(minecraft.player);
			warmupMicros = (System.nanoTime() - started) / 1_000L;
		} catch (RuntimeException | LinkageError failure) {
			warmupMicros = -1L;
			KoHsInventoryTweaksClient.LOGGER.debug("Inventory warm-up skipped", failure);
		}
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String warmupSnapshot() {
		if (!warmed) {
			return "state=idle";
		}
		return warmupMicros < 0L ? "state=skipped" : "state=warmed; cost=" + warmupMicros + "us";
	}
}
