package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.HotbarStyle;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.NextGenStyle;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.PartStyle;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.TabStyle;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.EffectsPlacement;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.render.NextGenHotbar;
import dev.zymekoh.kohsinventorytweaks.screen.AdvancedSettingsScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.NextGenInventoriesScreen;
import dev.zymekoh.kohsinventorytweaks.screen.NextGenLayoutScreen;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.lwjgl.glfw.GLFW;

/**
 * The tabs, tooltips and buttons theme and the Next Gen inventories, in one launch: every style is
 * drawn and captured for review, and in the moved layouts every slot is found under the pointer at
 * its new place, a click reaches the server through its own slot, and the slots go back when the
 * screen closes. Singleplayer lab world only.
 */
public final class InterfaceThemeLab {
	private static final String BACKGROUND = "lab-interface.png";
	private static int checks;
	private static int failures;

	private InterfaceThemeLab() {
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
		checks = 0;
		failures = 0;
		try {
			stage(mc);
			writeBackground();
			tabsAndTooltips(mc);
			settingsPage(mc);
			nextGenEntry(mc);
			defaults();
			nextGenStyles(mc);
			customEditor(mc);
			creativeVariants(mc);
			hotbars(mc);
			DebugCollector.info("INTERFACE_LAB_SUMMARY", "checks=" + checks + "; failures=" + failures);
			System.out.println("[lab] interface-theme checks=" + checks + " failures=" + failures);
			if (failures > 0) throw new IllegalStateException("Interface lab failures=" + failures);
		} finally {
			mc.executeBlocking(() -> {
				if (mc.screen != null) mc.setScreen(null);
				ConfigStore.replaceAndSave(saved);
				InventoryTextureManager.invalidateConfiguration();
			});
		}
	}

	private static void stage(final Minecraft mc) throws Exception {
		command(mc, "gamemode survival");
		command(mc, "clear @s");
		command(mc, "effect clear @s");
		command(mc, "effect give @s minecraft:speed 900 1");
		command(mc, "effect give @s minecraft:strength 900 0");
		command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
		command(mc, "item replace entity @s armor.head with minecraft:netherite_helmet");
		command(mc, "item replace entity @s armor.chest with minecraft:netherite_chestplate");
		command(mc, "item replace entity @s armor.legs with minecraft:netherite_leggings");
		command(mc, "item replace entity @s armor.feet with minecraft:netherite_boots");
		command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
		command(mc, "item replace entity @s hotbar.1 with minecraft:end_crystal 64");
		command(mc, "item replace entity @s hotbar.8 with minecraft:golden_apple 16");
		command(mc, "item replace entity @s inventory.0 with minecraft:totem_of_undying");
		command(mc, "item replace entity @s inventory.13 with minecraft:obsidian 32");
		command(mc, "item replace entity @s inventory.26 with minecraft:ender_pearl 16");
		Thread.sleep(1200);
		mc.executeBlocking(() -> {
			mc.gui.getChat().clearMessages(false);
			mc.getToastManager().clear();
		});
	}

