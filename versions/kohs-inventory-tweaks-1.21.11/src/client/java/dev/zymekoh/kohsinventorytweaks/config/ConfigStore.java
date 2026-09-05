package dev.zymekoh.kohsinventorytweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.cursor.CursorTarget;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks.json");
	private static final Path BACKGROUNDS_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks").resolve("backgrounds");
	private static final Path BACKUPS_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks").resolve("backups");
	private static final Path EXPORTS_PATH = FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks").resolve("exports");
	private static final int HISTORY_LIMIT = 24;
	private static final long BACKUP_INTERVAL_MILLIS = 60_000L;
	private static final Deque<InventoryTweaksConfig> UNDO = new ArrayDeque<>();
	private static final Deque<InventoryTweaksConfig> REDO = new ArrayDeque<>();
	private static InventoryTweaksConfig config = new InventoryTweaksConfig();
	private static long lastBackupAtMillis;

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

	public static Path backupsDirectory() {
		return BACKUPS_PATH;
	}

	public static Path exportsDirectory() {
		return EXPORTS_PATH;
	}

	public static Path resolveBackground(final String fileName) {
		return BACKGROUNDS_PATH.resolve(Paths.get(fileName).getFileName().toString());
	}

	public static synchronized void replaceAndSave(final InventoryTweaksConfig replacement) {
		InventoryTweaksConfig next = sanitize(replacement == null ? null : replacement.copy());
		if (config.sameValues(next)) {
			return;
		}
		pushHistory(UNDO, config.copy());
		REDO.clear();
		backupBeforeOverwrite();
		config = next;
		save();
	}

	public static synchronized boolean canUndo() {
		return !UNDO.isEmpty();
	}

	public static synchronized boolean canRedo() {
		return !REDO.isEmpty();
	}

	public static synchronized boolean undo() {
		if (UNDO.isEmpty()) {
			return false;
		}
		pushHistory(REDO, config.copy());
		config = sanitize(UNDO.removeLast());
		save();
		return true;
	}

	public static synchronized boolean redo() {
		if (REDO.isEmpty()) {
			return false;
		}
		pushHistory(UNDO, config.copy());
		config = sanitize(REDO.removeLast());
		save();
		return true;
	}

	public static synchronized Path exportSnapshot() throws java.io.IOException {
		Files.createDirectories(EXPORTS_PATH);
		Path target = EXPORTS_PATH.resolve("kohs-inventory-tweaks-" + System.currentTimeMillis() + ".json");
		try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
		return target;
	}

	public static synchronized void importSnapshot(final Path source) throws java.io.IOException {
		if (source == null || !Files.isRegularFile(source)) {
			throw new java.io.IOException("The selected configuration file does not exist");
		}
		try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
			InventoryTweaksConfig imported = GSON.fromJson(reader, InventoryTweaksConfig.class);
			replaceAndSave(imported);
		}
	}

	public static synchronized boolean restoreLatestBackup() {
		try {
			if (!Files.isDirectory(BACKUPS_PATH)) {
				return false;
			}
			Path latest;
			try (java.util.stream.Stream<Path> files = Files.list(BACKUPS_PATH)) {
				latest = files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.max(Comparator.comparingLong(ConfigStore::lastModifiedQuietly))
					.orElse(null);
			}
			if (latest == null) {
				return false;
			}
			importSnapshot(latest);
			return true;
		} catch (Exception exception) {
			KoHsInventoryTweaksClient.LOGGER.error("Could not restore the latest KoHs configuration backup", exception);
			return false;
		}
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
		// Removed profiles are migrated to their inert compatibility values.
		sanitized.activeProfile = InventoryTweaksConfig.ProfilePreset.CUSTOM;
		sanitized.autoProfileSwitch = false;
		sanitized.singleplayerProfile = InventoryTweaksConfig.ProfilePreset.BUILDING;
		sanitized.multiplayerProfile = InventoryTweaksConfig.ProfilePreset.PVP;
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
		sanitized.visiblePlayerDepthIntensity = clampByte(candidate.visiblePlayerDepthIntensity);
		// Smart Highlighter was removed; keep old JSON readable but never execute it.
		sanitized.smartHighlighterEnabled = false;
		sanitized.smartLowDurabilityEnabled = false;
		sanitized.smartLowDurabilityThreshold = clamp(candidate.smartLowDurabilityThreshold, 1, 100);
		sanitized.smartLowDurabilityColor = candidate.smartLowDurabilityColor & 0xFFFFFF;
		sanitized.smartEnchantedEnabled = false;
		sanitized.smartEnchantedColor = candidate.smartEnchantedColor & 0xFFFFFF;
		sanitized.smartHighlighterHotbar = false;
		sanitized.accessibilitySlotFocusEnabled = candidate.accessibilitySlotFocusEnabled;
		sanitized.accessibilitySlotFocusPulse = candidate.accessibilitySlotFocusPulse;
		sanitized.accessibilitySlotFocusOpacity = clampByte(candidate.accessibilitySlotFocusOpacity);
		sanitized.accessibilitySlotFocusColor = candidate.accessibilitySlotFocusColor & 0xFFFFFF;
		sanitized.visiblePlayerGlowEnabled = candidate.visiblePlayerGlowEnabled;
		sanitized.visiblePlayerHighlightEnabled = candidate.visiblePlayerHighlightEnabled;
		sanitized.visiblePlayerLightGlowEnabled = candidate.visiblePlayerLightGlowEnabled;
		sanitized.visiblePlayerGlowIntensity = clampByte(candidate.visiblePlayerGlowIntensity);
		sanitized.visiblePlayerGlowBrightness = clampByte(candidate.visiblePlayerGlowBrightness);
		sanitized.visiblePlayerGlowDistance = clamp(candidate.visiblePlayerGlowDistance, 4, 96);
		sanitized.visiblePlayerGlowPulse = candidate.visiblePlayerGlowPulse;
		sanitized.visiblePlayerGlowColor = candidate.visiblePlayerGlowColor & 0xFFFFFF;
		sanitized.containerProfilesEnabled = candidate.containerProfilesEnabled;
		sanitized.chestContainerScaleEnabled = candidate.chestContainerScaleEnabled;
		sanitized.chestContainerScale = InventoryGuiScaler.clampConfiguredScale(candidate.chestContainerScale);
		sanitized.shulkerContainerScaleEnabled = candidate.shulkerContainerScaleEnabled;
		sanitized.shulkerContainerScale = InventoryGuiScaler.clampConfiguredScale(candidate.shulkerContainerScale);
		sanitized.enderChestContainerScaleEnabled = candidate.enderChestContainerScaleEnabled;
		sanitized.enderChestContainerScale = InventoryGuiScaler.clampConfiguredScale(candidate.enderChestContainerScale);
		sanitized.barrelContainerScaleEnabled = candidate.barrelContainerScaleEnabled;
		sanitized.barrelContainerScale = InventoryGuiScaler.clampConfiguredScale(candidate.barrelContainerScale);
		sanitized.menuParticleDensity = clamp(candidate.menuParticleDensity, 0, 200);
		sanitized.animatedBackgroundFps = clamp(candidate.animatedBackgroundFps, 1, 60);
		sanitized.pauseAnimatedBackgroundWhenUnfocused = candidate.pauseAnimatedBackgroundWhenUnfocused;
		sanitized.reduceParticlesWhenUnfocused = candidate.reduceParticlesWhenUnfocused;
		sanitized.automaticBackups = candidate.automaticBackups;
		sanitized.backupRetention = clamp(candidate.backupRetention, 1, 10);
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
		sanitized.inventoryLandingItem = sanitizeItemId(candidate.inventoryLandingItem);
		for (CursorTarget target : CursorTarget.values()) {
			sanitized.setPosition(target, candidate.getPosition(target));
		}
		return sanitized;
	}

	private static @Nullable String sanitizeItemId(final @Nullable String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(itemId);
		return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier)
			? identifier.toString()
			: null;
	}

	private static int clampByte(final int value) {
		return Math.max(0, Math.min(255, value));
	}

	private static int clamp(final int value, final int minimum, final int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static void pushHistory(final Deque<InventoryTweaksConfig> history, final InventoryTweaksConfig value) {
		while (history.size() >= HISTORY_LIMIT) {
			history.removeFirst();
		}
		history.addLast(value);
	}

	private static void backupBeforeOverwrite() {
		if (!config.automaticBackups || !Files.isRegularFile(CONFIG_PATH)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBackupAtMillis < BACKUP_INTERVAL_MILLIS) {
			return;
		}
		try {
			Files.createDirectories(BACKUPS_PATH);
			Path target = BACKUPS_PATH.resolve("kohs-inventory-tweaks-" + now + ".json");
			Files.copy(CONFIG_PATH, target, StandardCopyOption.REPLACE_EXISTING);
			lastBackupAtMillis = now;
			pruneBackups(config.backupRetention);
		} catch (Exception exception) {
			KoHsInventoryTweaksClient.LOGGER.warn("Could not create a KoHs configuration backup", exception);
		}
	}

	private static void pruneBackups(final int retention) throws java.io.IOException {
		try (java.util.stream.Stream<Path> files = Files.list(BACKUPS_PATH)) {
			List<Path> ordered = files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted(Comparator.comparingLong(ConfigStore::lastModifiedQuietly).reversed())
				.toList();
			for (int index = Math.max(1, retention); index < ordered.size(); index++) {
				Files.deleteIfExists(ordered.get(index));
			}
		}
	}

	private static long lastModifiedQuietly(final Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static @Nullable String sanitizeFileName(final @Nullable String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		String sanitized = Paths.get(fileName).getFileName().toString();
		return sanitized.isBlank() ? null : sanitized;
	}
}
