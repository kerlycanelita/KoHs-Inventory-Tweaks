package dev.zymekoh.kohsinventorytweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.cursor.CursorTarget;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks.json");
	private static final Path BACKGROUNDS_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks").resolve("backgrounds");
	private static InventoryTweaksConfig config = new InventoryTweaksConfig();

	private ConfigStore() {
	}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			InventoryTweaksConfig loaded = GSON.fromJson(reader, InventoryTweaksConfig.class);
			config = sanitize(loaded);
		} catch (Exception exception) {
			KoHsInventoryTweaksClient.LOGGER.error("Could not read {}", CONFIG_PATH, exception);
			config = new InventoryTweaksConfig();
		}
	}

	public static InventoryTweaksConfig get() {
		return config;
	}

	public static Path backgroundsDirectory() {
		return BACKGROUNDS_PATH;
	}

	public static Path resolveBackground(final String fileName) {
		return BACKGROUNDS_PATH.resolve(Paths.get(fileName).getFileName().toString());
	}

	public static synchronized void replaceAndSave(final InventoryTweaksConfig replacement) {
		config = sanitize(replacement == null ? null : replacement.copy());
		save();
	}

	public static synchronized void save() {
		Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
			try {
				Files.move(temporary, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception exception) {
			KoHsInventoryTweaksClient.LOGGER.error("Could not write {}", CONFIG_PATH, exception);
			try {
				Files.deleteIfExists(temporary);
			} catch (Exception cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
		}
	}

	private static InventoryTweaksConfig sanitize(final InventoryTweaksConfig candidate) {
		if (candidate == null) {
			return new InventoryTweaksConfig();
		}

		InventoryTweaksConfig sanitized = new InventoryTweaksConfig();
		sanitized.centerMouseFix = candidate.centerMouseFix;
		sanitized.superFastInventory = candidate.superFastInventory;
		sanitized.removeAllInventoryAnimations = candidate.removeAllInventoryAnimations;
		sanitized.inventoryGuiScalerEnabled = candidate.inventoryGuiScalerEnabled;
		sanitized.inventoryGuiScale = InventoryGuiScaler.clampConfiguredScale(candidate.inventoryGuiScale);
		sanitized.guiScalerWarningDismissed = candidate.guiScalerWarningDismissed;
		sanitized.affectAllContainers = candidate.affectAllContainers;
		sanitized.chestCursorEnabled = candidate.chestCursorEnabled;
		sanitized.shulkerCursorEnabled = candidate.shulkerCursorEnabled;
		sanitized.enderChestCursorEnabled = candidate.enderChestCursorEnabled;
		sanitized.barrelCursorEnabled = candidate.barrelCursorEnabled;
		sanitized.inventoryTextureSource = candidate.inventoryTextureSource == null
			? InventoryTweaksConfig.TextureSource.APPLIED
			: candidate.inventoryTextureSource;
		sanitized.frameColor = candidate.frameColor & 0xFFFFFF;
		sanitized.frameOpacity = clampByte(candidate.frameOpacity);
		sanitized.slotColor = candidate.slotColor & 0xFFFFFF;
		sanitized.slotOpacity = clampByte(candidate.slotOpacity);
		sanitized.backgroundOpacity = clampByte(candidate.backgroundOpacity);
		sanitized.inventoryBackdropOpacity = clampByte(candidate.inventoryBackdropOpacity);
		sanitized.customBackgroundFile = sanitizeFileName(candidate.customBackgroundFile);
		sanitized.itemHighlights.clear();
		Set<String> seenItems = new HashSet<>();
		if (candidate.itemHighlights != null) {
			for (InventoryTweaksConfig.ItemHighlight highlight : candidate.itemHighlights) {
				if (highlight == null || sanitized.itemHighlights.size() >= 256) {
					continue;
				}
				Identifier itemId = Identifier.tryParse(highlight.itemId);
				if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId) || !seenItems.add(itemId.toString())) {
					continue;
				}
				InventoryTweaksConfig.ItemHighlight clean = new InventoryTweaksConfig.ItemHighlight(itemId.toString());
				clean.backgroundColor = highlight.backgroundColor & 0xFFFFFF;
				clean.borderColor = highlight.borderColor & 0xFFFFFF;
				clean.highlightHotbar = highlight.highlightHotbar;
				clean.dynamicHighlight = highlight.dynamicHighlight;
				sanitized.itemHighlights.add(clean);
			}
		}
		for (CursorTarget target : CursorTarget.values()) {
			sanitized.setPosition(target, candidate.getPosition(target));
		}
		return sanitized;
	}

	private static int clampByte(final int value) {
		return Math.max(0, Math.min(255, value));
	}

	private static @Nullable String sanitizeFileName(final @Nullable String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		String sanitized = Paths.get(fileName).getFileName().toString();
		return sanitized.isBlank() ? null : sanitized;
	}
}
