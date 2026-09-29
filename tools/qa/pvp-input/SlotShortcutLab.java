package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SlotTargeting;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Fast slot shortcuts: the pointer flicks to a slot and a shortcut follows before
 * the next frame, the way a player reaches for a totem and presses F.
 *
 * <p>Every step first points the screen's rendered target at a different slot, the
 * state Vanilla is in one frame after a flick, then moves the pointer and presses
 * the shortcut inside the same input poll, so no render can refresh the target in
 * between. The shortcut must act on the slot under the pointer. Each block ends by
 * comparing the whole inventory with the integrated server, so a client-side
 * prediction that the server rejected fails too.</p>
 *
 * <p>Covered: offhand swap, hotbar keys, drop one, drop stack, quick move,
 * pickup-and-place, a burst of mixed shortcuts in one poll, and shortcuts right
 * after a fast opening before the first frame; across GUI scale 2 and 3, the GUI
 * Scaler on and off, and the recipe book open and closed. A Vanilla-targeting pass
 * measures what the refresh changes, and a container pass checks the target in
 * thirteen Vanilla container screens. Integrated singleplayer only.</p>
 */
public final class SlotShortcutLab {
	private static final int OFFHAND = 45;
	private static final int FIRST_HOTBAR = 36;
	private static final int TOTEM_SLOT = 20;
	private static final Item[] ITEMS = {
		Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.OAK_PLANKS, Items.SAND, Items.GRAVEL,
		Items.GOLD_INGOT, Items.IRON_INGOT, Items.DIAMOND, Items.EMERALD, Items.COAL, Items.REDSTONE,
		Items.LAPIS_LAZULI, Items.QUARTZ, Items.AMETHYST_SHARD, Items.COPPER_INGOT, Items.BRICK,
		Items.CLAY_BALL, Items.GLOWSTONE_DUST, Items.STRING, Items.FEATHER, Items.BONE, Items.ARROW,
		Items.APPLE, Items.BREAD, Items.CARROT, Items.POTATO, Items.SLIME_BALL, Items.ENDER_PEARL,
		Items.BLAZE_ROD, Items.GHAST_TEAR, Items.SNOWBALL, Items.EGG, Items.PAPER, Items.BOOK, Items.SUGAR
	};

	private static int checks;
	private static int failures;
	private static int vanillaCases;
	private static int vanillaStale;

	private SlotShortcutLab() {}

