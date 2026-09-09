package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Pays the cost of the first inventory once the player is already in the world.
 *
 * <p>Opening the inventory for the first time in a session loads the screen and
 * recipe book classes and, when the surface is customized, composes its texture.
 * Both land on the very first press, which is the one press a fight is least able
 * to afford. Doing them on arrival moves that cost to a moment where nothing is
 * waiting on it.</p>
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
			// Leaving the world arms the warm-up again for the next one, whose
			// resource pack and configuration may compose a different surface.
			warmed = false;
			return;
		}
		if (warmed) {
			return;
		}
		warmed = true;

		long started = System.nanoTime();
		try {
			// Composes and uploads the customized surface if the configuration asks
			// for one, and is a cheap no-op when it does not.
			InventoryTextureManager.textureFor(ConfigStore.get());
			// Constructing the screen loads it, AbstractContainerScreen, the recipe
			// book component and the effect renderer. The instance is deliberately
			// discarded.
			new InventoryScreen(minecraft.player);
			warmupMicros = (System.nanoTime() - started) / 1_000L;
		} catch (RuntimeException | LinkageError failure) {
			// A warm-up that cannot run costs the player nothing beyond the original
			// first-open spike, so it must never interrupt joining a world.
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
