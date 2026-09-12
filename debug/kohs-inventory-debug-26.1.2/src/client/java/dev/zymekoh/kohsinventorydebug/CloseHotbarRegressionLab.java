package dev.zymekoh.kohsinventorydebug;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Integrated-server fixture plus ordered input callbacks, never available on multiplayer. */
public final class CloseHotbarRegressionLab {
	private static final AtomicReference<Runnable> POLL = new AtomicReference<>();
	private CloseHotbarRegressionLab() {}

	public static void drainPoll(final Minecraft minecraft) {
		Runnable task = POLL.getAndSet(null);
		if (task != null) task.run();
	}

	static void atPoll(final Minecraft mc, final Runnable action) throws Exception {
		CompletableFuture<Void> done = new CompletableFuture<>();
		Runnable task = () -> {
			try {
				if (!MacroTestController.isSafeLocalWorld(mc) || !mc.isWindowActive()) {
					throw new IllegalStateException("Local/focus guard failed");
				}
				action.run();
				done.complete(null);
			} catch (Throwable error) { done.completeExceptionally(error); }
		};
		if (!POLL.compareAndSet(null, task)) throw new IllegalStateException("Poll job already pending");
		try { done.get(5, TimeUnit.SECONDS); }
		finally { POLL.compareAndSet(task, null); }
	}

	private static void key(final Minecraft mc, final int code, final int mods, final int action) {
		((KeyboardHandlerDebugInvoker) mc.keyboardHandler).kohsInventoryDebug$invokeKeyPress(
			mc.getWindow().handle(), action, new KeyEvent(code, GLFW.glfwGetKeyScancode(code), mods));
	}

	private static void tap(final Minecraft mc, final int code, final int mods) {
		key(mc, code, mods, GLFW.GLFW_PRESS);
		key(mc, code, mods, GLFW.GLFW_RELEASE);
	}