	public static void run(final Minecraft mc) {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		checks = failures = vanillaCases = vanillaStale = 0;
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		ItemStack[] savedInventory = server.submit(() -> {
			Inventory inventory = server.getPlayerList().getPlayer(playerId).getInventory();
			ItemStack[] copy = new ItemStack[inventory.getContainerSize()];
			for (int index = 0; index < copy.length; index++) copy[index] = inventory.getItem(index).copy();
			return copy;
		}).join();
		var savedConfig = ConfigStore.get().copy();
		int savedGui = mc.options.guiScale().get();
		boolean savedBook = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
		try {
			command(mc, "gamemode survival");
			for (int gui : new int[]{2, 3}) {
				for (boolean scaler : new boolean[]{false, true}) {
					for (boolean book : new boolean[]{false, true}) {
						String setup = "gui=" + gui + " scaler=" + scaler + " book=" + book;
						configure(mc, gui, scaler);
						runBlock(mc, setup, book);
					}
				}
			}
			configure(mc, 2, false);
			vanillaBaseline(mc);
			containerTargets(mc);
			DebugCollector.info("SLOT_SHORTCUT_SUMMARY", "checks=" + checks + "; failures=" + failures
				+ "; vanillaStale=" + vanillaStale + "/" + vanillaCases);
			if (failures > 0) throw new IllegalStateException("Slot shortcut failures=" + failures);
		} catch (RuntimeException error) {
			throw error;
		} catch (Exception error) {
			throw new IllegalStateException(error);
		} finally {
			SlotTargeting.labVanillaTargeting = false;
			try {
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					if (mc.screen != null) mc.screen.onClose();
					KeyMapping.releaseAll();
					ConfigStore.replaceAndSave(savedConfig);
					mc.options.guiScale().set(savedGui);
					mc.resizeGui();
					mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, savedBook);
				});
			} catch (Exception ignored) {
				// The fixture below still restores the inventory.
			}
			server.submit(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				for (int index = 0; index < savedInventory.length; index++) player.getInventory().setItem(index, savedInventory[index]);
				player.inventoryMenu.broadcastFullState();
			}).join();
			try {
				command(mc, "kill @e[type=minecraft:item]");
			} catch (Exception ignored) {
				// Dropped items left in the lab world are harmless.
			}
		}
	}

	private static void runBlock(final Minecraft mc, final String setup, final boolean book) throws Exception {
		fixture(mc);
		inScreen(mc, book, screen -> {
			// Offhand swap, the classic flick to the totem and F.
			int[] targets = {TOTEM_SLOT, 12, 30, FIRST_HOTBAR, 25, FIRST_HOTBAR + 5};
			for (int target : targets) {
				ItemStack beforeTarget = item(screen, target).copy();
				ItemStack beforeOffhand = item(screen, OFFHAND).copy();
				seedStale(screen, target == 12 ? 30 : 12);
				flick(mc, screen, target);
				tap(mc, binding(mc.options.keySwapOffhand), 0);
				check(ItemStack.matches(item(screen, OFFHAND), beforeTarget), setup + " F offhand takes slot " + target);
				check(ItemStack.matches(item(screen, target), beforeOffhand), setup + " F slot " + target + " takes offhand");
			}
			return null;
		});
		verifyServer(mc, setup + " offhand");

		fixture(mc);
		inScreen(mc, book, screen -> {
			int[][] pairs = {{14, 1}, {22, 3}, {33, 7}, {10, 9}};
			for (int[] pair : pairs) {
				int target = pair[0];
				int hotbar = FIRST_HOTBAR + pair[1] - 1;
				ItemStack beforeTarget = item(screen, target).copy();
				ItemStack beforeHotbar = item(screen, hotbar).copy();
				seedStale(screen, target + 1);
				flick(mc, screen, target);
				tap(mc, binding(mc.options.keyHotbarSlots[pair[1] - 1]), 0);
				check(ItemStack.matches(item(screen, hotbar), beforeTarget), setup + " key " + pair[1] + " takes slot " + target);
				check(ItemStack.matches(item(screen, target), beforeHotbar), setup + " slot " + target + " takes hotbar " + pair[1]);
			}
			return null;
		});
		verifyServer(mc, setup + " hotbar keys");

		fixture(mc);
		inScreen(mc, book, screen -> {
			for (int target : new int[]{15, 27, FIRST_HOTBAR + 2}) {
				int before = item(screen, target).getCount();
				seedStale(screen, target == 15 ? 16 : 15);
				flick(mc, screen, target);
				tap(mc, binding(mc.options.keyDrop), 0);
				check(item(screen, target).getCount() == before - 1, setup + " Q drops one from slot " + target);
			}
			for (int target : new int[]{16, 28}) {
				seedStale(screen, target + 1);
				flick(mc, screen, target);
				tap(mc, binding(mc.options.keyDrop), GLFW.GLFW_MOD_CONTROL);
				check(item(screen, target).isEmpty(), setup + " Ctrl+Q drops the stack of slot " + target);
			}
			return null;
		});
		verifyServer(mc, setup + " drop");

		fixture(mc);
		inScreen(mc, book, screen -> {
			for (int target : new int[]{17, 29}) {
				Item moved = item(screen, target).getItem();
				int total = count(screen, moved);
				seedStale(screen, target + 1);
				flick(mc, screen, target);
				click(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT);
				check(item(screen, target).isEmpty(), setup + " Shift+click empties slot " + target);
				check(count(screen, moved) == total, setup + " Shift+click keeps the stack of slot " + target);
			}
			// Pick up, flick, place: swaps two stacks through the carried item.
			ItemStack first = item(screen, 18).copy();
			ItemStack second = item(screen, 31).copy();
			seedStale(screen, 19);
			flick(mc, screen, 18);
			click(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
			flick(mc, screen, 31);
			click(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
			flick(mc, screen, 18);
			click(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
			check(ItemStack.matches(item(screen, 18), second) && ItemStack.matches(item(screen, 31), first),
				setup + " pickup-and-place swaps 18 and 31");
			check(mc.player.containerMenu.getCarried().isEmpty(), setup + " nothing left on the cursor");
			return null;
		});
		verifyServer(mc, setup + " clicks");

		fixture(mc);
		inScreen(mc, book, screen -> {
			// Four shortcuts on four slots inside one poll.
			ItemStack a = item(screen, 19).copy();
			ItemStack b = item(screen, 24).copy();
			ItemStack hotbar2 = item(screen, FIRST_HOTBAR + 1).copy();
			int c = item(screen, 26).getCount();
			ItemStack d = item(screen, 32).copy();
			seedStale(screen, 11);
			flick(mc, screen, 19);
			tap(mc, binding(mc.options.keySwapOffhand), 0);
			flick(mc, screen, 24);
			tap(mc, binding(mc.options.keyHotbarSlots[1]), 0);
			flick(mc, screen, 26);
			tap(mc, binding(mc.options.keyDrop), 0);
			flick(mc, screen, 32);
			tap(mc, binding(mc.options.keySwapOffhand), 0);
			check(ItemStack.matches(item(screen, OFFHAND), d), setup + " burst: offhand ends with slot 32");
			check(ItemStack.matches(item(screen, 32), a), setup + " burst: slot 32 takes the first swap");
			check(item(screen, 19).isEmpty(), setup + " burst: slot 19 went to the offhand");
			check(ItemStack.matches(item(screen, FIRST_HOTBAR + 1), b) && ItemStack.matches(item(screen, 24), hotbar2),
				setup + " burst: key 2 swapped slot 24");
			check(item(screen, 26).getCount() == c - 1, setup + " burst: Q dropped one from slot 26");
			return null;
		});
		verifyServer(mc, setup + " burst");

		if (ConfigStore.get().superFastInventory) {
			fixture(mc);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.screen != null) mc.screen.onClose();
				KeyMapping.releaseAll();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
				ItemStack totem = mc.player.getInventory().getItem(TOTEM_SLOT).copy();
				tap(mc, binding(mc.options.keyInventory), 0);
				SuperFastInventoryController.afterInputPoll(mc);
				check(mc.screen instanceof InventoryScreen, setup + " fast opening before the first frame");
				if (mc.screen instanceof InventoryScreen screen) {
					flick(mc, screen, TOTEM_SLOT);
					tap(mc, binding(mc.options.keySwapOffhand), 0);
					check(ItemStack.matches(mc.player.getOffhandItem(), totem), setup + " open, flick and F before the first frame");
				}
			});
			verifyServer(mc, setup + " open and act");
		}
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.screen != null) mc.screen.onClose();
			KeyMapping.releaseAll();
		});
	}

	/** Same offhand flicks with Vanilla's render-time target: how often a stale slot is hit. */
	private static void vanillaBaseline(final Minecraft mc) throws Exception {
		fixture(mc);
		SlotTargeting.labVanillaTargeting = true;
		try {
			inScreen(mc, false, screen -> {
				int[][] steps = {{TOTEM_SLOT, 12}, {30, 21}, {25, 13}, {FIRST_HOTBAR + 3, 34}};
				for (int[] step : steps) {
					ItemStack target = item(screen, step[0]).copy();
					ItemStack stale = item(screen, step[1]).copy();
					seedStale(screen, step[1]);
					flick(mc, screen, step[0]);
					tap(mc, binding(mc.options.keySwapOffhand), 0);
					vanillaCases++;
					if (!ItemStack.matches(item(screen, OFFHAND), target) && ItemStack.matches(item(screen, OFFHAND), stale)) {
						vanillaStale++;
					}
				}
				return null;
			});
		} finally {
			SlotTargeting.labVanillaTargeting = false;
		}
		verifyServer(mc, "vanilla baseline");
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.screen != null) mc.screen.onClose();
			KeyMapping.releaseAll();
		});
	}

	/** The refreshed target in Vanilla container screens; an unbound key resolves it without acting. */
	private static void containerTargets(final Minecraft mc) throws Exception {
		List<String> names = new ArrayList<>();
		List<Function<Inventory, AbstractContainerScreen<?>>> screens = new ArrayList<>();
		Component title = Component.literal("Lab");
		names.add("chest");
		screens.add(inv -> new ContainerScreen(ChestMenu.threeRows(0, inv), inv, title));
		names.add("double chest");
		screens.add(inv -> new ContainerScreen(ChestMenu.sixRows(0, inv), inv, title));
		names.add("shulker");
		screens.add(inv -> new ShulkerBoxScreen(new ShulkerBoxMenu(0, inv), inv, title));
		names.add("crafting");
		screens.add(inv -> new CraftingScreen(new CraftingMenu(0, inv), inv, title));
		names.add("furnace");
		screens.add(inv -> new FurnaceScreen(new FurnaceMenu(0, inv), inv, title));
		names.add("hopper");
		screens.add(inv -> new HopperScreen(new HopperMenu(0, inv), inv, title));
		names.add("dispenser");
		screens.add(inv -> new DispenserScreen(new DispenserMenu(0, inv), inv, title));
		names.add("anvil");
		screens.add(inv -> new AnvilScreen(new AnvilMenu(0, inv), inv, title));
		names.add("smithing");
		screens.add(inv -> new SmithingScreen(new SmithingMenu(0, inv), inv, title));
		names.add("brewing");
		screens.add(inv -> new BrewingStandScreen(new BrewingStandMenu(0, inv), inv, title));
		names.add("grindstone");
		screens.add(inv -> new GrindstoneScreen(new GrindstoneMenu(0, inv), inv, title));
		names.add("stonecutter");
		screens.add(inv -> new StonecutterScreen(new StonecutterMenu(0, inv), inv, title));
		names.add("enchanting");
		screens.add(inv -> new EnchantmentScreen(new EnchantmentMenu(0, inv), inv, title));
		for (int index = 0; index < screens.size(); index++) {
			String name = names.get(index);
			var factory = screens.get(index);
			for (int gui : new int[]{2, 3}) {
				for (boolean scaler : new boolean[]{false, true}) {
					configure(mc, gui, scaler);
					CloseHotbarRegressionLab.atPoll(mc, () -> {
						AbstractContainerScreen<?> screen = factory.apply(mc.player.getInventory());
						mc.setScreen(screen);
						int slots = screen.getMenu().slots.size();
						// One container slot and one player-inventory slot at the far end of the menu.
						for (int target : new int[]{0, slots - 5}) {
							int stale = target == 0 ? slots - 1 : 0;
							seedStale(screen, stale);
							flick(mc, screen, target);
							tap(mc, GLFW.GLFW_KEY_J, 0);
							var hovered = ((AbstractContainerScreenAccessor) screen).kohsInventoryTweaks$getHoveredSlot();
							check(hovered == screen.getMenu().getSlot(target),
								name + " gui=" + gui + " scaler=" + scaler + " targets menu slot " + target);
						}
						mc.setScreen(null);
					});
				}
			}
		}
	}

	private interface ScreenStep {
		Void run(InventoryScreen screen) throws Exception;
	}

	private static void inScreen(final Minecraft mc, final boolean book, final ScreenStep step) throws Exception {
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.screen != null) mc.screen.onClose();
			KeyMapping.releaseAll();
			mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
			var screen = new InventoryScreen(mc.player);
			mc.setScreen(screen);
			try {
				step.run(screen);
			} catch (RuntimeException error) {
				throw error;
			} catch (Exception error) {
				throw new IllegalStateException(error);
			}
		});
	}

	private static void configure(final Minecraft mc, final int gui, final boolean scaler) throws Exception {
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.screen != null) mc.screen.onClose();
			KeyMapping.releaseAll();
			var config = ConfigStore.get();
			config.superFastInventory = true;
			config.inventoryGuiScalerEnabled = scaler;
			config.inventoryGuiScale = 2.5;
			config.affectAllContainers = true;
			mc.options.guiScale().set(gui);
			mc.resizeGui();
		});
	}

	/**
	 * Distinct stacks of two in every storage slot and the first six hotbar slots,
	 * a totem at {@link #TOTEM_SLOT}, the last three hotbar slots free for quick moves
	 * and an empty offhand, all set on the server and waited for on the client.
	 */
	private static void fixture(final Minecraft mc) throws Exception {
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		server.submit(() -> {
			var player = server.getPlayerList().getPlayer(playerId);
			var inventory = player.getInventory();
			for (int index = 0; index < inventory.getContainerSize(); index++) inventory.setItem(index, ItemStack.EMPTY);
			for (int index = 0; index < 6; index++) inventory.setItem(index, new ItemStack(ITEMS[index], 2));
			for (int index = 9; index < 36; index++) inventory.setItem(index, new ItemStack(ITEMS[index - 3], 2));
			inventory.setItem(TOTEM_SLOT, new ItemStack(Items.TOTEM_OF_UNDYING));
			player.inventoryMenu.broadcastFullState();
		}).join();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		boolean[] synced = {false};
		while (!synced[0] && System.nanoTime() < deadline) {
			mc.executeBlocking(() -> {
				var inventory = mc.player.getInventory();
				synced[0] = inventory.getItem(0).is(ITEMS[0]) && inventory.getItem(35).is(ITEMS[32])
					&& inventory.getItem(TOTEM_SLOT).is(Items.TOTEM_OF_UNDYING) && inventory.getItem(40).isEmpty()
					&& inventory.getItem(8).isEmpty();
			});
			if (!synced[0]) Thread.sleep(10);
		}
		if (!synced[0]) throw new IllegalStateException("Fixture sync timed out");
	}

	/** The server must end with exactly the inventory the client predicted. */
	private static void verifyServer(final Minecraft mc, final String label) throws Exception {
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		String mismatch = "unchecked";
		while (System.nanoTime() < deadline) {
			ItemStack[] client = new ItemStack[41];
			mc.executeBlocking(() -> {
				for (int index = 0; index < client.length; index++) client[index] = mc.player.getInventory().getItem(index).copy();
			});
			mismatch = server.submit(() -> {
				var inventory = server.getPlayerList().getPlayer(playerId).getInventory();
				for (int index = 0; index < client.length; index++) {
					if (!ItemStack.matches(inventory.getItem(index), client[index])) {
						return "slot " + index + " server=" + inventory.getItem(index) + " client=" + client[index];
					}
				}
				return null;
			}).join();
			if (mismatch == null) break;
			Thread.sleep(20);
		}
		check(mismatch == null, label + " server matches client" + (mismatch == null ? "" : ": " + mismatch));
	}

	private static ItemStack item(final AbstractContainerScreen<?> screen, final int menuSlot) {
		return screen.getMenu().getSlot(menuSlot).getItem();
	}

	private static int count(final InventoryScreen screen, final Item item) {
		int total = 0;
		for (int index = 9; index <= OFFHAND; index++) {
			ItemStack stack = item(screen, index);
			if (stack.is(item)) total += stack.getCount();
		}
		return total;
	}

	private static void seedStale(final AbstractContainerScreen<?> screen, final int menuSlot) {
		((AbstractContainerScreenAccessor) screen).kohsInventoryTweaks$setHoveredSlot(screen.getMenu().getSlot(menuSlot));
	}

	/** Moves the pointer onto a slot through the real mouse handler, in window pixels. */
	private static void flick(final Minecraft mc, final AbstractContainerScreen<?> screen, final int menuSlot) {
		var access = (AbstractContainerScreenAccessor) screen;
		var slot = screen.getMenu().getSlot(menuSlot);
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		double x = screen.width * 0.5 + (access.kohsInventoryTweaks$getLeftPos() + slot.x + 8 - screen.width * 0.5) * scale;
		double y = screen.height * 0.5 + (access.kohsInventoryTweaks$getTopPos() + slot.y + 8 - screen.height * 0.5) * scale;
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(),
			x * mc.getWindow().getScreenWidth() / screen.width, y * mc.getWindow().getScreenHeight() / screen.height);
	}

	private static void tap(final Minecraft mc, final int key, final int mods) {
		var keyboard = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
		KeyEvent event = new KeyEvent(key, GLFW.glfwGetKeyScancode(key), mods);
		keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
		keyboard.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
	}

	private static void click(final Minecraft mc, final int button, final int mods) {
		var mouse = (MouseHandlerDebugInvoker) mc.mouseHandler;
		mouse.kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(button, mods), GLFW.GLFW_PRESS);
		mouse.kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(button, mods), GLFW.GLFW_RELEASE);
	}

	private static int binding(final KeyMapping mapping) {
		return ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey().getValue();
	}

	private static void command(final Minecraft mc, final String command) throws Exception {
		mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
		Thread.sleep(120);
	}

	private static void check(final boolean result, final String label) {
		checks++;
		if (!result) {
			failures++;
			DebugCollector.issue("SLOT_SHORTCUT_FAIL", label);
		}
	}
}
