package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.KeyMappingAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Advances only construction of the ordinary local player inventory.
 *
 * <p>GLFW delivers keyboard and mouse callbacks as one display-update batch, and
 * Minecraft runs every one of them as a queued task inside {@code runAllTasks}.
 * This controller records that complete batch and makes its decision the moment
 * the drain finishes, still ahead of {@code Minecraft#tick}. A sole queued
 * Inventory press can then open on the frame it arrived instead of waiting for
 * the next 20 TPS client tick. Any overlapping non-movement mapping leaves the
 * complete Vanilla queue untouched.</p>
 */
public final class SuperFastInventoryController {
	/** Trailing repeat marker appended by the queued-action report, as in `key.attackx2`. */
	private static final Pattern REPEAT_COUNT = Pattern.compile("x\\d+$");
	private static boolean physicalInputObserved;
	private static boolean conflictingPhysicalInputObserved;
	private static String conflictingPhysicalMappings = "none";
	private static long firstPhysicalInputNanos;
	private static long inventoryPhysicalInputNanos;
	private static String lastDecision = "idle";
	private static String lastDecisionReason = "not-evaluated";
	private static long lastDecisionNanos;

	private SuperFastInventoryController() {
	}

	public static void onKeyboardEvent(
		final Minecraft minecraft,
		final long windowHandle,
		final int action,
		final KeyEvent event
	) {
		if (minecraft == null
			|| action != GLFW.GLFW_PRESS
			|| windowHandle != minecraft.getWindow().handle()) {
			return;
		}

		long now = System.nanoTime();
		observePhysicalInput(now);
		if (minecraft.options.keyInventory.matches(event)) {
			inventoryPhysicalInputNanos = now;
		}
		appendPhysicalConflicts(matchingNonMovementMappings(
			minecraft,
			mapping -> mapping.matches(event)
		));
	}

	public static void onMouseButton(
		final Minecraft minecraft,
		final long windowHandle,
		final MouseButtonInfo buttonInfo,
		final int action
	) {
		if (minecraft == null
			|| action != GLFW.GLFW_PRESS
			|| windowHandle != minecraft.getWindow().handle()
			|| buttonInfo == null) {
			return;
		}

		long now = System.nanoTime();
		MouseButtonEvent event = new MouseButtonEvent(0.0, 0.0, buttonInfo);
		observePhysicalInput(now);
		if (minecraft.options.keyInventory.matchesMouse(event)) {
			inventoryPhysicalInputNanos = now;
		}
		appendPhysicalConflicts(matchingNonMovementMappings(
			minecraft,
			mapping -> mapping.matchesMouse(event)
		));
	}

	/** Called once per frame, after every event queued by the last poll has run. */
	public static void afterInputPoll(final Minecraft minecraft) {
		if (!physicalInputObserved) {
			return;
		}

		boolean physicalConflict = conflictingPhysicalInputObserved;
		String physicalConflictMappings = conflictingPhysicalMappings;
		physicalInputObserved = false;
		conflictingPhysicalInputObserved = false;
		conflictingPhysicalMappings = "none";
		firstPhysicalInputNanos = 0L;

		if (minecraft == null || queuedClicks(minecraft.options.keyInventory) == 0) {
			inventoryPhysicalInputNanos = 0L;
			return;
		}

		if (inventoryPhysicalInputNanos == 0L) {
			// The queued click was pressed in an earlier batch, and that batch already
			// decided what to do with it. Frames run far faster than the 20 TPS tick
			// that consumes the queue, so a press handed to Vanilla is still sitting
			// there several batches later; re-deciding it here would take it back and
			// strand whatever action it was handed over for until the screen closes.
			// Only the batch that contains the press decides it.
			return;
		}

		if (physicalConflict) {
			finishDecision("vanilla-fallback", "physical-conflict:" + physicalConflictMappings);
			inventoryPhysicalInputNanos = 0L;
			return;
		}

		String queuedConflict = queuedVanillaActions(minecraft);
		if (!queuedConflict.equals("none")) {
			finishDecision("vanilla-fallback", "queued-conflict:" + queuedConflict);
			inventoryPhysicalInputNanos = 0L;
			return;
		}

		String unavailableReason = unavailableReason(minecraft);
		if (unavailableReason != null) {
			finishDecision("vanilla-fallback", unavailableReason);
			inventoryPhysicalInputNanos = 0L;
			return;
		}

		if (!minecraft.options.keyInventory.consumeClick()) {
			finishDecision("vanilla-fallback", "inventory-click-already-consumed");
			inventoryPhysicalInputNanos = 0L;
			return;
		}
		while (minecraft.options.keyInventory.consumeClick()) {
			// Vanilla can only display one local InventoryScreen for this batch.
		}

		minecraft.getTutorial().onOpenInventory();
		minecraft.setScreen(new InventoryScreen(minecraft.player));
		finishDecision("early-open", "sole-inventory-input");
		inventoryPhysicalInputNanos = 0L;
	}

	/** Whether the last inventory press opened before the next client tick. */
	public static boolean lastOpenWasImmediate() {
		return "early-open".equals(lastDecision);
	}

	/** Whether the last press fell back because other input shared its batch. */
	public static boolean lastOpenHadInputConflict() {
		return lastDecisionReason.startsWith("physical-conflict:")
			|| lastDecisionReason.startsWith("queued-conflict:");
	}

	/**
	 * The mappings that shared the batch, as their own translation keys.
	 *
	 * <p>A player whose binds overlap takes the slow path on every open and has
	 * nothing on screen to say so; naming the mappings turns that into something
	 * they can act on.</p>
	 */
	public static List<String> lastConflictMappings() {
		int separator = lastDecisionReason.indexOf(':');
		if (!lastOpenHadInputConflict() || separator < 0) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (String entry : lastDecisionReason.substring(separator + 1).split(",")) {
			String name = REPEAT_COUNT.matcher(entry).replaceFirst("").trim();
			if (!name.isEmpty() && !names.contains(name)) {
				names.add(name);
			}
		}
		return List.copyOf(names);
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String pendingInputSnapshot() {
		long now = System.nanoTime();
		return "observed=" + physicalInputObserved
			+ "; conflict=" + conflictingPhysicalInputObserved
			+ "; conflictMappings=" + conflictingPhysicalMappings
			+ "; firstInputAge=" + nanosToMicros(now - firstPhysicalInputNanos, firstPhysicalInputNanos) + "us"
			+ "; inventoryInputAge=" + nanosToMicros(now - inventoryPhysicalInputNanos, inventoryPhysicalInputNanos) + "us";
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String lastDecisionSnapshot() {
		long now = System.nanoTime();
		return "decision=" + lastDecision
			+ "; reason=" + lastDecisionReason
			+ "; age=" + nanosToMicros(now - lastDecisionNanos, lastDecisionNanos) + "us";
	}

	private static void observePhysicalInput(final long now) {
		if (!physicalInputObserved) {
			firstPhysicalInputNanos = now;
		}
		physicalInputObserved = true;
	}

	private static void appendPhysicalConflicts(final String mappings) {
		if (mappings.equals("none")) {
			return;
		}
		conflictingPhysicalInputObserved = true;
		if (conflictingPhysicalMappings.equals("none")) {
			conflictingPhysicalMappings = mappings;
		} else if (!containsMapping(conflictingPhysicalMappings, mappings)) {
			conflictingPhysicalMappings += "," + mappings;
		}
	}

	private static boolean containsMapping(final String existing, final String candidate) {
		for (String value : existing.split(",")) {
			if (value.equals(candidate)) {
				return true;
			}
		}
		return false;
	}

	private static String matchingNonMovementMappings(
		final Minecraft minecraft,
		final Predicate<KeyMapping> matches
	) {
		StringBuilder result = new StringBuilder();
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != minecraft.options.keyInventory
				&& mapping.getCategory() != KeyMapping.Category.MOVEMENT
				&& matches.test(mapping)) {
				if (!result.isEmpty()) {
					result.append(',');
				}
				result.append(mapping.getName());
			}
		}
		return result.isEmpty() ? "none" : result.toString();
	}

	private static String queuedVanillaActions(final Minecraft minecraft) {
		StringBuilder result = new StringBuilder();
		appendQueued(result, minecraft.options.keyTogglePerspective);
		appendQueued(result, minecraft.options.keySmoothCamera);
		appendQueued(result, minecraft.options.keyToggleGui);
		appendQueued(result, minecraft.options.keyToggleSpectatorShaderEffects);
		appendQueued(result, minecraft.options.keySocialInteractions);
		appendQueued(result, minecraft.options.keyAdvancements);
		appendQueued(result, minecraft.options.keyQuickActions);
		appendQueued(result, minecraft.options.keySwapOffhand);
		appendQueued(result, minecraft.options.keyDrop);
		appendQueued(result, minecraft.options.keyChat);
		appendQueued(result, minecraft.options.keyCommand);
		appendQueued(result, minecraft.options.keyAttack);
		appendQueued(result, minecraft.options.keyUse);
		appendQueued(result, minecraft.options.keyPickItem);
		appendQueued(result, minecraft.options.keySpectatorHotbar);
		for (KeyMapping mapping : minecraft.options.keyHotbarSlots) {
			appendQueued(result, mapping);
		}
		return result.isEmpty() ? "none" : result.toString();
	}

	private static void appendQueued(final StringBuilder result, final KeyMapping mapping) {
		int clicks = queuedClicks(mapping);
		if (clicks <= 0) {
			return;
		}
		if (!result.isEmpty()) {
			result.append(',');
		}
		result.append(mapping.getName()).append('x').append(clicks);
	}

	private static String unavailableReason(final Minecraft minecraft) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return "inventory-tweaks-unavailable";
		}
		if (!ConfigStore.get().superFastInventory) {
			return "disabled-by-config";
		}
		if (minecraft.getOverlay() != null) {
			return "overlay-open";
		}
		if (minecraft.screen != null) {
			return "screen-open:" + minecraft.screen.getClass().getName();
		}
		if (minecraft.player == null) {
			return "player-unavailable";
		}
		if (minecraft.gameMode == null) {
			return "game-mode-unavailable";
		}
		if (minecraft.gameMode.isServerControlledInventory()) {
			return "server-controlled-inventory";
		}
		return null;
	}

	private static void finishDecision(final String decision, final String reason) {
		lastDecision = decision;
		lastDecisionReason = reason;
		lastDecisionNanos = System.nanoTime();
	}

	private static int queuedClicks(final KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).kohsInventoryTweaks$getClickCount();
	}

	private static long nanosToMicros(final long duration, final long origin) {
		return origin == 0L ? -1L : Math.max(0L, duration / 1_000L);
	}
}
