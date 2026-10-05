package dev.zymekoh.kohsinventorydebug;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SlotTargeting;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.render.SlotOverlays;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The 1.2.0 inventory features, checked in a real inventory and captured for review.
 *
 * <p>Totem protection is checked against the integrated server: a declined swap or
 * drop must leave the totem where it was there too, and the same press with the
 * protection off must go through, so the check cannot pass by doing nothing. The
 * rest checks pixel-perfect scaling, the recipe book lock, steady tooltips, inventory
 * totals, key labels and the shortcut target switch, then captures the overlays. Keys
 * go through the ordinary keyboard handler and the pointer through the ordinary move
 * callback; the player's real pointer is untouched. Singleplayer lab world only.</p>
 */
public final class FeatureLab {
	/** Menu slots of the player inventory: main grid starts at 9, hotbar at 36, offhand 45. */
	private static final int EMPTY_SLOT = 11;
	private static final int TOTEM_SLOT = 9;
	private static final int TOTALS_SLOT = 13;
	private static final int OFFHAND_SLOT = 45;
	private static final long FRAME_MILLIS = 110L;
	private static int checks;
	private static int failures;
	// Written by the timing mixin on the render thread, read by the lab after each block.
	private static volatile long overlayNanos;
	private static volatile long overlayFrames;
	private static long overlayStarted;

	private FeatureLab() {
	}

	public static void overlayStart() {
		overlayStarted = System.nanoTime();
	}

	public static void overlayEnd() {
		overlayNanos += System.nanoTime() - overlayStarted;
	}

	public static void overlayFrame() {
		overlayFrames++;
	}

