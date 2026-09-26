package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Local-only callbacks and UI checks. Never packaged into Inventory Tweaks. */
public final class PvpInputRegressionLab {
	private static int checks;
	private static int failures;
	private PvpInputRegressionLab() {}

	private static void check(final boolean result, final String label) {
		checks++;
		if (!result) {
			failures++;
			DebugCollector.issue("PVP_INPUT_FAIL", label);
		}
	}

	private static void key(final Minecraft mc, final int key, final int action) {
		((KeyboardHandlerDebugInvoker) mc.keyboardHandler).kohsInventoryDebug$invokeKeyPress(
			mc.getWindow().handle(), action, new KeyEvent(key, GLFW.glfwGetKeyScancode(key), 0));
	}

	private static void tap(final Minecraft mc, final int key) {
		key(mc, key, GLFW.GLFW_PRESS);
		key(mc, key, GLFW.GLFW_RELEASE);
	}

	private static void mouse(final Minecraft mc, final int button, final int action) {
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeButton(
			mc.getWindow().handle(), new MouseButtonInfo(button, 0), action);
	}

	private static void close(final Minecraft mc) {
		if (mc.screen != null) mc.screen.onClose();
		KeyMapping.releaseAll();
	}

	private static int binding(final KeyMapping mapping) {
		return ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey().getValue();
	}

