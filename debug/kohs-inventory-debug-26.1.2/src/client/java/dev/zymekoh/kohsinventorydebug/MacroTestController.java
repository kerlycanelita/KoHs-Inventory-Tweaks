package dev.zymekoh.kohsinventorydebug;

import com.mojang.blaze3d.platform.InputConstants;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;
import dev.zymekoh.kohsinventorydebug.gui.DebugLogScreen;
import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * Disposable integrated-singleplayer stress lab for reproducing input races.
 *
 * <p>The lab injects only normal Minecraft keyboard and mouse-handler events.
 * It never calls a packet, container action, slot, cooldown or server method
 * directly. Every macro is hard-blocked on remote multiplayer.</p>
 */
public final class MacroTestController {
	private static final AtomicBoolean RUNNING = new AtomicBoolean();
	private static final String AUTORUN_PROPERTY = "kohs.inventory.debug.autorun";
	private static final String EXIT_AFTER_MACRO_PROPERTY = "kohs.inventory.debug.exitAfterMacro";
	private static volatile String current = "idle";
	private static int autorunTicks;
	private static boolean autorunAttempted;

	private MacroTestController() {
	}

	public enum MacroKind {
		FAST_OPEN_CLOSE("fast-open-close", "kohs_inventory_debug.screen.macro_fast", "kohs_inventory_debug.lab.fast.desc", false, false),
		INVENTORY_THEN_OFFHAND("inventory-then-offhand", "kohs_inventory_debug.screen.macro_inv_off", "kohs_inventory_debug.lab.inv_off.desc", true, false),
		OFFHAND_THEN_INVENTORY("offhand-then-inventory", "kohs_inventory_debug.screen.macro_off_inv", "kohs_inventory_debug.lab.off_inv.desc", true, false),
		CENTERED_CURSOR("centered-cursor", "kohs_inventory_debug.screen.macro_center", "kohs_inventory_debug.lab.center.desc", false, false),
		CURSOR_CONTRACT("cursor-contract", "kohs_inventory_debug.lab.cursor_contract", "kohs_inventory_debug.lab.cursor_contract.desc", false, true),
		LATENCY_SWEEP("latency-sweep", "kohs_inventory_debug.lab.latency", "kohs_inventory_debug.lab.latency.desc", false, true),
		EXTREME_OPEN_CLOSE("extreme-open-close", "kohs_inventory_debug.lab.extreme", "kohs_inventory_debug.lab.extreme.desc", false, true),
		FRAME_JITTER("frame-jitter", "kohs_inventory_debug.lab.jitter", "kohs_inventory_debug.lab.jitter.desc", false, true),
		BURST_TOGGLE("burst-toggle", "kohs_inventory_debug.lab.burst", "kohs_inventory_debug.lab.burst.desc", false, true),
		MOUSE_TORNADO("mouse-tornado", "kohs_inventory_debug.lab.tornado", "kohs_inventory_debug.lab.tornado.desc", false, true),
		OPEN_MOVE_CHAOS("open-move-chaos", "kohs_inventory_debug.lab.chaos", "kohs_inventory_debug.lab.chaos.desc", false, true),
		OFFHAND_RACE_EXTREME("offhand-race-extreme", "kohs_inventory_debug.lab.offhand_race", "kohs_inventory_debug.lab.offhand_race.desc", true, true),
		LONG_SOAK("long-soak", "kohs_inventory_debug.lab.soak", "kohs_inventory_debug.lab.soak.desc", true, true),
		HELD_INVENTORY("held-inventory", "kohs_inventory_debug.lab.hold", "kohs_inventory_debug.lab.hold.desc", false, true),
		CLOSE_HOTBAR("close-hotbar", "kohs_inventory_debug.lab.close_hotbar", "kohs_inventory_debug.lab.close_hotbar.desc", true, true),
		FULL_STRESS("full-stress", "kohs_inventory_debug.screen.macro_full", "kohs_inventory_debug.lab.full.desc", true, false),
		AGGRESSIVE_SUITE("aggressive-suite", "kohs_inventory_debug.lab.aggressive", "kohs_inventory_debug.lab.aggressive.desc", true, true);

		private final String id;
		private final String titleKey;
		private final String descriptionKey;
		private final boolean requiresOffhand;
		private final boolean aggressive;

		MacroKind(
			final String id,
			final String titleKey,
			final String descriptionKey,
			final boolean requiresOffhand,
			final boolean aggressive
		) {
			this.id = id;
			this.titleKey = titleKey;
			this.descriptionKey = descriptionKey;
			this.requiresOffhand = requiresOffhand;
			this.aggressive = aggressive;
		}

		public String id() {
			return this.id;
		}

		public String titleKey() {
			return this.titleKey;
		}

