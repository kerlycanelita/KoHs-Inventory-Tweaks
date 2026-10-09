package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.KeyboardHandlerInvoker;
import dev.zymekoh.kohsinventorytweaks.mixin.MouseHandlerAccessor;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps what the player does in an inventory opened ahead of its tick from reaching
 * the server ahead of that tick.
 *
 * <p>Vanilla opens the inventory inside a client tick. That same tick tells the server
 * the movement keys were released and the sprint ended, and only a later input poll can
 * click a slot, so a server never receives a container click from a player it still
 * believes to be sprinting forward, nor a turn of the head after the first click.
 * Super Fast Inventory builds the screen between two ticks. A click or a shortcut that
 * followed at once used to be sent at once, ahead of those packets: an order no Vanilla
 * client can produce, and one that servers check for.</p>
 *
 * <p>While the fence is up, the keys, buttons, wheel and typed characters that arrive
 * are kept, each with the pointer and the hovered slot it was made at. At the first
 * input poll after the opening tick, the earliest moment a Vanilla client could act in
 * that inventory, the first of them is handed to Minecraft's own handlers, and each one
 * after it follows as far behind it as the player made it. Whatever comes within a tick
 * of something that acted late is kept the same way, so two actions never reach the
 * server closer together than the hand made them, unless a full tick still separates
 * them. Nothing is generated, repeated or reordered, and the pointer keeps moving
 * meanwhile. Once one of them closes the inventory, the rest was made in the world and
 * goes there at once. With nothing kept when the tick has run, the fence is simply gone.</p>
 */
public final class InputFence {
	/** Far more input than a hand makes in a tick or two; past it, input is dropped, never sent early. */
	private static final int LIMIT = 64;
	/** One client tick: how long after a late action what follows still keeps its distance from it. */
	private static final long SETTLE_NANOS = 50_000_000L;

	@FunctionalInterface
	private interface Delivery {
		void to(Minecraft minecraft, long window);
	}

	/**
	 * One input: the poll it arrived in, the pointer and the hovered slot it was made at,
	 * and whether it can act on a slot, which a released key or a typed character cannot.
	 */
	private record Held(long at, double x, double y, @Nullable Slot hovered, boolean acts, Delivery delivery) {
	}

	private static final ArrayDeque<Held> HELD = new ArrayDeque<>();
	private static boolean up;
	/** The screen the next delivery expects to find; null once the player closed the fenced one. */
	private static @Nullable Screen owner;
	/** Client ticks completed, and how many had been when the fence went up. */
	private static long ticks;
	private static long raisedAt;
	/** When the first input kept in the current poll arrived; zero between polls. */
	private static long pollNanos;
	/** How late everything kept acts, set by the first of them; negative until the tick has run. */
	private static long delayNanos = -1L;
	/** Until when input is still kept after the last late action. */
	private static long settleNanos;
	private static boolean delivering;

	private InputFence() {
	}

	/** A client tick has ended: its packets, the tick-end marker included, have been sent. */
	public static void onClientTick() {
		ticks++;
	}

	/** Goes up around the screen Super Fast Inventory has just opened between two ticks. */
	static void raise(final Minecraft minecraft) {
		lower();
		owner = minecraft.screen;
		up = owner != null;
		raisedAt = ticks;
	}

	/** Whether input is being held right now. Read-only, for KoHs Inventory Debug. */
	public static boolean isUp() {
		return up;
	}

	public static boolean holdKey(final Minecraft minecraft, final long window, final int action, final KeyEvent event) {
		return hold(minecraft, window, action != GLFW.GLFW_RELEASE, (client, handle) ->
			((KeyboardHandlerInvoker) client.keyboardHandler).kohsInventoryTweaks$keyPress(handle, action, event));
	}

	public static boolean holdChar(final Minecraft minecraft, final long window, final CharacterEvent event) {
		return hold(minecraft, window, false, (client, handle) ->
			((KeyboardHandlerInvoker) client.keyboardHandler).kohsInventoryTweaks$charTyped(handle, event));
	}

