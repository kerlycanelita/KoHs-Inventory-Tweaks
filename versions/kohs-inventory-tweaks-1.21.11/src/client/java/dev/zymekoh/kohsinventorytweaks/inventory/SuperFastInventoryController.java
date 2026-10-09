package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractRecipeBookScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.KeyMappingAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.MinecraftAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.MouseHandlerAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.RecipeBookComponentAccessor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * Opens the ordinary local player inventory as soon as its key arrives, instead
 * of at the next client tick.
 *
 * <p>GLFW hands every keyboard and mouse event of a frame to the callbacks inside
 * one poll. The callbacks only record what they see ({@link #onKeyboardEvent},
 * {@link #onMouseButton}); the decision is taken once the whole batch is in
 * ({@link #afterInputPoll}), so it knows everything the player pressed in that
 * frame. The opening itself is Vanilla's: the same screen, built from the same
 * queued click, with the same tutorial hook.</p>
 *
 * <p>It opens early only when the tick-time opening would have done exactly the
 * same thing. Whenever that pass would still act on another click first, or the
 * opening would change what happens to one, the whole batch is left to Vanilla
 * untouched: {@link VanillaKeyOrder} says which clicks those are on this version.</p>
 *
 * <p>Presses are counted rather than queued. A run this controller watched arrive
 * while no screen existed carries one meaning per press -- open, close, open -- so
 * an even run has already finished and settles without building anything, where
 * Vanilla's open-only drain would have ended open. Any click it did not watch
 * arrive makes the run uncountable, and the whole queue goes back.</p>
 */
public final class SuperFastInventoryController {
	private static final String EARLY_OPEN = "early-open";
	private static final String EARLY_CANCEL = "early-cancel";
	private static final String FALLBACK = "vanilla-fallback";
	/** Trailing repeat marker appended by the queued-action report, as in {@code key.attackx2}. */
	private static final Pattern REPEAT_COUNT = Pattern.compile("x\\d+$");
	/**
	 * Shortest interval in which a hand can deliver two deliberate presses.
	 *
	 * <p>Contact bounce lands one to ten milliseconds apart; the quickest human
	 * double tap is around fifty, and forty for a practised one. Twenty-five sits
	 * between the two, and erring high only ever sends a press to Vanilla.</p>
	 */
	private static final long HUMAN_DOUBLE_TAP_FLOOR_NANOS = 25_000_000L;

	/** What the callbacks saw during the current poll. */
	private static final PollBatch BATCH = new PollBatch();
	private static long previousPollNanos;
	/** Inventory clicks this controller watched enter Vanilla's queue and left there. */
	private static int deferredClicks;
	/** Fresh physical presses retained alongside the deferred Vanilla clicks. */
	private static int deferredIntents;
	private static Decision lastDecision = new Decision("idle", "not-evaluated", 0L, 0L);

	private SuperFastInventoryController() {
	}

	/** One poll's physical input, filled by the callbacks and drained after the poll. */
	private static final class PollBatch {
		private boolean observed;
		private int inventoryPresses;
		private long firstInputNanos;
		private long inventoryInputNanos;
		private final Set<String> conflicts = new LinkedHashSet<>();

		private void observe(final long now) {
			if (!this.observed) {
				this.firstInputNanos = now;
			}
			this.observed = true;
		}

		private void inventoryPress(final long now) {
			this.inventoryInputNanos = now;
			this.inventoryPresses++;
		}

		private Polled drain() {
			Polled polled = new Polled(this.inventoryPresses, this.inventoryInputNanos != 0L, String.join(",", this.conflicts));
			this.observed = false;
			this.inventoryPresses = 0;
			this.firstInputNanos = 0L;
			this.inventoryInputNanos = 0L;
			this.conflicts.clear();
			return polled;
		}
	}

	/** A drained poll. {@code conflicts} is empty when no other action shared it. */
	private record Polled(int inventoryPresses, boolean inventoryPressed, String conflicts) {
	}

	private record Decision(String outcome, String reason, long nanos, long revision) {
	}

	/** No debounce timer: a fresh press always keeps Vanilla's immediate path. */
	public static boolean suppressInventoryKeyRepeat(
		final Minecraft minecraft, final long windowHandle, final int action, final KeyEvent event
	) {
		if (action != GLFW.GLFW_REPEAT || minecraft == null
			|| windowHandle != minecraft.getWindow().handle()
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

	/** Records a keyboard press of the current poll. */
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
		BATCH.observe(now);
		if (minecraft.options.keyInventory.matches(event)) {
			BATCH.inventoryPress(now);
		}
		recordConflicts(minecraft, mapping -> mapping.matches(event));
	}

	/** Records a mouse-button press of the current poll. */
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
		BATCH.observe(now);
		if (minecraft.options.keyInventory.matchesMouse(event)) {
			BATCH.inventoryPress(now);
		}
		recordConflicts(minecraft, mapping -> mapping.matchesMouse(event));
	}

	/** Called once after GLFW has delivered every event of this rendered frame. */
	public static void afterInputPoll(final Minecraft minecraft) {
		// Measured once per frame, including frames carrying no input, because it is
		// the only available bound on how far apart two presses inside one batch can
		// physically be: GLFW hands the whole queued burst to the callbacks from within
		// a single pollEvents, so their own timestamps are all but identical.
		long now = System.nanoTime();
		long batchSpanNanos = previousPollNanos == 0L ? Long.MAX_VALUE : now - previousPollNanos;
		previousPollNanos = now;
		// What was done in an inventory opened ahead of its tick is handed over here, where
		// Vanilla handles input, once that tick has run.
		InputFence.afterInputPoll(minecraft);
		if (!BATCH.observed) {
			return;
		}
		Polled polled = BATCH.drain();
		if (minecraft == null || queuedClicks(minecraft.options.keyInventory) == 0) {
			clearDeferred();
			return;
		}
		// A queued click pressed in an earlier batch was decided by that batch. Frames
		// run far faster than the 20 TPS tick that consumes the queue, so a press handed
		// to Vanilla is still there several batches later; deciding it again would take
		// it back and strand whatever it was handed over for. Only the batch that
		// contains the press decides it.
		if (!polled.inventoryPressed()) {
			return;
		}
		decide(minecraft, polled, batchSpanNanos);
	}

	/**
	 * The decision for a batch that contains an inventory press. Every fallback
	 * returns before anything touches Vanilla's queue, so it finds the queue exactly
	 * as the player left it.
	 */
	private static void decide(final Minecraft minecraft, final Polled polled, final long batchSpanNanos) {
		int queued = queuedClicks(minecraft.options.keyInventory);
		// Contact bounce reaches GLFW as repeated PRESS, never as GLFW_REPEAT. A batch
		// shorter than a hand can tap twice cannot hold two intentions: one physical
		// press counted as two would settle into nothing and the key would do nothing.
		boolean bounceCollapsed = polled.inventoryPresses() > 1 && batchSpanNanos <= HUMAN_DOUBLE_TAP_FLOOR_NANOS;
		int batchIntents = bounceCollapsed ? 1 : polled.inventoryPresses();
		// Nothing can still be owed when the queue holds no more than this batch put
		// there, so a tally that survived a tick is stale and says nothing.
		if (queued <= polled.inventoryPresses()) {
			clearDeferred();
		}
		int owned = Math.min(deferredClicks + polled.inventoryPresses(), queued);
		int intents = deferredIntents + batchIntents;

		// Another action pressed in the same frame: Vanilla orders the two, not us.
		if (!polled.conflicts().isEmpty()) {
			clearDeferred();
			fallback("physical-conflict:" + polled.conflicts());
			return;
		}
		// A queued click this controller never watched arrive carries an intention it
		// cannot count -- a screen declined the key and queued one of its own -- so the
		// whole queue goes back to Vanilla rather than being guessed at.
		if (queued > owned) {
			clearDeferred();
			fallback("unowned-inventory-clicks");
			return;
		}
		boolean opensScreen = intents % 2 == 1;
		String unavailable = unavailableReason(minecraft, opensScreen);
		if (unavailable != null) {
			defer(owned, intents);
			fallback(unavailable);
			return;
		}
		if (!opensScreen) {
			settlePair(minecraft, owned);
			return;
		}
		String pending = pendingVanillaActions(minecraft);
		if (!pending.isEmpty()) {
			defer(owned, intents);
			fallback("queued-conflict:" + pending);
			return;
		}
		// From here the opening is committed.
		if (drainInventoryClicks(minecraft, owned) == 0) {
			fallback("inventory-click-already-consumed");
			return;
		}
		open(minecraft);
		finish(EARLY_OPEN, bounceCollapsed ? "collapsed-contact-bounce"
			: intents == 1 ? "sole-inventory-input" : "settled-press-run");
	}

	/**
	 * An even run is an open and a close the player has already finished. Vanilla
	 * cannot answer that: {@code handleKeybinds} drains the queue in a loop that only
	 * ever opens, and the closing half lives on a screen that was never built, so it
	 * ends open however many times the key was pressed. Settling it takes the clicks,
	 * builds nothing, and leaves the world as the player left it.
	 */
	private static void settlePair(final Minecraft minecraft, final int owned) {
		// Cancelling assumes every meaning in the run belongs to the inventory; a second
		// mapping on the same key owns half of it, and taking the clicks would answer its
		// half too. Opening has no such claim, so only this branch checks the binding.
		if (hasSharedInventoryBinding(minecraft)) {
			clearDeferred();
			fallback("shared-inventory-binding");
			return;
		}
		drainInventoryClicks(minecraft, owned);
		finish(EARLY_CANCEL, "open-and-close");
	}

	/** Vanilla's own opening, a tick early. */
	private static void open(final Minecraft minecraft) {
		minecraft.getTutorial().onOpenInventory();
		minecraft.setScreen(new InventoryScreen(minecraft.player));
		discardPreOpenWorldMovement(minecraft);
		// A tick-time opening goes on to call continueAttack(false) in the same pass, and
		// with no block being broken its only effect is clearing the miss penalty.
		((MinecraftAccessor) minecraft).kohsInventoryTweaks$setMissTime(0);
		// The server learns that the keys were released in the coming tick; until then
		// nothing done in this screen may reach it.
		InputFence.raise(minecraft);
	}

	/**
	 * Why an opening cannot happen now, or null.
	 *
	 * <p>A tick-time opening ends a sustained world action in the same
	 * {@code handleKeybinds} pass: {@code releaseAll} lifts use and attack, and the
	 * lines after the inventory release the item in use and abort block breaking. An
	 * opening made here skips that pass until the screen closes, which would keep a
	 * shield raised, a bow drawn or food being eaten behind the inventory, so those
	 * openings wait. Any other held button drives nothing that outlives the opening:
	 * a fresh screen starts with {@code skipNextRelease} and no clicked slot, so the
	 * inherited release cannot become an inventory click or drag.</p>
	 */
	private static String unavailableReason(final Minecraft minecraft, final boolean opensScreen) {
		if (!minecraft.isWindowActive() || minecraft.getWindow().isMinimized()) {
			return "window-not-active";
		}
		if (opensScreen && minecraft.player != null && minecraft.player.isUsingItem()) {
			return "item-in-use";
		}
		if (opensScreen && minecraft.gameMode != null && minecraft.gameMode.isDestroying()) {
			return "block-breaking";
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

	/**
	 * Clicks queued in this tick that the tick-time opening would still act on,
	 * as {@code key.namexN}, or empty. See {@link VanillaKeyOrder}.
	 */
	private static String pendingVanillaActions(final Minecraft minecraft) {
		StringBuilder result = new StringBuilder();
		for (KeyMapping mapping : VanillaKeyOrder.beforeInventory(minecraft.options)) {
			appendQueued(result, mapping);
		}
		for (KeyMapping mapping : VanillaKeyOrder.stillRunAfterOpening(minecraft.options)) {
			appendQueued(result, mapping);
		}
		return result.toString();
	}

	private static void recordConflicts(final Minecraft minecraft, final Predicate<KeyMapping> matches) {
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != minecraft.options.keyInventory
				&& mapping.getCategory() != KeyMapping.Category.MOVEMENT
				&& !VanillaKeyOrder.droppedByOpening(minecraft.options, mapping)
				&& matches.test(mapping)) {
				BATCH.conflicts.add(mapping.getName());
			}
		}
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
		clearDeferred();
		return taken;
	}

	private static void defer(final int clicks, final int intents) {
		deferredClicks = clicks;
		deferredIntents = intents;
	}

	private static void clearDeferred() {
		deferredClicks = 0;
		deferredIntents = 0;
	}

	/** Whether the last inventory press opened before the next client tick. */
	public static boolean lastOpenWasImmediate() {
		return EARLY_OPEN.equals(lastDecision.outcome());
	}

	/** Whether the last run of presses was an open and a close settled without opening. */
	public static boolean lastPressSettledAPair() {
		return EARLY_CANCEL.equals(lastDecision.outcome());
	}

	/** Whether the last press fell back because other input shared its batch or tick. */
	public static boolean lastOpenHadInputConflict() {
		return lastDecision.reason().startsWith("physical-conflict:")
			|| lastDecision.reason().startsWith("queued-conflict:");
	}

	/**
	 * The mappings that shared the batch, as their own translation keys.
	 *
	 * <p>A player whose binds overlap takes the slow path on every open and has
	 * nothing on screen to say so; naming the mappings turns that into something
	 * they can act on.</p>
	 */
	public static List<String> lastConflictMappings() {
		int separator = lastDecision.reason().indexOf(':');
		if (!lastOpenHadInputConflict() || separator < 0) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (String entry : lastDecision.reason().substring(separator + 1).split(",")) {
			String name = REPEAT_COUNT.matcher(entry).replaceFirst("").trim();
			if (!name.isEmpty() && !names.contains(name)) {
				names.add(name);
			}
		}
		return List.copyOf(names);
	}

	/**
	 * The category of the last fallback, empty while no press has been decided or
	 * the last one opened early. The detail suffix is dropped so the screen can name
	 * every reason.
	 */
	public static String lastFallbackCode() {
		if (!FALLBACK.equals(lastDecision.outcome())) {
			return "";
		}
		String reason = lastDecision.reason();
		int separator = reason.indexOf(':');
		return separator < 0 ? reason : reason.substring(0, separator);
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String pendingInputSnapshot() {
		long now = System.nanoTime();
		return "observed=" + BATCH.observed
			+ "; inventoryPresses=" + BATCH.inventoryPresses
			+ "; conflict=" + !BATCH.conflicts.isEmpty()
			+ "; conflictMappings=" + (BATCH.conflicts.isEmpty() ? "none" : String.join(",", BATCH.conflicts))
			+ "; firstInputAge=" + nanosToMicros(now - BATCH.firstInputNanos, BATCH.firstInputNanos) + "us"
			+ "; inventoryInputAge=" + nanosToMicros(now - BATCH.inventoryInputNanos, BATCH.inventoryInputNanos) + "us";
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String lastDecisionSnapshot() {
		long now = System.nanoTime();
		return "decision=" + lastDecision.outcome()
			+ "; revision=" + lastDecision.revision()
			+ "; reason=" + lastDecision.reason()
			+ "; age=" + nanosToMicros(now - lastDecision.nanos(), lastDecision.nanos()) + "us";
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

	private static boolean hasSharedInventoryBinding(final Minecraft minecraft) {
		String inventoryKey = minecraft.options.keyInventory.saveString();
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping != minecraft.options.keyInventory && mapping.saveString().equals(inventoryKey)) return true;
		}
		return false;
	}

	/**
	 * Avoids scanning every modded key mapping for clicks that cannot possibly open
	 * the player inventory. In particular, slot and offhand input inside an open GUI
	 * stays entirely on Vanilla's path.
	 */
	private static boolean canObserveFastOpen(final Minecraft minecraft) {
		return minecraft.screen == null
			&& minecraft.getOverlay() == null
			&& ConfigStore.get().superFastInventory
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	/**
	 * A fast opening happens before {@code handleAccumulatedMovement}. The deltas
	 * already present at that boundary were sampled while the mouse turned the camera;
	 * forwarding them to the new screen could synthesize a hover or drag at the
	 * landing point. Only that inherited pair is cleared; movement from the next poll
	 * reaches the inventory immediately.
	 */
	private static void discardPreOpenWorldMovement(final Minecraft minecraft) {
		MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
		mouse.kohsInventoryTweaks$setAccumulatedDX(0.0);
		mouse.kohsInventoryTweaks$setAccumulatedDY(0.0);
	}

	private static void fallback(final String reason) {
		finish(FALLBACK, reason);
	}

	private static void finish(final String outcome, final String reason) {
		lastDecision = new Decision(outcome, reason, System.nanoTime(), lastDecision.revision() + 1);
	}

	private static int queuedClicks(final KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).kohsInventoryTweaks$getClickCount();
	}

	private static long nanosToMicros(final long duration, final long origin) {
		return origin == 0L ? -1L : Math.max(0L, duration / 1_000L);
	}
}