		public String descriptionKey() {
			return this.descriptionKey;
		}

		public boolean aggressive() {
			return this.aggressive;
		}

		public static MacroKind parse(final String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
			for (MacroKind kind : values()) {
				if (kind.id.equals(normalized)) {
					return kind;
				}
			}
			return null;
		}
	}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	public static String current() {
		return current;
	}

	public static boolean isSafeLocalWorld(final Minecraft minecraft) {
		return minecraft != null
			&& minecraft.player != null
			&& minecraft.level != null
			&& minecraft.hasSingleplayerServer()
			&& minecraft.isSingleplayer()
			&& minecraft.getCurrentServer() == null;
	}

	public static boolean start(final Minecraft minecraft, final MacroKind kind, final Screen returnParent) {
		if (kind == null) {
			DebugCollector.warn("MACRO_BLOCKED", "Unknown macro requested.");
			return false;
		}
		if (!isSafeLocalWorld(minecraft)) {
			DebugCollector.issue("MACRO_BLOCKED", "Macro " + kind.id + " rejected: only an integrated singleplayer test world is allowed.");
			return false;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			DebugCollector.warn("MACRO_BLOCKED", "Macro " + kind.id + " rejected because " + current + " is already running.");
			return false;
		}

		InputBinding inventory = binding(minecraft.options.keyInventory);
		InputBinding offhand = binding(minecraft.options.keySwapOffhand);
		if (!inventory.supported() || (kind.requiresOffhand && !offhand.supported())) {
			RUNNING.set(false);
			DebugCollector.issue("MACRO_BLOCKED", "Unsupported remapped key. inventory=" + inventory + "; offhand=" + offhand);
			return false;
		}

		current = kind.id;
		DebugCollector.addUserMarker();
		DebugCollector.warn("MACRO_START", "LOCAL-ONLY L2 LAB macro=" + kind.id
			+ "; inventory=" + inventory + "; offhand=" + offhand
			+ "; starts after 1500ms; normal input handlers only; never use on multiplayer.");
		minecraft.setScreen(null);

		Thread.ofPlatform().daemon(true).name("KoHs Inventory Debug macro").start(() -> {
			MacroMetrics metrics = new MacroMetrics(kind.id);
			try {
				NativeInput input = new NativeInput(minecraft);
				sleep(1_500);
				guard(minecraft, "before first input");
				double[] originalPointer = localCursor(minecraft);
				run(input, minecraft, kind, inventory, offhand, metrics);
				normalizeClosed(input, minecraft, inventory, metrics, "final-normalize");
				if (originalPointer != null && minecraft.isWindowActive()) {
					input.moveCursor(
						minecraft.getWindow().getX() + (int) Math.round(originalPointer[0]),
						minecraft.getWindow().getY() + (int) Math.round(originalPointer[1])
					);
				}
				if (kind != MacroKind.CURSOR_CONTRACT) {
					DebugCollector.info("MACRO_SUMMARY", metrics.summary());
				}
				DebugCollector.info("MACRO_COMPLETE", "macro=" + kind.id + "; screen=" + screenName(screenSnapshot(minecraft))
					+ "; nativeInputBridgeEvents=" + input.dispatchedEvents());
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				DebugCollector.warn("MACRO_ABORT", "macro=" + kind.id + "; interrupted");
			} catch (Throwable throwable) {
				DebugCollector.issue("MACRO_ABORT", "macro=" + kind.id + "; " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			} finally {
				current = "idle";
				RUNNING.set(false);
				minecraft.execute(() -> {
					if (Boolean.getBoolean(EXIT_AFTER_MACRO_PROPERTY)) {
						DebugCollector.info("MACRO_AUTO_EXIT", "Closing the disposable development client after the completed local stress run.");
						minecraft.stop();
					} else {
						minecraft.setScreen(new DebugLogScreen(returnParent));
					}
				});
			}
		});
		return true;
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (autorunAttempted || RUNNING.get()) {
			return;
		}
		MacroKind requested = MacroKind.parse(System.getProperty(AUTORUN_PROPERTY));
		if (requested == null || !isSafeLocalWorld(minecraft)) {
			return;
		}
		if (++autorunTicks < 60) {
			return;
		}
		autorunAttempted = true;
		DebugCollector.info("MACRO_AUTORUN", "Starting requested development macro=" + requested.id);
		start(minecraft, requested, null);
	}

	private static void run(
		final NativeInput input,
		final Minecraft minecraft,
		final MacroKind kind,
		final InputBinding inventory,
		final InputBinding offhand,
		final MacroMetrics metrics
	) throws InterruptedException {
		switch (kind) {
			case FAST_OPEN_CLOSE -> fastOpenClose(input, minecraft, inventory, 24, metrics);
			case INVENTORY_THEN_OFFHAND -> inventoryOffhand(input, minecraft, inventory, offhand, false, 18, metrics);
			case OFFHAND_THEN_INVENTORY -> inventoryOffhand(input, minecraft, inventory, offhand, true, 18, metrics);
			case CENTERED_CURSOR -> centeredCursor(input, minecraft, inventory, 18, false, metrics);
			case LATENCY_SWEEP -> latencySweep(input, minecraft, inventory, 64, metrics);
			case EXTREME_OPEN_CLOSE -> extremeOpenClose(input, minecraft, inventory, 96, metrics);
			case FRAME_JITTER -> frameJitter(input, minecraft, inventory, 72, metrics);
			case BURST_TOGGLE -> burstToggle(input, minecraft, inventory, 36, metrics);
			case MOUSE_TORNADO -> mouseTornado(input, minecraft, inventory, 40, metrics);
			case OPEN_MOVE_CHAOS -> openMoveChaos(input, minecraft, inventory, 36, metrics);
			case OFFHAND_RACE_EXTREME -> offhandRaceExtreme(input, minecraft, inventory, offhand, 64, metrics);
			case LONG_SOAK -> longSoak(input, minecraft, inventory, offhand, 180, metrics);
			case HELD_INVENTORY -> heldInventory(input, minecraft, inventory, 8, metrics);
			case CLOSE_HOTBAR -> CloseHotbarRegressionLab.run(minecraft);
			case CURSOR_CONTRACT -> CursorContractLab.run(minecraft);
			case FULL_STRESS -> {
				fastOpenClose(input, minecraft, inventory, 12, metrics);
				inventoryOffhand(input, minecraft, inventory, offhand, false, 10, metrics);
				inventoryOffhand(input, minecraft, inventory, offhand, true, 10, metrics);
				centeredCursor(input, minecraft, inventory, 10, false, metrics);
			}
			case AGGRESSIVE_SUITE -> {
				latencySweep(input, minecraft, inventory, 24, metrics);
				extremeOpenClose(input, minecraft, inventory, 48, metrics);
				frameJitter(input, minecraft, inventory, 24, metrics);
				burstToggle(input, minecraft, inventory, 16, metrics);
				mouseTornado(input, minecraft, inventory, 16, metrics);
				openMoveChaos(input, minecraft, inventory, 16, metrics);
				closeUnderHeldMovement(input, minecraft, inventory, 32, metrics);
				heldInventory(input, minecraft, inventory, 4, metrics);
				offhandRaceExtreme(input, minecraft, inventory, offhand, 24, metrics);
				longSoak(input, minecraft, inventory, offhand, 60, metrics);
			}
		}
	}

	private static void fastOpenClose(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("fast-open-close", cycles);
		int[] gaps = {120, 90, 70, 55, 40, 30, 22, 16};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "fast cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "fast-pre cycle=" + cycle);
			int gap = gaps[cycle % gaps.length];
			long started = System.nanoTime();
			pulse(input, inventory, 4, "fast-open cycle=" + cycle + "; gap=" + gap + "ms");
			boolean opened = awaitPlayerInventory(minecraft, true, Math.max(150, gap + 60));
			metrics.open(started, opened);
			observeInventory(minecraft, "fast-open cycle=" + cycle + "; gap=" + gap + "ms");
			if (opened) {
				sleep(gap);
				pulse(input, inventory, 4, "fast-close cycle=" + cycle + "; gap=" + gap + "ms");
				metrics.state("fast-close cycle=" + cycle, awaitPlayerInventory(minecraft, false, 180));
			}
			sleep(Math.max(20, gap / 2));
		}
	}

