package dev.zymekoh.kohsinventorytweaks;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityNoticeController;
import dev.zymekoh.kohsinventorytweaks.compat.BlockingCompatibilityController;
import dev.zymekoh.kohsinventorytweaks.compat.MouseConflictNotificationController;
import dev.zymekoh.kohsinventorytweaks.input.ConfigMenuKeyBinding;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KoHsInventoryTweaksClient implements ClientModInitializer {
	public static final String MOD_ID = "kohs_inventory_tweaks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		CompatibilityIssueManager.initialize();
		if (CompatibilityIssueManager.isSafelyBlocked()) {
			ClientTickEvents.END_CLIENT_TICK.register(BlockingCompatibilityController::onClientTick);
			LOGGER.error(
				"KoHs Inventory Tweaks blocked normal initialization because {} crash-risk conflict(s) were detected",
				CompatibilityIssueManager.blockingIssues().size()
			);
			return;
		}
		ConfigStore.load();
		ConfigMenuKeyBinding.register();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			CompatibilityNoticeController.onClientTick(minecraft);
			MouseConflictNotificationController.onClientTick(minecraft);
			ConfigMenuKeyBinding.onClientTick(minecraft);
		});
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			new SimpleSynchronousResourceReloadListener() {
				@Override
				public ResourceLocation getFabricId() {
					return ResourceLocation.fromNamespaceAndPath(MOD_ID, "inventory_texture");
				}

				@Override
				public void onResourceManagerReload(final ResourceManager resourceManager) {
					InventoryTextureManager.onResourcesReloaded();
				}
			}
		);
		LOGGER.info("KoHs Inventory Tweaks initialized for Minecraft 1.21.6-1.21.8");
	}
}
