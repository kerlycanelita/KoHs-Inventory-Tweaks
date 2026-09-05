package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Applies a local tint to the ordinary depth-tested player model. It deliberately
 * does not use Minecraft's outline/glowing pipeline because that pipeline can be
 * visible through blocks.
 */
public final class VisiblePlayerGlowController {
	private static final Map<AvatarRenderState, InventoryTweaksConfig> PREVIEW_STATES = Collections.synchronizedMap(new WeakHashMap<>());

	private VisiblePlayerGlowController() {
	}

	public static int tint(final AvatarRenderState state, final int vanillaTint) {
		InventoryTweaksConfig config = configFor(state);
		if (config == null || !config.visiblePlayerHighlightEnabled) {
			return vanillaTint;
		}

		double strength = config.visiblePlayerGlowIntensity / 255.0;
		if (config.visiblePlayerGlowPulse && !InventoryAnimationController.suppressAllInventoryAnimations()) {
			strength *= 0.78 + 0.22 * Math.sin(System.nanoTime() / 210_000_000.0 + state.id);
		}
		return multiply(vanillaTint, blendWhite(config.visiblePlayerGlowColor, strength));
	}

	/** Raises only the submitted model light; normal depth testing remains intact. */
	public static int light(final AvatarRenderState state, final int vanillaLight) {
		InventoryTweaksConfig config = configFor(state);
		if (config == null || !config.visiblePlayerLightGlowEnabled) {
			return vanillaLight;
		}
		double amount = config.visiblePlayerGlowBrightness / 255.0;
		if (config.visiblePlayerGlowPulse && !InventoryAnimationController.suppressAllInventoryAnimations()) {
			amount *= 0.84 + 0.16 * Math.sin(System.nanoTime() / 210_000_000.0 + state.id);
		}
		int block = vanillaLight & 0xFFFF;
		int sky = vanillaLight >>> 16 & 0xFFFF;
		block = (int) Math.round(block + (0xF0 - block) * Math.max(0.0, Math.min(1.0, amount)));
		sky = (int) Math.round(sky + (0xF0 - sky) * Math.max(0.0, Math.min(1.0, amount)));
		return sky << 16 | block;
	}

	/** Registers an extracted render state without mutating the real player entity. */
	public static void registerPreview(final AvatarRenderState state, final InventoryTweaksConfig config) {
		if (state != null && config != null) {
			PREVIEW_STATES.put(state, config.copy());
		}
	}

	private static @Nullable InventoryTweaksConfig configFor(final AvatarRenderState state) {
		if (state == null || state.isInvisible) {
			return null;
		}
		InventoryTweaksConfig preview = PREVIEW_STATES.get(state);
		if (preview != null) {
			return preview.visiblePlayerGlowEnabled ? preview : null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		InventoryTweaksConfig config = ConfigStore.get();
		if (!config.visiblePlayerGlowEnabled
			|| !(minecraft.screen instanceof InventoryScreen inventoryScreen)
			|| minecraft.player == null
			|| state.id == minecraft.player.getId()
			|| state.distanceToCameraSq > (double) config.visiblePlayerGlowDistance * config.visiblePlayerGlowDistance
			|| overlapsInventory(inventoryScreen, state, config)) {
			return null;
		}
		return config;
	}

	private static boolean overlapsInventory(
		final InventoryScreen screen,
		final AvatarRenderState state,
		final InventoryTweaksConfig config
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.getMainCamera();
		Vec3 cameraPosition = camera.position();
		double centerX = state.x;
		double centerY = state.y + state.boundingBoxHeight * 0.5;
		double centerZ = state.z;
		double dx = centerX - cameraPosition.x;
		double dy = centerY - cameraPosition.y;
		double dz = centerZ - cameraPosition.z;
		Vector3fc forward = camera.forwardVector();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		double depth = dx * forward.x() + dy * forward.y() + dz * forward.z();
		if (depth <= 0.05) {
			return true;
		}

		double horizontal = dx * left.x() + dy * left.y() + dz * left.z();
		double vertical = dx * up.x() + dy * up.y() + dz * up.z();
		double focal = screen.height / (2.0 * Math.tan(Math.toRadians(Math.max(20.0F, camera.getFov())) * 0.5));
		double projectedX = screen.width * 0.5 - horizontal * focal / depth;
		double projectedY = screen.height * 0.5 - vertical * focal / depth;
		double halfWidth = Math.max(2.0, state.boundingBoxWidth * focal / Math.max(0.1, depth) * 0.6);
		double halfHeight = Math.max(4.0, state.boundingBoxHeight * focal / Math.max(0.1, depth) * 0.55);

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		double scale = InventoryGuiScaler.appliedScale(screen, config);
		double inventoryLeft = screen.width * 0.5
			+ (accessor.kohsInventoryTweaks$getLeftPos() - screen.width * 0.5) * scale - 3.0;
		double inventoryTop = screen.height * 0.5
			+ (accessor.kohsInventoryTweaks$getTopPos() - screen.height * 0.5) * scale - 3.0;
		double inventoryRight = inventoryLeft + accessor.kohsInventoryTweaks$getImageWidth() * scale + 6.0;
		double inventoryBottom = inventoryTop + accessor.kohsInventoryTweaks$getImageHeight() * scale + 6.0;

		return projectedX + halfWidth >= inventoryLeft
			&& projectedX - halfWidth <= inventoryRight
			&& projectedY + halfHeight >= inventoryTop
			&& projectedY - halfHeight <= inventoryBottom;
	}

	private static int blendWhite(final int color, final double amount) {
		double value = Math.max(0.0, Math.min(1.0, amount));
		int red = (int) Math.round(255 + ((color >> 16 & 0xFF) - 255) * value);
		int green = (int) Math.round(255 + ((color >> 8 & 0xFF) - 255) * value);
		int blue = (int) Math.round(255 + ((color & 0xFF) - 255) * value);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	/** Preserves any tint already supplied by Vanilla or another compatible renderer. */
	private static int multiply(final int first, final int second) {
		int alpha = (first >>> 24) * (second >>> 24) / 255;
		int red = (first >> 16 & 0xFF) * (second >> 16 & 0xFF) / 255;
		int green = (first >> 8 & 0xFF) * (second >> 8 & 0xFF) / 255;
		int blue = (first & 0xFF) * (second & 0xFF) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}
}
