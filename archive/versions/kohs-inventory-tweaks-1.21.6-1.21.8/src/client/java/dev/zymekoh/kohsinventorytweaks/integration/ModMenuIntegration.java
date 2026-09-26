package dev.zymekoh.kohsinventorytweaks.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.kohsinventorytweaks.screen.ConfigurationUnavailableScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import net.minecraft.client.Minecraft;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || minecraft.level == null) {
				return new ConfigurationUnavailableScreen(parent);
			}
			return new InventoryTweaksScreen(parent);
		};
	}
}

