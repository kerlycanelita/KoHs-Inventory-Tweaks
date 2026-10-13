package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Steady inventory: on a server with ping, the slots the player has just clicked keep showing what
 * those clicks did while the clicks are still on their way, instead of flashing back.
 *
 * <p>The client applies each click at once and sends it with the last state number the server gave
 * it. In a fight the server keeps changing the inventory (armor loses durability on every hit, each
 * change a new state number), so with ping almost every click arrives carrying an old number and
 * the server answers it with the whole inventory as it is after that click. When a second click
 * follows within the ping, the answer to the first one arrives after the second was already shown:
 * Vanilla puts the first answer on screen and the second click's result disappears until its own
 * answer arrives. A player who trusts that presses again, and the second press undoes the first.</p>
 *
 * <p>An answer that arrives while later clicks are still in flight is put on screen everywhere
 * except on the slots those later clicks changed: there the screen keeps the player's own result,
 * and the server's stack for the slot is kept aside. The answer to the last click goes on screen
 * whole. What was kept aside is put on screen when the clicks stay unanswered for as long as an
 * answer takes, and the moment the screen closes, so the game never acts on it outside the
 * inventory. A slot the server updates on its own (a totem that pops, an item picked up) shows at
 * once and is no longer kept. Nothing the client sends changes: the server decides every click, and
 * its last word is what stays on screen.</p>
 */
public final class SteadyInventory {
	private static final long MIN_WINDOW_NANOS = 80_000_000L;
	private static final long MAX_WINDOW_NANOS = 500_000_000L;
	/** Room for the server's tick and the network's jitter on top of the round trip. */
	private static final long MARGIN_NANOS = 60_000_000L;
	private static final long MAX_SAMPLE_NANOS = 1_000_000_000L;

	/** A click sent and not answered yet: when it left and what it changed on screen. */
	private record Click(long sentNanos, int[] slots, boolean carried) {
	}

	/** What Vanilla is to put on a menu for one whole-inventory answer. */
	public record Answer(List<ItemStack> items, ItemStack carried) {
	}

	private static final ArrayDeque<Click> PENDING = new ArrayDeque<>();
	/** The menu the pending clicks went to. */
	private static @Nullable AbstractContainerMenu menu;
	private static long lastClickNanos;
	/** Smoothed time from a click to the whole-inventory answer to it, or 0 before any. */
	private static long roundTripNanos;
	/** The server's stacks for the slots the screen is keeping the player's own result on. */
	private static final Int2ObjectMap<ItemStack> KEPT = new Int2ObjectOpenHashMap<>();
	private static @Nullable ItemStack keptCarried;

	private SteadyInventory() {
	}

