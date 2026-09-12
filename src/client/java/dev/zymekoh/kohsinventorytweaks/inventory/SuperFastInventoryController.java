package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.KeyMappingAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.MouseHandlerAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractRecipeBookScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.RecipeBookComponentAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Advances only construction of the ordinary local player inventory.
 *
 * <p>GLFW delivers keyboard and mouse callbacks as one display-update batch.
 * This controller records that complete batch and makes its decision immediately
 * after {@code RenderSystem.pollEvents()} returns. A sole queued Inventory press can then
 * open on the next rendered frame instead of waiting for the next 20 TPS client
 * tick. Any overlapping non-movement mapping leaves the complete Vanilla queue
 * untouched.</p>
 *
 * <p>Presses are counted rather than queued. A run this controller watched arrive
 * while no screen existed carries one meaning per press -- open, close, open --
 * so an even run has already finished and settles without building anything,
 * where Vanilla's open-only drain would have ended open. Any click it did not
 * watch arrive makes the run uncountable and the whole queue goes back.</p>
 */
public final class SuperFastInventoryController {
	/** Trailing repeat marker appended by the queued-action report, as in `key.attackx2`. */
	private static final Pattern REPEAT_COUNT = Pattern.compile("x\\d+$");
	/** Inventory clicks this controller watched enter Vanilla's queue and left there. */
	private static int deferredInventoryClicks;
	/** Fresh physical presses retained alongside the deferred Vanilla clicks. */
	private static int deferredInventoryIntents;
	private static boolean physicalInputObserved;
	private static boolean conflictingPhysicalInputObserved;
	private static String conflictingPhysicalMappings = "none";
	private static long firstPhysicalInputNanos;
	private static long inventoryPhysicalInputNanos;
	private static int inventoryPressesThisPoll;
	private static String lastDecision = "idle";
	private static String lastDecisionReason = "not-evaluated";
	private static long lastDecisionNanos;
	private static long decisionRevision;

	private SuperFastInventoryController() {
	}