	private static void writeBackground() throws Exception {
		BufferedImage image = new BufferedImage(176, 166, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int wave = (x * 3 + y * 5) % 64;
				image.setRGB(x, y, new Color(96 + wave / 2, 12 + wave / 6, 18 + wave / 5).getRGB());
			}
		}
		Path file = ConfigStore.resolveBackground(BACKGROUND);
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}

	private static void tabsAndTooltips(final Minecraft mc) throws Exception {
		configure(mc, config -> {
			config.frameColor = 0xE04040;
			config.frameOpacity = 255;
			config.customBackgroundFile = BACKGROUND;
			config.backgroundOpacity = 255;
			config.tabStyle = TabStyle.FOLLOW;
			config.tooltipStyle = PartStyle.VANILLA;
			config.buttonStyle = PartStyle.VANILLA;
			config.hideRecipeBook = false;
			config.nextGenStyle = NextGenStyle.OFF;
		});
		command(mc, "gamemode creative");
		Thread.sleep(400);
		openCreative(mc);
		shot(mc, "interface-1-tabs-match-all");
		configure(mc, config -> config.tabStyle = TabStyle.COLOR);
		shot(mc, "interface-2-tabs-match-colour");
		configure(mc, config -> {
			config.tabStyle = TabStyle.CUSTOM;
			config.tabColor = 0x40D0FF;
		});
		shot(mc, "interface-3-tabs-custom");
		configure(mc, config -> {
			config.tabStyle = TabStyle.VANILLA;
		});
		shot(mc, "interface-4-tabs-vanilla");
		configure(mc, config -> config.tabStyle = TabStyle.FOLLOW);
		check(mc.screen instanceof CreativeModeInventoryScreen, "the creative screen stayed open");
		command(mc, "gamemode survival");
		Thread.sleep(500);
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(150);

		int gui = mc.options.guiScale().get();
		setGuiScale(mc, 2);
		setBookOpen(mc, true);
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(500);
		configure(mc, config -> {
			config.tooltipStyle = PartStyle.INVENTORY;
		});
		pointAtSlot(mc, 36);
		shot(mc, "interface-5-recipe-effects-tooltip-inventory");
		configure(mc, config -> {
			config.tooltipStyle = PartStyle.CUSTOM;
			config.tooltipBackgroundColor = 0x061A2E;
			config.tooltipFrameColor = 0x40D0FF;
			config.tooltipOpacity = 240;
		});
		shot(mc, "interface-6-tooltip-custom");
		setBookOpen(mc, false);
		setGuiScale(mc, gui);
		configure(mc, config -> {
			config.tooltipStyle = PartStyle.VANILLA;
			config.buttonStyle = PartStyle.INVENTORY;
		});
		mc.executeBlocking(() -> mc.setScreen(new TitleScreen()));
		Thread.sleep(900);
		shot(mc, "interface-7-buttons-inventory");
		configure(mc, config -> {
			config.buttonStyle = PartStyle.CUSTOM;
			config.buttonColor = 0x40D0FF;
		});
		shot(mc, "interface-8-buttons-custom");
		configure(mc, config -> config.buttonStyle = PartStyle.VANILLA);
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(300);
	}

	private static void settingsPage(final Minecraft mc) throws Exception {
		configure(mc, config -> {
			config.tabStyle = TabStyle.CUSTOM;
			config.tooltipStyle = PartStyle.INVENTORY;
			config.buttonStyle = PartStyle.CUSTOM;
		});
		int gui = mc.options.guiScale().get();
		setGuiScale(mc, 2);
		mc.executeBlocking(() -> mc.setScreen(AdvancedSettingsScreen.interfaceTheme(null)));
		Thread.sleep(600);
		shot(mc, "interface-9-settings-page");
		setGuiScale(mc, gui);
		configure(mc, config -> {
			config.tabStyle = TabStyle.FOLLOW;
			config.tooltipStyle = PartStyle.VANILLA;
			config.buttonStyle = PartStyle.VANILLA;
		});
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
	}

	/** The card, the warning in front of it, and the page behind the warning. */
	private static void nextGenEntry(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
		Thread.sleep(500);
		shot(mc, "next-gen-1-main-menu");
		String card = Component.translatable("screen.kohs_inventory_tweaks.next_gen").getString();
		check(press(mc, card), "the main menu has the Next Gen card");
		Thread.sleep(500);
		check(!(mc.screen instanceof NextGenInventoriesScreen), "the card shows the warning first, it does not open the page");
		shot(mc, "next-gen-2-warning");
		String agree = Component.translatable("screen.kohs_inventory_tweaks.next_gen.warning.continue").getString();
		check(press(mc, agree), "the warning has its continue button");
		Thread.sleep(500);
		check(mc.screen instanceof NextGenInventoriesScreen, "continuing opens the page; screen=" + name(mc));
		shot(mc, "next-gen-3-page-vanilla");
		statusBarsWarning(mc);
		press(mc, ">");
		Thread.sleep(400);
		check(ConfigStore.get().nextGenStyle == NextGenStyle.BROKEN, "the selector moves to Broken; style=" + ConfigStore.get().nextGenStyle);
		shot(mc, "next-gen-4-page-broken");
		press(mc, ">");
		Thread.sleep(400);
		check(ConfigStore.get().nextGenStyle == NextGenStyle.VISOR, "and on to Visor; style=" + ConfigStore.get().nextGenStyle);
		shot(mc, "next-gen-5-page-visor");
		for (int step = 0; step < NextGenStyle.values().length - 2; step++) {
			press(mc, ">");
			Thread.sleep(250);
			shot(mc, "next-gen-5-page-" + ConfigStore.get().nextGenStyle.name().toLowerCase());
		}
		check(ConfigStore.get().nextGenStyle == NextGenStyle.OFF, "and round to Vanilla; style=" + ConfigStore.get().nextGenStyle);
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
	}

	private static void nextGenStyles(final Minecraft mc) throws Exception {
		int[] vanilla = new int[2];
		mc.executeBlocking(() -> {
			Slot slot = mc.player.inventoryMenu.slots.get(9);
			vanilla[0] = slot.x;
			vanilla[1] = slot.y;
		});
		for (NextGenStyle style : NextGenStyle.values()) {
			if (style == NextGenStyle.OFF) {
				continue;
			}
			configure(mc, config -> {
				config.nextGenStyle = style;
				config.frameColor = 0xFFFFFF;
				config.frameOpacity = 255;
				config.slotColor = 0xFFFFFF;
				config.slotOpacity = 255;
				config.customBackgroundFile = null;
				config.hideRecipeBook = false;
			});
			setBookOpen(mc, false);
			mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
			Thread.sleep(500);
			String label = style.name().toLowerCase();
			check(mc.screen instanceof InventoryScreen screen && NextGenInventory.activeStyle(screen) == style, label + " is the style drawn");
			boolean book = NextGenInventory.allowsRecipeBook(style);
			check(hasBookButton(mc) == book, label + (book ? " has its recipe book button" : " has no recipe book button"));
			everySlotUnderPointer(mc, label);
			clickReachesServer(mc, label);
			if (style == NextGenStyle.BROKEN) {
				throwsOnlyOutside(mc);
			}
			pointAtSlot(mc, 13);
			shot(mc, "next-gen-6-" + label);
			if (book) {
				withRecipeBook(mc, label);
			}
			int gui = mc.options.guiScale().get();
			setGuiScale(mc, 2);
			everySlotUnderPointer(mc, label + " at GUI scale 2");
			pointAtSlot(mc, 13);
			effectsStayInTheWindow(mc, label + " at GUI scale 2");
			shot(mc, "next-gen-7-" + label + "-gui2");
			setGuiScale(mc, gui);
			parkPointer(mc);
			mc.executeBlocking(() -> mc.setScreen(null));
			Thread.sleep(250);
			check(mc.player.inventoryMenu.slots.get(9).x == vanilla[0] && mc.player.inventoryMenu.slots.get(9).y == vanilla[1],
				label + " puts the slots back when it closes");
		}
		configure(mc, config -> config.nextGenStyle = NextGenStyle.OFF);
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(400);
		int guiBeforeVanilla = mc.options.guiScale().get();
		setGuiScale(mc, 2);
		effectsStayInTheWindow(mc, "the Vanilla layout at GUI scale 2");
		setGuiScale(mc, guiBeforeVanilla);
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
		visorFollowsSliders(mc);
		// Small windows keep the Vanilla layout rather than a broken one.
		configure(mc, config -> config.nextGenStyle = NextGenStyle.VISOR);
		check(NextGenInventory.styleFor(ConfigStore.get(), 180, 400) == NextGenStyle.OFF, "too narrow a window keeps Vanilla");
		check(NextGenInventory.styleFor(ConfigStore.get(), 800, 190) == NextGenStyle.OFF, "too short a window keeps Vanilla");
		check(NextGenInventory.styleFor(ConfigStore.get(), 320, 240) == NextGenStyle.VISOR, "Minecraft smallest GUI still fits the visor");
		configure(mc, config -> config.nextGenStyle = NextGenStyle.OFF);
	}

	/** What a player who never opened these options gets. */
	private static void defaults() {
		InventoryTweaksConfig fresh = new InventoryTweaksConfig();
		check(fresh.tabStyle == TabStyle.FOLLOW && fresh.themeCreativeTabs && fresh.themeRecipeBookTabs && fresh.themeScrollbars && fresh.themeEffects,
			"tabs and their parts are on by default");
		check(fresh.tooltipStyle == PartStyle.INVENTORY, "tooltips are on by default");
		check(!fresh.tooltipEverywhere, "tooltips stay out of the Minecraft menus by default");
		check(fresh.buttonStyle == PartStyle.VANILLA, "the Minecraft menu buttons are left alone by default");
		check(fresh.nextGenStyle == NextGenStyle.OFF && fresh.hotbarStatusFollow, "Next Gen is off and the moving bars are on by default");
	}

	/** Turning the bars on goes through its warning, and turning them off does not. */
	private static void statusBarsWarning(final Minecraft mc) throws Exception {
		String bars = Component.translatable("screen.kohs_inventory_tweaks.next_gen.hotbar.bars").getString();
		String enable = Component.translatable("screen.kohs_inventory_tweaks.next_gen.hotbar.bars.warning.enable").getString();
		configure(mc, config -> config.hotbarStatusFollow = false);
		Thread.sleep(150);
		check(press(mc, bars), "the page has the bars switch");
		Thread.sleep(300);
		check(!ConfigStore.get().hotbarStatusFollow, "the bars stay off until the warning is accepted");
		shot(mc, "next-gen-3b-bars-warning");
		check(press(mc, enable), "the warning has its enable button");
		Thread.sleep(300);
		check(ConfigStore.get().hotbarStatusFollow, "accepting the warning turns the bars on");
		check(press(mc, bars), "the switch is back after the warning");
		Thread.sleep(200);
		check(!ConfigStore.get().hotbarStatusFollow, "the bars turn off without another warning");
	}

	/** The editor: the hotbar goes whole, a slot alone, a bad drop is refused, Shift takes the group. */
	private static void customEditor(final Minecraft mc) throws Exception {
		configure(mc, config -> {
			config.nextGenStyle = NextGenStyle.CUSTOM;
			config.customSlotX = new int[0];
			config.customSlotY = new int[0];
			config.customModelX = 26;
			config.customModelY = 8;
			config.customEffectsX = InventoryTweaksConfig.DEFAULT_EFFECTS_X;
			config.customEffectsY = 0;
		});
		mc.executeBlocking(() -> mc.setScreen(new NextGenLayoutScreen(null)));
		Thread.sleep(500);
		shot(mc, "custom-1-editor");
		dragSlot(mc, 36, 8, 142, 0, 24, 0);
		int[] ys = ConfigStore.get().customSlotY;
		boolean whole = ys.length == 46;
		for (int index = 36; whole && index <= 44; index++) {
			whole = ys[index] == 166;
		}
		check(whole, "dragging the hotbar moves all of it");
		dragSlot(mc, 9, 8, 84, -30, 0, 0);
		check(ConfigStore.get().customSlotX[9] == -22 && ConfigStore.get().customSlotX[10] == 26, "dragging another slot moves only that slot");
		dragSlot(mc, 10, 26, 84, 18, 0, 0);
		check(ConfigStore.get().customSlotX[10] == 26, "a slot dropped on another goes back");
		dragSlot(mc, 36, 8, 166, -24, 0, GLFW.GLFW_MOD_SHIFT);
		check(ConfigStore.get().customSlotX[36] == -16 && ConfigStore.get().customSlotX[37] == 26, "Shift moves one slot of the hotbar on its own");
		dragSlot(mc, 20, 44, 102, -6, 0, GLFW.GLFW_MOD_SHIFT);
		check(ConfigStore.get().customSlotX[20] == 38 && ConfigStore.get().customSlotX[11] == 38, "Shift moves a whole group of the other slots");
		int[] effects = {InventoryTweaksConfig.DEFAULT_EFFECTS_X + 10, 10};
		dragPoint(mc, effects[0], effects[1], 30, 30, 0);
		check(ConfigStore.get().customEffectsY == 30, "the simulated effects can be placed too");
		shot(mc, "custom-2-edited");
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(500);
		everySlotUnderPointer(mc, "custom after editing");
		clickReachesServer(mc, "custom after editing");
		shot(mc, "custom-3-inventory");
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
		configure(mc, config -> {
			config.nextGenStyle = NextGenStyle.OFF;
			config.customSlotX = new int[0];
			config.customSlotY = new int[0];
			config.customEffectsX = InventoryTweaksConfig.DEFAULT_EFFECTS_X;
			config.customEffectsY = 0;
		});
	}

	private static void dragSlot(final Minecraft mc, final int slot, final int x, final int y, final int dx, final int dy, final int modifiers) throws Exception {
		dragPoint(mc, x + 8, y + 8, dx, dy, modifiers);
	}

	/** A real press, drag and release on the editor, from a point in the panel's coordinates. */
	private static void dragPoint(final Minecraft mc, final int x, final int y, final int dx, final int dy, final int modifiers) throws Exception {
		mc.executeBlocking(() -> {
			int startX = (mc.screen.width - 176) / 2 + x;
			int startY = (mc.screen.height - 166) / 2 + y;
			MouseButtonInfo button = new MouseButtonInfo(0, modifiers);
			mc.screen.mouseClicked(new MouseButtonEvent(startX, startY, button), false);
			mc.screen.mouseDragged(new MouseButtonEvent(startX + dx, startY + dy, button), dx, dy);
			mc.screen.mouseReleased(new MouseButtonEvent(startX + dx, startY + dy, button));
		});
		Thread.sleep(120);
	}

	/** Every style in the Creative inventory, on an item tab and on the inventory tab. */
	private static void creativeVariants(final Minecraft mc) throws Exception {
		command(mc, "gamemode creative");
		Thread.sleep(500);
		for (NextGenStyle style : NextGenStyle.values()) {
			if (style == NextGenStyle.OFF) {
				continue;
			}
			String label = "creative " + style.name().toLowerCase();
			configure(mc, config -> {
				config.nextGenStyle = style;
				config.frameColor = 0xFFFFFF;
				config.frameOpacity = 255;
				config.slotColor = 0xFFFFFF;
				config.slotOpacity = 255;
			});
			mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
			Thread.sleep(600);
			check(mc.screen instanceof CreativeModeInventoryScreen, label + " opens the Creative inventory");
			selectTab(mc, CreativeModeTabs.getDefaultTab());
			Thread.sleep(250);
			everySlotReachable(mc, label + " items");
			pickAndPlace(mc, label);
			shot(mc, "creative-" + style.name().toLowerCase());
			selectTab(mc, inventoryTab());
			Thread.sleep(250);
			everySlotReachable(mc, label + " inventory");
			effectsStayInTheWindow(mc, "creative " + style.name().toLowerCase());
			shot(mc, "creative-" + style.name().toLowerCase() + "-inventory");
			selectTab(mc, CreativeModeTabs.getDefaultTab());
			mc.executeBlocking(() -> mc.setScreen(null));
			Thread.sleep(200);
		}
		configure(mc, config -> config.nextGenStyle = NextGenStyle.OFF);
		command(mc, "gamemode survival");
		Thread.sleep(500);
	}

	private static CreativeModeTab inventoryTab() {
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			if (tab.getType() == CreativeModeTab.Type.INVENTORY) {
				return tab;
			}
		}
		throw new IllegalStateException("No inventory tab");
	}

	private static void selectTab(final Minecraft mc, final CreativeModeTab tab) throws Exception {
		var method = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
		method.setAccessible(true);
		Exception[] failure = new Exception[1];
		mc.executeBlocking(() -> {
			try {
				method.invoke(mc.screen, tab);
			} catch (Exception exception) {
				failure[0] = exception;
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	private static void everySlotReachable(final Minecraft mc, final String label) throws Exception {
		int[] wrong = {0};
		int[] offscreen = {0};
		mc.executeBlocking(() -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			for (Slot slot : screen.getMenu().slots) {
				if (slot.x <= -1000) {
					continue;
				}
				double x = access.kohsInventoryTweaks$getLeftPos() + slot.x + 8.0;
				double y = access.kohsInventoryTweaks$getTopPos() + slot.y + 8.0;
				if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) offscreen[0]++;
				if (access.kohsInventoryTweaks$findHoveredSlot(x, y) != slot) wrong[0]++;
			}
		});
		check(wrong[0] == 0, label + " finds every slot; wrong=" + wrong[0]);
		check(offscreen[0] == 0, label + " keeps every slot on the screen; offscreen=" + offscreen[0]);
	}

	/** Takes an item from the grid and puts it on the hotbar with real clicks. */
	private static void pickAndPlace(final Minecraft mc, final String label) throws Exception {
		pointAtSlot(mc, 0);
		button(mc, true);
		Thread.sleep(40);
		button(mc, false);
		Thread.sleep(150);
		check(!mc.player.containerMenu.getCarried().isEmpty(), label + " picks up an item from the grid");
		pointAtSlot(mc, 49);
		button(mc, true);
		Thread.sleep(40);
		button(mc, false);
		Thread.sleep(150);
		check(mc.player.containerMenu.getCarried().isEmpty(), label + " places it on the hotbar");
		check(!mc.player.getInventory().getItem(4).isEmpty(), label + " the hotbar slot received it");
	}

	private static boolean hasBookButton(final Minecraft mc) {
		return mc.screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget
			&& widget.getWidth() == 20 && widget.getHeight() == 18);
	}

	/** The book opens beside the panel; every slot still works, and it is left as it was found. */
	private static void withRecipeBook(final Minecraft mc, final String label) throws Exception {
		// Under 379 wide Vanilla shows the open book in place of the inventory, so this needs room.
		int gui = mc.options.guiScale().get();
		setGuiScale(mc, 2);
		int before = ((AbstractContainerScreenAccessor) mc.screen).kohsInventoryTweaks$getLeftPos();
		toggleBook(mc);
		Thread.sleep(350);
		int after = ((AbstractContainerScreenAccessor) mc.screen).kohsInventoryTweaks$getLeftPos();
		check(after != before, label + " moves aside for the open recipe book; left " + before + " to " + after);
		everySlotUnderPointer(mc, label + " with the book open");
		pointAtSlot(mc, 13);
		shot(mc, "next-gen-8-book-" + label);
		toggleBook(mc);
		Thread.sleep(250);
		setGuiScale(mc, gui);
	}

	/** Earlier labs and earlier runs leave the book however they found it; here it has to be known. */
	private static void setBookOpen(final Minecraft mc, final boolean open) {
		mc.executeBlocking(() -> mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, open));
	}

	private static void toggleBook(final Minecraft mc) {
		mc.executeBlocking(() -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget widget && widget.getWidth() == 20 && widget.getHeight() == 18) {
					widget.onClick(new MouseButtonEvent(widget.getX() + 2, widget.getY() + 2, new MouseButtonInfo(0, 0)), false);
					return;
				}
			}
		});
	}

	private static void visorFollowsSliders(final Minecraft mc) throws Exception {
		configure(mc, config -> {
			config.nextGenStyle = NextGenStyle.VISOR;
			config.frameOpacity = 70;
			config.slotOpacity = 70;
		});
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(500);
		shot(mc, "next-gen-9-visor-sliders-low");
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
		configure(mc, config -> {
			config.frameOpacity = 255;
			config.slotOpacity = 255;
		});
	}

	/** Every hotbar on the HUD, then its movement and its reactions to real events. */
	private static void hotbars(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
		for (HotbarStyle style : HotbarStyle.values()) {
			configure(mc, config -> {
				config.hotbarStyle = style;
				config.hotbarMotion = false;
				config.hotbarReactions = false;
			});
			shot(mc, "hotbar-" + style.name().toLowerCase());
		}
		configure(mc, config -> {
			config.hotbarStyle = HotbarStyle.VISOR;
			config.hotbarMotion = true;
			config.hotbarReactions = true;
		});
		Thread.sleep(200);
		for (int frame = 0; frame < 14; frame++) {
			mc.executeBlocking(() -> mc.player.setDeltaMovement(0.4, 0.0, 0.0));
			Thread.sleep(16);
		}
		check(Math.abs(hotbarValue("swayX")) > 0.2F, "the hotbar leans when the player moves; " + NextGenHotbar.labSnapshot());
		configure(mc, config -> config.hotbarStatusFollow = true);
		for (int frame = 0; frame < 14; frame++) {
			mc.executeBlocking(() -> mc.player.setDeltaMovement(0.5, 0.0, 0.5));
			Thread.sleep(16);
		}
		UiShowcaseLab.screenshotAfter(mc, "hotbar-bars-following", 0L);
		configure(mc, config -> config.hotbarStatusFollow = false);

		command(mc, "gamemode survival");
		command(mc, "effect clear @s minecraft:resistance");
		command(mc, "damage @s 4 minecraft:generic");
		Thread.sleep(120);
		check(hotbarValue("hurt") > 0.0F, "the hotbar reacts to a hit taken; " + NextGenHotbar.labSnapshot());
		UiShowcaseLab.screenshotAfter(mc, "hotbar-reaction-hit", 0L);

		command(mc, "effect give @s minecraft:instant_health 1 1 true");
		Thread.sleep(200);
		check(hotbarValue("heal") > 0.0F, "the hotbar reacts to hearts coming back; " + NextGenHotbar.labSnapshot());
		UiShowcaseLab.screenshotAfter(mc, "hotbar-reaction-heal", 0L);

		command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
		command(mc, "damage @s 1000 minecraft:generic");
		Thread.sleep(200);
		check(hotbarValue("totem") > 0.0F, "the hotbar reacts to a popped totem; " + NextGenHotbar.labSnapshot());
		// 1.21.11 lets the 1000 damage kill through the totem it pops: come back before the next checks,
		// which need a living player in the world and no death screen.
		respawnIfDead(mc);
		UiShowcaseLab.screenshotAfter(mc, "hotbar-reaction-totem", 0L);

		command(mc, "summon minecraft:pig ~ ~ ~2");
		Thread.sleep(400);
		boolean[] attacked = new boolean[1];
		mc.executeBlocking(() -> {
			var pigs = mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mc.player.getBoundingBox().inflate(6.0));
			if (!pigs.isEmpty()) {
				mc.gameMode.attack(mc.player, pigs.get(0));
				attacked[0] = true;
			}
		});
		Thread.sleep(40);
		check(attacked[0] && hotbarValue("strike") > 0.0F, "the hotbar reacts to a hit landed; " + NextGenHotbar.labSnapshot());
		UiShowcaseLab.screenshotAfter(mc, "hotbar-reaction-attack", 0L);

		configure(mc, config -> {
			config.hotbarStyle = HotbarStyle.VANILLA;
			config.hotbarMotion = false;
			config.hotbarReactions = false;
		});
		command(mc, "kill @e[type=minecraft:pig]");
		command(mc, "effect give @s minecraft:instant_health 1 4 true");
	}

	private static float hotbarValue(final String name) {
		for (String part : NextGenHotbar.labSnapshot().split("; ")) {
			if (part.startsWith(name + "=")) {
				return Float.parseFloat(part.substring(name.length() + 1));
			}
		}
		throw new IllegalStateException("No value " + name);
	}

	private static void everySlotUnderPointer(final Minecraft mc, final String label) throws Exception {
		int[] wrong = {0};
		int[] offscreen = {0};
		mc.executeBlocking(() -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			for (Slot slot : screen.getMenu().slots) {
				double x = access.kohsInventoryTweaks$getLeftPos() + slot.x + 8.0;
				double y = access.kohsInventoryTweaks$getTopPos() + slot.y + 8.0;
				if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) offscreen[0]++;
				Slot found = access.kohsInventoryTweaks$findHoveredSlot(x, y);
				if (found != slot) wrong[0]++;
			}
		});
		check(wrong[0] == 0, label + " finds every slot under the pointer at its new place; wrong=" + wrong[0]);
		check(offscreen[0] == 0, label + " keeps every slot on the screen; offscreen=" + offscreen[0]);
	}

	/** A real press and release over a moved slot, as a player clicks: the server must move that item. */
	private static void clickReachesServer(final Minecraft mc, final String label) throws Exception {
		int slotIndex = 9;
		pointAtSlot(mc, slotIndex);
		button(mc, true);
		Thread.sleep(60);
		button(mc, false);
		Thread.sleep(400);
		check(!mc.player.containerMenu.getCarried().isEmpty(), label + " picks up the item under the pointer");
		boolean[] server = new boolean[1];
		mc.executeBlocking(() -> {
			var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
			server[0] = !player.containerMenu.getCarried().isEmpty() && player.inventoryMenu.slots.get(slotIndex).getItem().isEmpty();
		});
		check(server[0], label + " the server took it from that very slot");
		button(mc, true);
		Thread.sleep(60);
		button(mc, false);
		Thread.sleep(400);
		check(mc.player.containerMenu.getCarried().isEmpty(), label + " puts it back");
	}

	/** The middle of the gap is inside the inventory; only past the two halves is outside. */
	private static void throwsOnlyOutside(final Minecraft mc) throws Exception {
		boolean[] result = new boolean[3];
		mc.executeBlocking(() -> {
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) mc.screen;
			int left = access.kohsInventoryTweaks$getLeftPos();
			int top = access.kohsInventoryTweaks$getTopPos();
			result[0] = NextGenInventory.clickedOutside(NextGenStyle.BROKEN, left + 97, top + 40, left, top);
			result[1] = NextGenInventory.clickedOutside(NextGenStyle.BROKEN, left - NextGenInventory.BROKEN_SHIFT + 1, top + 40, left, top);
			result[2] = NextGenInventory.clickedOutside(NextGenStyle.BROKEN, left - NextGenInventory.BROKEN_SHIFT - 2, top + 40, left, top);
		});
		check(!result[0], "the gap between the halves is not outside");
		check(!result[1], "the moved left half is not outside");
		check(result[2], "past the left half is outside");
	}

	private static void openCreative(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
		Thread.sleep(800);
		check(mc.screen instanceof CreativeModeInventoryScreen, "creative mode opens the creative inventory; screen=" + name(mc));
	}

	private static boolean press(final Minecraft mc, final String message) throws Exception {
		boolean[] done = new boolean[1];
		mc.executeBlocking(() -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(message)) {
					widget.onClick(new MouseButtonEvent(widget.getX() + 2, widget.getY() + 2, new MouseButtonInfo(0, 0)), false);
					done[0] = true;
					return;
				}
			}
		});
		return done[0];
	}

	private static String name(final Minecraft mc) {
		return mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
	}

	private static void pointAtSlot(final Minecraft mc, final int index) throws Exception {
		mc.executeBlocking(() -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			Slot slot = screen.getMenu().getSlot(index);
			var window = mc.getWindow();
			// The inventory's own surface scale sits between the slot and the window.
			double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
			double guiX = screen.width * 0.5 + (access.kohsInventoryTweaks$getLeftPos() + slot.x + 8.0 - screen.width * 0.5) * scale;
			double guiY = screen.height * 0.5 + (access.kohsInventoryTweaks$getTopPos() + slot.y + 8.0 - screen.height * 0.5) * scale;
			((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(window.handle(),
				Math.floor(guiX * window.getScreenWidth() / screen.width), Math.floor(guiY * window.getScreenHeight() / screen.height));
		});
		Thread.sleep(220);
	}

	private static void setGuiScale(final Minecraft mc, final int scale) throws Exception {
		mc.executeBlocking(() -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		Thread.sleep(350);
	}

	private static void parkPointer(final Minecraft mc) throws Exception {
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), 1.0, 1.0));
		Thread.sleep(150);
	}

	private static void button(final Minecraft mc, final boolean down) {
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler)
			.kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(0, 0), down ? 1 : 0));
	}

	private static void configure(final Minecraft mc, final Consumer<InventoryTweaksConfig> change) throws Exception {
		mc.executeBlocking(() -> {
			InventoryTweaksConfig config = ConfigStore.get().copy();
			change.accept(config);
			ConfigStore.replaceAndSave(config);
			InventoryTextureManager.invalidateConfiguration();
		});
		Thread.sleep(250);
	}

	private static void command(final Minecraft mc, final String command) throws Exception {
		mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
		Thread.sleep(120);
	}

	/** Measured in window pixels, which the scaled surface does not share: panels end inside or leave it to the HUD. */
	private static void effectsStayInTheWindow(final Minecraft mc, final String label) throws Exception {
		boolean[] inside = {true};
		mc.executeBlocking(() -> {
			if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
				inside[0] = false;
				return;
			}
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			int left = access.kohsInventoryTweaks$getLeftPos();
			int top = access.kohsInventoryTweaks$getTopPos();
			int window = screen.width;
			double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
			boolean shown = screen.showsActiveEffects();
			EffectsPlacement.whileMeasuring(screen, () -> {
				int column = access.kohsInventoryTweaks$getLeftPos() + access.kohsInventoryTweaks$getImageWidth() + 2;
				int room = screen.width - column;
				int panel = room >= 120 ? room - 7 : 32;
				double rightEdge = window * 0.5 + (column + panel - window * 0.5) * scale;
				inside[0] = shown == (room >= 32) && (!shown || rightEdge <= window + 0.5);
				return null;
			});
			inside[0] &= access.kohsInventoryTweaks$getLeftPos() == left && access.kohsInventoryTweaks$getTopPos() == top && screen.width == window;
		});
		check(inside[0], label + " keeps its effect panels inside the window");
	}

	private static void respawnIfDead(final Minecraft mc) throws Exception {
		boolean[] dead = new boolean[1];
		mc.executeBlocking(() -> dead[0] = mc.player == null || mc.player.isDeadOrDying());
		if (!dead[0]) {
			return;
		}
		mc.executeBlocking(() -> mc.player.respawn());
		for (int wait = 0; wait < 40 && dead[0]; wait++) {
			Thread.sleep(100);
			mc.executeBlocking(() -> dead[0] = mc.player == null || mc.player.isDeadOrDying());
		}
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(300);
	}

	private static void shot(final Minecraft mc, final String name) throws Exception {
		Thread.sleep(250);
		UiShowcaseLab.screenshot(mc, name);
	}

	private static void check(final boolean passed, final String reason) {
		checks++;
		System.out.println("[lab] " + (passed ? "ok   " : "FAIL ") + reason);
		if (!passed) {
			failures++;
			DebugCollector.issue("INTERFACE_LAB_FAIL", reason);
		}
	}
}