	private static boolean active() {
		return ConfigStore.get().steadyInventory
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	/** A container click is about to leave; the menu already shows its result. */
	public static void onClickSent(final Minecraft minecraft, final ServerboundContainerClickPacket click) {
		AbstractContainerMenu clicked = minecraft.player == null ? null : minecraft.player.containerMenu;
		long now = System.nanoTime();
		if (clicked != menu || !inFlight(now) || !active() || !shown(minecraft, clicked)) {
			release();
			PENDING.clear();
			menu = null;
		}
		if (clicked == null || !active() || !shown(minecraft, clicked)) {
			return;
		}
		menu = clicked;
		lastClickNanos = now;
		int[] slots = click.changedSlots().keySet().toIntArray();
		ContainerInput input = click.containerInput();
		// Swaps, shift-clicks and drops from a slot never touch the stack on the pointer.
		boolean carried = input != ContainerInput.SWAP && input != ContainerInput.QUICK_MOVE && input != ContainerInput.THROW;
		PENDING.add(new Click(now, slots, carried));
		// What this click just changed is newer than anything kept aside for those slots.
		for (int slot : slots) {
			KEPT.remove(slot);
		}
		if (carried) {
			keptCarried = null;
		}
	}

	/**
	 * A whole-inventory answer is about to go on {@code target}: what Vanilla should put there now.
	 * The slots that clicks still in flight changed keep what they show.
	 */
	public static Answer onAnswer(final Minecraft minecraft, final AbstractContainerMenu target, final List<ItemStack> items, final ItemStack carried) {
		if (target != menu || PENDING.isEmpty()) {
			// Not an answer to these clicks. Whatever is kept aside is older: it goes first.
			release();
			return new Answer(items, carried);
		}
		long now = System.nanoTime();
		long sample = Math.min(now - PENDING.poll().sentNanos(), MAX_SAMPLE_NANOS);
		roundTripNanos = roundTripNanos == 0L ? sample : (roundTripNanos * 3 + sample) / 4;
		// This answer is newer than everything kept aside.
		KEPT.clear();
		keptCarried = null;
		if (PENDING.isEmpty() || !active() || !shown(minecraft, target) || !inFlight(now)) {
			PENDING.clear();
			return new Answer(items, carried);
		}
		List<ItemStack> visible = new ArrayList<>(items);
		for (Click click : PENDING) {
			for (int slot : click.slots()) {
				if (slot >= 0 && slot < visible.size() && slot < target.slots.size() && !KEPT.containsKey(slot)) {
					KEPT.put(slot, items.get(slot));
					visible.set(slot, target.getSlot(slot).getItem());
				}
			}
			if (click.carried() && keptCarried == null) {
				keptCarried = carried;
			}
		}
		return new Answer(visible, keptCarried == null ? carried : target.getCarried());
	}

	/** The server updated one slot on its own: it shows at once and is no longer kept. */
	public static void onServerSlot(final Container container, final int containerSlot) {
		if (menu == null || KEPT.isEmpty()) {
			return;
		}
		KEPT.int2ObjectEntrySet().removeIf(entry -> {
			int slot = entry.getIntKey();
			return slot >= menu.slots.size()
				|| menu.getSlot(slot).container == container && menu.getSlot(slot).getContainerSlot() == containerSlot;
		});
	}

	/** The server set the stack on the pointer. */
	public static void onServerCarried() {
		keptCarried = null;
	}

	/** Every frame: clicks left unanswered for as long as an answer takes are waited for no longer. */
	public static void update(final Minecraft minecraft) {
		if (menu == null) {
			return;
		}
		long now = System.nanoTime();
		if (holding() && (!inFlight(now) || !active() || !shown(minecraft, menu))) {
			release();
		}
		// The send times stay a while longer: a late answer still tells how long answers take.
		if (!holding() && now - lastClickNanos > MAX_SAMPLE_NANOS) {
			PENDING.clear();
			menu = null;
		}
	}

	/** A container screen is closing: its menu shows the server's word from here on. */
	public static void onScreenRemoved(final AbstractContainerScreen<?> screen) {
		if (screen.getMenu() == menu) {
			release();
		}
	}

	/** Whether the screen is keeping the player's own result on some slot right now. Read-only, for KoHs Inventory Debug. */
	public static boolean holding() {
		return !KEPT.isEmpty() || keptCarried != null;
	}

	/** Puts on screen what was kept aside. */
	private static void release() {
		if (menu != null) {
			for (Int2ObjectMap.Entry<ItemStack> entry : KEPT.int2ObjectEntrySet()) {
				if (entry.getIntKey() < menu.slots.size()) {
					menu.getSlot(entry.getIntKey()).set(entry.getValue());
				}
			}
			if (keptCarried != null) {
				menu.setCarried(keptCarried);
			}
		}
		KEPT.clear();
		keptCarried = null;
	}

	private static boolean shown(final Minecraft minecraft, final @Nullable AbstractContainerMenu target) {
		return minecraft.screen instanceof AbstractContainerScreen<?> screen && screen.getMenu() == target
			&& !(screen instanceof CreativeModeInventoryScreen);
	}

	private static boolean inFlight(final long now) {
		return lastClickNanos != 0L && now - lastClickNanos < window();
	}

	/** How long after the last click its answer can still be on its way. */
	private static long window() {
		long roundTrip = roundTripNanos;
		Minecraft minecraft = Minecraft.getInstance();
		if (roundTrip == 0L && minecraft.player != null && minecraft.getConnection() != null) {
			PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
			if (info != null) {
				roundTrip = info.getLatency() * 1_000_000L;
			}
		}
		return Math.clamp(roundTrip + roundTrip / 4 + MARGIN_NANOS, MIN_WINDOW_NANOS, MAX_WINDOW_NANOS);
	}
}