	public static boolean holdButton(final Minecraft minecraft, final long window, final MouseButtonInfo button, final int action) {
		return hold(minecraft, window, true, (client, handle) ->
			((MouseHandlerAccessor) client.mouseHandler).kohsInventoryTweaks$onButton(handle, button, action));
	}

	public static boolean holdScroll(final Minecraft minecraft, final long window, final double horizontal, final double vertical) {
		return hold(minecraft, window, true, (client, handle) ->
			((MouseHandlerAccessor) client.mouseHandler).kohsInventoryTweaks$onScroll(handle, horizontal, vertical));
	}

	/** Keeps this input for later and reports that it did, so the caller can stop Vanilla handling it now. */
	private static boolean hold(final Minecraft minecraft, final long window, final boolean acts, final Delivery delivery) {
		if (!up || delivering || minecraft == null || window != minecraft.getWindow().handle()) {
			return false;
		}
		if (minecraft.screen != owner) {
			// Something other than the player's own input replaced the screen: nothing kept still applies.
			lower();
			return false;
		}
		if (pollNanos == 0L) {
			// Everything one poll brings shares its moment, as it does for Vanilla.
			pollNanos = System.nanoTime();
		}
		if (HELD.size() < LIMIT) {
			Slot hovered = owner instanceof AbstractContainerScreen<?> container
				? ((AbstractContainerScreenAccessor) container).kohsInventoryTweaks$getHoveredSlot() : null;
			HELD.add(new Held(pollNanos, minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos(), hovered, acts, delivery));
		}
		return true;
	}

	/**
	 * Runs at the end of every input poll, where Vanilla's own input is handled, before
	 * the poll's openings are decided.
	 */
	static void afterInputPoll(final Minecraft minecraft) {
		long arrived = pollNanos;
		pollNanos = 0L;
		if (!up || minecraft == null) {
			return;
		}
		if (minecraft.screen != owner) {
			lower();
			return;
		}
		if (ticks == raisedAt) {
			// The opening tick has not run yet: the server has not been told the keys were released.
			return;
		}
		// This poll's moment is the one its own input carries, so none of it is ever a hair late.
		long now = arrived != 0L ? arrived : System.nanoTime();
		Held first = HELD.peek();
		if (first != null) {
			if (delayNanos < 0L) {
				delayNanos = now - first.at();
			}
			if (now - first.at() >= delayNanos) {
				deliver(minecraft, now);
			}
		}
		if (up && HELD.isEmpty() && (owner == null || now >= settleNanos)) {
			lower();
		}
	}

	private static void deliver(final Minecraft minecraft, final long now) {
		MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
		double liveX = minecraft.mouseHandler.xpos();
		double liveY = minecraft.mouseHandler.ypos();
		long window = minecraft.getWindow().handle();
		delivering = true;
		try {
			Held held;
			while ((held = HELD.peek()) != null && (owner == null || now - held.at() >= delayNanos)) {
				HELD.poll();
				if (owner instanceof AbstractContainerScreen<?> container) {
					// It acts where the pointer was, on what was highlighted, when the player made it.
					mouse.kohsInventoryTweaks$setXpos(held.x());
					mouse.kohsInventoryTweaks$setYpos(held.y());
					((AbstractContainerScreenAccessor) container).kohsInventoryTweaks$setHoveredSlot(held.hovered());
					if (held.acts() && delayNanos > 0L) {
						settleNanos = now + SETTLE_NANOS;
					}
				}
				held.delivery().to(minecraft, window);
				if (minecraft.screen != owner) {
					if (minecraft.screen != null) {
						// It opened another screen, which nothing kept was aimed at.
						lower();
						break;
					}
					// The player's own input closed the inventory: the rest was made in the world.
					owner = null;
				}
			}
		} finally {
			delivering = false;
			if (!minecraft.mouseHandler.isMouseGrabbed()) {
				mouse.kohsInventoryTweaks$setXpos(liveX);
				mouse.kohsInventoryTweaks$setYpos(liveY);
			}
		}
	}

	private static void lower() {
		up = false;
		owner = null;
		delayNanos = -1L;
		settleNanos = 0L;
		HELD.clear();
	}
}
