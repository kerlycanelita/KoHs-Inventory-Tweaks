package dev.zymekoh.kohsinventorytweaks.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.compat.MouseConflictNotificationController;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.IssuesTrackerScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ConfigMenuKeyBinding {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath(KoHsInventoryTweaksClient.MOD_ID, "menu")
	);
	private static final KeyMapping OPEN_CONFIG = new KeyMapping(
		"key.kohs_inventory_tweaks.open_config",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_Y,
		CATEGORY
	);
	private static boolean registered;

	private ConfigMenuKeyBinding() {
	}

	public static void register() {
		if (!registered) {
			KeyMappingHelper.registerKeyMapping(OPEN_CONFIG);
			registered = true;
		}
	}

	public static void onClientTick(final Minecraft minecraft) {
		while (OPEN_CONFIG.consumeClick()) {
			// Never steal input from chat, inventories, or another configuration screen.
			if (minecraft.screen == null && minecraft.player != null && minecraft.gameMode != null) {
				InventoryTweaksScreen menu = new InventoryTweaksScreen(null);
				if (MouseConflictNotificationController.consumeIssuesTrackerRoute()) {
					minecraft.setScreen(new IssuesTrackerScreen(menu));
				} else {
					minecraft.setScreen(menu);
				}
			}
		}
	}

	public static KeyMapping mapping() {
		return OPEN_CONFIG;
	}

	public static void assign(final Minecraft minecraft, final InputConstants.Key key) {
		OPEN_CONFIG.setKey(key);
		KeyMapping.resetMapping();
		minecraft.options.save();
	}

	public static void reset(final Minecraft minecraft) {
		assign(minecraft, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y));
	}

	public static int conflictCount(final Minecraft minecraft) {
		if (minecraft == null || minecraft.options == null) {
			return 0;
		}
		int conflicts = 0;
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != OPEN_CONFIG && mapping.same(OPEN_CONFIG)) {
				conflicts++;
			}
		}
		return conflicts;
	}
}
