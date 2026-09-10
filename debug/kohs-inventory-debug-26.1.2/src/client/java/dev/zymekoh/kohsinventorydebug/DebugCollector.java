package dev.zymekoh.kohsinventorydebug;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded, asynchronous, observer-only trace for KoHs Inventory Tweaks.
 *
 * <p>This class never changes a key state, cursor coordinate, screen, slot,
 * item, cooldown, packet or world value. It intentionally excludes chat,
 * typed characters, player identity and server addresses.</p>
 */
public final class DebugCollector {
	public static final String VERSION = "0.2.0";
	private static final Logger LOGGER = LoggerFactory.getLogger("KoHs Inventory Debug");
	private static final int MAX_EVENTS = 50_000;
	private static final int FILE_QUEUE_CAPACITY = 32_768;
	private static final long INPUT_CORRELATION_NANOS = 750_000_000L;
	private static final long OVERLAP_WINDOW_NANOS = 80_000_000L;
	private static final long CURSOR_TRACE_NANOS = 500_000_000L;
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
	private static final Object EVENT_LOCK = new Object();
	private static final ArrayDeque<DebugEvent> EVENTS = new ArrayDeque<>(MAX_EVENTS);
	private static final LinkedBlockingQueue<String> FILE_QUEUE = new LinkedBlockingQueue<>(FILE_QUEUE_CAPACITY);
	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final AtomicLong ISSUES = new AtomicLong();
	private static final AtomicLong DROPPED_FILE_LINES = new AtomicLong();
	private static final AtomicBoolean STARTED = new AtomicBoolean();
	private static final long SESSION_START_NANOS = System.nanoTime();
	private static final String SESSION_ID = Long.toUnsignedString(SESSION_START_NANOS, 36);

	private static volatile Path logFile;
	private static volatile boolean writerRunning;
	private static volatile long frameStartedNanos;
	private static volatile long tickStartedNanos;
	private static volatile long frameNumber;
	private static volatile long tickNumber;
	private static volatile long framesInSample;
	private static volatile long frameNanosInSample;
	private static volatile long maximumFrameNanos;
	private static volatile long sampleStartedNanos = System.nanoTime();
	private static volatile StatusSnapshot status = StatusSnapshot.empty();

	private static volatile long lastInventoryPhysicalNanos;
	private static volatile long lastOffhandPhysicalNanos;
	private static volatile String lastInventoryInput = "none";
	private static volatile long lastSetScreenRequestNanos;
	private static volatile long lastInventoryAppliedNanos;
	private static volatile long lastInventoryInitNanos;
	private static volatile long lastAppliedInputNanos;
	private static volatile long lastInitializedInputNanos;
	private static volatile String lastScreen = "<none>";
	private static volatile String lastConfig = "<unread>";
	private static volatile String lastHands = "<none>";
	private static volatile int lastSelectedSlot = -1;
	private static volatile double lastMouseX = Double.NaN;
	private static volatile double lastMouseY = Double.NaN;
	private static volatile long cursorTraceUntilNanos;
	private static volatile int cursorWritesForOpening;
	private static volatile double expectedCursorX = Double.NaN;
	private static volatile double expectedCursorY = Double.NaN;
	private static volatile String cursorTarget = "none";
	private static volatile long lastCursorWriteNanos;
	private static volatile boolean cursorSettlePending;
	private static volatile boolean fastBatchActive;
	private static volatile long fastBatchStartedNanos;
	private static volatile long fastInputToPollMicros = -1L;
	private static volatile String lastFastDecision = "idle";
	private static Screen pendingCloseScreen;
	private static long pendingCloseNanos;
	private static long closedInputNanos;
	private static long firstClosedFramePending;

	private DebugCollector() {
	}

	public static void start() {
		if (!STARTED.compareAndSet(false, true)) {
			return;
		}
		try {
			Path directory = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("kohs-inventory-debug");
			Files.createDirectories(directory);
			logFile = directory.resolve("kohs-inventory-debug-" + FILE_TIME.format(LocalDateTime.now()) + ".log");
			writerRunning = true;
			Thread.ofPlatform().daemon(true).name("KoHs Inventory Debug log writer").start(DebugCollector::writeLoop);
			Runtime.getRuntime().addShutdownHook(Thread.ofPlatform()
				.name("KoHs Inventory Debug shutdown")
				.unstarted(() -> {
					writerRunning = false;
					flushQueueSynchronously();
				}));
		} catch (IOException exception) {
			LOGGER.warn("Could not create the persistent debug log; the in-memory viewer remains available.", exception);
		}

		info("SESSION", "KoHs Inventory Debug " + VERSION + "; session=" + SESSION_ID);
		info("BOUNDARY", "Recorder=L0 observer: no input, cursor, screen, slot, cooldown or packet mutation.");
		info("MACRO_BOUNDARY", "Optional QA macros=L2 synthetic client input; hard-blocked outside integrated singleplayer; no direct packet or inventory/server action calls.");
		info("PRIVACY", "Chat, typed characters, player identity and server addresses are excluded.");
		info("VERSIONS", "minecraft=" + modVersion("minecraft")
			+ "; inventoryTweaks=" + modVersion("kohs_inventory_tweaks")
			+ "; fabricLoader=" + loaderVersion()
			+ "; java=" + System.getProperty("java.version"));
		info("FILE", logFile == null ? "Persistent log unavailable" : "Persistent log=" + logFile.toAbsolutePath());
		LOGGER.info("KoHs Inventory Debug {} started. Live trace: {}", VERSION, logFile);
	}

	public static void trace(final String category, final String message) {
		add(Level.TRACE, category, message);
	}

	public static void info(final String category, final String message) {
		add(Level.INFO, category, message);
	}

	public static void warn(final String category, final String message) {
		add(Level.WARN, category, message);
	}

	public static void issue(final String category, final String message) {
		ISSUES.incrementAndGet();
		add(Level.ERROR, category, message);
		LOGGER.warn("[{}] {}", category, message);
	}