	private static void latencySweep(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("latency-sweep", cycles);
		int[] phases = {0, 1, 2, 3, 4, 5, 7, 9, 12, 16, 19, 23};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "latency cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "latency-pre cycle=" + cycle);
			sleep(phases[cycle % phases.length]);
			long started = System.nanoTime();
			pulse(input, inventory, cycle % 3, "latency-open cycle=" + cycle);
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			if (opened) {
				pulse(input, inventory, cycle % 2, "latency-close cycle=" + cycle);
				metrics.state("latency-close cycle=" + cycle, awaitPlayerInventory(minecraft, false, 250));
			}
			sleep(18);
		}
	}

	private static void extremeOpenClose(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("extreme-open-close", cycles);
		int[] gaps = {0, 1, 2, 3, 4, 5, 7, 9, 12, 16, 24, 32};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "extreme cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "extreme-pre cycle=" + cycle);
			int gap = gaps[cycle % gaps.length];
			long started = System.nanoTime();
			pulse(input, inventory, cycle % 2, "extreme-open cycle=" + cycle + "; gap=" + gap + "ms");
			sleep(gap);
			boolean openedAtGap = isPlayerInventoryOpen(minecraft);
			metrics.open(started, openedAtGap);
			pulse(input, inventory, cycle % 2, "extreme-close cycle=" + cycle + "; gap=" + gap + "ms");
			sleep(55);
			boolean closed = !isPlayerInventoryOpen(minecraft);
			metrics.state("extreme parity cycle=" + cycle + "; gap=" + gap + "ms", closed);
			if (!closed) {
				normalizeClosed(input, minecraft, inventory, metrics, "extreme-recovery cycle=" + cycle);
			}
			sleep(12);
		}
	}

	private static void frameJitter(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("frame-jitter", cycles);
		int[] jitter = {0, 17, 1, 16, 2, 15, 3, 14, 4, 13, 5, 12, 6, 11, 7, 10, 8, 9};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "jitter cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "jitter-pre cycle=" + cycle);
			sleep(jitter[cycle % jitter.length]);
			long started = System.nanoTime();
			pulse(input, inventory, 1, "jitter-open cycle=" + cycle + "; phase=" + jitter[cycle % jitter.length] + "ms");
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			if (opened) {
				sleep(jitter[(cycle * 7 + 3) % jitter.length]);
				pulse(input, inventory, 1, "jitter-close cycle=" + cycle);
				metrics.state("jitter-close cycle=" + cycle, awaitPlayerInventory(minecraft, false, 250));
			}
		}
	}

	private static void burstToggle(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("burst-toggle", cycles);
		int[] separations = {0, 1, 2, 3};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "burst cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "burst-pre cycle=" + cycle);
			int presses = 2 + cycle % 4;
			int separation = separations[cycle % separations.length];
			DebugCollector.warn("MACRO_BURST", "cycle=" + cycle + "; presses=" + presses + "; separation=" + separation + "ms");
			for (int index = 0; index < presses; index++) {
				pulse(input, inventory, index % 2, "burst cycle=" + cycle + "; press=" + index + '/' + presses);
				sleep(separation);
			}
			sleep(150);
			boolean actualOpen = isPlayerInventoryOpen(minecraft);
			// More than one physical press in the same poll is deliberately handed
			// back to Vanilla.  Vanilla consumes the queued clicks as a batch and
			// does not promise parity (it may open once, then stop consuming after
			// the screen changes), so treating odd/even parity as a regression would
			// report a false failure in this stress macro.
			DebugCollector.info("MACRO_BURST_VANILLA", "cycle=" + cycle + "; presses=" + presses
				+ "; actualOpen=" + actualOpen + "; parityAssertion=skipped; reason=vanilla-batch-semantics");
			normalizeClosed(input, minecraft, inventory, metrics, "burst-recovery cycle=" + cycle);
			sleep(25);
		}
	}

	private static void inventoryOffhand(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final InputBinding offhand,
		final boolean offhandFirst,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch(offhandFirst ? "offhand-then-inventory" : "inventory-then-offhand", cycles);
		int[] separations = {0, 1, 2, 4, 6, 8, 12, 16, 24};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "overlap cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "overlap-pre cycle=" + cycle);
			int separation = separations[cycle % separations.length];
			InputBinding first = offhandFirst ? offhand : inventory;
			InputBinding second = offhandFirst ? inventory : offhand;
			long started = System.nanoTime();
			pulse(input, first, 2, "overlap-first=" + (offhandFirst ? "offhand" : "inventory")
				+ "; cycle=" + cycle + "; separation=" + separation + "ms");
			sleep(separation);
			pulse(input, second, 2, "overlap-second=" + (offhandFirst ? "inventory" : "offhand")
				+ "; cycle=" + cycle + "; separation=" + separation + "ms");
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			observeInventory(minecraft, "overlap cycle=" + cycle + "; offhandFirst=" + offhandFirst
				+ "; separation=" + separation + "ms");
			normalizeClosed(input, minecraft, inventory, metrics, "overlap-close cycle=" + cycle);
			sleep(45);
		}
	}

	private static void offhandRaceExtreme(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final InputBinding offhand,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("offhand-race-extreme", cycles);
		int[] separations = {0, 0, 1, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "offhand-race cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "offhand-race-pre cycle=" + cycle);
			boolean offhandFirst = (cycle & 1) == 1;
			InputBinding first = offhandFirst ? offhand : inventory;
			InputBinding second = offhandFirst ? inventory : offhand;
			int separation = separations[cycle % separations.length];
			long started = System.nanoTime();
			pulse(input, first, cycle % 2, "race-first cycle=" + cycle + "; offhandFirst=" + offhandFirst);
			sleep(separation);
			pulse(input, second, (cycle + 1) % 2, "race-second cycle=" + cycle + "; separation=" + separation + "ms");
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			if (!opened) {
				DebugCollector.warn("MACRO_RACE_MISS", "cycle=" + cycle + "; offhandFirst=" + offhandFirst
					+ "; separation=" + separation + "ms; screen=" + screenName(screenSnapshot(minecraft)));
			}
			normalizeClosed(input, minecraft, inventory, metrics, "offhand-race-close cycle=" + cycle);
			sleep(35);
		}
	}

	private static void centeredCursor(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final boolean aggressive,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch(aggressive ? "centered-cursor-aggressive" : "centered-cursor", cycles);
		double[][] normalized = cursorPoints();
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "cursor cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "cursor-pre cycle=" + cycle);
			double[] point = normalized[cycle % normalized.length];
			moveNormalized(input, minecraft, point[0], point[1]);
			sleep(aggressive ? cycle % 3 : 20);
			long started = System.nanoTime();
			pulse(input, inventory, aggressive ? cycle % 2 : 4, "cursor-open cycle=" + cycle);
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			double[] afterOpen = localCursor(minecraft);
			DebugCollector.info("MACRO_OBSERVE", "cursor-open cycle=" + cycle + "; inventoryOpen=" + opened
				+ "; cursor=" + point(afterOpen));
			if (opened) {
				sleep(aggressive ? 8 : 45);
				moveNormalized(input, minecraft, 0.15 + (cycle % 7) * 0.11, 0.20 + (cycle % 5) * 0.13);
				sleep(aggressive ? 4 : 30);
			}
			normalizeClosed(input, minecraft, inventory, metrics, "cursor-close cycle=" + cycle);
			sleep(aggressive ? 12 : 60);
		}
	}

	private static void mouseTornado(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("mouse-tornado", cycles);
		double[][] points = cursorPoints();
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "tornado cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "tornado-pre cycle=" + cycle);
			DebugCollector.warn("MACRO_MOUSE_SWEEP", "tornado cycle=" + cycle + "; pre-openMoves=6; openMoves=14");
			for (int move = 0; move < 6; move++) {
				double[] point = points[(cycle * 5 + move * 3) % points.length];
				moveNormalized(input, minecraft, point[0], point[1]);
				sleep((cycle + move) % 3);
			}
			long started = System.nanoTime();
			pulse(input, inventory, cycle % 2, "tornado-open cycle=" + cycle);
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			if (opened) {
				for (int move = 0; move < 14; move++) {
					double x = 0.08 + ((cycle * 37 + move * 67) % 84) / 100.0;
					double y = 0.08 + ((cycle * 53 + move * 41) % 84) / 100.0;
					moveNormalized(input, minecraft, x, y);
					sleep(move % 3);
				}
			}
			normalizeClosed(input, minecraft, inventory, metrics, "tornado-close cycle=" + cycle);
			sleep(18);
		}
	}

	private static void openMoveChaos(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("open-move-chaos", cycles);
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "open-move cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "open-move-pre cycle=" + cycle);
			moveNormalized(input, minecraft, (cycle & 1) == 0 ? 0.03 : 0.97, cycle % 3 == 0 ? 0.04 : 0.96);
			long started = System.nanoTime();
			pulse(input, inventory, 0, "open-move-open cycle=" + cycle);
			for (int move = 0; move < 20; move++) {
				double angle = (cycle * 0.71) + move * 0.83;
				double x = 0.50 + Math.cos(angle) * (0.43 - (move % 4) * 0.04);
				double y = 0.50 + Math.sin(angle) * (0.43 - (move % 5) * 0.035);
				moveNormalized(input, minecraft, x, y);
				sleep(move % 2);
			}
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			metrics.state("open-move screen cycle=" + cycle, opened);
			normalizeClosed(input, minecraft, inventory, metrics, "open-move-close cycle=" + cycle);
			sleep(14);
		}
	}

	/**
	 * Holds several ordinary gameplay keys while the inventory key closes the
	 * already-open player inventory. This reproduces the reported PvP chord
	 * without calling a screen, menu or packet method directly.
	 */
	private static void closeUnderHeldMovement(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("close-under-held-movement", cycles);
		List<InputBinding> movement = List.of(
			binding(minecraft.options.keyUp),
			binding(minecraft.options.keyLeft),
			binding(minecraft.options.keyJump),
			binding(minecraft.options.keySprint)
		);
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "held-close cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "held-close-pre cycle=" + cycle);
			List<InputBinding> held = new ArrayList<>();
			try {
				for (int index = 0; index < movement.size(); index++) {
					InputBinding candidate = movement.get((cycle + index) % movement.size());
					if (candidate.supported() && !candidate.samePhysicalInput(inventory)) {
						candidate.press(input);
						held.add(candidate);
					}
				}
				long openStarted = System.nanoTime();
				pulse(input, inventory, cycle % 2, "held-close-open cycle=" + cycle + "; held=" + held.size());
				boolean opened = awaitPlayerInventory(minecraft, true, 250);
				metrics.open(openStarted, opened);
				if (!opened) {
					metrics.state("held-close-open cycle=" + cycle, false);
					continue;
				}
				long started = System.nanoTime();
				pulse(input, inventory, cycle % 2, "held-close cycle=" + cycle + "; held=" + held.size());
				boolean closed = awaitPlayerInventory(minecraft, false, 250);
				long elapsedMicros = Math.max(0L, (System.nanoTime() - started) / 1_000L);
				DebugCollector.info("MACRO_CLOSE_LATENCY", "held-close cycle=" + cycle
					+ "; closed=" + closed + "; elapsed=" + elapsedMicros + "us; held=" + held.size());
				metrics.state("held-close cycle=" + cycle, closed);
			} finally {
				for (int index = held.size() - 1; index >= 0; index--) {
					held.get(index).release(input);
				}
			}
			sleep(12);
		}
	}

	private static void longSoak(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final InputBinding offhand,
		final int cycles,
		final MacroMetrics metrics
	) throws InterruptedException {
		batch("long-soak", cycles);
		int[] rests = {8, 12, 16, 20, 28, 36, 48, 64};
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "soak cycle " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "soak-pre cycle=" + cycle);
			if (cycle % 5 == 0) {
				moveNormalized(input, minecraft, 0.08 + (cycle * 17 % 84) / 100.0, 0.08 + (cycle * 29 % 84) / 100.0);
			}
			long started = System.nanoTime();
			pulse(input, inventory, cycle % 3, "soak-open cycle=" + cycle);
			if (cycle % 11 == 0) {
				sleep(cycle % 4);
				pulse(input, offhand, 1, "soak-offhand-overlap cycle=" + cycle);
			}
			boolean opened = awaitPlayerInventory(minecraft, true, 250);
			metrics.open(started, opened);
			if (opened && cycle % 7 == 0) {
				for (int move = 0; move < 5; move++) {
					moveNormalized(input, minecraft, 0.18 + move * 0.14, 0.25 + ((cycle + move) % 4) * 0.14);
					sleep(1);
				}
			}
			normalizeClosed(input, minecraft, inventory, metrics, "soak-close cycle=" + cycle);
			sleep(rests[cycle % rests.length]);
		}
	}

	private static void normalizeClosed(
		final NativeInput input,
		final Minecraft minecraft,
		final InputBinding inventory,
		final MacroMetrics metrics,
		final String label
	) throws InterruptedException {
		Screen screen = screenSnapshot(minecraft);
		if (screen == null) {
			return;
		}
		if (!InventoryScreenClassifier.isPlayerInventory(screen)) {
			metrics.state(label + "; unexpectedScreen=" + screenName(screen), false);
			throw new IllegalStateException("unexpected screen at " + label + ": " + screenName(screen));
		}
		pulse(input, inventory, 2, label);
		metrics.state(label, awaitPlayerInventory(minecraft, false, 250));
	}

	/** Sends OS-style REPEAT events: a held key is not another physical press. */
	private static void heldInventory(
		final NativeInput input, final Minecraft minecraft, final InputBinding inventory,
		final int cycles, final MacroMetrics metrics
	) throws InterruptedException {
		if (inventory.type != InputBinding.Type.KEYBOARD) {
			DebugCollector.info("MACRO_HOLD", "Mouse bindings do not generate keyboard repeat events.");
			return;
		}
		batch("held-inventory-repeat", cycles);
		for (int cycle = 0; cycle < cycles; cycle++) {
			guard(minecraft, "held-inventory " + cycle);
			normalizeClosed(input, minecraft, inventory, metrics, "hold-pre");
			try {
				long started = System.nanoTime();
				inventory.press(input);
				boolean opened = awaitPlayerInventory(minecraft, true, 250);
				metrics.open(started, opened);
				Screen original = screenSnapshot(minecraft);
				sleep(300);
				int stable = 0;
				for (int repeat = 0; repeat < 12; repeat++) {
					input.keyAction(inventory.code, GLFW.GLFW_REPEAT);
					if (opened && screenSnapshot(minecraft) == original) stable++;
					sleep(20);
				}
				metrics.state("held-open cycle=" + cycle + "; stable=" + stable + "/12", stable == 12);
				DebugCollector.info("MACRO_HOLD", "cycle=" + cycle + "; sameOpening=" + stable + "/12");
			} finally {
				inventory.release(input);
			}
			normalizeClosed(input, minecraft, inventory, metrics, "hold-close-normalize");
			pulse(input, inventory, 1, "hold-reopen");
			metrics.state("hold-new-press-opens", awaitPlayerInventory(minecraft, true, 250));
			try {
				inventory.press(input);
				metrics.state("hold-new-press-closes", awaitPlayerInventory(minecraft, false, 250));
				sleep(300);
				for (int repeat = 0; repeat < 12; repeat++) {
					input.keyAction(inventory.code, GLFW.GLFW_REPEAT);
					sleep(20);
				}
				metrics.state("held-close-stays-closed", screenSnapshot(minecraft) == null);
			} finally {
				inventory.release(input);
			}
		}
	}

	private static boolean awaitPlayerInventory(final Minecraft minecraft, final boolean expectedOpen, final long timeoutMillis)
		throws InterruptedException {
		long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
		do {
			boolean open = isPlayerInventoryOpen(minecraft);
			if (open == expectedOpen) {
				return true;
			}
			sleep(1);
		} while (System.nanoTime() < deadline);
		return isPlayerInventoryOpen(minecraft) == expectedOpen;
	}

	private static boolean isPlayerInventoryOpen(final Minecraft minecraft) {
		return InventoryScreenClassifier.isPlayerInventory(screenSnapshot(minecraft));
	}

	private static Screen screenSnapshot(final Minecraft minecraft) {
		AtomicReference<Screen> value = new AtomicReference<>();
		minecraft.executeBlocking(() -> value.set(minecraft.screen));
		return value.get();
	}

	private static void observeInventory(final Minecraft minecraft, final String label) {
		Screen screen = screenSnapshot(minecraft);
		DebugCollector.info("MACRO_OBSERVE", label + "; inventoryOpen="
			+ InventoryScreenClassifier.isPlayerInventory(screen) + "; screen=" + screenName(screen));
	}

	private static void batch(final String name, final int cycles) {
		DebugCollector.warn("MACRO_BATCH", "begin=" + name + "; cycles=" + cycles);
	}

	private static void pulse(
		final NativeInput input,
		final InputBinding binding,
		final long holdMillis,
		final String label
	) throws InterruptedException {
		DebugCollector.info("MACRO_STEP", label + "; hold=" + holdMillis + "ms; binding=" + binding);
		binding.press(input);
		sleep(holdMillis);
		binding.release(input);
	}

	private static void moveNormalized(
		final NativeInput input,
		final Minecraft minecraft,
		final double normalizedX,
		final double normalizedY
	) {
		double x = Math.max(0.01, Math.min(0.99, normalizedX));
		double y = Math.max(0.01, Math.min(0.99, normalizedY));
		int globalX = minecraft.getWindow().getX() + (int) Math.round(minecraft.getWindow().getScreenWidth() * x);
		int globalY = minecraft.getWindow().getY() + (int) Math.round(minecraft.getWindow().getScreenHeight() * y);
		input.moveCursor(globalX, globalY);
	}

	private static double[][] cursorPoints() {
		return new double[][] {
			{0.03, 0.03}, {0.50, 0.03}, {0.97, 0.03}, {0.97, 0.50},
			{0.97, 0.97}, {0.50, 0.97}, {0.03, 0.97}, {0.03, 0.50},
			{0.18, 0.20}, {0.82, 0.20}, {0.82, 0.80}, {0.18, 0.80}, {0.50, 0.50}
		};
	}

	private static void guard(final Minecraft minecraft, final String stage) {
		if (!isSafeLocalWorld(minecraft)) {
			throw new IllegalStateException("local-world guard failed at " + stage);
		}
		if (!minecraft.isWindowActive() || !minecraft.getWindow().isFocused() || minecraft.getWindow().isMinimized()) {
			throw new IllegalStateException("Minecraft lost focus at " + stage + "; no input was sent");
		}
	}

	private static void sleep(final long millis) throws InterruptedException {
		if (millis > 0L) {
			Thread.sleep(millis);
		}
	}

	private static InputBinding binding(final KeyMapping mapping) {
		InputConstants.Key key = ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return key.getValue() >= 0 && key.getValue() <= 7
				? InputBinding.mouse(key.getName(), key.getValue())
				: InputBinding.unsupported(key.getName());
		}
		if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) {
			return InputBinding.unsupported(key.getName());
		}
		return InputBinding.keyboard(key.getName(), key.getValue());
	}

	private record InputBinding(String name, Type type, int code) {
		private enum Type { KEYBOARD, MOUSE, UNSUPPORTED }

		static InputBinding keyboard(final String name, final int code) {
			return new InputBinding(name, Type.KEYBOARD, code);
		}

		static InputBinding mouse(final String name, final int code) {
			return new InputBinding(name, Type.MOUSE, code);
		}

		static InputBinding unsupported(final String name) {
			return new InputBinding(name, Type.UNSUPPORTED, -1);
		}

		boolean supported() {
			return this.type != Type.UNSUPPORTED;
		}

		boolean samePhysicalInput(final InputBinding other) {
			return other != null && this.type == other.type && this.code == other.code;
		}

		void press(final NativeInput input) {
			if (this.type == Type.KEYBOARD) {
				input.key(this.code, true);
			} else if (this.type == Type.MOUSE) {
				input.mouseButton(this.code, true);
			}
		}

		void release(final NativeInput input) {
			if (this.type == Type.KEYBOARD) {
				input.key(this.code, false);
			} else if (this.type == Type.MOUSE) {
				input.mouseButton(this.code, false);
			}
		}
	}

	private static double[] localCursor(final Minecraft minecraft) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			DoubleBuffer x = stack.mallocDouble(1);
			DoubleBuffer y = stack.mallocDouble(1);
			GLFW.glfwGetCursorPos(minecraft.getWindow().handle(), x, y);
			return new double[] {x.get(0), y.get(0)};
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String point(final double[] point) {
		return point == null ? "unavailable" : String.format(Locale.ROOT, "(%.3f,%.3f)", point[0], point[1]);
	}

	private static String screenName(final Screen screen) {
		return screen == null ? "none" : screen.getClass().getName();
	}

	private interface User32 extends Library {
		User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean SetCursorPos(int x, int y);
	}

	private static final class NativeInput {
		private final Minecraft minecraft;
		private int dispatchedEvents;

		private NativeInput(final Minecraft minecraft) {
			this.minecraft = minecraft;
		}

		private void key(final int glfwKey, final boolean down) {
			this.keyAction(glfwKey, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
		}

		private void keyAction(final int glfwKey, final int action) {
			this.minecraft.executeBlocking(() -> ((KeyboardHandlerDebugInvoker) this.minecraft.keyboardHandler)
				.kohsInventoryDebug$invokeKeyPress(
					this.minecraft.getWindow().handle(),
					action,
					new KeyEvent(glfwKey, 0, 0)
				));
			this.dispatchedEvents++;
		}

		private void mouseButton(final int glfwButton, final boolean down) {
			this.minecraft.executeBlocking(() -> ((MouseHandlerDebugInvoker) this.minecraft.mouseHandler)
				.kohsInventoryDebug$invokeButton(
					this.minecraft.getWindow().handle(),
					new MouseButtonInfo(glfwButton, 0),
					down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE
				));
			this.dispatchedEvents++;
		}

		private int dispatchedEvents() {
			return this.dispatchedEvents;
		}

		private void moveCursor(final int x, final int y) {
			if (!User32.INSTANCE.SetCursorPos(x, y)) {
				throw new IllegalStateException("SetCursorPos failed");
			}
		}
	}

	private static final class MacroMetrics {
		private final String macro;
		private final List<Long> openingMicros = new ArrayList<>();
		private int openingAttempts;
		private int openingFailures;
		private int assertions;
		private int assertionFailures;

		private MacroMetrics(final String macro) {
			this.macro = macro;
		}

		private void open(final long startedNanos, final boolean success) {
			this.openingAttempts++;
			long elapsedMicros = Math.max(0L, System.nanoTime() - startedNanos) / 1_000L;
			if (success) {
				this.openingMicros.add(elapsedMicros);
				DebugCollector.info("MACRO_LATENCY", "macro=" + this.macro + "; open=" + elapsedMicros + "us");
			} else {
				this.openingFailures++;
				DebugCollector.warn("MACRO_OPEN_MISS", "macro=" + this.macro + "; not open at sample; elapsed=" + elapsedMicros + "us");
			}
		}

		private void state(final String label, final boolean success) {
			this.assertions++;
			if (!success) {
				this.assertionFailures++;
				DebugCollector.warn("MACRO_ASSERT", label + "; failed");
			} else {
				DebugCollector.trace("MACRO_ASSERT", label + "; passed");
			}
		}

		private String summary() {
			List<Long> sorted = new ArrayList<>(this.openingMicros);
			Collections.sort(sorted);
			return "macro=" + this.macro
				+ "; openAttempts=" + this.openingAttempts
				+ "; openSuccess=" + sorted.size()
				+ "; openMisses=" + this.openingFailures
				+ "; p50=" + percentile(sorted, 0.50) + "us"
				+ "; p95=" + percentile(sorted, 0.95) + "us"
				+ "; max=" + (sorted.isEmpty() ? -1L : sorted.getLast()) + "us"
				+ "; assertions=" + this.assertions
				+ "; assertionFailures=" + this.assertionFailures;
		}

		private static long percentile(final List<Long> sorted, final double percentile) {
			if (sorted.isEmpty()) {
				return -1L;
			}
			int index = (int) Math.ceil(percentile * sorted.size()) - 1;
			return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
		}
	}
}
