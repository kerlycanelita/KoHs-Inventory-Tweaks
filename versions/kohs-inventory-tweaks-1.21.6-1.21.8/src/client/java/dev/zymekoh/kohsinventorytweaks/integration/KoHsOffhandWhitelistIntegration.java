package dev.zymekoh.kohsinventorytweaks.integration;

import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/** Keeps KoHs Offhand Whitelist as the authority for permitted offhand items. */
public final class KoHsOffhandWhitelistIntegration {
	private static final String MOD_ID = "kohs_offhand_whitelist";
	private static boolean lookupComplete;
	private static Method enabledMethod;
	private static Method allowedMethod;

	private KoHsOffhandWhitelistIntegration() {
	}

	public static boolean allows(final ItemStack stack) {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return true;
		}
		try {
			resolveApi();
			if (enabledMethod == null || allowedMethod == null
				|| !Boolean.TRUE.equals(enabledMethod.invoke(null))) {
				return true;
			}
			return Boolean.TRUE.equals(allowedMethod.invoke(null, stack));
		} catch (ReflectiveOperationException | LinkageError exception) {
			// A changed API must not bypass the other mod. Its own click hook still
			// validates the swap, so KoHs leaves final authority to it.
			KoHsInventoryTweaksClient.LOGGER.debug(
				"KoHs Offhand Whitelist API bridge is unavailable; its own validator remains authoritative",
				exception
			);
			return true;
		}
	}

	private static void resolveApi() throws ReflectiveOperationException {
		if (lookupComplete) {
			return;
		}
		Class<?> api = Class.forName("dev.zymekoh.kohsoffhandwhitelist.KoHsOffhandWhitelist");
		enabledMethod = api.getMethod("isOffhandWhitelistEnabled");
		allowedMethod = api.getMethod("isAllowedOffhandStack", ItemStack.class);
		lookupComplete = true;
	}
}

