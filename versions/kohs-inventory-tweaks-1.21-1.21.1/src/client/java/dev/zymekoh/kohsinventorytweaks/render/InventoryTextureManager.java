package dev.zymekoh.kohsinventorytweaks.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.TextureSource;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.media.BackgroundMediaManager;
import dev.zymekoh.kohsinventorytweaks.media.BackgroundMediaManager.AnimatedBackground;
import dev.zymekoh.kohsinventorytweaks.media.BackgroundMediaManager.AnimationFrame;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;

public final class InventoryTextureManager {
	public static final ResourceLocation VANILLA_INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
	public static final ResourceLocation VANILLA_CONTAINER = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final ResourceLocation GENERATED_INVENTORY = ResourceLocation.fromNamespaceAndPath(
		KoHsInventoryTweaksClient.MOD_ID,
		"dynamic/custom_inventory"
	);
	private static final ResourceLocation[] GENERATED_CONTAINERS = new ResourceLocation[7];
	private static final int TEXTURE_SIZE = 256;
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static DynamicTexture dynamicTexture;
	private static int[] basePixels;
	private static final DynamicTexture[] containerTextures = new DynamicTexture[7];
	private static int[] containerBasePixels;
	private static final AnimationFrame[] containerRenderedFrames = new AnimationFrame[7];
	private static final String[] containerStyleKeys = new String[7];
	private static final Map<String, SurfaceTextureState> surfaceTextures = new HashMap<>();
	private static AnimatedBackground background;
	private static AnimationFrame renderedFrame;
	private static String loadedBaseKey = "";
	private static String loadedContainerBaseKey = "";
	private static String loadedBackgroundKey = "";
	private static String composedStyleKey = "";
	private static int resourceGeneration;
	private static long animationStartedAt;
	private static int nextSurfaceTextureId;

	public record SlotRegion(int x, int y) {
	}

	static {
		for (int rows = 1; rows <= 6; rows++) {
			GENERATED_CONTAINERS[rows] = ResourceLocation.fromNamespaceAndPath(
				KoHsInventoryTweaksClient.MOD_ID,
				"dynamic/custom_container_" + rows
			);
			containerStyleKeys[rows] = "";
		}
	}

	private InventoryTextureManager() {
	}

