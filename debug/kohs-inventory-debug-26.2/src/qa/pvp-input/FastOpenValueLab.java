package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * What Super Fast Inventory changes for a hand that re-totems fast, against Vanilla.
 *
 * <p>Each trial presses the inventory key at a random moment of the tick and, a set number of
 * milliseconds later, flicks to a totem and presses the offhand key, as one practised motion.
 * Vanilla only builds the inventory at the next tick: a hand that arrives before it turns the
 * camera instead of the pointer and its offhand press goes to the world, where the opening tick
 * throws it away. With Super Fast Inventory the inventory is there by the next frame, and what
 * the hand does in it reaches the server at the earliest moment a Vanilla client could send it.
 * Recorded per trial: whether the totem reached the offhand, when the inventory appeared and when
 * the click left, all measured from the inventory key. Network latency adds the same to both, so
 * it is left out. Integrated singleplayer only.</p>
 */
public final class FastOpenValueLab {
	private static final int TOTEM_SLOT = 20;
	private static final int[] DELAYS = {0, 10, 20, 30, 40, 60, 90};
	private static final int REPEATS = 10;

	private static volatile boolean recording;
	private static volatile long screenNanos;
	private static final List<Long> CLICKS = Collections.synchronizedList(new ArrayList<>());

	private FastOpenValueLab() {
	}

	public static void onScreenApplied(final Screen screen) {
		if (recording && screen instanceof InventoryScreen && screenNanos == 0L) screenNanos = System.nanoTime();
	}

	public static void onPacket(final Packet<?> packet) {
		if (recording && packet instanceof ServerboundContainerClickPacket) CLICKS.add(System.nanoTime());
	}

	private record Trial(boolean swapped, double screenMs, double clickMs, boolean turned) {
	}

