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
import dev.zymekoh.kohsinventorytweaks.screen.GuiScalerScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.KohsScreen;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * The 1.2.0 options, checked in a real inventory and captured for review: the
 * pixel-perfect sizes of the GUI Scaler, the shortcut target and the Super Fast
 * Inventory it belongs to, the Inventory Tweaks page as a tree whose sub-option
 * sleeps with its parent, and the KoHs tab, where Zymekoh gets cross when the mascot
 * is carried to her face. Keys go through the ordinary keyboard handler and the
 * pointer through the ordinary move callback; the player's real pointer is untouched.
 * Singleplayer lab world only.
 */
public final class FeatureLab {
	/** Menu slots of the player inventory: the main grid starts at 9. */
	private static final int TOTEM_SLOT = 9;
	private static final int TARGET_SLOT = 13;
	private static final long FRAME_MILLIS = 110L;
	private static int checks;
	private static int failures;

	private FeatureLab() {
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
		checks = 0;
		failures = 0;
		try {
			stage(mc);
			pixelPerfect(mc);
			configure(mc, config -> {
				config.inventoryGuiScalerEnabled = true;
				config.inventoryGuiScale = 1.799152933573374;
				config.pixelPerfectScale = true;
				config.superFastInventory = true;
				config.shortcutsFollowPointer = true;
				config.reduceInventoryMotion = false;
				return null;
			}, 3);
			openInventory(mc);
			shortcutTarget(mc);
			tree(mc);
			kohsTab(mc);
			DebugCollector.info("FEATURE_LAB_SUMMARY", "checks=" + checks + "; failures=" + failures);
			if (failures > 0) throw new IllegalStateException("Feature lab failures=" + failures);
		} finally {
			mc.executeBlocking(() -> {
				if (mc.screen != null) mc.setScreen(null);
				ConfigStore.replaceAndSave(saved);
				mc.options.guiScale().set(gui);
				mc.resizeDisplay();
			});
		}
	}

