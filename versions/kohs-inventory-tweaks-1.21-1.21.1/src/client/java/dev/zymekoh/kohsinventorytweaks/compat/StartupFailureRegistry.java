package dev.zymekoh.kohsinventorytweaks.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists confirmed startup blockers and attributable startup crash reports.
 * It deliberately refuses to guess a culprit from the ordinary Fabric mod list.
 */
public final class StartupFailureRegistry {
	private static final String MOD_ID = "kohs_inventory_tweaks";
	private static final String MOD_PACKAGE = "dev.zymekoh.kohsinventorytweaks";
	private static final int MAX_FAILURES = 24;
	private static final int MAX_REPORT_CHARS = 1_500_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "/startup-failures");
	private static final Path REGISTRY_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("kohs_inventory_tweaks")
		.resolve("startup-failures.json");
	private static final Pattern DESCRIPTION = Pattern.compile("(?m)^Description:\\s*(.+)$");
	private static final Pattern LAUNCHED_VERSION = Pattern.compile("(?m)^\\s*Launched Version:\\s*(.+)$");
	private static final Pattern CAUSED_BY = Pattern.compile("(?m)^Caused by:\\s*(.+)$");
	private static final Pattern FROM_MOD = Pattern.compile("(?i)\\bfrom mod\\s+([a-z0-9_.-]+)");
	private static final Pattern MIXIN_CONFIG = Pattern.compile("(?i)([a-z0-9_.-]+)\\.(?:client\\.)?mixins?\\.json");
	private static RegistryData data;
	private static boolean reportsScanned;

	private StartupFailureRegistry() {
	}

	public static synchronized void recordBlockingIssues(final List<CompatibilityIssue> issues) {
		ensureLoaded();
		boolean changed = false;
		long now = System.currentTimeMillis();
		for (CompatibilityIssue issue : issues) {
			if (issue.severity() != CompatibilityIssue.Severity.BLOCKING) {
				continue;
			}
			String sourceId = "blocker:" + issue.modId() + ':' + issue.version() + ':'
				+ issue.reason().name() + ':' + String.join("|", issue.conflictPoints());
			StoredFailure existing = find(sourceId);
			if (existing == null) {
				existing = new StoredFailure();
				existing.sourceId = sourceId;
				existing.firstSeen = now;
				existing.occurrences = 1;
				data.failures.add(existing);
			} else {
				existing.occurrences = Math.max(1, existing.occurrences) + 1;
			}
			existing.lastSeen = now;
			existing.minecraftVersion = minecraftVersion();
			existing.modId = issue.modId();
			existing.modName = issue.modName();
			existing.modVersion = issue.version();
			existing.reasonCode = issue.reason().name();
			existing.reasonText = "";
			existing.details = String.join(", ", issue.conflictPoints());
			existing.sourceType = "CONFIRMED_BLOCKER";
			changed = true;
		}
		if (changed) {
			trimAndSave();
		}
	}

	public static synchronized void refreshFromCrashReports() {
		ensureLoaded();
		if (reportsScanned) {
			return;
		}
		reportsScanned = true;
		Path crashDirectory = FabricLoader.getInstance().getGameDir().resolve("crash-reports");
		if (!Files.isDirectory(crashDirectory)) {
			return;
		}

		boolean changed = false;
		try (Stream<Path> paths = Files.list(crashDirectory)) {
			for (Path path : paths
				.filter(Files::isRegularFile)
				.filter(candidate -> candidate.getFileName().toString().startsWith("crash-")
					&& candidate.getFileName().toString().endsWith(".txt"))
				.sorted(Comparator.comparingLong(StartupFailureRegistry::lastModified).reversed())
				.limit(32)
				.toList()) {
				String sourceId = "report:" + path.getFileName() + ':' + lastModified(path) + ':' + safeSize(path);
				if (find(sourceId) != null) {
					continue;
				}
				StoredFailure parsed = parseStartupCrash(path, sourceId);
				if (parsed != null) {
					data.failures.add(parsed);
					changed = true;
				}
			}
		} catch (Exception exception) {
			LOGGER.debug("Could not scan startup crash reports", exception);
		}
		if (changed) {
			trimAndSave();
		}
	}

	public static synchronized List<Failure> entries() {
		refreshFromCrashReports();
		return data.failures.stream()
			.sorted(Comparator.comparingLong((StoredFailure entry) -> entry.lastSeen).reversed())
			.map(StoredFailure::snapshot)
			.toList();
	}

	private static StoredFailure parseStartupCrash(final Path path, final String sourceId) {
		try {
			String report = readReport(path);
			String primary = primarySection(report);
			String description = firstGroup(DESCRIPTION, primary);
			if (!isStartupFailure(description, primary)) {
				return null;
			}

			String lowerPrimary = primary.toLowerCase(Locale.ROOT);
			boolean mentionsKoHs = lowerPrimary.contains(MOD_ID)
				|| lowerPrimary.contains(MOD_PACKAGE)
				|| lowerPrimary.contains("kohs inventory tweaks");
			String culpritId = lastGroup(FROM_MOD, primary);
			if (culpritId == null) {
				culpritId = lastGroup(MIXIN_CONFIG, primary);
			}
			if (!mentionsKoHs && !MOD_ID.equals(culpritId)) {
				return null;
			}
			if (culpritId == null || culpritId.isBlank()) {
				culpritId = MOD_ID;
			}

			ModContainer culprit = FabricLoader.getInstance().getModContainer(culpritId).orElse(null);
			String reason = lastGroup(CAUSED_BY, primary);
			if (reason == null || reason.isBlank()) {
				reason = firstFailureLine(primary);
			}
			StoredFailure entry = new StoredFailure();
			entry.sourceId = sourceId;
			entry.firstSeen = lastModified(path);
			entry.lastSeen = entry.firstSeen;
			entry.minecraftVersion = valueOr(firstGroup(LAUNCHED_VERSION, report), minecraftVersion());
			entry.modId = culpritId;
			entry.modName = culprit == null ? culpritId : culprit.getMetadata().getName();
			entry.modVersion = culprit == null ? "?" : culprit.getMetadata().getVersion().getFriendlyString();
			entry.reasonCode = "";
			entry.reasonText = shorten(valueOr(reason, description), 360);
			entry.details = shorten(valueOr(description, path.getFileName().toString()) + " · " + path.getFileName(), 220);
			entry.sourceType = "CRASH_REPORT";
			entry.occurrences = 1;
			return entry;
		} catch (Exception exception) {
			LOGGER.debug("Could not parse startup crash report {}", path, exception);
			return null;
		}
	}

	private static boolean isStartupFailure(final String description, final String primary) {
		String normalized = valueOr(description, "").toLowerCase(Locale.ROOT);
		if (normalized.contains("initializing game")
			|| normalized.contains("bootstrap")
			|| normalized.contains("loading game")
			|| normalized.contains("rendering overlay")) {
			return true;
		}
		String lower = primary.toLowerCase(Locale.ROOT);
		return normalized.isBlank() && lower.contains("mixintransformererror") && lower.contains("minecraft.<init>");
	}

	private static String primarySection(final String report) {
		int end = report.indexOf("\n-- System Details --");
		if (end < 0) {
			end = report.indexOf("\r\n-- System Details --");
		}
		return end < 0 ? report : report.substring(0, end);
	}

	private static String firstFailureLine(final String primary) {
		for (String line : primary.lines().toList()) {
			String clean = line.trim();
			if (!clean.startsWith("at ")
				&& (clean.contains("Exception:") || clean.contains("Error:") || clean.startsWith("java.lang."))) {
				return clean;
			}
		}
		return null;
	}

	private static String readReport(final Path path) throws Exception {
		StringBuilder result = new StringBuilder();
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			char[] buffer = new char[8192];
			int read;
			while (result.length() < MAX_REPORT_CHARS && (read = reader.read(buffer)) >= 0) {
				result.append(buffer, 0, Math.min(read, MAX_REPORT_CHARS - result.length()));
			}
		}
		return result.toString();
	}

	private static String firstGroup(final Pattern pattern, final String input) {
		Matcher matcher = pattern.matcher(input);
		return matcher.find() ? matcher.group(1).trim() : null;
	}

	private static String lastGroup(final Pattern pattern, final String input) {
		Matcher matcher = pattern.matcher(input);
		String result = null;
		while (matcher.find()) {
			result = matcher.group(1).trim();
		}
		return result;
	}

	private static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}

	private static String valueOr(final String value, final String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String shorten(final String value, final int maximum) {
		String compact = value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
		return compact.length() <= maximum ? compact : compact.substring(0, maximum - 1) + "…";
	}

	private static long lastModified(final Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static long safeSize(final Path path) {
		try {
			return Files.size(path);
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static StoredFailure find(final String sourceId) {
		for (StoredFailure entry : data.failures) {
			if (sourceId.equals(entry.sourceId)) {
				return entry;
			}
		}
		return null;
	}

	private static void ensureLoaded() {
		if (data != null) {
			return;
		}
		if (Files.isRegularFile(REGISTRY_PATH)) {
			try (Reader reader = Files.newBufferedReader(REGISTRY_PATH, StandardCharsets.UTF_8)) {
				data = GSON.fromJson(reader, RegistryData.class);
			} catch (Exception exception) {
				LOGGER.warn("Could not read {}", REGISTRY_PATH, exception);
			}
		}
		if (data == null) {
			data = new RegistryData();
		}
		if (data.failures == null) {
			data.failures = new ArrayList<>();
		}
	}

	private static void trimAndSave() {
		data.failures.sort(Comparator.comparingLong((StoredFailure entry) -> entry.lastSeen).reversed());
		if (data.failures.size() > MAX_FAILURES) {
			data.failures = new ArrayList<>(data.failures.subList(0, MAX_FAILURES));
		}
		Path temporary = REGISTRY_PATH.resolveSibling(REGISTRY_PATH.getFileName() + ".tmp");
		try {
			Files.createDirectories(REGISTRY_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
			try {
				Files.move(temporary, REGISTRY_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, REGISTRY_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception exception) {
			LOGGER.warn("Could not write {}", REGISTRY_PATH, exception);
			try {
				Files.deleteIfExists(temporary);
			} catch (Exception cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
		}
	}

	public record Failure(
		long firstSeen,
		long lastSeen,
		String minecraftVersion,
		String modId,
		String modName,
		String modVersion,
		String reasonCode,
		String reasonText,
		String details,
		String sourceType,
		int occurrences
	) {
		public Instant lastSeenInstant() {
			return Instant.ofEpochMilli(this.lastSeen);
		}
	}

	private static final class RegistryData {
		private List<StoredFailure> failures = new ArrayList<>();
	}

	private static final class StoredFailure {
		private String sourceId = "";
		private long firstSeen;
		private long lastSeen;
		private String minecraftVersion = "?";
		private String modId = "unknown";
		private String modName = "Unknown";
		private String modVersion = "?";
		private String reasonCode = "";
		private String reasonText = "";
		private String details = "";
		private String sourceType = "CRASH_REPORT";
		private int occurrences = 1;

		private Failure snapshot() {
			return new Failure(
				this.firstSeen,
				this.lastSeen,
				valueOr(this.minecraftVersion, "?"),
				valueOr(this.modId, "unknown"),
				valueOr(this.modName, this.modId),
				valueOr(this.modVersion, "?"),
				valueOr(this.reasonCode, ""),
				valueOr(this.reasonText, ""),
				valueOr(this.details, ""),
				valueOr(this.sourceType, "CRASH_REPORT"),
				Math.max(1, this.occurrences)
			);
		}
	}
}