	private static void moveTo(final Minecraft mc, final InventoryScreen screen, final int slotIndex) {
		var access = (AbstractContainerScreenAccessor) screen;
		var slot = screen.getMenu().getSlot(slotIndex);
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		double x = screen.width * 0.5 + (access.kohsInventoryTweaks$getLeftPos() + slot.x + 8 - screen.width * 0.5) * scale;
		double y = screen.height * 0.5 + (access.kohsInventoryTweaks$getTopPos() + slot.y + 8 - screen.height * 0.5) * scale;
		// Feed the real handler; no render/tick may occur before the following shortcut.
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(),
			x * mc.getWindow().getScreenWidth() / screen.width, y * mc.getWindow().getScreenHeight() / screen.height);
	}

	public static void run(final Minecraft mc) {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		var saved = ConfigStore.get().copy();
		Path configPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kohs_inventory_tweaks.json");
		byte[] savedJson;
		try { savedJson = Files.exists(configPath) ? Files.readAllBytes(configPath) : null; }
		catch (java.io.IOException error) { throw new IllegalStateException(error); }
		int savedGui = mc.options.guiScale().get();
		boolean savedBook = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
		var savedOffhandKey = ((KeyMappingDebugAccessor) mc.options.keySwapOffhand).kohsInventoryDebug$getKey();
		int inventory = binding(mc.options.keyInventory);
		checks = failures = 0;
		try {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				close(mc);
				var config = ConfigStore.get();
				config.superFastInventory = true;
				config.suppressInventoryKeyRepeats = true;
				config.fastInventoryWhileMouseHeld = false;
				for (int presses = 1; presses <= 6; presses++) {
					for (int index = 0; index < presses; index++) tap(mc, inventory);
					SuperFastInventoryController.afterInputPoll(mc);
					check((mc.screen instanceof InventoryScreen) == (presses % 2 == 1), "fresh press parity " + presses);
					check(((KeyMappingDebugAccessor) mc.options.keyInventory).kohsInventoryDebug$getClickCount() == 0, "owned queue drained " + presses);
					close(mc);
				}
				for (boolean fast : new boolean[]{false, true}) {
					config.superFastInventory = fast;
					mc.setScreen(new InventoryScreen(mc.player));
					var screen = mc.screen;
					key(mc, inventory, GLFW.GLFW_REPEAT);
					check(mc.screen == screen, "repeat ignored independently, fast=" + fast);
					tap(mc, inventory);
					check(mc.screen == null, "fresh close immediate, fast=" + fast);
				}
				config.superFastInventory = true;
				config.suppressInventoryKeyRepeats = false;
				mc.setScreen(new InventoryScreen(mc.player));
				key(mc, inventory, GLFW.GLFW_REPEAT);
				check(mc.screen == null, "repeat disabled restores Vanilla repeat close");
				config.suppressInventoryKeyRepeats = true;
				close(mc);

				for (boolean held : new boolean[]{false, true}) {
					config.fastInventoryWhileMouseHeld = held;
					mouse(mc, 0, GLFW.GLFW_PRESS);
					SuperFastInventoryController.afterInputPoll(mc);
					// Model a held attack whose queued press was already processed by a tick.
					while (mc.options.keyAttack.consumeClick()) { }
					tap(mc, inventory);
					SuperFastInventoryController.afterInputPoll(mc);
					check((mc.screen instanceof InventoryScreen) == held, "held button policy=" + held);
					if (!held) check(SuperFastInventoryController.lastFallbackCode().equals("mouse-button-held"), "held fallback reason");
					mouse(mc, 0, GLFW.GLFW_RELEASE);
					check(mc.player.inventoryMenu.getCarried().isEmpty(), "inherited release does not pick up items");
					close(mc);
				}
				// handleKeybinds reaches keyAttack after keyInventory, and the opening it has just
				// made zeroes every click count, so a tick-time opening drops a pending attack too.
				mouse(mc, 0, GLFW.GLFW_PRESS);
				tap(mc, inventory);
				SuperFastInventoryController.afterInputPoll(mc);
				check(mc.screen instanceof InventoryScreen && SuperFastInventoryController.lastOpenWasImmediate(), "pending attack no longer delays the open");
				check(((KeyMappingDebugAccessor) mc.options.keyAttack).kohsInventoryDebug$getClickCount() == 0, "attack dropped as the tick-time opening drops it");
				check(((KeyMappingDebugAccessor) mc.options.keyInventory).kohsInventoryDebug$getClickCount() == 0, "inventory click consumed");
				mouse(mc, 0, GLFW.GLFW_RELEASE);
				close(mc);
				// A hotbar key is drained before keyInventory, so an early opening would take a
				// selection Vanilla still makes: that batch keeps waiting for the tick.
				tap(mc, binding(mc.options.keyHotbarSlots[1]));
				tap(mc, inventory);
				SuperFastInventoryController.afterInputPoll(mc);
				check(mc.screen == null && SuperFastInventoryController.lastOpenHadInputConflict(), "pending hotbar selection retains Vanilla ownership");
				check(((KeyMappingDebugAccessor) mc.options.keyHotbarSlots[1]).kohsInventoryDebug$getClickCount() == 1, "hotbar queue preserved");
				check(((KeyMappingDebugAccessor) mc.options.keyInventory).kohsInventoryDebug$getClickCount() == 1, "inventory fallback queue preserved");
				while (mc.options.keyHotbarSlots[1].consumeClick()) { }
				while (mc.options.keyInventory.consumeClick()) { }
				close(mc);
			});

			for (int gui = 2; gui <= 4; gui++) {
				int guiScale = gui;
				for (boolean book : new boolean[]{false, true}) {
					for (double physicalScale : new double[]{0.65, 2.0, 3.15}) {
						CloseHotbarRegressionLab.atPoll(mc, () -> {
							var config = ConfigStore.get();
							config.inventoryGuiScalerEnabled = true;
							config.inventoryGuiScale = physicalScale;
							mc.options.guiScale().set(guiScale);
							mc.resizeGui();
							check(mc.getWindow().getGuiScale() == guiScale, "effective GUI " + guiScale);
							mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
							var screen = new InventoryScreen(mc.player);
							mc.setScreen(screen);
							var access = (AbstractContainerScreenAccessor) screen;
							for (int target : new int[]{9, 17, 35}) {
								access.kohsInventoryTweaks$setHoveredSlot(screen.getMenu().getSlot(10));
								moveTo(mc, screen, target);
								tap(mc, GLFW.GLFW_KEY_F24);
								// Vanilla refuses slots behind the narrow recipe-book overlay.
								boolean covered = book && screen.width < 379;
								check(access.kohsInventoryTweaks$getHoveredSlot() == (covered ? null : screen.getMenu().getSlot(target)),
									"fresh slot " + target + "; gui=" + guiScale + "; scale=" + physicalScale + "; book=" + book);
							}
							close(mc);
						});
					}
				}
			}

			// Mouse-bound swap and bundle-scroll paths must refresh at the same coordinates.
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
				var screen = new InventoryScreen(mc.player);
				mc.setScreen(screen);
				var access = (AbstractContainerScreenAccessor) screen;
				moveTo(mc, screen, 9);
				// Use an unbound side button to exercise checkHotbarMouseClicked without moving items.
				mouse(mc, 7, GLFW.GLFW_PRESS);
				check(access.kohsInventoryTweaks$getHoveredSlot() == screen.getMenu().getSlot(9), "side-button target uses one scale transform");
				mouse(mc, 7, GLFW.GLFW_RELEASE);
				moveTo(mc, screen, 17);
				((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeScroll(mc.getWindow().handle(), 0, -1);
				check(access.kohsInventoryTweaks$getHoveredSlot() == screen.getMenu().getSlot(17), "scroll target current");
				close(mc);
			});
			checkConfigCopy();
			checkPersistence(mc);
			checkOffhandThenClose(mc, inventory);
			ContainerScaleRegressionLab.run(mc);
			checkMenu(mc);
			DebugCollector.info("PVP_INPUT_SUMMARY", "checks=" + checks + "; failures=" + failures);
			if (failures != 0) throw new IllegalStateException("PvP input regression failures=" + failures);
		} catch (Exception error) {
			throw new IllegalStateException(error);
		} finally {
			mc.executeBlocking(() -> {
				if (mc.screen instanceof InventoryScreen) mc.screen.onClose();
				else if (mc.screen instanceof InventoryTweaksScreen) mc.setScreen(null);
				KeyMapping.releaseAll();
				mc.options.keySwapOffhand.setKey(savedOffhandKey);
				KeyMapping.resetMapping();
				try {
					Field config = ConfigStore.class.getDeclaredField("config");
					config.setAccessible(true);
					config.set(null, saved);
					if (savedJson == null) Files.deleteIfExists(configPath);
					else Files.write(configPath, savedJson);
				} catch (ReflectiveOperationException | java.io.IOException error) { throw new IllegalStateException(error); }
				mc.options.guiScale().set(savedGui);
				mc.resizeGui();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, savedBook);
			});
		}
	}

	private static void checkOffhandThenClose(final Minecraft mc, final int inventoryKey) throws Exception {
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		int[] slots = {9, 10, 40};
		ItemStack[] saved = server.submit(() -> {
			var inventory = server.getPlayerList().getPlayer(playerId).getInventory();
			return new ItemStack[]{inventory.getItem(9).copy(), inventory.getItem(10).copy(), inventory.getItem(40).copy()};
		}).join();
		try {
			server.submit(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				player.getInventory().setItem(9, new ItemStack(Items.STONE));
				player.getInventory().setItem(10, new ItemStack(Items.TOTEM_OF_UNDYING));
				player.getInventory().setItem(40, ItemStack.EMPTY);
				player.inventoryMenu.broadcastFullState();
			}).join();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			boolean[] synced = {false};
			while (!synced[0] && System.nanoTime() < deadline) {
				mc.executeBlocking(() -> synced[0] = mc.player.getInventory().getItem(9).is(Items.STONE)
					&& mc.player.getInventory().getItem(10).is(Items.TOTEM_OF_UNDYING) && mc.player.getOffhandItem().isEmpty());
				if (!synced[0]) Thread.sleep(10);
			}
			if (!synced[0]) throw new IllegalStateException("Fixture sync timed out");
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
				var screen = new InventoryScreen(mc.player);
				mc.setScreen(screen);
				// Deliberately seed a stale rendered target before the next move and F.
				((AbstractContainerScreenAccessor) screen).kohsInventoryTweaks$setHoveredSlot(screen.getMenu().getSlot(9));
				moveTo(mc, screen, 10);
				tap(mc, binding(mc.options.keySwapOffhand));
				check(mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "F uses new slot in same poll");
				check(mc.player.getInventory().getItem(9).is(Items.STONE), "stale slot remains untouched");
				long started = System.nanoTime();
				tap(mc, inventoryKey);
				check(mc.screen == null, "F then close finishes synchronously");
				DebugCollector.info("PVP_CLOSE_CALLBACK", "microseconds=" + (System.nanoTime() - started) / 1_000);
			});
			deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			boolean confirmed = false;
			while (!confirmed && System.nanoTime() < deadline) {
				confirmed = server.submit(() -> server.getPlayerList().getPlayer(playerId).getOffhandItem().is(Items.TOTEM_OF_UNDYING)).join();
				if (!confirmed) Thread.sleep(10);
			}
			check(confirmed, "integrated server accepts the ordinary offhand transaction");
		} finally {
			server.submit(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				for (int index = 0; index < slots.length; index++) player.getInventory().setItem(slots[index], saved[index]);
				player.inventoryMenu.broadcastFullState();
			}).join();
		}
	}

	private static void checkPersistence(final Minecraft mc) throws Exception {
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			var saved = ConfigStore.get().copy();
			for (boolean value : new boolean[]{false, true}) {
				var baseline = saved.copy();
				baseline.automaticBackups = false;
				baseline.centerMouseFix = !value;
				baseline.superFastInventory = !value;
				baseline.fastInventoryWhileMouseHeld = !value;
				baseline.suppressInventoryKeyRepeats = !value;
				baseline.reduceInventoryMotion = !value;
				ConfigStore.replaceAndSave(baseline);
				var changed = ConfigStore.get().copy();
				changed.centerMouseFix = value;
				changed.superFastInventory = value;
				changed.fastInventoryWhileMouseHeld = value;
				changed.suppressInventoryKeyRepeats = value;
				changed.reduceInventoryMotion = value;
				ConfigStore.replaceAndSave(changed);
				checkRuntimeOptions(value, "replaceAndSave");
				ConfigStore.load();
				checkRuntimeOptions(value, "reload");
				check(ConfigStore.undo(), "undo recorded options");
				checkRuntimeOptions(!value, "undo");
				check(ConfigStore.redo(), "redo recorded options");
				checkRuntimeOptions(value, "redo");
			}
			try {
				Path snapshot = Files.createTempFile(mc.gameDirectory.toPath(), "pvp-legacy-config-", ".json");
				try {
					Files.writeString(snapshot, "{\"centerMouseFix\":false,\"immediateSlotTargeting\":false,"
						+ "\"removeAllInventoryAnimations\":true,\"fastInventoryWhileMouseHeld\":true,"
						+ "\"suppressInventoryKeyRepeats\":false,\"automaticBackups\":false}");
					ConfigStore.importSnapshot(snapshot);
					check(!ConfigStore.get().centerMouseFix, "import retains disabled center fix");
					check(ConfigStore.get().reduceInventoryMotion, "legacy motion flag migrated");
					check(ConfigStore.get().fastInventoryWhileMouseHeld, "import retains held option");
					check(!ConfigStore.get().suppressInventoryKeyRepeats, "import retains false repeat option");
					ConfigStore.load();
					check(ConfigStore.get().reduceInventoryMotion, "migrated option reloads");
				} finally { Files.deleteIfExists(snapshot); }
			} catch (java.io.IOException error) { throw new IllegalStateException(error); }
			ConfigStore.replaceAndSave(saved);
		});
	}

	private static void checkRuntimeOptions(final boolean value, final String stage) {
		var actual = ConfigStore.get();
		check(actual.centerMouseFix == value, stage + " center=" + value);
		check(actual.superFastInventory == value, stage + " fast=" + value);
		check(actual.fastInventoryWhileMouseHeld == value, stage + " held=" + value);
		check(actual.suppressInventoryKeyRepeats == value, stage + " repeat=" + value);
		check(actual.reduceInventoryMotion == value, stage + " motion=" + value);
	}

	private static void checkConfigCopy() {
		var defaults = new InventoryTweaksConfig();
		var copy = defaults.copy();
		check(defaults.sameValues(copy), "config copy equal");
		copy.centerMouseFix = !copy.centerMouseFix;
		check(!defaults.sameValues(copy), "center fix participates in config equality");
		copy = defaults.copy();
		copy.fastInventoryWhileMouseHeld = !copy.fastInventoryWhileMouseHeld;
		check(!defaults.sameValues(copy), "held policy participates in config equality");
		copy = defaults.copy();
		copy.suppressInventoryKeyRepeats = !copy.suppressInventoryKeyRepeats;
		check(!defaults.sameValues(copy), "repeat policy participates in config equality");
		for (var profile : InventoryTweaksConfig.ProfilePreset.values()) {
			var config = new InventoryTweaksConfig();
			config.applyProfile(profile);
			check(config.sameValues(config.copy()), "profile copy " + profile);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void openTweaks(final InventoryTweaksScreen screen) {
		try {
			Class modal = Class.forName(InventoryTweaksScreen.class.getName() + "$Modal");
			var method = InventoryTweaksScreen.class.getDeclaredMethod("openModal", modal);
			method.setAccessible(true);
			method.invoke(screen, Enum.valueOf(modal, "TWEAKS"));
		} catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
	}

	private static void clickLabel(final Screen screen, final String key) {
		String label = Component.translatable(key).getString();
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label)) {
				var event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
					widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0));
				check(screen.mouseClicked(event, false), "click " + key);
				screen.mouseReleased(event);
				return;
			}
		}
		throw new IllegalStateException("Missing button " + key);
	}

	private static void screenshot(final Minecraft mc, final String name) throws Exception {
		Thread.sleep(350);
		CompletableFuture<Void> saved = new CompletableFuture<>();
		mc.executeBlocking(() -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
			message -> saved.complete(null)));
		saved.get(10, TimeUnit.SECONDS);
	}

	private static Object field(final Screen screen, final String name) {
		try {
			var field = InventoryTweaksScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(screen);
		} catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
	}

	private static void clickTweak(final Screen screen, final int index) {
		var buttons = (java.util.List<?>) field(screen, "tweakScrollingWidgets");
		var widget = (AbstractWidget) buttons.get(index);
		var event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
			widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0));
		check(widget.visible && widget.isMouseOver(event.x(), event.y()), "tweak switch reachable " + index);
		check(screen.mouseClicked(event, false), "tweak switch consumes click " + index);
		screen.mouseReleased(event);
	}

	private static void checkMenu(final Minecraft mc) throws Exception {
		for (int gui = 2; gui <= 4; gui++) {
			int scale = gui;
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				ConfigStore.get().superFastInventory = true;
				ConfigStore.get().centerMouseFix = false;
				ConfigStore.get().fastInventoryWhileMouseHeld = false;
				ConfigStore.get().reduceInventoryMotion = false;
				mc.options.guiScale().set(scale);
				mc.resizeGui();
				var screen = new InventoryTweaksScreen(null);
				mc.setScreen(screen);
			});
			screenshot(mc, "pvp-main-icons-gui" + scale);
			CloseHotbarRegressionLab.atPoll(mc, () -> openTweaks((InventoryTweaksScreen) mc.screen));
			screenshot(mc, "pvp-tweaks-response-gui" + scale);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				int x = (int) field(mc.screen, "tweakOptionsX") + 10;
				int top = (int) field(mc.screen, "tweakViewportTop");
				mc.screen.mouseScrolled(x, top + 10, 0, -20);
				check(field(mc.screen, "tweakScroll").equals(field(mc.screen, "tweakMaxScroll")), "wheel reaches bottom");
				var buttons = (java.util.List<?>) field(mc.screen, "tweakScrollingWidgets");
				for (var value : buttons) {
					var widget = (AbstractWidget) value;
					check(!widget.isMouseOver(widget.getX() + 1, top - 1), "clipped switch rejects outside input");
				}
				clickTweak(mc.screen, 1);
				check(field(mc.screen, "modal").toString().equals("TWEAK_WARNING"), "held option explains tradeoff before enabling");
				check(!ConfigStore.get().fastInventoryWhileMouseHeld, "warning has not enabled held option");
			});
			screenshot(mc, "pvp-tweaks-held-warning-gui" + scale);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
				check(field(mc.screen, "modal").toString().equals("TWEAKS"), "Escape cancels warning");
				check(!ConfigStore.get().fastInventoryWhileMouseHeld, "cancel retains setting");
				clickTweak(mc.screen, 1);
				clickLabel(mc.screen, "screen.kohs_inventory_tweaks.remove_animations.warning.enable");
				check(ConfigStore.get().fastInventoryWhileMouseHeld, "accepted held option reaches runtime store");
				ConfigStore.load();
				check(ConfigStore.get().fastInventoryWhileMouseHeld, "held option survives menu save and reload");
				mc.screen.mouseScrolled((int) field(mc.screen, "tweakOptionsX") + 10,
					(int) field(mc.screen, "tweakViewportTop") + 10, 0, 20);
				clickTweak(mc.screen, 0);
				var buttonsAfter = (java.util.List<?>) field(mc.screen, "tweakScrollingWidgets");
				check(!((AbstractWidget) buttonsAfter.get(1)).active, "held option inactive when fast opening disabled");
				check(ConfigStore.get().fastInventoryWhileMouseHeld, "inactive dependency retains preference");
			});
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				clickLabel(mc.screen, "screen.kohs_inventory_tweaks.tweaks.cursor");
			});
			screenshot(mc, "pvp-tweaks-cursor-gui" + scale);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				clickTweak(mc.screen, 0);
				check(field(mc.screen, "modal").toString().equals("TWEAK_WARNING"), "center warning describes remembered-position conflict");
				check(!ConfigStore.get().centerMouseFix, "center fix unchanged before warning accepted");
				clickLabel(mc.screen, "screen.kohs_inventory_tweaks.remove_animations.warning.enable");
				check(ConfigStore.get().centerMouseFix, "center fix enabled from cursor tab");
				ConfigStore.load();
				check(ConfigStore.get().centerMouseFix, "center fix survives reload");
			});
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				clickLabel(mc.screen, "screen.kohs_inventory_tweaks.tweaks.visuals");
			});
			screenshot(mc, "pvp-tweaks-visuals-gui" + scale);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				clickTweak(mc.screen, 0);
				check(field(mc.screen, "modal").toString().equals("TWEAK_WARNING"), "visual warning is shown");
			});
			screenshot(mc, "pvp-tweaks-visual-warning-gui" + scale);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				clickLabel(mc.screen, "screen.kohs_inventory_tweaks.remove_animations.warning.enable");
				check(ConfigStore.get().reduceInventoryMotion, "accepted motion option persists");
				ConfigStore.load();
				check(ConfigStore.get().reduceInventoryMotion, "motion option survives reload");
				check(mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)), "Escape leaves tweaks modal");
				check(field(mc.screen, "modal").toString().equals("NONE"), "Escape actually returns to main page");
				mc.setScreen(null);
			});
		}
	}
}