	public static void run(final Minecraft mc) {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		var savedConfig = ConfigStore.get().copy();
		int savedGui = mc.options.guiScale().get();
		boolean savedBook = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
		ItemStack[] savedInventory = server.submit(() -> {
			var inventory = server.getPlayerList().getPlayer(playerId).getInventory();
			ItemStack[] copy = new ItemStack[inventory.getContainerSize()];
			for (int index = 0; index < copy.length; index++) copy[index] = inventory.getItem(index).copy();
			return copy;
		}).join();
		Random random = new Random(20261012L);
		StringBuilder summary = new StringBuilder();
		try {
			command(mc, "gamemode survival");
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.options.guiScale().set(3);
				mc.resizeGui();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
			});
			for (boolean fast : new boolean[] {false, true}) {
				for (int delay : LabSuite.quick() ? new int[] {0, 20, 60} : DELAYS) {
					int swapped = 0;
					int turned = 0;
					List<Double> screen = new ArrayList<>();
					List<Double> click = new ArrayList<>();
					int repeats = LabSuite.quick() ? 4 : REPEATS;
					for (int repeat = 0; repeat < repeats; repeat++) {
						Trial trial = trial(mc, fast, delay, random);
						if (trial.swapped()) swapped++;
						if (trial.turned()) turned++;
						if (trial.screenMs() >= 0) screen.add(trial.screenMs());
						if (trial.swapped() && trial.clickMs() >= 0) click.add(trial.clickMs());
					}
					String line = String.format("%s d=%dms: totem %d/%d; camera turned %d; screen p50=%.1fms max=%.1fms; click p50=%.1fms",
						fast ? "fast" : "vanilla", delay, swapped, repeats, turned, median(screen), max(screen), median(click));
					DebugCollector.info("FAST_OPEN_VALUE", line);
					summary.append(fast ? "F" : "V").append(delay).append('=').append(swapped).append('/').append(repeats).append(' ');
				}
			}
			DebugCollector.info("FAST_OPEN_VALUE_SUMMARY", summary.toString().trim());
		} catch (RuntimeException error) {
			throw error;
		} catch (Exception error) {
			throw new IllegalStateException(error);
		} finally {
			recording = false;
			try {
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					if (mc.gui.screen() != null) mc.gui.screen().onClose();
					KeyMapping.releaseAll();
					ConfigStore.replaceAndSave(savedConfig);
					mc.options.guiScale().set(savedGui);
					mc.resizeGui();
					mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, savedBook);
				});
			} catch (Exception ignored) {
				// The inventory below is still restored.
			}
			server.submit(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				for (int index = 0; index < savedInventory.length; index++) player.getInventory().setItem(index, savedInventory[index]);
				player.inventoryMenu.broadcastFullState();
			}).join();
		}
	}

	private static Trial trial(final Minecraft mc, final boolean fast, final int delay, final Random random) throws Exception {
		int inventory = binding(mc.options.keyInventory);
		int offhand = binding(mc.options.keySwapOffhand);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			KeyMapping.releaseAll();
			var config = ConfigStore.get();
			config.superFastInventory = fast;
			config.shortcutsFollowPointer = true;
			config.inventoryGuiScalerEnabled = false;
		});
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		server.submit(() -> {
			var player = server.getPlayerList().getPlayer(playerId);
			var items = player.getInventory();
			for (int index = 0; index < items.getContainerSize(); index++) items.setItem(index, ItemStack.EMPTY);
			items.setItem(TOTEM_SLOT, new ItemStack(Items.TOTEM_OF_UNDYING));
			player.inventoryMenu.broadcastFullState();
		}).join();
		Thread.sleep(200 + random.nextInt(50));
		float[] yaw = new float[2];
		mc.executeBlocking(() -> yaw[0] = mc.player.getYRot());
		screenNanos = 0L;
		CLICKS.clear();
		recording = true;
		long[] press = new long[1];
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			press[0] = System.nanoTime();
			tap(mc, inventory);
			if (delay == 0) {
				// The inventory key and the flick inside one frame, as one motion.
				SuperFastInventoryController.afterInputPoll(mc);
				flickOrTurn(mc);
			}
		});
		if (delay > 0) {
			long until = press[0] + delay * 1_000_000L;
			while (System.nanoTime() < until) Thread.onSpinWait();
			CloseHotbarRegressionLab.atPoll(mc, () -> flickOrTurn(mc));
		}
		// The offhand key a frame after the flick: a hand presses once the pointer is on the totem.
		CloseHotbarRegressionLab.atPoll(mc, () -> tap(mc, offhand));
		Thread.sleep(250);
		recording = false;
		boolean[] swapped = {false};
		mc.executeBlocking(() -> {
			swapped[0] = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
			yaw[1] = mc.player.getYRot();
		});
		double screenMs = screenNanos == 0L ? -1 : (screenNanos - press[0]) / 1.0E6;
		double clickMs = CLICKS.isEmpty() ? -1 : (CLICKS.get(0) - press[0]) / 1.0E6;
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			KeyMapping.releaseAll();
		});
		Thread.sleep(80);
		return new Trial(swapped[0], screenMs, clickMs, Math.abs(yaw[1] - yaw[0]) > 0.01F);
	}

	/** The hand's flick toward the totem. Without an inventory yet, it turns the camera instead. */
	private static void flickOrTurn(final Minecraft mc) {
		if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
			flick(mc, screen, TOTEM_SLOT);
		} else {
			((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(),
				mc.mouseHandler.xpos() - 120.0, mc.mouseHandler.ypos() + 40.0);
			mc.mouseHandler.handleAccumulatedMovement();
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

	private static double median(final List<Double> values) {
		if (values.isEmpty()) return -1;
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	private static double max(final List<Double> values) {
		return values.stream().mapToDouble(Double::doubleValue).max().orElse(-1);
	}

	private static void command(final Minecraft mc, final String command) throws Exception {
		mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
		Thread.sleep(120);
	}
}
