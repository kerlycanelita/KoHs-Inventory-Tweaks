package dev.zymekoh.kohsinventorytweaks;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityNoticeController;
import dev.zymekoh.kohsinventorytweaks.compat.BlockingCompatibilityController;
import dev.zymekoh.kohsinventorytweaks.compat.MouseConflictNotificationController;
import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import dev.zymekoh.kohsinventorytweaks.input.ConfigMenuKeyBinding;
import dev.zymekoh.kohsinventorytweaks.inventory.InputFence;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryWarmup;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.render.PlayerGlowLayer;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
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
		ZMascot.install();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			CompatibilityNoticeController.onClientTick(minecraft);
			MouseConflictNotificationController.onClientTick(minecraft);
			ConfigMenuKeyBinding.onClientTick(minecraft);
			InventoryWarmup.onClientTick(minecraft);
			InputFence.onClientTick();
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, layers, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				layers.register(new PlayerGlowLayer(avatar));
			}
		});
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
			Identifier.fromNamespaceAndPath(MOD_ID, "inventory_texture"),
			(ResourceManagerReloadListener) resourceManager -> InventoryTextureManager.onResourcesReloaded()
		);
		LOGGER.info("KoHs Inventory Tweaks initialized for Minecraft {}", runtimeMinecraftVersion());
	}

	private static String runtimeMinecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}
}