	private static int binding(final KeyMapping mapping) {
		InputConstants.Key key = ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey();
		if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) {
			throw new IllegalStateException("This focused lab requires keyboard mappings: " + mapping.getName());
		}
		return key.getValue();
	}

	private static void await(final Minecraft mc, final BooleanSupplier condition, final String label) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (System.nanoTime() < deadline) {
			AtomicReference<Boolean> result = new AtomicReference<>(false);
			mc.executeBlocking(() -> result.set(condition.getAsBoolean()));
			if (result.get()) return;
			Thread.sleep(2);
		}
		throw new IllegalStateException("Timed out: " + label);
	}

	private static double number(final Object object, final Class<?> owner, final String method) throws Exception {
		return ((Number) owner.getMethod(method).invoke(object)).doubleValue();
	}

	/** The reason the fast path recorded for its last decision, or "" if unavailable. */
	private static String decisionReason() {
		try {
			Class<?> controller = Class.forName("dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController");
			return String.valueOf(controller.getMethod("lastDecisionSnapshot").invoke(null));
		} catch (Exception error) {
			return "";
		}
	}

	public static void run(final Minecraft mc) {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		int inventoryKey = binding(mc.options.keyInventory);
		int offhandKey = binding(mc.options.keySwapOffhand);
		int hotbarKey = binding(mc.options.keyHotbarSlots[8]);
		int[] slots = {8, 9, 40};
		ItemStack[] saved = server.submit(() -> {
			var player = server.getPlayerList().getPlayer(playerId);
			return new ItemStack[] {player.getInventory().getItem(8).copy(),
				player.getInventory().getItem(9).copy(), player.getInventory().getItem(40).copy()};
		}).join();
		int selected = mc.player.getInventory().getSelectedSlot();
		int[] passes = {0};
		long[] maxCloseMicros = {0L};
		DebugCollector.info("CLOSE_LAB_START", "E/F/hotbar callbacks in ONE poll; server fixture slots 8,9,40 restored afterward");
		try {
			for (int cycle = 0; cycle < 32; cycle++) {
				int index = cycle;
				int modifiers = cycle % 4;
				boolean reverse = cycle >= 24;
				int closeKey = cycle >= 16 && cycle < 24 ? GLFW.GLFW_KEY_ESCAPE : inventoryKey;
				server.submit(() -> {
					var player = server.getPlayerList().getPlayer(playerId);
					player.getInventory().setItem(8, new ItemStack(Items.ENDER_PEARL, 16));
					player.getInventory().setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
					player.getInventory().setItem(40, ItemStack.EMPTY);
					player.inventoryMenu.broadcastFullState();
				}).join();
				await(mc, () -> mc.player.getInventory().getItem(8).is(Items.ENDER_PEARL)
					&& mc.player.getInventory().getItem(9).is(Items.TOTEM_OF_UNDYING)
					&& mc.player.getOffhandItem().isEmpty(), "fixture sync");
				atPoll(mc, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					tap(mc, inventoryKey, 0);
				});
				await(mc, () -> mc.screen instanceof InventoryScreen, "inventory open");
				atPoll(mc, () -> {
					try {
						var screen = (InventoryScreen) mc.screen;
						var slot = screen.getMenu().getSlot(9);
						Class<?> accessor = Class.forName("dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor");
						Object config = Class.forName("dev.zymekoh.kohsinventorytweaks.config.ConfigStore").getMethod("get").invoke(null);
						Class<?> scaler = Class.forName("dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler");
						double scale = ((Number) scaler.getMethod("appliedScale", InventoryScreen.class, config.getClass()).invoke(null, screen, config)).doubleValue();
						double x = screen.width * 0.5 + (number(screen, accessor, "kohsInventoryTweaks$getLeftPos") + slot.x + 8 - screen.width * 0.5) * scale;
						double y = screen.height * 0.5 + (number(screen, accessor, "kohsInventoryTweaks$getTopPos") + slot.y + 8 - screen.height * 0.5) * scale;
						double physicalX = x * mc.getWindow().getScreenWidth() / screen.width;
						double physicalY = y * mc.getWindow().getScreenHeight() / screen.height;
						GLFW.glfwSetCursorPos(mc.getWindow().handle(), physicalX, physicalY);
						((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), physicalX, physicalY);
					} catch (Exception error) { throw new RuntimeException(error); }
				});
				await(mc, () -> {
					try {
						Class<?> accessor = Class.forName("dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor");
						return accessor.getMethod("kohsInventoryTweaks$getHoveredSlot").invoke(mc.screen) == ((InventoryScreen) mc.screen).getMenu().getSlot(9);
					} catch (Exception error) { throw new RuntimeException(error); }
				}, "real rendered hover on totem");
				atPoll(mc, () -> {
					tap(mc, offhandKey, modifiers);
					if (reverse) tap(mc, hotbarKey, modifiers);
					long closeStart = System.nanoTime();
					tap(mc, closeKey, modifiers);
					long elapsed = (System.nanoTime() - closeStart) / 1_000L;
					maxCloseMicros[0] = Math.max(maxCloseMicros[0], elapsed);
					boolean closedBeforeHotbar = mc.screen == null;
					if (!reverse) tap(mc, hotbarKey, modifiers);
					boolean totem = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
					boolean pearlInHotbar = mc.player.getInventory().getItem(8).is(Items.ENDER_PEARL);
					boolean pearlInStorage = mc.player.getInventory().getItem(9).is(Items.ENDER_PEARL);
					boolean passed = closedBeforeHotbar && totem && (reverse ? pearlInStorage && !pearlInHotbar : pearlInHotbar && !pearlInStorage);
					if (passed) passes[0]++;
					DebugCollector.info("CLOSE_LAB_CASE", "cycle=" + index + "; reverse=" + reverse + "; mods=" + modifiers + "; closeKey=" + closeKey
						+ "; closeCallback=" + elapsed + "us; closed=" + closedBeforeHotbar + "; totem=" + totem
						+ "; pearlHotbar=" + pearlInHotbar + "; pearlStorage=" + pearlInStorage + "; passed=" + passed);
				});
				if (!reverse) await(mc, () -> mc.player.getInventory().getSelectedSlot() == 8, "hotbar selection after close");
				await(mc, () -> mc.screen == null, "closed");
				Thread.sleep(60);
			}
			DebugCollector.info("CLOSE_LAB_SUMMARY", "passed=" + passes[0] + "/32; maxCloseCallback=" + maxCloseMicros[0]
				+ "us; orderedCases=24; reversedControls=8");
			if (passes[0] != 32) throw new IllegalStateException("Close/hotbar regression");
			// Fresh presses preserve parity regardless of poll duration. The clean pair
			// settles closed; mixed actions retain Vanilla ownership of the whole queue.
			int settledCases = 0;
			int yieldedCases = 0;
			for (int cycle = 0; cycle < 8; cycle++) {
				boolean clean = cycle < 4;
				int index = cycle;
				atPoll(mc, () -> {
					tap(mc, inventoryKey, 0);
					tap(mc, inventoryKey, 0);
					if (!clean) {
						if ((index & 1) != 0) tap(mc, offhandKey, 0);
						tap(mc, hotbarKey, 0);
					}
				});
				// The decision is taken on the next poll, not while atPoll is queuing the
				// taps. Reading before the wait samples the previous cycle instead.
				Thread.sleep(120);
				String reason = decisionReason();
				AtomicReference<Boolean> open = new AtomicReference<>(false);
				mc.executeBlocking(() -> open.set(mc.screen instanceof InventoryScreen));
				boolean settled = reason.contains("open-and-close");
				boolean yielded = reason.contains("physical-conflict");
				if (clean && settled && !open.get()) settledCases++;
				if (!clean && yielded && open.get()) yieldedCases++;
				DebugCollector.info("CLOSE_DOUBLE_CASE", "cycle=" + cycle + "; batch=" + (clean ? "two-presses-only" : "two-presses-plus-other")
					+ "; open=" + open.get() + "; reason=" + reason);
				if (open.get()) atPoll(mc, () -> tap(mc, inventoryKey, 0));
				await(mc, () -> mc.screen == null, "double-press recovery");
			}
			DebugCollector.info("CLOSE_DOUBLE_SUMMARY", "clean=" + settledCases + "/4 settled-and-closed; mixed="
				+ yieldedCases + "/4 yielded-and-open");
			if (settledCases != 4 || yieldedCases != 4) throw new IllegalStateException("Double-press contract");
		} catch (Exception error) { throw new RuntimeException(error); }
		finally {
			server.submit(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				if (player != null) {
					for (int i = 0; i < slots.length; i++) player.getInventory().setItem(slots[i], saved[i]);
					player.inventoryMenu.broadcastFullState();
				}
			}).join();
			mc.executeBlocking(() -> { if (mc.player != null) mc.player.getInventory().setSelectedSlot(selected); });
		}
	}
}