	private static void add(final Level level, final String category, final String message) {
		long sequence = SEQUENCE.incrementAndGet();
		long elapsedNanos = Math.max(0L, System.nanoTime() - SESSION_START_NANOS);
		DebugEvent event = new DebugEvent(
			sequence,
			elapsedNanos,
			Thread.currentThread().getName(),
			level,
			sanitize(category),
			sanitize(message)
		);
		synchronized (EVENT_LOCK) {
			if (EVENTS.size() == MAX_EVENTS) {
				EVENTS.removeFirst();
			}
			EVENTS.addLast(event);
		}
		if (logFile != null && !FILE_QUEUE.offer(event.fileLine())) {
			DROPPED_FILE_LINES.incrementAndGet();
		}
	}

	private static String sanitize(final String value) {
		return value == null ? "null" : value.replace('\r', ' ').replace('\n', ' ');
	}

	public static List<DebugEvent> snapshot(final int maximum) {
		synchronized (EVENT_LOCK) {
			int skip = Math.max(0, EVENTS.size() - Math.max(1, maximum));
			List<DebugEvent> result = new ArrayList<>(EVENTS.size() - skip);
			int index = 0;
			for (DebugEvent event : EVENTS) {
				if (index++ >= skip) {
					result.add(event);
				}
			}
			return result;
		}
	}

	public static void clear() {
		synchronized (EVENT_LOCK) {
			EVENTS.clear();
		}
		info("SESSION", "In-memory viewer cleared by user; persistent file continues.");
	}

	public static void addUserMarker() {
		info("MARK", "========== USER MARKER: reproduce immediately before/after this line ==========");
	}

	public static long latestSequence() {
		return SEQUENCE.get();
	}

	public static StatusSnapshot status() {
		return status;
	}

	public static Path logFile() {
		return logFile;
	}

	public static String fullReport() {
		StringBuilder report = new StringBuilder(512_000);
		report.append("KOHS INVENTORY DEBUG REPORT\n")
			.append("debugVersion=").append(VERSION).append('\n')
			.append("session=").append(SESSION_ID).append('\n')
			.append("generated=").append(LocalDateTime.now()).append('\n')
			.append("minecraft=").append(modVersion("minecraft")).append('\n')
			.append("kohsInventoryTweaks=").append(modVersion("kohs_inventory_tweaks")).append('\n')
			.append("fabricLoader=").append(loaderVersion()).append('\n')
			.append("java=").append(System.getProperty("java.version")).append('\n')
			.append("os=").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append('\n')
			.append("logFile=").append(logFile == null ? "unavailable" : logFile.toAbsolutePath()).append('\n')
			.append("droppedFileLines=").append(DROPPED_FILE_LINES.get()).append('\n')
			.append("privacy=No chat, typed text, player identity or server address is recorded.\n")
			.append("boundary=Recorder L0 observer; optional QA macros L2 and integrated-singleplayer only; no direct packet/action calls.\n")
			.append("status=").append(status.compact()).append("\n\n")
			.append("LOADED MODS\n");

		FabricLoader.getInstance().getAllMods().stream()
			.sorted(Comparator.comparing(mod -> mod.getMetadata().getId()))
			.forEach(mod -> report.append(mod.getMetadata().getId())
				.append('=').append(mod.getMetadata().getVersion().getFriendlyString()).append('\n'));

		report.append("\nCONFIG SNAPSHOT\n").append(configSnapshot()).append("\n\nEVENTS\n");
		synchronized (EVENT_LOCK) {
			for (DebugEvent event : EVENTS) {
				report.append(event.fileLine()).append('\n');
			}
		}
		return report.toString();
	}

	public static void onFrameStart() {
		frameStartedNanos = System.nanoTime();
		frameNumber++;
	}

	public static void onFrameEnd(final Minecraft minecraft) {
		long elapsed = Math.max(0L, System.nanoTime() - frameStartedNanos);
		framesInSample++;
		frameNanosInSample += elapsed;
		maximumFrameNanos = Math.max(maximumFrameNanos, elapsed);
		samplePerformance(minecraft);
		if (firstClosedFramePending != 0L) {
			info("CLOSE_FRAME", "sinceCloseInput=" + micros(System.nanoTime() - firstClosedFramePending)
				+ "us; screen=" + screen(minecraft.screen) + "; frame=" + frameNumber);
			firstClosedFramePending = 0L;
		}
	}

	public static void onTickStart() {
		tickStartedNanos = System.nanoTime();
		tickNumber++;
	}

	public static void onTickEnd(final Minecraft minecraft) {
		long now = System.nanoTime();
		String config = configSnapshot();
		if (!config.equals(lastConfig)) {
			String previous = lastConfig;
			lastConfig = config;
			info("CONFIG", previous.equals("<unread>") ? "initial " + config : "changed from {" + previous + "} to {" + config + "}");
		}

		LocalPlayer player = minecraft.player;
		if (player != null) {
			int selected = player.getInventory().getSelectedSlot();
			String hands = "selected=" + selected + "; main=" + stack(player.getMainHandItem()) + "; off=" + stack(player.getOffhandItem());
			if (!hands.equals(lastHands)) {
				info("HAND_STATE", lastHands + " -> " + hands);
				lastHands = hands;
			}
			if (lastSelectedSlot != selected) {
				trace("HOTBAR", "selected " + lastSelectedSlot + " -> " + selected);
				lastSelectedSlot = selected;
			}
		}

		if (now <= cursorTraceUntilNanos) {
			double[] cursor = cursor(minecraft);
			if (cursor != null && !Double.isNaN(expectedCursorX)) {
				double distance = Math.hypot(cursor[0] - expectedCursorX, cursor[1] - expectedCursorY);
				long sinceWrite = now - lastCursorWriteNanos;
				if (cursorSettlePending && sinceWrite >= 12_000_000L) {
					cursorSettlePending = false;
					String message = "settled after " + micros(sinceWrite) + "us; error=" + decimal(distance)
						+ "px; actual=" + point(cursor) + "; target=" + point(expectedCursorX, expectedCursorY);
					if (distance > 3.0) {
						warn("CURSOR_SETTLE", message);
					} else {
						info("CURSOR_SETTLE", message);
					}
				}
			}
		}

		long tickMicros = micros(Math.max(0L, now - tickStartedNanos));
		if (tickMicros > 50_000L) {
			warn("TICK_STALL", "client tick=" + tickNumber + " took " + tickMicros + "us while screen=" + screen(minecraft.screen));
		}
		MacroTestController.onClientTick(minecraft);
	}