	/** No debounce timer: a fresh press always retains Vanilla's immediate path. */
	public static boolean suppressInventoryKeyRepeat(
		final Minecraft minecraft, final long windowHandle, final int action, final KeyEvent event
	) {
		if (action != GLFW.GLFW_REPEAT || minecraft == null
			|| windowHandle != minecraft.getWindow().handle()
			|| !ConfigStore.get().suppressInventoryKeyRepeats
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)
			|| !minecraft.options.keyInventory.matches(event)
			|| minecraft.player == null || minecraft.gameMode == null
			|| minecraft.gameMode.isServerControlledInventory()
			|| minecraft.getOverlay() != null
			|| (minecraft.screen != null && !(minecraft.screen instanceof InventoryScreen))
			|| event.isEscape() || minecraft.options.keyDebugModifier.isDown()) {
			return false;
		}
		// Do not suppress the repeat of another action that shares this key.
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != minecraft.options.keyInventory && mapping.matches(event)) return false;
		}
		if (minecraft.screen != null) {
			if (minecraft.screen.getFocused() instanceof EditBox edit && edit.canConsumeInput()) return false;
			var book = ((AbstractRecipeBookScreenAccessor) minecraft.screen).kohsInventoryTweaks$getRecipeBookComponent();
			EditBox search = ((RecipeBookComponentAccessor) book).kohsInventoryTweaks$getSearchBox();
			if (book.isVisible() && search != null && search.canConsumeInput()) return false;
		}
		return true;
	}

	public static void onKeyboardEvent(
		final Minecraft minecraft,
		final long windowHandle,
		final int action,
		final KeyEvent event
	) {
		if (minecraft == null
			|| action != GLFW.GLFW_PRESS
			|| windowHandle != minecraft.getWindow().handle()
			|| !canObserveFastOpen(minecraft)) {
			return;
		}

		long now = System.nanoTime();
		observePhysicalInput(now);
		if (minecraft.options.keyInventory.matches(event)) {
			inventoryPhysicalInputNanos = now;
			inventoryPressesThisPoll++;
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
			|| buttonInfo == null
			|| !canObserveFastOpen(minecraft)) {
			return;
		}

		long now = System.nanoTime();
		MouseButtonEvent event = new MouseButtonEvent(0.0, 0.0, buttonInfo);
		observePhysicalInput(now);
		if (minecraft.options.keyInventory.matchesMouse(event)) {
			inventoryPhysicalInputNanos = now;
			inventoryPressesThisPoll++;
		}
		appendPhysicalConflicts(matchingNonMovementMappings(
			minecraft,
			mapping -> mapping.matchesMouse(event)
		));
	}

	/** Called once after GLFW has delivered every event in this rendered frame. */
	public static void afterInputPoll(final Minecraft minecraft) {
		if (!physicalInputObserved) {
			return;
		}

		boolean physicalConflict = conflictingPhysicalInputObserved;
		int inventoryPresses = inventoryPressesThisPoll;
		inventoryPressesThisPoll = 0;
		String physicalConflictMappings = conflictingPhysicalMappings;
		physicalInputObserved = false;
		conflictingPhysicalInputObserved = false;
		conflictingPhysicalMappings = "none";
		firstPhysicalInputNanos = 0L;

		if (minecraft == null || queuedClicks(minecraft.options.keyInventory) == 0) {
			inventoryPhysicalInputNanos = 0L;
			deferredInventoryClicks = 0;
			deferredInventoryIntents = 0;
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

		inventoryPhysicalInputNanos = 0L;
		int queuedInventoryClicks = queuedClicks(minecraft.options.keyInventory);

		// GLFW_REPEAT is handled separately. A batch duration cannot identify switch
		// bounce: each fresh PRESS must count, even when two presses share a fast frame.
		int batchIntents = inventoryPresses;

		// Nothing can still be owed when the queue holds no more than this batch put
		// there, so a tally that survived a tick is stale and says nothing.
		if (queuedInventoryClicks <= inventoryPresses) {
			deferredInventoryClicks = 0;
			deferredInventoryIntents = 0;
		}
		int ownedClicks = Math.min(deferredInventoryClicks + inventoryPresses, queuedInventoryClicks);
		int intents = deferredInventoryIntents + batchIntents;

		if (physicalConflict) {
			deferredInventoryClicks = 0;
			deferredInventoryIntents = 0;
			finishDecision("vanilla-fallback", "physical-conflict:" + physicalConflictMappings);
			return;
		}

		// A queued click this controller never watched arrive carries an intention it
		// cannot count: a screen that declined the key queued one of its own, or the
		// binding is shared and another mapping owns half the meaning. Neither is a
		// queue to reclaim, so the whole thing goes back to Vanilla and the tally is
		// abandoned rather than guessed at.
		if (queuedInventoryClicks > ownedClicks || hasSharedInventoryBinding(minecraft)) {
			deferredInventoryClicks = 0;
			deferredInventoryIntents = 0;
			finishDecision("vanilla-fallback", "unowned-inventory-clicks");
			return;
		}

		boolean opensScreen = intents % 2 == 1;
		String unavailableReason = unavailableReason(minecraft, opensScreen);
		if (unavailableReason != null) {
			deferredInventoryClicks = ownedClicks;
			deferredInventoryIntents = intents;
			finishDecision("vanilla-fallback", unavailableReason);
			return;
		}

		// An even run is an open and a close the player has already finished. Vanilla
		// cannot answer that: `handleKeybinds` drains the queue in a loop that only
		// ever opens, and the closing half lives on a screen that was never built, so
		// it ends open however many times the key was pressed. Settling it here takes
		// the clicks, builds nothing, and leaves the world as the player left it --
		// and settles it now rather than at a tick that would get it wrong. No screen
		// appears, so no queued action of any other mapping can be stranded by it.
		if (!opensScreen) {
			drainInventoryClicks(minecraft, ownedClicks);
			finishDecision("early-cancel", "open-and-close");
			return;
		}

		String queuedConflict = queuedVanillaActions(minecraft);
		if (!queuedConflict.equals("none")) {
			deferredInventoryClicks = ownedClicks;
			deferredInventoryIntents = intents;
			finishDecision("vanilla-fallback", "queued-conflict:" + queuedConflict);
			return;
		}

		// Nothing above this line touches Vanilla's queue, so every fallback leaves it
		// exactly as it was found. From here the opening is committed.
		if (drainInventoryClicks(minecraft, ownedClicks) == 0) {
			finishDecision("vanilla-fallback", "inventory-click-already-consumed");
			return;
		}
		minecraft.getTutorial().onOpenInventory();
		minecraft.setScreen(new InventoryScreen(minecraft.player));
		discardPreOpenWorldMovement(minecraft);
		String openReason = intents == 1 ? "sole-inventory-input" : "settled-press-run";
		finishDecision("early-open", openReason);
	}

	/**
	 * Takes exactly the clicks this controller owns and reports how many it got.
	 *
	 * <p>The tally is cleared either way: once the queue has been touched there is
	 * nothing left to defer, and a short count means Vanilla drained it underneath
	 * us, which is equally a reason to stop tracking it.</p>
	 */
	private static int drainInventoryClicks(final Minecraft minecraft, final int clicks) {
		int taken = 0;
		while (taken < clicks && minecraft.options.keyInventory.consumeClick()) {
			taken++;
		}
		deferredInventoryClicks = 0;
		deferredInventoryIntents = 0;
		return taken;
	}

	/** Whether the last inventory press opened before the next client tick. */
	public static boolean lastOpenWasImmediate() {
		return "early-open".equals(lastDecision);
	}

	/** Whether the last run of presses was an open and a close settled without opening. */
	public static boolean lastPressSettledAPair() {
		return "early-cancel".equals(lastDecision);
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

	/**
	 * The category of the last fallback, empty while no press has been decided or
	 * the last one opened early.
	 *
	 * <p>A player only ever sees the acceleration fail; the readout that explains
	 * it covered the two conflict reasons and silently showed nothing for the
	 * rest, including the held mouse button that is the common one in a fight.
	 * This drops the detail suffix so the screen can name every reason.</p>
	 */
	public static String lastFallbackCode() {
		if (!"vanilla-fallback".equals(lastDecision)) {
			return "";
		}
		int separator = lastDecisionReason.indexOf(':');
		return separator < 0 ? lastDecisionReason : lastDecisionReason.substring(0, separator);
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String pendingInputSnapshot() {
		long now = System.nanoTime();
		return "observed=" + physicalInputObserved
			+ "; inventoryPresses=" + inventoryPressesThisPoll
			+ "; conflict=" + conflictingPhysicalInputObserved
			+ "; conflictMappings=" + conflictingPhysicalMappings
			+ "; firstInputAge=" + nanosToMicros(now - firstPhysicalInputNanos, firstPhysicalInputNanos) + "us"
			+ "; inventoryInputAge=" + nanosToMicros(now - inventoryPhysicalInputNanos, inventoryPhysicalInputNanos) + "us";
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String lastDecisionSnapshot() {
		long now = System.nanoTime();
		return "decision=" + lastDecision
			+ "; revision=" + decisionRevision
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
		StringBuilder result = null;
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != minecraft.options.keyInventory
				&& mapping.getCategory() != KeyMapping.Category.MOVEMENT
				&& matches.test(mapping)) {
				if (result == null) result = new StringBuilder();
				if (!result.isEmpty()) {
					result.append(',');
				}
				result.append(mapping.getName());
			}
		}
		return result == null ? "none" : result.toString();
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

	private static String unavailableReason(final Minecraft minecraft, final boolean opensScreen) {
		if (!minecraft.isWindowActive() || !minecraft.getWindow().isFocused() || minecraft.getWindow().isMinimized()) {
			return "window-not-active";
		}
		// The optional held-button path still rejects queued attacks/use actions below.
		// A fresh InventoryScreen starts with skipNextRelease=true and no clickedSlot,
		// so the inherited release cannot become a new inventory click or drag.
		if (opensScreen
			&& !ConfigStore.get().fastInventoryWhileMouseHeld
			&& ((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$getActiveButton() != null) {
			return "mouse-button-held";
		}
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

	private static boolean hasSharedInventoryBinding(final Minecraft minecraft) {
		String inventoryKey = minecraft.options.keyInventory.saveString();
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != minecraft.options.keyInventory && mapping.saveString().equals(inventoryKey)) return true;
		}
		return false;
	}

	/**
	 * Avoids scanning every modded key mapping for clicks that cannot possibly
	 * open the player inventory. In particular, slot/offhand input inside an open
	 * GUI must stay entirely on Vanilla's hot path.
	 */
	private static boolean canObserveFastOpen(final Minecraft minecraft) {
		return minecraft.screen == null
			&& minecraft.getOverlay() == null
			&& ConfigStore.get().superFastInventory
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	/**
	 * A fast open happens before {@code handleAccumulatedMovement}. The deltas
	 * already present at that boundary were sampled while the mouse controlled the
	 * camera; forwarding them to the newly-created screen can synthesize a hover or
	 * drag at the landing point. Clear only that inherited pair after the synchronous
	 * open. Any movement sampled from the next GLFW poll remains untouched and is
	 * delivered to the inventory immediately.
	 */
	private static void discardPreOpenWorldMovement(final Minecraft minecraft) {
		MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
		mouse.kohsInventoryTweaks$setAccumulatedDX(0.0);
		mouse.kohsInventoryTweaks$setAccumulatedDY(0.0);
	}

	private static void finishDecision(final String decision, final String reason) {
		lastDecision = decision;
		lastDecisionReason = reason;
		lastDecisionNanos = System.nanoTime();
		decisionRevision++;
	}

	private static int queuedClicks(final KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).kohsInventoryTweaks$getClickCount();
	}

	private static long nanosToMicros(final long duration, final long origin) {
		return origin == 0L ? -1L : Math.max(0L, duration / 1_000L);
	}
}