	/** Totems in two top-row slots, so a shortcut target is easy to tell apart. */
	private static void stage(final Minecraft mc) throws Exception {
		command(mc, "gamemode survival");
		command(mc, "clear @s");
		command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
		command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
		command(mc, "item replace entity @s hotbar.1 with minecraft:end_crystal 64");
		command(mc, "item replace entity @s hotbar.2 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.0 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.4 with minecraft:totem_of_undying");
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

	/**
	 * The slot the shortcuts take right after a flick: under the pointer only while both
	 * the option and Super Fast Inventory, its parent, are on; otherwise the slot the last
	 * frame highlighted, as in Vanilla.
	 */
	private static void shortcutTarget(final Minecraft mc) throws Exception {
		if (!mc.isWindowActive()) {
			DebugCollector.info("FEATURE_LAB_SKIP", "shortcut target needs a focused window");
			return;
		}
		boolean[][] cases = {{true, false}, {true, true}, {false, true}};
		for (boolean[] each : cases) {
			boolean fast = each[0];
			boolean follow = each[1];
			configure(mc, config -> {
				config.superFastInventory = fast;
				config.shortcutsFollowPointer = follow;
				return null;
			}, 0);
			hover(mc, TOTEM_SLOT);
			int[] after = new int[1];
			double[] target = slotCenter(mc, TARGET_SLOT);
			mc.executeBlocking(() -> {
				((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), target[0], target[1]);
				SlotTargeting.refreshFromPointer((AbstractContainerScreen<?>) mc.screen);
				after[0] = hoveredIndex(mc);
			});
			int expected = fast && follow ? TARGET_SLOT : TOTEM_SLOT;
			check(after[0] == expected, "superFastInventory=" + fast + "; shortcutsFollowPointer=" + follow + " targets slot " + after[0]);
		}
	}

	/** The page as a tree: the sub-option follows its parent, and its switch sleeps while the parent is off. */
	private static void tree(final Minecraft mc) throws Exception {
		configure(mc, config -> {
			config.superFastInventory = true;
			config.shortcutsFollowPointer = true;
			return null;
		}, 0);
		mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
		Thread.sleep(400);
		String label = Component.translatable("screen.kohs_inventory_tweaks.inventory_tweaks").getString();
		mc.executeBlocking(() -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label)) {
					widget.onClick(new MouseButtonEvent(widget.getX() + 2, widget.getY() + 2, new MouseButtonInfo(0, 0)), false);
					return;
				}
			}
		});
		Thread.sleep(500);
		parkPointer(mc);
		List<?> options = (List<?>) field(mc.screen, "visibleTweaks");
		check(options.toString().equals("[FAST, POINTER, CENTER, ANIMATIONS, MASCOT]"), "the page is the three options, the sub-option and the mascot; " + options);
		List<?> switches = (List<?>) field(mc.screen, "tweakScrollingWidgets");
		check(switches.size() == options.size(), "every option has its switch; switches=" + switches.size());
		check(((AbstractWidget) switches.get(1)).active, "the sub-option works with Super Fast Inventory on");
		UiShowcaseLab.screenshot(mc, "features-tree");
		click(mc, (AbstractWidget) switches.get(0));
		check(!ConfigStore.get().superFastInventory, "the parent switch turns Super Fast Inventory off");
		check(!((AbstractWidget) switches.get(1)).active, "the sub-option sleeps with its parent off");
		check(ConfigStore.get().shortcutsFollowPointer, "the sub-option keeps its own value while it sleeps");
		UiShowcaseLab.screenshot(mc, "features-tree-parent-off");
		click(mc, (AbstractWidget) switches.get(0));
		check(((AbstractWidget) switches.get(1)).active, "the sub-option wakes with its parent");

		// Pixel-perfect scale lives with the GUI Scaler it shapes.
		mc.executeBlocking(() -> mc.setScreen(new GuiScalerScreen(null)));
		Thread.sleep(600);
		String on = Component.translatable("screen.kohs_inventory_tweaks.pixel_perfect_scale.short").getString();
		AbstractWidget[] pixel = new AbstractWidget[1];
		mc.executeBlocking(() -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(on)) pixel[0] = widget;
			}
		});
		check(pixel[0] != null && ConfigStore.get().pixelPerfectScale, "the GUI Scaler page has the pixel-perfect switch, on");
		UiShowcaseLab.screenshot(mc, "features-gui-scaler");
		if (pixel[0] != null) {
			click(mc, pixel[0]);
			check(!ConfigStore.get().pixelPerfectScale, "the switch turns pixel-perfect scale off");
			click(mc, pixel[0]);
			check(ConfigStore.get().pixelPerfectScale, "and back on");
		}
		mc.executeBlocking(() -> mc.setScreen(null));
	}

	/**
	 * The KoHs tab: its card opens it, it draws Zymekoh, and she gets cross while the mascot
	 * is carried to her face, then calms down once it is taken away.
	 */
	private static void kohsTab(final Minecraft mc) throws Exception {
		ZMascot.Prefs prefs = ZMascot.prefs();
		boolean enabled = prefs.enabled;
		prefs.enabled = true;
		try {
			mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
			Thread.sleep(500);
			String label = Component.translatable("screen.kohs_inventory_tweaks.kohs").getString();
			mc.executeBlocking(() -> {
				for (var child : mc.screen.children()) {
					if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(label)) {
						widget.onClick(new MouseButtonEvent(widget.getX() + 2, widget.getY() + 2, new MouseButtonInfo(0, 0)), false);
						return;
					}
				}
			});
			Thread.sleep(1800);
			check(mc.screen instanceof KohsScreen, "the KoHs card opens the KoHs tab; screen=" + (mc.screen == null ? null : mc.screen.getClass().getSimpleName()));
			if (!(mc.screen instanceof KohsScreen kohs)) return;
			float faceX = ((Number) field(kohs, "faceShownX")).floatValue();
			float faceY = ((Number) field(kohs, "faceShownY")).floatValue();
			check(faceX > 0 && faceY > 0, "the tab draws Zymekoh's face; face=" + faceX + "," + faceY);
			UiShowcaseLab.screenshot(mc, "features-kohs");
			double[] head = mascotHead();
			for (int settle = 0; settle < 4; settle++) {
				pointAt(mc, head[0], head[1]);
				Thread.sleep(90);
				head = mascotHead();
			}
			press(mc, true);
			Thread.sleep(80);
			carry(mc, head, faceX, faceY);
			// It dangles below the pointer: lift it until its head is at her face, wherever the window puts her.
			double[] pointer = {faceX, faceY};
			for (int nudge = 0; nudge < 4; nudge++) {
				Thread.sleep(300);
				float[] held = ZMascot.carriedHead();
				if (held == null) break;
				pointer = new double[] {pointer[0] + faceX - held[0], pointer[1] + faceY - held[1]};
				pointAt(mc, pointer[0], pointer[1]);
			}
			Thread.sleep(1200);
			float cross = ((Number) field(kohs, "anger")).floatValue();
			check(cross > 0.5F, "Zymekoh gets cross with the mascot at her face; anger=" + cross + "; held=" + (ZMascot.carriedHead() != null));
			UiShowcaseLab.screenshot(mc, "features-kohs-cross");
			carry(mc, pointer, pointer[0] + 220, pointer[1]);
			press(mc, false);
			Thread.sleep(2600);
			float calm = ((Number) field(kohs, "anger")).floatValue();
			check(calm < 0.2F, "and calms down once it is taken away; anger=" + calm);
		} finally {
			prefs.enabled = enabled;
			mc.executeBlocking(() -> mc.setScreen(null));
		}
	}

	/** The GUI point the mascot is picked up by: the middle of its head. */
	private static double[] mascotHead() throws Exception {
		int unit = mascotInt("unit");
		return new double[] {mascotInt("left") + 10.0 * unit, mascotInt("top") + 8.0 * unit};
	}

	private static int mascotInt(final String name) throws Exception {
		var field = ZMascot.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(null);
	}

	/** Moves the pointer to a GUI point through the ordinary move callback. */
	private static void pointAt(final Minecraft mc, final double guiX, final double guiY) {
		mc.executeBlocking(() -> {
			var window = mc.getWindow();
			((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(window.handle(),
				guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
				guiY * window.getScreenHeight() / window.getGuiScaledHeight());
		});
	}

	/** Carries what the left button holds from one GUI point to another in about a second, as a hand would. */
	private static void carry(final Minecraft mc, final double[] from, final double toX, final double toY) throws Exception {
		for (int step = 1; step <= 40; step++) {
			double t = step / 40.0;
			pointAt(mc, from[0] + (toX - from[0]) * t, from[1] + (toY - from[1]) * t);
			Thread.sleep(25);
		}
	}

	private static void press(final Minecraft mc, final boolean down) {
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler)
			.kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(0, 0), down ? 1 : 0));
	}

	private static void click(final Minecraft mc, final AbstractWidget widget) throws Exception {
		mc.executeBlocking(() -> widget.onClick(new MouseButtonEvent(widget.getX() + 2, widget.getY() + 2, new MouseButtonInfo(0, 0)), false));
		Thread.sleep(300);
	}

	private static Object field(final Object owner, final String name) throws Exception {
		var field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static void openInventory(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(400);
	}

	/** Points at a menu slot and waits for a frame, so the drawn highlight follows. */
	private static void hover(final Minecraft mc, final int slot) throws Exception {
		double[] target = slotCenter(mc, slot);
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler)
			.kohsInventoryDebug$invokeMove(mc.getWindow().handle(), target[0], target[1]));
		Thread.sleep(FRAME_MILLIS * 2);
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

	/** One press and release through the keyboard handler. */
	private static void tap(final Minecraft mc, final KeyMapping mapping) throws Exception {
		InputConstants.Key key = ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey();
		if (key.getType() != InputConstants.Type.KEYSYM) throw new IllegalStateException("Keyboard binding required: " + mapping.getName());
		KeyEvent event = new KeyEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
		mc.executeBlocking(() -> {
			KeyboardHandlerDebugInvoker keyboard = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
			keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
			keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
		});
		Thread.sleep(250);
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