	private static void samplePerformance(final Minecraft minecraft) {
		long now = System.nanoTime();
		long duration = now - sampleStartedNanos;
		if (duration < 1_000_000_000L) {
			return;
		}
		long averageMicros = framesInSample == 0 ? 0L : frameNanosInSample / framesInSample / 1_000L;
		status = new StatusSnapshot(
			frameNumber,
			tickNumber,
			minecraft.getFps(),
			averageMicros,
			maximumFrameNanos / 1_000L,
			SEQUENCE.get(),
			ISSUES.get(),
			DROPPED_FILE_LINES.get(),
			lastFastDecision,
			cursorTarget
		);
		framesInSample = 0;
		frameNanosInSample = 0;
		maximumFrameNanos = 0;
		sampleStartedNanos = now;
	}

	public static void onSetScreen(final Minecraft minecraft, final Screen requested, final boolean applied) {
		long now = System.nanoTime();
		String current = screen(minecraft.screen);
		String target = screen(requested);
		if (!applied) {
			lastSetScreenRequestNanos = now;
			info("SCREEN_REQUEST", "current=" + current + "; requested=" + target
				+ inputLatencySuffix(now));
			if (requested instanceof AbstractContainerScreen<?>) {
				cursorWritesForOpening = 0;
				cursorTraceUntilNanos = now + CURSOR_TRACE_NANOS;
				cursorTarget = target;
			}
			return;
		}

		lastScreen = screen(minecraft.screen);
		long setScreenMicros = micros(now - lastSetScreenRequestNanos);
		info("SCREEN_APPLIED", "screen=" + lastScreen + "; setScreen=" + setScreenMicros + "us" + inputLatencySuffix(now));
		if (InventoryScreenClassifier.isPlayerInventory(minecraft.screen)) {
			lastInventoryAppliedNanos = now;
			long latency = correlatedInventoryLatency(now, false);
			if (latency > 150_000L) {
				issue("FAST_INVENTORY_LATENCY", "physical press -> InventoryScreen applied took " + latency + "us");
			} else if (latency > 50_000L) {
				warn("FAST_INVENTORY_LATENCY", "physical press -> InventoryScreen applied took " + latency + "us");
			}
		}
	}

	public static void onContainerInitialized(final AbstractContainerScreen<?> screen) {
		long now = System.nanoTime();
		long setScreenLatency = lastSetScreenRequestNanos == 0L ? -1L : micros(now - lastSetScreenRequestNanos);
		long inputLatency = InventoryScreenClassifier.isPlayerInventory(screen)
			? correlatedInventoryLatency(now, true)
			: -1L;
		lastInventoryInitNanos = now;
		info("CONTAINER_INIT", "screen=" + screen(screen) + "; size=" + screen.width + 'x' + screen.height
			+ "; setScreenToInit=" + setScreenLatency + "us; inputToInit=" + inputLatency + "us"
			+ "; cursor=" + point(cursor(Minecraft.getInstance())));
		if (InventoryScreenClassifier.isPlayerInventory(screen)) {
			auditInventoryPhysicalScale(screen);
		}
	}

