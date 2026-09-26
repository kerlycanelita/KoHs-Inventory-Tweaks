package dev.zymekoh.kohsinventorytweaks.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.compat.MouseConflictNotificationController;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.IssuesTrackerScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class ConfigMenuKeyBinding {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		ResourceLocation.fromNamespaceAndPath(KoHsInventoryTweaksClient.MOD_ID, "menu")
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
			KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
			registered = true;
		}
	}

	public static void onClientTick(final Minecraft minecraft) {
		while (OPEN_CONFIG.consumeClick()) {
			// Never steal input from chat, inventories, or another configuration screen.
			if (minecraft.screen == null && minecraft.player != null && minecraft.gameMode != null) {
				InventoryTweaksScreen menu = new InventoryTweaksScreen(null);
				minecraft.setScreen(MouseConflictNotificationController.consumeIssuesTrackerRoute()
					? new IssuesTrackerScreen(menu)
					: menu);
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
}
