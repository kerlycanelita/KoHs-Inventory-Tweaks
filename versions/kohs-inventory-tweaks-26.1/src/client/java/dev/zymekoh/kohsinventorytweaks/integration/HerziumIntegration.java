package dev.zymekoh.kohsinventorytweaks.integration;

import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class HerziumIntegration {
	public static final String RELEASES_URL = "https://github.com/kerlycanelita/Herzium/releases";

	private HerziumIntegration() {
	}

	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("herzium");
	}

	public static @Nullable Screen createConfigScreen(final Screen parent) {
		if (!installed()) {
			return null;
		}
		try {
			Class<?> modMenu = Class.forName("com.terraformersmc.modmenu.ModMenu");
			Method hasConfigScreen = modMenu.getMethod("hasConfigScreen", String.class);
			if (Boolean.TRUE.equals(hasConfigScreen.invoke(null, "herzium"))) {
				Method getConfigScreen = modMenu.getMethod("getConfigScreen", String.class, Screen.class);
				Object screen = getConfigScreen.invoke(null, "herzium", parent);
				if (screen instanceof Screen resolved) {
					return resolved;
				}
			}

			Class<?> screenClass = Class.forName("dev.zymekoh.herzium.gui.HerziumConfigScreen");
			Object screen = screenClass.getConstructor(Screen.class).newInstance(parent);
			return screen instanceof Screen resolved ? resolved : null;
		} catch (ReflectiveOperationException | LinkageError exception) {
			KoHsInventoryTweaksClient.LOGGER.warn("Herzium is installed but its configuration screen could not be opened", exception);
			return null;
		}
	}
}