	/**
	 * Verifies the 26.1.2 physical-size contract without linking the debugger to
	 * implementation classes. A saved 100% must occupy the same physical pixels
	 * at Vanilla GUI Scale 1x, 2x, 3x or Auto.
	 */
	private static void auditInventoryPhysicalScale(final AbstractContainerScreen<?> screen) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			Class<?> store = Class.forName("dev.zymekoh.kohsinventorytweaks.config.ConfigStore");
			Object config = store.getMethod("get").invoke(null);
			boolean enabled = config.getClass().getField("inventoryGuiScalerEnabled").getBoolean(config);
			Class<?> scaler = Class.forName("dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler");
			Method configuredMethod = scaler.getMethod(
				"configuredPhysicalScale", int.class, int.class, config.getClass()
			);
			Method appliedMethod = scaler.getMethod(
				"appliedScale", int.class, int.class, config.getClass()
			);
			double configuredPhysical = ((Number) configuredMethod.invoke(null, screen.width, screen.height, config)).doubleValue();
			double appliedLogical = ((Number) appliedMethod.invoke(null, screen.width, screen.height, config)).doubleValue();
			double vanillaGuiScale = Math.max(1.0, minecraft.getWindow().getGuiScale());
			double referenceGuiScale = ((Number) scaler.getField("PHYSICAL_REFERENCE_GUI_SCALE").get(null)).doubleValue();
			double measuredPhysical = appliedLogical * vanillaGuiScale / referenceGuiScale;
			double delta = enabled ? Math.abs(measuredPhysical - configuredPhysical) : 0.0;
			String message = "enabled=" + enabled
				+ "; vanillaGuiScale=" + decimal(vanillaGuiScale)
				+ "; fixedReference=" + decimal(referenceGuiScale)
				+ "; logicalSurface=" + decimal(appliedLogical)
				+ "; configuredPhysical=" + decimal(configuredPhysical)
				+ "; measuredPhysical=" + decimal(measuredPhysical)
				+ "; inventoryPixels=" + decimal(176.0 * appliedLogical * vanillaGuiScale)
				+ 'x' + decimal(166.0 * appliedLogical * vanillaGuiScale)
				+ "; delta=" + decimal(delta)
				+ "; gui=" + screen.width + 'x' + screen.height
				+ "; framebuffer=" + minecraft.getWindow().getWidth() + 'x' + minecraft.getWindow().getHeight();
			if (enabled && delta > 0.01) {
				issue("GUI_SCALE_INVARIANT", message);
			} else {
				info("GUI_SCALE_INVARIANT", message);
			}
		} catch (Throwable throwable) {
			warn("GUI_SCALE_INVARIANT", "audit unavailable: " + throwable.getClass().getSimpleName()
				+ ": " + String.valueOf(throwable.getMessage()));
		}
	}

	public static void onPhysicalKey(final Minecraft minecraft, final int action, final KeyEvent event) {
		String matched = matchingRelevantMappings(minecraft, event);
		if (event.isEscape() && InventoryScreenClassifier.isPlayerInventory(minecraft.screen)) matched = "escape-close";
		if (matched.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		if (action == GLFW.GLFW_PRESS && InventoryScreenClassifier.isPlayerInventory(minecraft.screen)
			&& (minecraft.options.keyInventory.matches(event) || event.isEscape())) {
			pendingCloseScreen = minecraft.screen;
			pendingCloseNanos = now;
			info("CLOSE_INPUT", "mappings=" + matched + "; mods=" + event.modifiers()
				+ "; focused=" + (minecraft.screen.getFocused() == null ? "none" : minecraft.screen.getFocused().getClass().getName()));
		}
		if (action == GLFW.GLFW_PRESS && matched.contains("hotbar")) {
			info("HOTBAR_CONTEXT", "mappings=" + matched + "; screen=" + screen(minecraft.screen)
				+ "; sinceCompletedClose=" + (closedInputNanos == 0L ? -1 : micros(now - closedInputNanos))
				+ "us; pendingClose=" + (pendingCloseScreen != null));
		}
		boolean qaMacro = MacroTestController.isRunning();
		String source = qaMacro ? "qa-macro" : "physical";
		info(qaMacro ? "MACRO_KEY" : "PHYSICAL_KEY", "source=" + source + "; action=" + glfwAction(action) + "; mappings=" + matched
			+ "; key=" + event.key() + "; scan=" + event.scancode() + "; mods=" + event.modifiers()
			+ "; screen=" + screen(minecraft.screen));
		if (action == GLFW.GLFW_PRESS && matches(minecraft.options.keyInventory, event)) {
			lastInventoryPhysicalNanos = now;
			lastInventoryInput = source + "-keyboard(key=" + event.key() + ")";
			reportOverlap(now, "inventory", source);
		}
		if (action == GLFW.GLFW_PRESS && matches(minecraft.options.keySwapOffhand, event)) {
			lastOffhandPhysicalNanos = now;
			reportOverlap(now, "offhand", source);
		}
	}

	public static void onMouseButton(final Minecraft minecraft, final MouseButtonInfo info, final int action) {
		String matched = matchingRelevantMouseMappings(minecraft, info);
		if (matched.isEmpty() && minecraft.screen == null) {
			return;
		}
		boolean qaMacro = MacroTestController.isRunning();
		info(qaMacro ? "MACRO_MOUSE" : "PHYSICAL_MOUSE", "source=" + (qaMacro ? "qa-macro" : "physical")
			+ "; action=" + glfwAction(action) + "; button=" + (info == null ? -1 : info.button())
			+ "; mods=" + (info == null ? -1 : info.modifiers()) + "; mappings=" + (matched.isEmpty() ? "screen-input" : matched)
			+ "; screen=" + screen(minecraft.screen) + "; cursor=" + point(cursor(minecraft)));
	}

	public static void onKeyboardReturn(final Minecraft minecraft, final int action, final KeyEvent event) {
		if (action != GLFW.GLFW_PRESS || pendingCloseScreen == null
			|| !(minecraft.options.keyInventory.matches(event) || event.isEscape())) return;
		long elapsed = micros(System.nanoTime() - pendingCloseNanos);
		boolean closed = minecraft.screen == null;
		info(closed ? "CLOSE_APPLIED" : "CLOSE_NOT_APPLIED", "callback=" + elapsed + "us; screen=" + screen(minecraft.screen)
			+ "; sameScreen=" + (minecraft.screen == pendingCloseScreen)
			+ "; note=callback-result-not-mod-attribution");
		if (closed) {
			closedInputNanos = pendingCloseNanos;
			firstClosedFramePending = pendingCloseNanos;
		}
		pendingCloseScreen = null;
	}

	public static void onMouseMove(final Minecraft minecraft, final double x, final double y) {
		long now = System.nanoTime();
		double dx = Double.isNaN(lastMouseX) ? 0.0 : x - lastMouseX;
		double dy = Double.isNaN(lastMouseY) ? 0.0 : y - lastMouseY;
		lastMouseX = x;
		lastMouseY = y;
		if (now <= cursorTraceUntilNanos && (Math.abs(dx) > 0.05 || Math.abs(dy) > 0.05)) {
			trace("CURSOR_CALLBACK", "pos=" + point(x, y) + "; delta=" + point(dx, dy)
				+ "; sinceWrite=" + (lastCursorWriteNanos == 0L ? -1L : micros(now - lastCursorWriteNanos)) + "us"
				+ "; screen=" + screen(minecraft.screen));
		}
	}

	public static void onMouseGrab(final Minecraft minecraft, final boolean grabbing, final boolean after) {
		if (after || !grabbing || minecraft.screen instanceof AbstractContainerScreen<?>) {
			trace("MOUSE_CAPTURE", (after ? "after " : "before ") + (grabbing ? "grab" : "release")
				+ "; screen=" + screen(minecraft.screen) + "; pos=" + point(cursor(minecraft)));
		}
	}

	public static void onKeyClick(final InputConstants.Key key) {
		Minecraft minecraft = Minecraft.getInstance();
		String mappings = mappingsForKey(minecraft, key);
		if (!mappings.isEmpty()) {
			trace("KEY_QUEUE_ADD", "key=" + key.getName() + "; mappings=" + mappings + "; queue=" + queueSnapshot(minecraft));
		}
	}

	public static void onKeyState(final InputConstants.Key key, final boolean down) {
		Minecraft minecraft = Minecraft.getInstance();
		String mappings = mappingsForKey(minecraft, key);
		if (!mappings.isEmpty()) {
			trace("KEY_STATE", "key=" + key.getName() + "; down=" + down + "; mappings=" + mappings);
		}
	}

	public static void onKeyConsumed(final KeyMapping mapping, final boolean consumed) {
		if (consumed && isRelevant(mapping, Minecraft.getInstance())) {
			trace("KEY_CONSUME", "mapping=" + mapping.getName() + "; remaining=" + queued(mapping)
				+ "; screen=" + screen(Minecraft.getInstance().screen));
		}
	}

	public static void onFastKeyboard(final Minecraft minecraft, final int action, final KeyEvent event, final boolean after) {
		if (action != GLFW.GLFW_PRESS || !matchesAnyRelevant(minecraft, event)) {
			return;
		}
		trace("FAST_KEY_HOOK", (after ? "return" : "head") + "; mappings=" + matchingRelevantMappings(minecraft, event)
			+ "; queue=" + queueSnapshot(minecraft));
	}

	public static void onFastMouse(final Minecraft minecraft, final MouseButtonInfo info, final int action, final boolean after) {
		if (action == GLFW.GLFW_PRESS) {
			trace("FAST_MOUSE_HOOK", (after ? "return" : "head") + "; button=" + (info == null ? -1 : info.button())
				+ "; mappings=" + matchingRelevantMouseMappings(minecraft, info) + "; queue=" + queueSnapshot(minecraft));
		}
	}

	public static void onFastBatch(final Minecraft minecraft, final boolean after) {
		if (!after) {
			fastBatchStartedNanos = System.nanoTime();
			// Old/repeat-generated queue entries are not new fast-open decisions.
			// Checking the last decision against them produces false mismatches.
			fastBatchActive = queued(minecraft.options.keyInventory) > 0
				&& !snapshotValue(fastControllerSnapshot("pendingInputSnapshot"), "inventoryInputAge", "-1us").equals("-1us");
			if (fastBatchActive) {
				lastFastDecision = "evaluating";
				fastInputToPollMicros = lastInventoryPhysicalNanos == 0L
					? -1L
					: micros(fastBatchStartedNanos - lastInventoryPhysicalNanos);
				info("FAST_POLL_HEAD", "inputToPoll=" + fastInputToPollMicros + "us; queue="
					+ queueSnapshot(minecraft) + "; eligibility=" + fastEligibility(minecraft)
					+ "; controller=" + fastControllerSnapshot("pendingInputSnapshot"));
			}
			return;
		}
		if (fastBatchActive) {
			long elapsed = micros(System.nanoTime() - fastBatchStartedNanos);
			String controller = fastControllerSnapshot("lastDecisionSnapshot");
			lastFastDecision = snapshotValue(controller, "decision", "unavailable");
			info("FAST_POLL_RETURN", "inputToPoll=" + fastInputToPollMicros + "us; controller=" + controller
				+ "; elapsed=" + elapsed + "us; queue=" + queueSnapshot(minecraft)
				+ "; screen=" + screen(minecraft.screen));
			long frameAwareThreshold = Math.max(25_000L, Math.max(1L, status.averageFrameMicros()) * 2L);
			if (fastInputToPollMicros > frameAwareThreshold) {
				warn("FAST_INPUT_POLL_LATENCY", "physical Inventory press waited " + fastInputToPollMicros
					+ "us for end-of-poll; threshold=" + frameAwareThreshold + "us; controller=" + controller);
			}
			if (lastFastDecision.equals("early-open") && !InventoryScreenClassifier.isPlayerInventory(minecraft.screen)) {
				issue("FAST_POLL_DECISION_MISMATCH", "controller reported early-open but current screen="
					+ screen(minecraft.screen) + "; controller=" + controller);
			}
			fastBatchActive = false;
			fastInputToPollMicros = -1L;
		}
	}

	public static void onFastOpen(final Minecraft minecraft, final boolean after) {
		info("FAST_OPEN", (after ? "return" : "head") + "; eligibility=" + fastEligibility(minecraft)
			+ "; queue=" + queueSnapshot(minecraft) + "; screen=" + screen(minecraft.screen));
	}

	public static void onCursorRequested(final Screen screen, final boolean after) {
		Minecraft minecraft = Minecraft.getInstance();
		info("CURSOR_REQUEST", (after ? "return" : "head") + "; screen=" + screen(screen)
			+ "; current=" + screen(minecraft.screen) + "; pos=" + point(cursor(minecraft))
			+ "; config=" + cursorConfigSnapshot());
	}

	public static void onCursorRelease(final Minecraft minecraft, final boolean after, final double[] result) {
		if (!after) {
			trace("CURSOR_RELEASE_HOOK", "head; current=" + screen(minecraft.screen) + "; pos=" + point(cursor(minecraft)));
			return;
		}
		if (result == null) {
			info("CURSOR_RELEASE_RESULT", "vanilla center retained; result=null; posBeforeRelease=" + point(cursor(minecraft)));
			return;
		}
		expectedCursorX = result[0];
		expectedCursorY = result[1];
		lastCursorWriteNanos = System.nanoTime();
		cursorSettlePending = true;
		cursorTraceUntilNanos = lastCursorWriteNanos + CURSOR_TRACE_NANOS;
		info("CURSOR_RELEASE_RESULT", "custom physical target=" + point(result) + "; window="
			+ minecraft.getWindow().getScreenWidth() + 'x' + minecraft.getWindow().getScreenHeight());
	}

	public static void onCursorScreenOpened(final Minecraft minecraft, final Screen screen, final boolean after) {
		trace("CURSOR_FINALIZE_HOOK", (after ? "return" : "head") + "; screen=" + screen(screen)
			+ "; actual=" + point(cursor(minecraft)) + "; expected=" + point(expectedCursorX, expectedCursorY));
	}

	public static void onCursorWarp(final Minecraft minecraft, final double[] target, final boolean after) {
		long now = System.nanoTime();
		if (!after) {
			cursorWritesForOpening++;
			expectedCursorX = target == null ? Double.NaN : target[0];
			expectedCursorY = target == null ? Double.NaN : target[1];
			lastCursorWriteNanos = now;
			cursorSettlePending = true;
			cursorTraceUntilNanos = now + CURSOR_TRACE_NANOS;
			info("CURSOR_WARP", "head; write=" + cursorWritesForOpening + "; before=" + point(cursor(minecraft))
				+ "; target=" + point(target));
			if (cursorWritesForOpening > 1) {
				issue("CURSOR_REPEAT", "multiple cursor writes for one screen opening; count=" + cursorWritesForOpening
					+ "; target=" + screen(minecraft.screen));
			}
			return;
		}
		double[] actual = cursor(minecraft);
		double error = target == null || actual == null ? Double.NaN : Math.hypot(actual[0] - target[0], actual[1] - target[1]);
		info("CURSOR_WARP", "return; actual=" + point(actual) + "; target=" + point(target) + "; error=" + decimal(error) + "px");
		if (!Double.isNaN(error) && error > 1.0) {
			trace("CURSOR_WARP_PENDING", "Immediate GLFW read differs by " + decimal(error)
				+ "px; deferred settle verification will run after 12ms");
		}
	}

	public static void onConfigEvent(final String operation, final boolean after) {
		info("CONFIG_STORE", operation + ':' + (after ? "return" : "head") + "; snapshot=" + configSnapshot());
	}

	public static void onContainerSlotAction(
		final AbstractContainerScreen<?> screen,
		final Slot slot,
		final int slotId,
		final int button,
		final ContainerInput input,
		final boolean after
	) {
		String item = slot == null ? "outside" : stack(slot.getItem());
		info("CONTAINER_ACTION", (after ? "return" : "head") + "; screen=" + screen(screen)
			+ "; slotId=" + slotId + "; containerSlot=" + (slot == null ? -1 : slot.getContainerSlot())
			+ "; button=" + button + "; input=" + input + "; item=" + item
			+ "; carried=" + stack(screen.getMenu().getCarried()) + "; queue=" + queueSnapshot(Minecraft.getInstance()));
	}

	public static void onContainerKey(final AbstractContainerScreen<?> screen, final KeyEvent event, final boolean after, final boolean result) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!matchesAnyRelevant(minecraft, event)) {
			return;
		}
		info("CONTAINER_KEY", (after ? "return" : "head") + "; mappings=" + matchingRelevantMappings(minecraft, event)
			+ "; screen=" + screen(screen) + "; handled=" + result + "; queue=" + queueSnapshot(minecraft));
	}

	public static void onPacketSent(final Packet<?> packet) {
		if (packet instanceof ServerboundContainerClosePacket) {
			info("PACKET_OUT_CLOSE", "screen=" + screen(Minecraft.getInstance().screen)
				+ "; sinceCloseInput=" + (pendingCloseScreen == null ? -1 : micros(System.nanoTime() - pendingCloseNanos)) + "us");
		}
		if (packet instanceof ServerboundSetCarriedItemPacket) {
			info("PACKET_OUT_SELECTED", "screen=" + screen(Minecraft.getInstance().screen));
		}
		if (packet instanceof ServerboundPlayerActionPacket action
			&& action.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			long sinceOffhand = lastOffhandPhysicalNanos == 0L ? -1L : micros(System.nanoTime() - lastOffhandPhysicalNanos);
			info("PACKET_OUT_OFFHAND", "action=" + action.getAction() + "; sincePhysical=" + sinceOffhand + "us"
				+ "; screen=" + screen(Minecraft.getInstance().screen));
			return;
		}
		if (packet instanceof ServerboundContainerClickPacket click) {
			info("PACKET_OUT_CONTAINER", "container=" + click.containerId() + "; state=" + click.stateId()
				+ "; slot=" + click.slotNum() + "; button=" + click.buttonNum() + "; input=" + click.containerInput()
				+ "; changedSlots=" + click.changedSlots().size());
		}
	}

	public static void onContainerSlot(final ClientboundContainerSetSlotPacket packet) {
		info("PACKET_IN_SLOT", "container=" + packet.getContainerId() + "; state=" + packet.getStateId()
			+ "; slot=" + packet.getSlot() + "; item=" + stack(packet.getItem()));
	}

	public static void onContainerContent(final ClientboundContainerSetContentPacket packet) {
		info("PACKET_IN_CONTENT", "container=" + packet.containerId() + "; state=" + packet.stateId()
			+ "; slots=" + packet.items().size() + "; carried=" + stack(packet.carriedItem()));
	}

	private static void reportOverlap(final long now, final String newest, final String source) {
		long other = newest.equals("inventory") ? lastOffhandPhysicalNanos : lastInventoryPhysicalNanos;
		if (other != 0L && Math.abs(now - other) <= OVERLAP_WINDOW_NANOS) {
			warn("INPUT_OVERLAP", "inventory and offhand input presses separated by " + micros(Math.abs(now - other))
				+ "us; newest=" + newest + "; source=" + source + "; Vanilla queue must remain authoritative");
		}
	}

	private static String inputLatencySuffix(final long now) {
		if (lastInventoryPhysicalNanos == 0L || now - lastInventoryPhysicalNanos > INPUT_CORRELATION_NANOS) {
			return "";
		}
		return "; sinceInventoryPhysical=" + micros(now - lastInventoryPhysicalNanos) + "us; source=" + lastInventoryInput;
	}

	private static long correlatedInventoryLatency(final long now, final boolean initialization) {
		long input = lastInventoryPhysicalNanos;
		long consumed = initialization ? lastInitializedInputNanos : lastAppliedInputNanos;
		long elapsed = input == 0L ? Long.MAX_VALUE : now - input;
		if (input == 0L || input == consumed || elapsed < 0L || elapsed > INPUT_CORRELATION_NANOS) {
			return -1L;
		}
		if (initialization) {
			lastInitializedInputNanos = input;
		} else {
			lastAppliedInputNanos = input;
		}
		return micros(elapsed);
	}

	private static String fastEligibility(final Minecraft minecraft) {
		return "configFast=" + configBoolean("superFastInventory")
			+ "; screen=" + screen(minecraft.screen)
			+ "; overlay=" + (minecraft.getOverlay() == null ? "none" : minecraft.getOverlay().getClass().getSimpleName())
			+ "; player=" + (minecraft.player != null)
			+ "; gameMode=" + (minecraft.gameMode != null)
			+ "; inventoryQueued=" + queued(minecraft.options.keyInventory)
			+ "; offhandQueued=" + queued(minecraft.options.keySwapOffhand)
			+ "; otherQueued=" + relevantQueuedExcludingInventory(minecraft);
	}

	private static String fastControllerSnapshot(final String methodName) {
		try {
			Class<?> controller = Class.forName(
				"dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController"
			);
			return String.valueOf(controller.getMethod(methodName).invoke(null));
		} catch (Throwable throwable) {
			return "unavailable(" + throwable.getClass().getSimpleName() + ')';
		}
	}

	private static String snapshotValue(final String snapshot, final String key, final String fallback) {
		if (snapshot == null) {
			return fallback;
		}
		String prefix = key + '=';
		for (String part : snapshot.split(";")) {
			String value = part.trim();
			if (value.startsWith(prefix)) {
				return value.substring(prefix.length());
			}
		}
		return fallback;
	}

	public static int queued(final KeyMapping mapping) {
		try {
			return ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getClickCount();
		} catch (Throwable ignored) {
			return -1;
		}
	}

	private static String queueSnapshot(final Minecraft minecraft) {
		if (minecraft == null || minecraft.options == null) {
			return "unavailable";
		}
		StringJoiner joiner = new StringJoiner(",", "{", "}");
		addQueue(joiner, "inventory", minecraft.options.keyInventory);
		addQueue(joiner, "offhand", minecraft.options.keySwapOffhand);
		addQueue(joiner, "attack", minecraft.options.keyAttack);
		addQueue(joiner, "use", minecraft.options.keyUse);
		addQueue(joiner, "drop", minecraft.options.keyDrop);
		addQueue(joiner, "pick", minecraft.options.keyPickItem);
		for (int index = 0; index < minecraft.options.keyHotbarSlots.length; index++) {
			int count = queued(minecraft.options.keyHotbarSlots[index]);
			if (count > 0 || minecraft.options.keyHotbarSlots[index].isDown()) {
				joiner.add("hotbar" + (index + 1) + '=' + count + '/' + minecraft.options.keyHotbarSlots[index].isDown());
			}
		}
		return joiner.toString();
	}

	private static void addQueue(final StringJoiner joiner, final String name, final KeyMapping mapping) {
		joiner.add(name + '=' + queued(mapping) + '/' + mapping.isDown());
	}

	private static boolean relevantQueuedExcludingInventory(final Minecraft minecraft) {
		if (queued(minecraft.options.keySwapOffhand) > 0
			|| queued(minecraft.options.keyAttack) > 0
			|| queued(minecraft.options.keyUse) > 0
			|| queued(minecraft.options.keyDrop) > 0
			|| queued(minecraft.options.keyPickItem) > 0) {
			return true;
		}
		for (KeyMapping hotbar : minecraft.options.keyHotbarSlots) {
			if (queued(hotbar) > 0) {
				return true;
			}
		}
		return false;
	}

	private static String matchingRelevantMappings(final Minecraft minecraft, final KeyEvent event) {
		if (minecraft == null || minecraft.options == null || event == null) {
			return "";
		}
		StringJoiner joiner = new StringJoiner(",");
		for (KeyMapping mapping : relevantMappings(minecraft)) {
			if (mapping.matches(event)) {
				joiner.add(shortName(mapping, minecraft));
			}
		}
		return joiner.toString();
	}

	private static String matchingRelevantMouseMappings(final Minecraft minecraft, final MouseButtonInfo info) {
		if (minecraft == null || minecraft.options == null || info == null) {
			return "";
		}
		net.minecraft.client.input.MouseButtonEvent event = new net.minecraft.client.input.MouseButtonEvent(0.0, 0.0, info);
		StringJoiner joiner = new StringJoiner(",");
		for (KeyMapping mapping : relevantMappings(minecraft)) {
			if (mapping.matchesMouse(event)) {
				joiner.add(shortName(mapping, minecraft));
			}
		}
		return joiner.toString();
	}

	private static boolean matchesAnyRelevant(final Minecraft minecraft, final KeyEvent event) {
		return !matchingRelevantMappings(minecraft, event).isEmpty();
	}

	private static boolean matches(final KeyMapping mapping, final KeyEvent event) {
		return mapping != null && event != null && mapping.matches(event);
	}

	private static List<KeyMapping> relevantMappings(final Minecraft minecraft) {
		List<KeyMapping> mappings = new ArrayList<>(15);
		mappings.add(minecraft.options.keyInventory);
		mappings.add(minecraft.options.keySwapOffhand);
		mappings.add(minecraft.options.keyAttack);
		mappings.add(minecraft.options.keyUse);
		mappings.add(minecraft.options.keyDrop);
		mappings.add(minecraft.options.keyPickItem);
		mappings.addAll(List.of(minecraft.options.keyHotbarSlots));
		return mappings;
	}

	private static String mappingsForKey(final Minecraft minecraft, final InputConstants.Key key) {
		if (minecraft == null || minecraft.options == null || key == null) {
			return "";
		}
		StringJoiner joiner = new StringJoiner(",");
		for (KeyMapping mapping : relevantMappings(minecraft)) {
			if (mapping.saveString().equals(key.getName())) {
				joiner.add(shortName(mapping, minecraft));
			}
		}
		return joiner.toString();
	}

	private static boolean isRelevant(final KeyMapping mapping, final Minecraft minecraft) {
		return minecraft != null && minecraft.options != null && relevantMappings(minecraft).contains(mapping);
	}

	private static String shortName(final KeyMapping mapping, final Minecraft minecraft) {
		if (mapping == minecraft.options.keyInventory) return "inventory";
		if (mapping == minecraft.options.keySwapOffhand) return "offhand";
		if (mapping == minecraft.options.keyAttack) return "attack";
		if (mapping == minecraft.options.keyUse) return "use";
		if (mapping == minecraft.options.keyDrop) return "drop";
		if (mapping == minecraft.options.keyPickItem) return "pick";
		for (int index = 0; index < minecraft.options.keyHotbarSlots.length; index++) {
			if (mapping == minecraft.options.keyHotbarSlots[index]) return "hotbar" + (index + 1);
		}
		return mapping.getName();
	}

	private static String configSnapshot() {
		try {
			Class<?> store = Class.forName("dev.zymekoh.kohsinventorytweaks.config.ConfigStore");
			Object config = store.getMethod("get").invoke(null);
			if (config == null) {
				return "null";
			}
			Map<String, String> values = new LinkedHashMap<>();
			for (Field field : config.getClass().getFields()) {
				Object value = field.get(config);
				if (value instanceof List<?> list) {
					values.put(field.getName(), "list(size=" + list.size() + ')');
				} else {
					values.put(field.getName(), String.valueOf(value));
				}
			}
			StringJoiner joiner = new StringJoiner(",");
			values.forEach((key, value) -> joiner.add(key + '=' + value));
			return joiner.toString();
		} catch (Throwable throwable) {
			return "unavailable(" + throwable.getClass().getSimpleName() + ')';
		}
	}

	private static String cursorConfigSnapshot() {
		return "centerMouseFix=" + configBoolean("centerMouseFix")
			+ "; cursorLandingAvailable=" + compatibilityFeature("CURSOR_LANDING")
			+ "; inventoryTweaksAvailable=" + compatibilityFeature("INVENTORY_TWEAKS")
			+ "; inventoryPoint=" + configField("inventory")
			+ "; scaleEnabled=" + configBoolean("inventoryGuiScalerEnabled")
			+ "; scale=" + configField("inventoryGuiScale");
	}

	private static String configBoolean(final String name) {
		return configField(name);
	}

	private static String configField(final String name) {
		try {
			Class<?> store = Class.forName("dev.zymekoh.kohsinventorytweaks.config.ConfigStore");
			Object config = store.getMethod("get").invoke(null);
			return String.valueOf(config.getClass().getField(name).get(config));
		} catch (Throwable ignored) {
			return "unavailable";
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String compatibilityFeature(final String featureName) {
		try {
			Class feature = Class.forName("dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature");
			Object value = Enum.valueOf(feature, featureName);
			Class<?> manager = Class.forName("dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager");
			Method method = manager.getMethod("isFeatureAvailable", feature);
			return String.valueOf(method.invoke(null, value));
		} catch (Throwable ignored) {
			return "unavailable";
		}
	}

	private static double[] cursor(final Minecraft minecraft) {
		if (minecraft == null || minecraft.getWindow() == null) {
			return null;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			java.nio.DoubleBuffer x = stack.mallocDouble(1);
			java.nio.DoubleBuffer y = stack.mallocDouble(1);
			GLFW.glfwGetCursorPos(minecraft.getWindow().handle(), x, y);
			return new double[] {x.get(0), y.get(0)};
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String stack(final ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "empty";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
	}

	private static String screen(final Screen screen) {
		return screen == null ? "none" : screen.getClass().getName();
	}

	private static String point(final double[] value) {
		return value == null || value.length < 2 ? "null" : point(value[0], value[1]);
	}

	private static String point(final double x, final double y) {
		return '(' + decimal(x) + ',' + decimal(y) + ')';
	}

	private static String decimal(final double value) {
		return Double.isNaN(value) ? "NaN" : String.format(Locale.ROOT, "%.3f", value);
	}

	private static long micros(final long nanos) {
		return nanos < 0L ? -1L : nanos / 1_000L;
	}

	private static String glfwAction(final int action) {
		return switch (action) {
			case GLFW.GLFW_PRESS -> "PRESS";
			case GLFW.GLFW_RELEASE -> "RELEASE";
			case GLFW.GLFW_REPEAT -> "REPEAT";
			default -> String.valueOf(action);
		};
	}

	private static String modVersion(final String id) {
		return FabricLoader.getInstance().getModContainer(id)
			.map(ModContainer::getMetadata)
			.map(metadata -> metadata.getVersion().getFriendlyString())
			.orElse("not-loaded");
	}

	private static String loaderVersion() {
		return FabricLoader.getInstance().getModContainer("fabricloader")
			.map(ModContainer::getMetadata)
			.map(metadata -> metadata.getVersion().getFriendlyString())
			.orElse("unknown");
	}

	private static void writeLoop() {
		try (BufferedWriter writer = logFile == null ? null : Files.newBufferedWriter(
			logFile,
			StandardCharsets.UTF_8,
			StandardOpenOption.CREATE_NEW,
			StandardOpenOption.WRITE
		)) {
			if (writer == null) {
				return;
			}
			while (writerRunning || !FILE_QUEUE.isEmpty()) {
				String line = FILE_QUEUE.poll(250, TimeUnit.MILLISECONDS);
				if (line != null) {
					writer.write(line);
					writer.newLine();
					while ((line = FILE_QUEUE.poll()) != null) {
						writer.write(line);
						writer.newLine();
					}
					writer.flush();
				}
			}
		} catch (Exception exception) {
			LOGGER.warn("Persistent debug writer stopped unexpectedly.", exception);
		}
	}

	private static void flushQueueSynchronously() {
		if (logFile == null || FILE_QUEUE.isEmpty()) {
			return;
		}
		try (BufferedWriter writer = Files.newBufferedWriter(
			logFile,
			StandardCharsets.UTF_8,
			StandardOpenOption.CREATE,
			StandardOpenOption.APPEND
		)) {
			String line;
			while ((line = FILE_QUEUE.poll()) != null) {
				writer.write(line);
				writer.newLine();
			}
		} catch (IOException exception) {
			LOGGER.warn("Could not flush the remaining debug lines.", exception);
		}
	}

	public enum Level {
		TRACE,
		INFO,
		WARN,
		ERROR
	}

	public record DebugEvent(long sequence, long elapsedNanos, String thread, Level level, String category, String message) {
		public String displayLine() {
			return String.format(Locale.ROOT, "%06d +%10.3fms %-5s [%-20s] %s",
				this.sequence, this.elapsedNanos / 1_000_000.0, this.level, this.category, this.message);
		}

		public String fileLine() {
			return displayLine() + " {thread=" + this.thread + "; frame=" + frameNumber + "; tick=" + tickNumber + '}';
		}
	}

	public record StatusSnapshot(
		long frames,
		long ticks,
		int fps,
		long averageFrameMicros,
		long maximumFrameMicros,
		long events,
		long issues,
		long dropped,
		String fastDecision,
		String cursorTarget
	) {
		static StatusSnapshot empty() {
			return new StatusSnapshot(0, 0, 0, 0, 0, 0, 0, 0, "idle", "none");
		}

		public String compact() {
			return "fps=" + this.fps + "; avgFrame=" + this.averageFrameMicros + "us; maxFrame="
				+ this.maximumFrameMicros + "us; events=" + this.events + "; issues=" + this.issues
				+ "; dropped=" + this.dropped + "; fast=" + this.fastDecision + "; cursor=" + this.cursorTarget;
		}
	}
}