	public static void run(final Minecraft mc) {
		try {
			checked(mc);
		} catch (RuntimeException error) {
			throw error;
		} catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static void checked(final Minecraft mc) throws Exception {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		InventoryTweaksConfig saved = ConfigStore.get().copy();
		int gui = mc.options.guiScale().get();
		boolean book = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
		checks = 0;
		failures = 0;
		try {
			stage(mc);
			labels();
			pixelPerfect(mc);
			configure(mc, config -> {
				config.inventoryGuiScalerEnabled = true;
				config.inventoryGuiScale = 1.799152933573374;
				config.pixelPerfectScale = true;
				config.shortcutsFollowPointer = true;
				config.swapWarning = true;
				config.heldSlotMarker = true;
				config.keyHints = true;
				config.durabilityReadout = true;
				config.itemTotals = true;
				config.slotFlash = true;
				config.steadyTooltips = true;
				config.recipeBookLock = true;
				config.totemGuard = false;
				config.reduceInventoryMotion = false;
				return null;
			}, 3);
			recipeBookLock(mc);
			openInventory(mc);
			hover(mc, EMPTY_SLOT);
			UiShowcaseLab.screenshot(mc, "features-overlays");
			configure(mc, config -> config.totemGuard = true, 0);
			hover(mc, EMPTY_SLOT);
			UiShowcaseLab.screenshot(mc, "features-swap-guarded");
			totemGuard(mc);
			steadyTooltips(mc);
			totals(mc);
			shortcutTarget(mc);
			flash(mc);
			tweaksList(mc);
			DebugCollector.info("FEATURE_LAB_SUMMARY", "checks=" + checks + "; failures=" + failures);
			if (failures > 0) throw new IllegalStateException("Feature lab failures=" + failures);
		} finally {
			mc.executeBlocking(() -> {
				if (mc.screen != null) mc.setScreen(null);
				ConfigStore.replaceAndSave(saved);
				mc.options.guiScale().set(gui);
				mc.resizeDisplay();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
			});
		}
	}

	/**
	 * Frame rate with the slot overlays on against off, in an open inventory with the
	 * frame cap and the AFK limit lifted, and the render-thread time the overlays take
	 * per frame, measured around every call. Rounds alternate so drift hits both sides.
	 */
	public static void perf(final Minecraft mc) {
		InventoryTweaksConfig saved = ConfigStore.get().copy();
		int limit = mc.options.framerateLimit().get();
		var inactivity = mc.options.inactivityFpsLimit().get();
		boolean vsync = mc.options.enableVsync().get();
		try {
			stage(mc);
			mc.executeBlocking(() -> {
				mc.options.framerateLimit().set(260);
				mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
				mc.options.enableVsync().set(false);
			});
			openInventory(mc);
			hover(mc, EMPTY_SLOT);
			StringBuilder report = new StringBuilder();
			for (int round = 0; round < 3; round++) {
				for (boolean on : new boolean[] {false, true}) {
					configure(mc, config -> {
						config.keyHints = on;
						config.durabilityReadout = on;
						config.heldSlotMarker = on;
						config.swapWarning = on;
						config.slotFlash = on;
						config.itemTotals = on;
						config.steadyTooltips = on;
						return null;
					}, 3);
					Thread.sleep(1500);
					overlayNanos = 0L;
					overlayFrames = 0L;
					int total = 0;
					for (int second = 0; second < 4; second++) {
						Thread.sleep(1000);
						int[] fps = new int[1];
						mc.executeBlocking(() -> fps[0] = mc.getFps());
						total += fps[0];
					}
					report.append(on ? " on=" : " off=").append(total / 4).append("fps/")
						.append(overlayFrames == 0 ? 0 : overlayNanos / overlayFrames / 1000).append("us");
				}
			}
			DebugCollector.info("OVERLAY_PERF", report.toString().trim());
		} catch (Exception error) {
			throw new IllegalStateException(error);
		} finally {
			mc.executeBlocking(() -> {
				if (mc.screen != null) mc.setScreen(null);
				ConfigStore.replaceAndSave(saved);
				mc.options.framerateLimit().set(limit);
				mc.options.inactivityFpsLimit().set(inactivity);
				mc.options.enableVsync().set(vsync);
			});
		}
	}

	/** Totems, damaged gear, empty slots in the top row and the third hotbar slot in hand. */
	private static void stage(final Minecraft mc) throws Exception {
		command(mc, "gamemode survival");
		command(mc, "clear @s");
		command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
		command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword[damage=1800]");
		command(mc, "item replace entity @s hotbar.1 with minecraft:end_crystal 64");
		command(mc, "item replace entity @s hotbar.2 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s hotbar.3 with minecraft:golden_apple 32");
		command(mc, "item replace entity @s hotbar.4 with minecraft:ender_pearl 16");
		command(mc, "item replace entity @s hotbar.5 with minecraft:obsidian 64");
		command(mc, "item replace entity @s hotbar.6 with minecraft:respawn_anchor 16");
		command(mc, "item replace entity @s hotbar.7 with minecraft:glowstone 64");
		command(mc, "item replace entity @s hotbar.8 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.0 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.1 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.4 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.5 with minecraft:ender_pearl 16");
		command(mc, "item replace entity @s inventory.9 with minecraft:netherite_pickaxe[damage=700]");
		command(mc, "item replace entity @s armor.head with minecraft:netherite_helmet[damage=200]");
		command(mc, "item replace entity @s armor.chest with minecraft:netherite_chestplate[damage=300]");
		command(mc, "item replace entity @s armor.legs with minecraft:netherite_leggings");
		command(mc, "item replace entity @s armor.feet with minecraft:netherite_boots[damage=400]");
		// The hotbar key goes through the world keybind pass, as a player's would.
		mc.executeBlocking(() -> {
			if (mc.screen != null) mc.setScreen(null);
		});
		tap(mc, mc.options.keyHotbarSlots[2]);
		Thread.sleep(1500);
		// The staging commands unlock recipes and advancements; their toasts would cover the captures.
		mc.executeBlocking(() -> {
			mc.gui.getChat().clearMessages(false);
			mc.getToastManager().clear();
		});
		check(mc.player.getInventory().getSelectedSlot() == 2, "the third hotbar slot is held");
	}

	private static void labels() {
		check(SlotOverlays.shortLabel("R").equals("R"), "a one-letter key stays itself");
		check(SlotOverlays.shortLabel("Left Shift").equals("LS"), "Left Shift reads LS");
		check(SlotOverlays.shortLabel("Button 4").equals("B4"), "a mouse button keeps its number");
		check(SlotOverlays.shortLabel("Keypad 7").equals("K7"), "keypad keys keep their number");
	}

	/** Whole screen pixels per GUI pixel with the switch on, the requested scale with it off. */
	private static void pixelPerfect(final Minecraft mc) throws Exception {
		for (int scale = 2; scale <= 4; scale++) {
			for (double physical : new double[] {0.9, 1.3, 1.799152933573374, 2.000097708843035, 2.6}) {
				int guiScale = scale;
				double[] pixels = new double[2];
				mc.executeBlocking(() -> {
					mc.options.guiScale().set(guiScale);
					mc.resizeDisplay();
					InventoryTweaksConfig config = ConfigStore.get().copy();
					config.inventoryGuiScalerEnabled = true;
					config.inventoryGuiScale = physical;
					int width = mc.getWindow().getGuiScaledWidth();
					int height = mc.getWindow().getGuiScaledHeight();
					config.pixelPerfectScale = true;
					pixels[0] = InventoryGuiScaler.appliedScale(width, height, config) * mc.getWindow().getGuiScale();
					config.pixelPerfectScale = false;
					pixels[1] = InventoryGuiScaler.appliedScale(width, height, config) * mc.getWindow().getGuiScale();
				});
				String label = "gui=" + guiScale + "; scale=" + physical;
				check(Math.abs(pixels[0] - Math.rint(pixels[0])) < 1.0E-6, "pixel-perfect draws whole pixels; " + label + "; pixels=" + pixels[0]);
				check(Math.abs(pixels[0] - physical * 2.0) <= 0.5 + 1.0E-6 || pixels[1] < physical * 2.0 - 1.0E-6,
					"pixel-perfect stays within half a pixel of the request; " + label + "; pixels=" + pixels[0]);
				check(pixels[1] <= physical * 2.0 + 1.0E-6, "with the switch off the request is drawn as it is; " + label + "; pixels=" + pixels[1]);
			}
		}
	}

	private static void recipeBookLock(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false));
		openInventory(mc);
		check(recipeButtons(mc) == 0, "a closed book loses its button with the lock on");
		UiShowcaseLab.screenshot(mc, "features-recipe-lock");
		configure(mc, config -> config.recipeBookLock = false, 0);
		openInventory(mc);
		check(recipeButtons(mc) == 1, "the button is back with the lock off");
		mc.executeBlocking(() -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true));
		configure(mc, config -> config.recipeBookLock = true, 0);
		openInventory(mc);
		check(recipeButtons(mc) == 1, "an open book keeps its button so it can be closed");
		mc.executeBlocking(() -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false));
	}

	private static void totemGuard(final Minecraft mc) throws Exception {
		configure(mc, config -> config.totemGuard = true, 0);
		openInventory(mc);
		hover(mc, EMPTY_SLOT);
		tap(mc, mc.options.keySwapOffhand);
		check(serverOffhandIsTotem(mc), "the offhand key over an empty slot keeps the offhand totem");
		hover(mc, TOTEM_SLOT);
		tap(mc, mc.options.keyDrop);
		check(serverSlotIsTotem(mc, TOTEM_SLOT), "the drop key never throws a totem");
		hover(mc, OFFHAND_SLOT);
		tap(mc, mc.options.keyHotbarSlots[0]);
		check(serverOffhandIsTotem(mc), "a hotbar key over the offhand slot keeps the totem there");
		// The same swap with the protection off must go through, or the checks above prove nothing.
		configure(mc, config -> config.totemGuard = false, 0);
		hover(mc, EMPTY_SLOT);
		tap(mc, mc.options.keySwapOffhand);
		check(!serverOffhandIsTotem(mc) && serverSlotIsTotem(mc, EMPTY_SLOT), "with protection off the swap moves the totem");
		hover(mc, EMPTY_SLOT);
		tap(mc, mc.options.keySwapOffhand);
		check(serverOffhandIsTotem(mc), "the totem goes back to the offhand");
		configure(mc, config -> config.totemGuard = true, 0);
	}

	private static void steadyTooltips(final Minecraft mc) throws Exception {
		openInventory(mc);
		hover(mc, OFFHAND_SLOT);
		Thread.sleep(300);
		moveTo(mc, TOTALS_SLOT);
		boolean held = false;
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(140);
		while (System.nanoTime() < deadline && !held) {
			boolean[] state = new boolean[1];
			mc.executeBlocking(() -> state[0] = hoveredIndex(mc) == TOTALS_SLOT && SlotOverlays.holdsTooltip());
			held = state[0];
			Thread.sleep(5);
		}
		check(held, "a tooltip waits right after the pointer reaches a slot");
		Thread.sleep(250);
		boolean[] released = new boolean[1];
		mc.executeBlocking(() -> released[0] = !SlotOverlays.holdsTooltip());
		check(released[0], "the tooltip shows once the pointer rests");
	}

	private static void totals(final Minecraft mc) throws Exception {
		int[] expected = new int[1];
		String[] line = new String[1];
		mc.executeBlocking(() -> {
			Inventory inventory = mc.player.getInventory();
			for (int index = 0; index < inventory.getContainerSize(); index++) {
				if (inventory.getItem(index).is(Items.TOTEM_OF_UNDYING)) expected[0] += inventory.getItem(index).getCount();
			}
			Component total = SlotOverlays.inventoryTotal(new ItemStack(Items.TOTEM_OF_UNDYING));
			line[0] = total == null ? "" : total.getString();
		});
		check(line[0].contains(Integer.toString(expected[0])), "inventory totals count every totem carried; line=" + line[0] + "; expected=" + expected[0]);
		hover(mc, TOTALS_SLOT);
		Thread.sleep(300);
		UiShowcaseLab.screenshot(mc, "features-totals-tooltip");
	}

	/** Off: shortcuts keep the slot the last frame highlighted. On: they follow the pointer. */
	private static void shortcutTarget(final Minecraft mc) throws Exception {
		if (!mc.isWindowActive()) {
			DebugCollector.info("FEATURE_LAB_SKIP", "shortcut target needs a focused window");
			return;
		}
		for (boolean follow : new boolean[] {false, true}) {
			configure(mc, config -> config.shortcutsFollowPointer = follow, 0);
			hover(mc, TOTEM_SLOT);
			int[] after = new int[1];
			double[] target = slotCenter(mc, TOTALS_SLOT);
			mc.executeBlocking(() -> {
				((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), target[0], target[1]);
				SlotTargeting.refreshFromPointer((AbstractContainerScreen<?>) mc.screen);
				after[0] = hoveredIndex(mc);
			});
			check(after[0] == (follow ? TOTALS_SLOT : TOTEM_SLOT), "shortcutsFollowPointer=" + follow + " targets slot " + after[0]);
		}
	}

	private static void flash(final Minecraft mc) throws Exception {
		// Pearls in menu slot 14 trade places with the golden apples of the fourth hotbar slot.
		hover(mc, 14);
		press(mc, mc.options.keyHotbarSlots[3]);
		UiShowcaseLab.screenshotAfter(mc, "features-flash", 40);
		Thread.sleep(300);
		tap(mc, mc.options.keyHotbarSlots[3]);
	}

	private static void tweaksList(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
		Thread.sleep(400);
		String label = Component.translatable("screen.kohs_inventory_tweaks.inventory_tweaks").getString();
		mc.executeBlocking(() -> {
			for (var child : mc.screen.children()) {
				if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getMessage().getString().equals(label)) {
					widget.onClick(new net.minecraft.client.input.MouseButtonEvent(widget.getX() + 2, widget.getY() + 2, new MouseButtonInfo(0, 0)), false);
					return;
				}
			}
		});
		Thread.sleep(500);
		parkPointer(mc);
		UiShowcaseLab.screenshot(mc, "features-tweaks-top");
		mc.executeBlocking(() -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0.0, -40.0));
		Thread.sleep(700);
		UiShowcaseLab.screenshot(mc, "features-tweaks-bottom");
		mc.executeBlocking(() -> mc.setScreen(null));
	}

	private static int recipeButtons(final Minecraft mc) throws Exception {
		int[] count = new int[1];
		mc.executeBlocking(() -> {
			for (var child : mc.screen.children()) {
				if (child instanceof ImageButton) count[0]++;
			}
		});
		return count[0];
	}

	private static void openInventory(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(400);
	}

	/** Points at a menu slot and waits for a frame, so the drawn highlight follows. */
	private static void hover(final Minecraft mc, final int slot) throws Exception {
		moveTo(mc, slot);
		Thread.sleep(FRAME_MILLIS * 2);
	}

	private static void moveTo(final Minecraft mc, final int slot) throws Exception {
		double[] target = slotCenter(mc, slot);
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler)
			.kohsInventoryDebug$invokeMove(mc.getWindow().handle(), target[0], target[1]));
	}

	private static void parkPointer(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), 1.0, 1.0));
		Thread.sleep(250);
	}

	/** The window pixel at the centre of a menu slot, through the inventory's surface scale. */
	private static double[] slotCenter(final Minecraft mc, final int index) throws Exception {
		double[] result = new double[2];
		mc.executeBlocking(() -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			Slot slot = screen.getMenu().getSlot(index);
			double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
			double x = access.kohsInventoryTweaks$getLeftPos() + slot.x + 8;
			double y = access.kohsInventoryTweaks$getTopPos() + slot.y + 8;
			double guiX = screen.width * 0.5 + (x - screen.width * 0.5) * scale;
			double guiY = screen.height * 0.5 + (y - screen.height * 0.5) * scale;
			result[0] = Math.floor(guiX * mc.getWindow().getScreenWidth() / screen.width);
			result[1] = Math.floor(guiY * mc.getWindow().getScreenHeight() / screen.height);
		});
		return result;
	}

	private static int hoveredIndex(final Minecraft mc) {
		Slot slot = ((AbstractContainerScreenAccessor) mc.screen).kohsInventoryTweaks$getHoveredSlot();
		return slot == null ? -1 : slot.index;
	}

	private static void configure(final Minecraft mc, final Function<InventoryTweaksConfig, Object> change, final int guiScale) throws Exception {
		mc.executeBlocking(() -> {
			change.apply(ConfigStore.get());
			if (guiScale > 0) {
				mc.options.guiScale().set(guiScale);
				mc.resizeDisplay();
			}
		});
		Thread.sleep(150);
	}

	private static void tap(final Minecraft mc, final KeyMapping mapping) throws Exception {
		press(mc, mapping);
		Thread.sleep(250);
	}

	/** One press and release through the keyboard handler, without waiting afterwards. */
	private static void press(final Minecraft mc, final KeyMapping mapping) throws Exception {
		InputConstants.Key key = ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey();
		if (key.getType() != InputConstants.Type.KEYSYM) throw new IllegalStateException("Keyboard binding required: " + mapping.getName());
		KeyEvent event = new KeyEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
		mc.executeBlocking(() -> {
			KeyboardHandlerDebugInvoker keyboard = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
			keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
			keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
		});
	}

	private static boolean serverOffhandIsTotem(final Minecraft mc) throws Exception {
		return server(mc, player -> player.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
	}

	private static boolean serverSlotIsTotem(final Minecraft mc, final int menuSlot) throws Exception {
		return server(mc, player -> player.inventoryMenu.getSlot(menuSlot).getItem().is(Items.TOTEM_OF_UNDYING));
	}

	private static boolean server(final Minecraft mc, final java.util.function.Predicate<ServerPlayer> test) throws Exception {
		Thread.sleep(150);
		var server = mc.getSingleplayerServer();
		var id = mc.player.getUUID();
		return server.submit(() -> test.test(server.getPlayerList().getPlayer(id))).get(5, TimeUnit.SECONDS);
	}

	private static void command(final Minecraft mc, final String command) throws Exception {
		mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
		Thread.sleep(120);
	}

	private static void check(final boolean passed, final String reason) {
		checks++;
		if (!passed) {
			failures++;
			DebugCollector.issue("FEATURE_LAB_FAIL", reason);
		}
	}
}
