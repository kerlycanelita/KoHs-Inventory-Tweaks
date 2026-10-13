package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SteadyInventory;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The inventory on a real server with 0 to 200 ms of added latency, while the server keeps
 * changing the player's inventory as a crystal fight does (armor losing durability on every hit).
 *
 * <p>Each trial is a double re-totem: the offhand key on one totem, and 60 ms later the first
 * hotbar key on another, then the same two back. With latency, every click the server answers
 * with a full inventory (its state numbers moved on while the click travelled), and the answer to
 * the first click arrives after the second was already predicted: Vanilla then shows the second
 * slot going back to what it held before, until the answer to the second click arrives. That is
 * the flicker counted here, frame by frame, as the time a slot shows anything but what the
 * player's own clicks put there. Each re-totem is then played again with the inventory closed in
 * the same breath, to see that nothing stays kept aside outside the screen. Run by KoHs-Debug-Tools/inventory-ping-lab against a local
 * Grim server, never anywhere else.</p>
 */
public final class InventoryPingLab {
	private static final int TOTEM_A = 20;
	private static final int TOTEM_B = 21;
	private static final int OFFHAND = 45;
	private static final int HOTBAR_ONE = 36;
	private static volatile boolean recording;
	private static volatile int contents;
	private static volatile int slots;

	private InventoryPingLab() {
	}

	/** Only a server on this machine. */
	public static boolean allowed(final Minecraft mc) {
		var server = mc.getCurrentServer();
		return mc.player != null && mc.level != null && server != null
			&& (server.ip.startsWith("127.0.0.1") || server.ip.startsWith("localhost"));
	}

	public static void onPacketReceived(final Packet<?> packet) {
		if (!recording) return;
		if (packet instanceof ClientboundContainerSetContentPacket) contents++;
		else if (packet instanceof ClientboundContainerSetSlotPacket) slots++;
	}

	private static void lab(final String line) {
		System.out.println("[lab] " + line);
		DebugCollector.info("INVENTORY_PING", line);
	}