	public static ResourceLocation textureFor(final InventoryTweaksConfig config) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION)
			|| isUnmodifiedAppliedTexture(config)) {
			return VANILLA_INVENTORY;
		}

		Minecraft minecraft = Minecraft.getInstance();
		String baseKey = resourceGeneration + ":" + config.inventoryTextureSource;
		if (!baseKey.equals(loadedBaseKey) || basePixels == null) {
			rebuildBase(minecraft, config.inventoryTextureSource, baseKey);
		}
		String backgroundKey = backgroundKey(config);
		if (!backgroundKey.equals(loadedBackgroundKey)) {
			rebuildBackground(config, backgroundKey);
		}

		AnimationFrame nextFrame = backgroundFrame(config);
		String styleKey = styleKey(config, baseKey, backgroundKey);
		if (dynamicTexture == null || nextFrame != renderedFrame || !styleKey.equals(composedStyleKey)) {
			composeAndUpload(minecraft, config, nextFrame);
			composedStyleKey = styleKey;
		}
		return dynamicTexture == null ? VANILLA_INVENTORY : GENERATED_INVENTORY;
	}

	/**
	 * Returns a customized generic container texture for chests, Ender chests and
	 * barrels. Other container textures are deliberately left untouched.
	 */
	public static ResourceLocation containerTextureFor(
		final InventoryTweaksConfig config,
		final ResourceLocation original,
		final int imageHeight
	) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION)
			|| !VANILLA_CONTAINER.equals(original) || isUnmodifiedAppliedTexture(config)) {
			return original;
		}
		int rows = Math.max(1, Math.min(6, (imageHeight - 114) / 18));
		Minecraft minecraft = Minecraft.getInstance();
		String baseKey = resourceGeneration + ":container:" + config.inventoryTextureSource;
		if (!baseKey.equals(loadedContainerBaseKey) || containerBasePixels == null) {
			rebuildContainerBase(minecraft, config.inventoryTextureSource, baseKey);
		}
		String backgroundKey = backgroundKey(config);
		if (!backgroundKey.equals(loadedBackgroundKey)) {
			rebuildBackground(config, backgroundKey);
		}
		AnimationFrame nextFrame = backgroundFrame(config);
		String styleKey = styleKey(config, baseKey, backgroundKey) + ":rows=" + rows;
		if (containerTextures[rows] == null
			|| nextFrame != containerRenderedFrames[rows]
			|| !styleKey.equals(containerStyleKeys[rows])) {
			composeContainerAndUpload(minecraft, config, nextFrame, rows);
			containerStyleKeys[rows] = styleKey;
		}
		return containerTextures[rows] == null ? original : GENERATED_CONTAINERS[rows];
	}

	/**
	 * Resolves the background used by the container screen that is currently
	 * being rendered. Slot coordinates come from the real menu, so modded slot
	 * arrangements and every Vanilla workstation keep their functional layout.
	 */
	public static ResourceLocation screenTextureFor(
		final InventoryTweaksConfig config,
		final ResourceLocation original,
		final AbstractContainerScreen<?> screen,
		final int imageWidth,
		final int imageHeight
	) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION)
			|| !isContainerSurface(original)) {
			return original;
		}
		if (VANILLA_INVENTORY.equals(original)) {
			return textureFor(config);
		}
		if (VANILLA_CONTAINER.equals(original)) {
			return containerTextureFor(config, original, imageHeight);
		}

		List<SlotRegion> slots = new ArrayList<>(screen.getMenu().slots.size());
		for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
			slots.add(new SlotRegion(slot.x, slot.y));
		}
		return surfaceTextureFor(
			config,
			original,
			imageWidth,
			imageHeight,
			logicalTextureWidth(original),
			256,
			slots
		);
	}

	/**
	 * Uses the same resource-pack-aware compositor as real screens for the
	 * configuration preview.
	 */
	public static ResourceLocation previewTextureFor(
		final InventoryTweaksConfig config,
		final ResourceLocation original,
		final int imageWidth,
		final int imageHeight,
		final int logicalTextureWidth,
		final int logicalTextureHeight,
		final List<SlotRegion> slots
	) {
		return surfaceTextureFor(
			config,
			original,
			imageWidth,
			imageHeight,
			logicalTextureWidth,
			logicalTextureHeight,
			slots
		);
	}

	public static boolean hasColorCustomization(final InventoryTweaksConfig config) {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION)
			&& (config.frameColor != 0xFFFFFF
			|| config.frameOpacity != 255
			|| config.slotColor != 0xFFFFFF
			|| config.slotOpacity != 255);
	}

	private static ResourceLocation surfaceTextureFor(
		final InventoryTweaksConfig config,
		final ResourceLocation original,
		final int imageWidth,
		final int imageHeight,
		final int logicalTextureWidth,
		final int logicalTextureHeight,
		final List<SlotRegion> slots
	) {
		if (isUnmodifiedAppliedTexture(config)) {
			return original;
		}
		String slotSignature = slotSignature(slots);
		String cacheKey = original + ":" + imageWidth + "x" + imageHeight + ":"
			+ logicalTextureWidth + "x" + logicalTextureHeight + ":" + slotSignature;
		SurfaceTextureState state = surfaceTextures.computeIfAbsent(
			cacheKey,
			ignored -> new SurfaceTextureState(
				ResourceLocation.fromNamespaceAndPath(
					KoHsInventoryTweaksClient.MOD_ID,
					"dynamic/container_surface_" + nextSurfaceTextureId++
				),
				imageWidth,
				imageHeight,
				logicalTextureWidth,
				logicalTextureHeight,
				slots
			)
		);

		Minecraft minecraft = Minecraft.getInstance();
		String baseKey = resourceGeneration + ":surface:" + config.inventoryTextureSource + ":" + original;
		if (!baseKey.equals(state.loadedBaseKey) || state.basePixels == null) {
			state.loadBase(minecraft, config.inventoryTextureSource, original, baseKey);
		}
		String backgroundKey = backgroundKey(config);
		if (!backgroundKey.equals(loadedBackgroundKey)) {
			rebuildBackground(config, backgroundKey);
		}
		AnimationFrame nextFrame = backgroundFrame(config);
		String styleKey = styleKey(config, baseKey, backgroundKey);
		if (state.texture == null || nextFrame != state.renderedFrame || !styleKey.equals(state.styleKey)) {
			state.composeAndUpload(minecraft, config, nextFrame);
			state.styleKey = styleKey;
		}
		return state.texture == null ? original : state.generated;
	}

	private static boolean isContainerSurface(final ResourceLocation texture) {
		return "minecraft".equals(texture.getNamespace())
			&& texture.getPath().startsWith("textures/gui/container/")
			&& texture.getPath().endsWith(".png");
	}

	private static int logicalTextureWidth(final ResourceLocation texture) {
		return texture.getPath().endsWith("/villager.png") ? 512 : 256;
	}

	private static String slotSignature(final List<SlotRegion> slots) {
		StringBuilder signature = new StringBuilder(slots.size() * 8);
		for (SlotRegion slot : slots) {
			signature.append(slot.x()).append(',').append(slot.y()).append(';');
		}
		return signature.toString();
	}

	public static void onResourcesReloaded() {
		resourceGeneration++;
		loadedBaseKey = "";
		composedStyleKey = "";
		basePixels = null;
		loadedContainerBaseKey = "";
		containerBasePixels = null;
		for (int rows = 1; rows <= 6; rows++) {
			containerStyleKeys[rows] = "";
		}
		for (SurfaceTextureState state : surfaceTextures.values()) {
			state.loadedBaseKey = "";
			state.styleKey = "";
			state.basePixels = null;
		}
	}

	public static void invalidateConfiguration() {
		composedStyleKey = "";
		for (int rows = 1; rows <= 6; rows++) {
			containerStyleKeys[rows] = "";
		}
		for (SurfaceTextureState state : surfaceTextures.values()) {
			state.styleKey = "";
		}
	}

	private static boolean isUnmodifiedAppliedTexture(final InventoryTweaksConfig config) {
		return config.inventoryTextureSource == TextureSource.APPLIED
			&& !InventoryAnimationController.suppressAllInventoryAnimations()
			&& config.frameColor == 0xFFFFFF
			&& config.frameOpacity == 255
			&& config.slotColor == 0xFFFFFF
			&& config.slotOpacity == 255
			&& (config.customBackgroundFile == null || config.customBackgroundFile.isBlank());
	}

	private static String backgroundKey(final InventoryTweaksConfig config) {
		long backgroundModified = 0L;
		if (config.customBackgroundFile != null) {
			try {
				backgroundModified = Files.getLastModifiedTime(ConfigStore.resolveBackground(config.customBackgroundFile)).toMillis();
			} catch (IOException ignored) {
			}
		}
		return config.customBackgroundFile + ":" + backgroundModified;
	}

	private static String styleKey(
		final InventoryTweaksConfig config,
		final String baseKey,
		final String backgroundKey
	) {
		return baseKey + ":" + backgroundKey + ":" + config.frameColor + ":" + config.frameOpacity
			+ ":" + config.slotColor + ":" + config.slotOpacity + ":" + config.backgroundOpacity
			+ ":static=" + InventoryAnimationController.suppressAllInventoryAnimations();
	}

	private static AnimationFrame backgroundFrame(final InventoryTweaksConfig config) {
		if (background == null) {
			return null;
		}
		return background.frameAt(
			InventoryAnimationController.suppressAllInventoryAnimations() ? 0L : System.currentTimeMillis() - animationStartedAt
		);
	}

	private static void rebuildBase(final Minecraft minecraft, final TextureSource source, final String key) {
		try {
			basePixels = loadBasePixels(minecraft, source, VANILLA_INVENTORY);
		} catch (Exception exception) {
			KoHsInventoryTweaksClient.LOGGER.error("Could not load the selected inventory texture", exception);
			basePixels = null;
		}
		loadedBaseKey = key;
		composedStyleKey = "";
	}

	private static void rebuildContainerBase(final Minecraft minecraft, final TextureSource source, final String key) {
		try {
			containerBasePixels = loadBasePixels(minecraft, source, VANILLA_CONTAINER);
		} catch (Exception exception) {
			KoHsInventoryTweaksClient.LOGGER.error("Could not load the selected container texture", exception);
			containerBasePixels = null;
		}
		loadedContainerBaseKey = key;
		for (int rows = 1; rows <= 6; rows++) {
			containerStyleKeys[rows] = "";
		}
	}

	private static void rebuildBackground(final InventoryTweaksConfig config, final String key) {
		background = null;
		if (config.customBackgroundFile != null) {
			Path path = ConfigStore.resolveBackground(config.customBackgroundFile);
			if (Files.isRegularFile(path)) {
				try {
					background = BackgroundMediaManager.load(path);
				} catch (Exception exception) {
					KoHsInventoryTweaksClient.LOGGER.warn("Could not load custom inventory background {}", path.getFileName(), exception);
				}
			}
		}
		loadedBackgroundKey = key;
		composedStyleKey = "";
		renderedFrame = null;
		animationStartedAt = System.currentTimeMillis();
	}

	private static int[] loadBasePixels(
		final Minecraft minecraft,
		final TextureSource source,
		final ResourceLocation texture
	) throws IOException {
		try (InputStream input = openBaseTexture(minecraft, source, texture); NativeImage original = NativeImage.read(input)) {
			NativeImage normalized = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
			try {
				int firstFrameHeight = Math.min(original.getHeight(), original.getWidth());
				original.resizeSubRectTo(0, 0, original.getWidth(), firstFrameHeight, normalized);
				int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
				for (int y = 0; y < TEXTURE_SIZE; y++) {
					for (int x = 0; x < TEXTURE_SIZE; x++) {
						pixels[x + y * TEXTURE_SIZE] = fromNativeRgba(normalized.getPixelRGBA(x, y));
					}
				}
				return pixels;
			} finally {
				normalized.close();
			}
		}
	}

	private static InputStream openBaseTexture(
		final Minecraft minecraft,
		final TextureSource source,
		final ResourceLocation texture
	) throws IOException {
		if (source == TextureSource.VANILLA) {
			IoSupplier<InputStream> supplier = minecraft.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, texture);
			if (supplier == null) {
				throw new IOException("Vanilla inventory texture is missing");
			}
			return supplier.get();
		}
		Resource resource = minecraft.getResourceManager().getResource(texture)
			.orElseThrow(() -> new IOException("Applied GUI texture is missing: " + texture));
		return resource.open();
	}

	private static void composeAndUpload(
		final Minecraft minecraft,
		final InventoryTweaksConfig config,
		final AnimationFrame frame
	) {
		if (basePixels == null) {
			return;
		}
		if (dynamicTexture == null) {
			NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
			dynamicTexture = DynamicTextureFactory.create("KoHs customized inventory", image);
			minecraft.getTextureManager().register(GENERATED_INVENTORY, dynamicTexture);
		}

		NativeImage output = dynamicTexture.getPixels();
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				int pixel = basePixels[x + y * TEXTURE_SIZE];
				if (x < INVENTORY_WIDTH && y < INVENTORY_HEIGHT) {
					if (isSlotPixel(x, y)) {
						pixel = tint(pixel, config.slotColor, config.slotOpacity);
					} else {
						int framePixel = tint(pixel, config.frameColor, config.frameOpacity);
						if (frame != null) {
							int backgroundPixel = withOpacity(frame.pixels()[x + y * INVENTORY_WIDTH], config.backgroundOpacity);
							pixel = blend(backgroundPixel, framePixel);
						} else {
							pixel = framePixel;
						}
					}
				}
				output.setPixelRGBA(x, y, toNativeRgba(pixel));
			}
		}
		dynamicTexture.upload();
		renderedFrame = frame;
	}

	private static void composeContainerAndUpload(
		final Minecraft minecraft,
		final InventoryTweaksConfig config,
		final AnimationFrame frame,
		final int rows
	) {
		if (containerBasePixels == null) {
			return;
		}
		if (containerTextures[rows] == null) {
			NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
			containerTextures[rows] = DynamicTextureFactory.create("KoHs customized container " + rows, image);
			minecraft.getTextureManager().register(GENERATED_CONTAINERS[rows], containerTextures[rows]);
		}

		int topHeight = rows * 18 + 17;
		int imageHeight = 114 + rows * 18;
		NativeImage output = containerTextures[rows].getPixels();
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				int pixel = containerBasePixels[x + y * TEXTURE_SIZE];
				int destinationY = containerDestinationY(y, topHeight);
				if (x < INVENTORY_WIDTH && destinationY >= 0 && destinationY < imageHeight) {
					if (isContainerSlotPixel(x, y, rows)) {
						pixel = tint(pixel, config.slotColor, config.slotOpacity);
					} else {
						int framePixel = tint(pixel, config.frameColor, config.frameOpacity);
						if (frame != null) {
							int backgroundX = Math.min(INVENTORY_WIDTH - 1, x);
							int backgroundY = Math.min(INVENTORY_HEIGHT - 1, destinationY * INVENTORY_HEIGHT / imageHeight);
							int backgroundPixel = withOpacity(
								frame.pixels()[backgroundX + backgroundY * INVENTORY_WIDTH],
								config.backgroundOpacity
							);
							pixel = blend(backgroundPixel, framePixel);
						} else {
							pixel = framePixel;
						}
					}
				}
				output.setPixelRGBA(x, y, toNativeRgba(pixel));
			}
		}
		containerTextures[rows].upload();
		containerRenderedFrames[rows] = frame;
	}

	private static int containerDestinationY(final int sourceY, final int topHeight) {
		if (sourceY >= 0 && sourceY < topHeight) {
			return sourceY;
		}
		if (sourceY >= 126 && sourceY < 222) {
			return topHeight + sourceY - 126;
		}
		return -1;
	}

	private static boolean isContainerSlotPixel(final int x, final int y, final int rows) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < 9; column++) {
				if (inside(x, y, 7 + column * 18, 17 + row * 18, 18, 18)) {
					return true;
				}
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				if (inside(x, y, 7 + column * 18, 139 + row * 18, 18, 18)) {
					return true;
				}
			}
		}
		for (int column = 0; column < 9; column++) {
			if (inside(x, y, 7 + column * 18, 197, 18, 18)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSlotPixel(final int x, final int y) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				if (inside(x, y, 7 + column * 18, 83 + row * 18, 18, 18)) {
					return true;
				}
			}
		}
		for (int column = 0; column < 9; column++) {
			if (inside(x, y, 7 + column * 18, 141, 18, 18)) {
				return true;
			}
		}
		for (int row = 0; row < 4; row++) {
			if (inside(x, y, 7, 7 + row * 18, 18, 18)) {
				return true;
			}
		}
		for (int row = 0; row < 2; row++) {
			for (int column = 0; column < 2; column++) {
				if (inside(x, y, 97 + column * 18, 17 + row * 18, 18, 18)) {
					return true;
				}
			}
		}
		return inside(x, y, 153, 27, 18, 18) || inside(x, y, 76, 61, 18, 18);
	}

	private static final class SurfaceTextureState {
		private final ResourceLocation generated;
		private final int imageWidth;
		private final int imageHeight;
		private final int logicalTextureWidth;
		private final int logicalTextureHeight;
		private final boolean[] slotMask;
		private DynamicTexture texture;
		private int[] basePixels;
		private int physicalWidth;
		private int physicalHeight;
		private String loadedBaseKey = "";
		private String styleKey = "";
		private AnimationFrame renderedFrame;

		private SurfaceTextureState(
			final ResourceLocation generated,
			final int imageWidth,
			final int imageHeight,
			final int logicalTextureWidth,
			final int logicalTextureHeight,
			final List<SlotRegion> slots
		) {
			this.generated = generated;
			this.imageWidth = Math.max(1, imageWidth);
			this.imageHeight = Math.max(1, imageHeight);
			this.logicalTextureWidth = Math.max(1, logicalTextureWidth);
			this.logicalTextureHeight = Math.max(1, logicalTextureHeight);
			this.slotMask = new boolean[this.logicalTextureWidth * this.logicalTextureHeight];
			for (SlotRegion slot : slots) {
				this.markSlot(slot.x() - 1, slot.y() - 1);
			}
		}

		private void markSlot(final int left, final int top) {
			int right = Math.min(this.logicalTextureWidth, left + 18);
			int bottom = Math.min(this.logicalTextureHeight, top + 18);
			for (int y = Math.max(0, top); y < bottom; y++) {
				for (int x = Math.max(0, left); x < right; x++) {
					this.slotMask[x + y * this.logicalTextureWidth] = true;
				}
			}
		}

		private void loadBase(
			final Minecraft minecraft,
			final TextureSource source,
			final ResourceLocation original,
			final String key
		) {
			try (InputStream input = openBaseTexture(minecraft, source, original); NativeImage image = NativeImage.read(input)) {
				this.physicalWidth = image.getWidth();
				int expectedFrameHeight = Math.max(
					1,
					this.physicalWidth * this.logicalTextureHeight / this.logicalTextureWidth
				);
				this.physicalHeight = Math.min(image.getHeight(), expectedFrameHeight);
				this.basePixels = new int[this.physicalWidth * this.physicalHeight];
				for (int y = 0; y < this.physicalHeight; y++) {
					for (int x = 0; x < this.physicalWidth; x++) {
						this.basePixels[x + y * this.physicalWidth] = fromNativeRgba(image.getPixelRGBA(x, y));
					}
				}
				this.texture = null;
				this.renderedFrame = null;
				this.styleKey = "";
			} catch (Exception exception) {
				KoHsInventoryTweaksClient.LOGGER.error("Could not load container surface {}", original, exception);
				this.basePixels = null;
			}
			this.loadedBaseKey = key;
		}

		private void composeAndUpload(
			final Minecraft minecraft,
			final InventoryTweaksConfig config,
			final AnimationFrame frame
		) {
			if (this.basePixels == null || this.physicalWidth <= 0 || this.physicalHeight <= 0) {
				return;
			}
			if (this.texture == null) {
				NativeImage image = new NativeImage(this.physicalWidth, this.physicalHeight, true);
				this.texture = DynamicTextureFactory.create("KoHs customized container surface", image);
				minecraft.getTextureManager().register(this.generated, this.texture);
			}

			NativeImage output = this.texture.getPixels();
			for (int physicalY = 0; physicalY < this.physicalHeight; physicalY++) {
				int logicalY = Math.min(
					this.logicalTextureHeight - 1,
					physicalY * this.logicalTextureHeight / this.physicalHeight
				);
				for (int physicalX = 0; physicalX < this.physicalWidth; physicalX++) {
					int logicalX = Math.min(
						this.logicalTextureWidth - 1,
						physicalX * this.logicalTextureWidth / this.physicalWidth
					);
					int pixel = this.basePixels[physicalX + physicalY * this.physicalWidth];
					if (logicalX < this.imageWidth && logicalY < this.imageHeight) {
						if (this.slotMask[logicalX + logicalY * this.logicalTextureWidth]) {
							pixel = tint(pixel, config.slotColor, config.slotOpacity);
						} else {
							int framePixel = tint(pixel, config.frameColor, config.frameOpacity);
							if (frame != null) {
								int backgroundX = Math.min(
									INVENTORY_WIDTH - 1,
									logicalX * INVENTORY_WIDTH / this.imageWidth
								);
								int backgroundY = Math.min(
									INVENTORY_HEIGHT - 1,
									logicalY * INVENTORY_HEIGHT / this.imageHeight
								);
								int backgroundPixel = withOpacity(
									frame.pixels()[backgroundX + backgroundY * INVENTORY_WIDTH],
									config.backgroundOpacity
								);
								pixel = blend(backgroundPixel, framePixel);
							} else {
								pixel = framePixel;
							}
						}
					}
					output.setPixelRGBA(physicalX, physicalY, toNativeRgba(pixel));
				}
			}
			this.texture.upload();
			this.renderedFrame = frame;
		}
	}

	private static int fromNativeRgba(final int abgr) {
		return abgr & 0xFF00FF00 | (abgr & 0x00FF0000) >>> 16 | (abgr & 0x000000FF) << 16;
	}

	private static int toNativeRgba(final int argb) {
		return fromNativeRgba(argb);
	}

	private static boolean inside(final int x, final int y, final int left, final int top, final int width, final int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private static int tint(final int pixel, final int color, final int opacity) {
		int alpha = channel(pixel, 24) * clampByte(opacity) / 255;
		int red = channel(pixel, 16) * channel(color, 16) / 255;
		int green = channel(pixel, 8) * channel(color, 8) / 255;
		int blue = channel(pixel, 0) * channel(color, 0) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int withOpacity(final int pixel, final int opacity) {
		int alpha = channel(pixel, 24) * clampByte(opacity) / 255;
		return alpha << 24 | (pixel & 0xFFFFFF);
	}

	private static int blend(final int over, final int under) {
		int overAlpha = channel(over, 24);
		int underAlpha = channel(under, 24);
		int outAlpha = overAlpha + underAlpha * (255 - overAlpha) / 255;
		if (outAlpha == 0) {
			return 0;
		}
		int red = blendChannel(channel(over, 16), overAlpha, channel(under, 16), underAlpha, outAlpha);
		int green = blendChannel(channel(over, 8), overAlpha, channel(under, 8), underAlpha, outAlpha);
		int blue = blendChannel(channel(over, 0), overAlpha, channel(under, 0), underAlpha, outAlpha);
		return outAlpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int blendChannel(final int over, final int overAlpha, final int under, final int underAlpha, final int outAlpha) {
		int numerator = over * overAlpha * 255 + under * underAlpha * (255 - overAlpha);
		return Math.max(0, Math.min(255, numerator / Math.max(1, outAlpha * 255)));
	}

	private static int channel(final int color, final int shift) {
		return color >> shift & 0xFF;
	}

	private static int clampByte(final int value) {
		return Math.max(0, Math.min(255, value));
	}
}