	public static void run(final Minecraft mc) throws Exception {
		if (!allowed(mc)) throw new IllegalStateException("Only a server on this machine");
		lab("READY " + mc.player.getName().getString());
		File flag = new File(mc.gameDirectory, "lab-setup.ok");
		long deadline = System.nanoTime() + 90_000_000_000L;
		while (!flag.exists() && System.nanoTime() < deadline) Thread.sleep(100);
		if (!flag.exists()) throw new IllegalStateException("The runner never set the field up");
		InventoryTweaksConfig saved = ConfigStore.get().copy();
		boolean steadyAvailable = hasOption("steadyInventory");
		String pings = System.getProperty("kohs.lab.pings", "0,70,130,200");
		int trials = Integer.getInteger("kohs.lab.trials", 8);
		try {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.options.guiScale().set(3);
				mc.resizeGui();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
				var config = ConfigStore.get();
				config.superFastInventory = true;
				config.shortcutsFollowPointer = true;
				config.inventoryGuiScalerEnabled = false;
			});
			for (String text : pings.split(",")) {
				int ping = Integer.parseInt(text.trim());
				lab("PING " + ping + " -> " + setPing(ping));
				Thread.sleep(2_500);
				for (boolean steady : steadyAvailable ? new boolean[] {false, true} : new boolean[] {false}) {
					CloseHotbarRegressionLab.atPoll(mc, () -> {
						if (steadyAvailable) setOption("steadyInventory", steady);
					});
					double flickerMs = 0;
					int flickerTrials = 0;
					int wrongEnd = 0;
					int contentsTotal = 0;
					int slotsTotal = 0;
					for (int trial = 0; trial < trials; trial++) {
						contents = 0;
						slots = 0;
						recording = true;
						double in = combo(mc, true);
						double out = combo(mc, false);
						recording = false;
						contentsTotal += contents;
						slotsTotal += slots;
						if (in < 0 || out < 0) {
							wrongEnd++;
						} else {
							flickerMs += in + out;
							if (in > 0 || out > 0) flickerTrials++;
						}
					}
					lab(String.format("RESULT ping=%d steady=%s combos=%d flickeringCombos=%d flickerMsPerCombo=%.1f wrongEnd=%d contents=%d slotUpdates=%d",
						ping, steady, trials * 2, flickerTrials, flickerMs / (trials * 2), wrongEnd, contentsTotal, slotsTotal));
					// The same re-totem with the inventory closed in the same breath, as in a fight: once
					// the screen is gone the menu must hold the server's word and nothing kept aside.
					int keptAfterClose = 0;
					int wrongAfterClose = 0;
					for (int trial = 0; trial < trials; trial++) {
						for (boolean in : new boolean[] {true, false}) {
							int[] closed = comboThenClose(mc, in, steadyAvailable);
							keptAfterClose += closed[0];
							wrongAfterClose += closed[1];
						}
					}
					lab(String.format("CLOSE ping=%d steady=%s combos=%d framesKeptAfterClose=%d wrongEnd=%d",
						ping, steady, trials * 2, keptAfterClose, wrongAfterClose));
				}
			}
		} finally {
			setPing(0);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.gui.screen() != null) mc.gui.screen().onClose();
				KeyMapping.releaseAll();
				ConfigStore.replaceAndSave(saved);
			});
			lab("DONE");
		}
	}

	/**
	 * The offhand key on totem A, then the first hotbar key on totem B 60 ms later; or both back.
	 * Returns how long, in ms, a slot showed anything but the player's own result before both
	 * answers settled, or -1 when the inventory did not end as the clicks left it.
	 */
	private static double combo(final Minecraft mc, final boolean in) throws Exception {
		int inventory = binding(mc.options.keyInventory);
		int offhand = binding(mc.options.keySwapOffhand);
		int hotbar = binding(mc.options.keyHotbarSlots[0]);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			KeyMapping.releaseAll();
			tap(mc, inventory);
		});
		long open = System.nanoTime() + 1_000_000_000L;
		while (!(mc.gui.screen() instanceof InventoryScreen) && System.nanoTime() < open) Thread.sleep(5);
		Thread.sleep(120);
		long[] second = new long[1];
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			flick(mc, (AbstractContainerScreen<?>) mc.gui.screen(), TOTEM_A);
			fenceReport("first key");
			tap(mc, offhand);
		});
		Thread.sleep(60);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			flick(mc, (AbstractContainerScreen<?>) mc.gui.screen(), TOTEM_B);
			fenceReport("second key");
			tap(mc, hotbar);
			second[0] = System.nanoTime();
		});
		// Every frame for 900 ms: is each slot showing what the clicks put there?
		double wrongMs = 0;
		long previous = System.nanoTime();
		long until = second[0] + 900_000_000L;
		while (System.nanoTime() < until) {
			boolean[] right = new boolean[1];
			mc.executeBlocking(() -> right[0] = showsResult(mc, in));
			long now = System.nanoTime();
			if (!right[0]) wrongMs += (now - previous) / 1.0E6;
			previous = now;
		}
		boolean[] settled = new boolean[1];
		mc.executeBlocking(() -> settled[0] = showsResult(mc, in));
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
		});
		Thread.sleep(150);
		return settled[0] ? wrongMs : -1;
	}

	/**
	 * The same two keys, then the inventory key at once. Returns the frames on which Steady inventory
	 * still kept something aside after the screen closed (there must be none), and 1 when the
	 * inventory did not end as the clicks left it.
	 */
	private static int[] comboThenClose(final Minecraft mc, final boolean in, final boolean steadyAvailable) throws Exception {
		int inventory = binding(mc.options.keyInventory);
		int offhand = binding(mc.options.keySwapOffhand);
		int hotbar = binding(mc.options.keyHotbarSlots[0]);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			KeyMapping.releaseAll();
			tap(mc, inventory);
		});
		long open = System.nanoTime() + 1_000_000_000L;
		while (!(mc.gui.screen() instanceof InventoryScreen) && System.nanoTime() < open) Thread.sleep(5);
		Thread.sleep(120);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			flick(mc, (AbstractContainerScreen<?>) mc.gui.screen(), TOTEM_A);
			fenceReport("first key");
			tap(mc, offhand);
		});
		Thread.sleep(60);
		long[] closedAt = new long[1];
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			flick(mc, (AbstractContainerScreen<?>) mc.gui.screen(), TOTEM_B);
			fenceReport("second key");
			tap(mc, hotbar);
			mc.gui.screen().onClose();
			closedAt[0] = System.nanoTime();
		});
		int kept = 0;
		while (System.nanoTime() < closedAt[0] + 900_000_000L) {
			boolean[] holding = new boolean[1];
			mc.executeBlocking(() -> holding[0] = steadyAvailable && SteadyInventory.holding());
			if (holding[0]) kept++;
		}
		boolean[] settled = new boolean[1];
		mc.executeBlocking(() -> settled[0] = showsResult(mc, in));
		Thread.sleep(150);
		return new int[] {kept, settled[0] ? 0 : 1};
	}

	/** Says so when the input fence is still up at a key pressed long after the inventory opened. */
	private static void fenceReport(final String where) {
		if (!dev.zymekoh.kohsinventorytweaks.inventory.InputFence.isUp()) return;
		String state = "?";
		try {
			state = String.valueOf(dev.zymekoh.kohsinventorytweaks.inventory.InputFence.class.getMethod("describe").invoke(null));
		} catch (ReflectiveOperationException older) {
			// A release without the description: the fact alone is reported.
		}
		lab("FENCE up at the " + where + ": " + state);
	}

	private static boolean showsResult(final Minecraft mc, final boolean in) {
		var menu = mc.player.inventoryMenu;
		boolean offhandTotem = menu.getSlot(OFFHAND).getItem().is(Items.TOTEM_OF_UNDYING);
		boolean hotbarTotem = menu.getSlot(HOTBAR_ONE).getItem().is(Items.TOTEM_OF_UNDYING);
		boolean aTotem = menu.getSlot(TOTEM_A).getItem().is(Items.TOTEM_OF_UNDYING);
		boolean bTotem = menu.getSlot(TOTEM_B).getItem().is(Items.TOTEM_OF_UNDYING);
		return in ? offhandTotem && hotbarTotem && !aTotem && !bTotem : !offhandTotem && !hotbarTotem && aTotem && bTotem;
	}

	private static String setPing(final int ms) {
		try {
			Class<?> type = Class.forName("net.ravenclaw.ravenclawspingequalizer.PingEqualizerState");
			Object state = type.getMethod("getInstance").invoke(null);
			if (ms <= 0) type.getMethod("setOff").invoke(state);
			else type.getMethod("setAddPing", int.class).invoke(state, ms);
			return String.valueOf(type.getMethod("getCurrentDelayMs").invoke(state));
		} catch (ReflectiveOperationException | LinkageError error) {
			return "unavailable: " + error;
		}
	}

	private static void flick(final Minecraft mc, final AbstractContainerScreen<?> screen, final int menuSlot) {
		var access = (AbstractContainerScreenAccessor) screen;
		var slot = screen.getMenu().getSlot(menuSlot);
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		double x = screen.width * 0.5 + (access.kohsInventoryTweaks$getLeftPos() + slot.x + 8 - screen.width * 0.5) * scale;
		double y = screen.height * 0.5 + (access.kohsInventoryTweaks$getTopPos() + slot.y + 8 - screen.height * 0.5) * scale;
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(),
			x * mc.getWindow().getScreenWidth() / screen.width, y * mc.getWindow().getScreenHeight() / screen.height);
	}

	private static void tap(final Minecraft mc, final int code) {
		var keyboard = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
		KeyEvent event = new KeyEvent(code, GLFW.glfwGetKeyScancode(code), 0);
		keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
		keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
	}

	private static int binding(final KeyMapping mapping) {
		return ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey().getValue();
	}

	private static boolean hasOption(final String name) {
		try {
			InventoryTweaksConfig.class.getField(name);
			return true;
		} catch (NoSuchFieldException missing) {
			return false;
		}
	}

	private static void setOption(final String name, final boolean value) {
		try {
			InventoryTweaksConfig.class.getField(name).setBoolean(ConfigStore.get(), value);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException(error);
		}
	}
}
