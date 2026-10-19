package dev.zymekoh.kohsinventorydebug;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SteadyInventory;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * The inventory bugs and rough edges of Minecraft 26.2 that Inventory Tweaks 1.3.0 fixes, each
 * played through the game's own input handlers and judged by what the inventory holds after.
 *
 * <ul>
 * <li>Double clicks: a third quick click places instead of collecting again (MC-306262), placing
 * a stack and shift-clicking it moves only that stack (MC-301852), a drag started by the second
 * click of a double click distributes (MC-312333) and a release on another slot does not
 * collect (MC-311852).</li>
 * <li>A held offhand or hotbar key swaps once, whatever the key repeat does.</li>
 * <li>The inventory and drop keys bound to mouse buttons close and drop (MC-577).</li>
 * <li>The inventory player model is built with the frame's partial tick (MC-265935).</li>
 * <li>A toggled sprint bound to a mouse button comes back after a screen (MC-308595).</li>
 * <li>A chest opened after the window changed size while the mouse was captured puts the pointer
 * on the new centre, with Center Mouse Fix.</li>
 * <li>Leave with movement keys closes the inventory before the movement is sent.</li>
 * <li>Hide recipe book: no book, no button, the saved setting untouched and nothing sent.</li>
 * <li>Steady inventory: a stale answer leaves the later click on screen, and the server's word is
 * what the menu holds once the screen closes.</li>
 * <li>Faster enchanted items: frames and item-atlas redraws per second, off and on, with every slot
 * enchanted and with a crystal kit.</li>
 * </ul>
 *
 * <p>Options this jar may not have are reached by reflection, so the same lab measures an older
 * release through {@code -PinventoryTweaksJar} and reports what it lacks. Singleplayer only.</p>
 */
public final class InventoryFeelLab {
	private static int checks;
	private static int failures;
	private static final List<String> FAILED = new ArrayList<>();
	private static volatile long atlasDraws;
	private static volatile boolean recording;
	private static volatile int bookPackets;
	private static final List<String> SENT = new ArrayList<>();

	private InventoryFeelLab() {
	}

	/** Counted by the companion at every item drawn into the GUI item atlas. */
	public static void onAtlasDraw() {
		atlasDraws++;
	}

	public static void onPacket(final Packet<?> packet) {
		if (packet instanceof ServerboundRecipeBookChangeSettingsPacket) bookPackets++;
		if (!recording) return;
		String kind;
		if (packet instanceof ServerboundContainerClosePacket) {
			kind = "close";
		} else if (packet instanceof ServerboundPlayerInputPacket input) {
			var keys = input.input();
			kind = keys.forward() || keys.backward() || keys.left() || keys.right() ? "input(move)" : "input(none)";
		} else {
			return;
		}
		synchronized (SENT) {
			SENT.add(kind);
		}
	}

	public static void run(final Minecraft mc) {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		checks = failures = 0;
		FAILED.clear();
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		ItemStack[] savedInventory = server.submit(() -> {
			var inventory = server.getPlayerList().getPlayer(playerId).getInventory();
			ItemStack[] copy = new ItemStack[inventory.getContainerSize()];
			for (int index = 0; index < copy.length; index++) copy[index] = inventory.getItem(index).copy();
			return copy;
		}).join();
		InventoryTweaksConfig savedConfig = ConfigStore.get().copy();
		int savedGui = mc.options.guiScale().get();
		int savedFps = mc.options.framerateLimit().get();
		boolean savedVsync = mc.options.enableVsync().get();
		boolean savedToggleSprint = mc.options.toggleSprint().get();
		boolean savedBook = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
		InputConstants.Key savedInventoryKey = key(mc.options.keyInventory);
		InputConstants.Key savedDropKey = key(mc.options.keyDrop);
		InputConstants.Key savedSprintKey = key(mc.options.keySprint);
		StringBuilder results = new StringBuilder();
		try {
			command(mc, "gamemode survival");
			command(mc, "effect give @s minecraft:resistance 600 4 true");
			// A platform of its own, whatever earlier labs left in the world: the chest test needs
			// a player standing still within reach.
			command(mc, "fill -4 119 -4 4 119 4 minecraft:smooth_stone");
			command(mc, "fill -4 120 -4 4 124 4 minecraft:air");
			command(mc, "tp @s 0 120 0 0 0");
			Thread.sleep(500);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.options.guiScale().set(3);
				mc.resizeGui();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
				ConfigStore.get().inventoryGuiScalerEnabled = false;
			});
			doubleClicks(mc);
			heldSwapKeys(mc);
			mouseBoundKeys(mc);
			modelPartialTick(mc);
			mouseToggleRestore(mc);
			results.append(containerCentre(mc));
			results.append(leaveWithMovement(mc));
			results.append(steadyInventory(mc));
			results.append(hideRecipeBook(mc));
			results.append(enchantedItems(mc));
			DebugCollector.info("INVENTORY_FEEL_SUMMARY", "checks=" + checks + "; failures=" + failures + results
				+ (FAILED.isEmpty() ? "" : "; failed=" + FAILED));
			if (failures > 0) throw new IllegalStateException("Inventory feel failures=" + failures + " " + FAILED);
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
					setKey(mc.options.keyInventory, savedInventoryKey);
					setKey(mc.options.keyDrop, savedDropKey);
					setKey(mc.options.keySprint, savedSprintKey);
					mc.options.toggleSprint().set(savedToggleSprint);
					mc.options.framerateLimit().set(savedFps);
					mc.options.enableVsync().set(savedVsync);
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
			try {
				command(mc, "kill @e[type=minecraft:item]");
			} catch (Exception ignored) {
				// Dropped items left in the lab world are harmless.
			}
		}
	}

	// ---------------------------------------------------------------- double clicks

	private static void doubleClicks(final Minecraft mc) throws Exception {
		// A third quick click on the slot a double click just emptied puts the stack back (MC-306262).
		fixture(mc, new int[][] {{9, 16}, {10, 16}, {11, 16}}, Items.COBBLESTONE);
		inInventory(mc, "triple-click", screen -> {
			flick(mc, screen, 9);
			click(mc, 0, 0);
			click(mc, 0, 0);
			click(mc, 0, 0);
			check(screen.getMenu().getCarried().isEmpty() && count(screen, 9) == 48,
				"third quick click places the collected stack (MC-306262): carried=" + screen.getMenu().getCarried().getCount() + " slot9=" + count(screen, 9));
		});
		verifyServer(mc, "triple click");

		// Placing a stack and shift-clicking it at once moves that stack only (MC-301852).
		fixture(mc, new int[][] {{9, 16}, {10, 16}}, Items.COBBLESTONE);
		inInventory(mc, "place-shift", screen -> {
			flick(mc, screen, 9);
			click(mc, 0, 0);
			flick(mc, screen, 20);
			click(mc, 0, 0);
			click(mc, 0, GLFW.GLFW_MOD_SHIFT);
			check(count(screen, 10) == 16, "place then shift-click moves one stack (MC-301852): slot10=" + count(screen, 10));
		});
		verifyServer(mc, "place then shift-click");

		// A drag begun by the second click of a double click distributes (MC-312333).
		fixture(mc, new int[][] {{9, 30}, {10, 16}}, Items.COBBLESTONE);
		inInventory(mc, "drag-double", screen -> {
			flick(mc, screen, 9);
			click(mc, 0, 0);
			press(mc, 0, 0);
			for (int target : new int[] {18, 19, 20}) {
				flick(mc, screen, target);
				mc.mouseHandler.handleAccumulatedMovement();
			}
			release(mc, 0, 0);
			check(count(screen, 18) == 10 && count(screen, 19) == 10 && count(screen, 20) == 10 && count(screen, 10) == 16,
				"drag after a double click distributes (MC-312333): " + count(screen, 18) + "/" + count(screen, 19) + "/" + count(screen, 20) + " slot10=" + count(screen, 10));
		});
		verifyServer(mc, "drag after double click");

		// A double click released over another slot does not collect there (MC-311852).
		fixture(mc, new int[][] {{9, 16}, {10, 16}}, Items.COBBLESTONE);
		inInventory(mc, "release-elsewhere", screen -> {
			flick(mc, screen, 9);
			click(mc, 0, 0);
			press(mc, 0, 0);
			flick(mc, screen, 20);
			release(mc, 0, 0);
			check(count(screen, 20) == 16 && count(screen, 10) == 16 && screen.getMenu().getCarried().isEmpty(),
				"double click released elsewhere places, no collect (MC-311852): slot20=" + count(screen, 20) + " slot10=" + count(screen, 10));
		});
		verifyServer(mc, "double click released elsewhere");
	}

	// ---------------------------------------------------------------- held keys

	private static void heldSwapKeys(final Minecraft mc) throws Exception {
		fixture(mc, new int[][] {{20, 1}}, Items.TOTEM_OF_UNDYING);
		inInventory(mc, "held-offhand", screen -> {
			flick(mc, screen, 20);
			// One press and five repeats: Vanilla swaps six times and ends where it began.
			key(mc, binding(mc.options.keySwapOffhand), GLFW.GLFW_PRESS, 0);
			for (int repeat = 0; repeat < 5; repeat++) key(mc, binding(mc.options.keySwapOffhand), GLFW.GLFW_REPEAT, 0);
			key(mc, binding(mc.options.keySwapOffhand), GLFW.GLFW_RELEASE, 0);
			check(screen.getMenu().getSlot(45).getItem().is(Items.TOTEM_OF_UNDYING) && !screen.getMenu().getSlot(20).hasItem(),
				"held offhand key swaps once");
		});
		verifyServer(mc, "held offhand key");

		fixture(mc, new int[][] {{21, 3}}, Items.COBBLESTONE);
		mc.getSingleplayerServer().submit(() -> {
			var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
			player.getInventory().setItem(0, new ItemStack(Items.APPLE, 2));
			player.inventoryMenu.broadcastFullState();
		}).join();
		Thread.sleep(200);
		inInventory(mc, "held-hotbar", screen -> {
			flick(mc, screen, 21);
			key(mc, binding(mc.options.keyHotbarSlots[0]), GLFW.GLFW_PRESS, 0);
			for (int repeat = 0; repeat < 3; repeat++) key(mc, binding(mc.options.keyHotbarSlots[0]), GLFW.GLFW_REPEAT, 0);
			key(mc, binding(mc.options.keyHotbarSlots[0]), GLFW.GLFW_RELEASE, 0);
			check(screen.getMenu().getSlot(21).getItem().is(Items.APPLE) && screen.getMenu().getSlot(36).getItem().is(Items.COBBLESTONE),
				"held hotbar key swaps once");
		});
		verifyServer(mc, "held hotbar key");
	}

	// ---------------------------------------------------------------- mouse-bound keys

	private static void mouseBoundKeys(final Minecraft mc) throws Exception {
		fixture(mc, new int[][] {{22, 5}}, Items.APPLE);
		InputConstants.Key inventory = key(mc.options.keyInventory);
		InputConstants.Key drop = key(mc.options.keyDrop);
		try {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				setKey(mc.options.keyInventory, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
				setKey(mc.options.keyDrop, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_5));
			});
			inInventory(mc, "mouse-keys", screen -> {
				flick(mc, screen, 22);
				click(mc, GLFW.GLFW_MOUSE_BUTTON_5, 0);
				check(count(screen, 22) == 4, "drop key on a mouse button drops in the inventory (MC-577): slot22=" + count(screen, 22));
				click(mc, GLFW.GLFW_MOUSE_BUTTON_4, 0);
				check(mc.gui.screen() == null, "inventory key on a mouse button closes the inventory (MC-577)");
			});
		} finally {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				setKey(mc.options.keyInventory, inventory);
				setKey(mc.options.keyDrop, drop);
			});
		}
		verifyServer(mc, "mouse-bound drop");
	}

	// ---------------------------------------------------------------- inventory model

	private static void modelPartialTick(final Minecraft mc) throws Exception {
		// The static builder of the model's state, InventoryScreen#extractRenderState(LivingEntity),
		// found by its shape so that no port renames it.
		Method extract = null;
		for (Method candidate : InventoryScreen.class.getDeclaredMethods()) {
			if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers()) && candidate.getParameterCount() == 1
				&& candidate.getParameterTypes()[0] == LivingEntity.class && candidate.getReturnType() != void.class) {
				extract = candidate;
			}
		}
		if (extract == null) throw new NoSuchMethodException("InventoryScreen model state builder");
		extract.setAccessible(true);
		Method builder = extract;
		int fractional = 0;
		for (int sample = 0; sample < 8; sample++) {
			float[] age = new float[1];
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				try {
					Object state = builder.invoke(null, mc.player);
					Field field = findField(state.getClass(), "ageInTicks");
					age[0] = field.getFloat(state);
				} catch (ReflectiveOperationException error) {
					throw new IllegalStateException(error);
				}
			});
			if (Math.abs(age[0] - Math.round(age[0])) > 1.0E-3F) fractional++;
			Thread.sleep(7);
		}
		check(fractional > 0, "inventory model uses the frame's partial tick (MC-265935): fractional samples=" + fractional + "/8");
	}

	// ---------------------------------------------------------------- toggles on mouse buttons

	private static void mouseToggleRestore(final Minecraft mc) throws Exception {
		InputConstants.Key sprint = key(mc.options.keySprint);
		boolean toggle = mc.options.toggleSprint().get();
		try {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.gui.screen() != null) mc.gui.screen().onClose();
				mc.options.toggleSprint().set(true);
				setKey(mc.options.keySprint, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_5));
				KeyMapping.releaseAll();
			});
			CloseHotbarRegressionLab.atPoll(mc, () -> click(mc, GLFW.GLFW_MOUSE_BUTTON_5, 0));
			boolean[] state = new boolean[3];
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				state[0] = mc.options.keySprint.isDown();
				mc.gui.setScreen(new InventoryScreen(mc.player));
				state[1] = mc.options.keySprint.isDown();
			});
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.gui.screen().onClose();
				state[2] = mc.options.keySprint.isDown();
			});
			check(state[0] && !state[1] && state[2], "toggled sprint on a mouse button comes back after a screen (MC-308595): "
				+ state[0] + "/" + state[1] + "/" + state[2]);
		} finally {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				KeyMapping.releaseAll();
				setKey(mc.options.keySprint, sprint);
				mc.options.toggleSprint().set(toggle);
			});
		}
	}

	// ---------------------------------------------------------------- chest centre

	private static String containerCentre(final Minecraft mc) throws Exception {
		BlockPos[] chest = new BlockPos[1];
		mc.executeBlocking(() -> chest[0] = mc.player.blockPosition().relative(Direction.SOUTH, 2));
		command(mc, "setblock " + chest[0].getX() + " " + chest[0].getY() + " " + chest[0].getZ() + " minecraft:chest");
		StringBuilder out = new StringBuilder();
		for (boolean fix : new boolean[] {false, true}) {
			double miss = chestOpeningMiss(mc, chest[0], fix);
			out.append("; chestCentre").append(fix ? "Fix" : "Vanilla").append("Px=").append(String.format("%.1f", miss));
			check(miss >= 0.0, "the chest opened (" + (fix ? "fix" : "vanilla") + ")");
			if (fix) check(miss >= 0.0 && miss <= 1.0, "chest opened after a resize centres the pointer: miss=" + miss + "px");
		}
		command(mc, "setblock " + chest[0].getX() + " " + chest[0].getY() + " " + chest[0].getZ() + " minecraft:air");
		return out.toString();
	}

	private static double chestOpeningMiss(final Minecraft mc, final BlockPos chest, final boolean fix) throws Exception {
		long window = mc.getWindow().handle();
		int[] size = new int[2];
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			ConfigStore.get().centerMouseFix = fix;
			size[0] = mc.getWindow().getScreenWidth();
			size[1] = mc.getWindow().getScreenHeight();
			// Captured afresh at this size, so GLFW remembers this centre and no other.
			mc.mouseHandler.releaseMouse();
			mc.mouseHandler.grabMouse();
		});
		Thread.sleep(250);
		// The window changes size while the mouse is captured: GLFW still remembers the old centre.
		CloseHotbarRegressionLab.atPoll(mc, () -> GLFW.glfwSetWindowSize(window, size[0] - 240, size[1] - 160));
		Thread.sleep(600);
		mc.executeBlocking(() -> {
			Vec3 centre = Vec3.atCenterOf(chest);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(centre, Direction.NORTH, chest, false));
		});
		long deadline = System.nanoTime() + 3_000_000_000L;
		while (!(mc.gui.screen() instanceof ContainerScreen) && System.nanoTime() < deadline) Thread.sleep(10);
		screenshot(mc, "feel-chest-" + (fix ? "fix" : "vanilla"));
		double[] miss = {-1};
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				DoubleBuffer x = stack.mallocDouble(1);
				DoubleBuffer y = stack.mallocDouble(1);
				GLFW.glfwGetCursorPos(window, x, y);
				double cx = mc.getWindow().getScreenWidth() / 2;
				double cy = mc.getWindow().getScreenHeight() / 2;
				miss[0] = mc.gui.screen() instanceof ContainerScreen ? Math.hypot(x.get(0) - cx, y.get(0) - cy) : -1;
			}
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
		});
		CloseHotbarRegressionLab.atPoll(mc, () -> GLFW.glfwSetWindowSize(window, size[0], size[1]));
		Thread.sleep(600);
		return miss[0];
	}

	// ---------------------------------------------------------------- leave with movement keys

	private static String leaveWithMovement(final Minecraft mc) throws Exception {
		if (!hasOption("leaveWithMovementKeys")) return "; leave=n/a";
		int up = binding(mc.options.keyUp);
		for (boolean on : new boolean[] {false, true}) {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.gui.screen() != null) mc.gui.screen().onClose();
				KeyMapping.releaseAll();
				setOption("leaveWithMovementKeys", on);
				mc.gui.setScreen(new InventoryScreen(mc.player));
			});
			synchronized (SENT) {
				SENT.clear();
			}
			recording = true;
			CloseHotbarRegressionLab.atPoll(mc, () -> key(mc, up, GLFW.GLFW_PRESS, 0));
			boolean[] closed = {false};
			mc.executeBlocking(() -> closed[0] = mc.gui.screen() == null && mc.options.keyUp.isDown());
			screenshot(mc, "feel-leave-" + (on ? "on" : "off"));
			Thread.sleep(200);
			CloseHotbarRegressionLab.atPoll(mc, () -> key(mc, up, GLFW.GLFW_RELEASE, 0));
			Thread.sleep(100);
			recording = false;
			List<String> sent;
			synchronized (SENT) {
				sent = new ArrayList<>(SENT);
			}
			if (on) {
				int close = sent.indexOf("close");
				int move = sent.indexOf("input(move)");
				check(closed[0], "a movement key closes the inventory and keeps walking");
				check(close >= 0 && move > close, "the close is sent before the movement: " + sent);
			} else {
				check(!closed[0], "with the option off a movement key leaves the inventory open");
			}
		}
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			setOption("leaveWithMovementKeys", false);
		});
		return "";
	}

	// ---------------------------------------------------------------- steady inventory

	/**
	 * Two clicks in flight and the server's answer to the first of them, played by hand: the answer
	 * says the second click has not happened yet and brings news for another slot. Vanilla shows the
	 * second click undone until its own answer arrives. With Steady inventory the screen keeps the
	 * second click, shows the news at once, shows a slot the server updates on its own at once, and
	 * holds the server's word and nothing else once the screen closes or the answer never comes.
	 */
	private static String steadyInventory(final Minecraft mc) throws Exception {
		if (!hasOption("steadyInventory")) return "; steady=n/a";
		int offhand = binding(mc.options.keySwapOffhand);
		int hotbar = binding(mc.options.keyHotbarSlots[0]);
		// 0: option off. 1: a slot update, then the screen closes. 2: no answer comes. 3: the last answer arrives.
		for (int mode = 0; mode < 4; mode++) {
			int scenario = mode;
			fixture(mc, new int[][] {{20, 1}}, Items.TOTEM_OF_UNDYING);
			var server = mc.getSingleplayerServer();
			var playerId = mc.player.getUUID();
			server.submit(() -> {
				var player = server.getPlayerList().getPlayer(playerId);
				player.getInventory().setItem(21, new ItemStack(Items.APPLE, 3));
				player.inventoryMenu.broadcastFullState();
			}).join();
			Thread.sleep(250);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.gui.screen() != null) mc.gui.screen().onClose();
				KeyMapping.releaseAll();
				setOption("steadyInventory", scenario != 0);
				mc.gui.setScreen(new InventoryScreen(mc.player));
			});
			// Past the opening tick: keys pressed before it are held back by the input fence.
			Thread.sleep(150);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				var screen = (InventoryScreen) mc.gui.screen();
				var menu = screen.getMenu();
				flick(mc, screen, 20);
				key(mc, offhand, GLFW.GLFW_PRESS, 0);
				key(mc, offhand, GLFW.GLFW_RELEASE, 0);
				flick(mc, screen, 21);
				key(mc, hotbar, GLFW.GLFW_PRESS, 0);
				key(mc, hotbar, GLFW.GLFW_RELEASE, 0);
				check(menu.getSlot(45).getItem().is(Items.TOTEM_OF_UNDYING) && menu.getSlot(36).getItem().is(Items.APPLE),
					"steady " + scenario + ": both clicks show at once");
				// The answer to the first click: the second has not happened, and slot 30 has news. It carries
				// the state number the client already has, so the fixtures still see the real server move on.
				List<ItemStack> answer = new ArrayList<>();
				for (var slot : menu.slots) answer.add(slot.getItem().copy());
				answer.set(21, new ItemStack(Items.APPLE, 3));
				answer.set(36, ItemStack.EMPTY);
				answer.set(30, new ItemStack(Items.COBBLESTONE, 7));
				mc.player.connection.handleContainerContent(
					new ClientboundContainerSetContentPacket(0, menu.getStateId(), answer, ItemStack.EMPTY));
				check(menu.getSlot(30).getItem().is(Items.COBBLESTONE), "steady " + scenario + ": the answer's news shows at once");
				boolean kept = menu.getSlot(36).getItem().is(Items.APPLE) && !menu.getSlot(21).hasItem();
				if (scenario == 0) {
					check(!kept, "steady off: Vanilla shows the second click undone (the flicker)");
					return;
				}
				check(kept && SteadyInventory.holding(), "steady " + scenario + ": the second click stays on screen");
				if (scenario == 1) {
					mc.player.connection.handleContainerSetSlot(
						new ClientboundContainerSetSlotPacket(0, menu.getStateId(), 36, new ItemStack(Items.DIAMOND)));
					check(menu.getSlot(36).getItem().is(Items.DIAMOND), "steady 1: a slot the server updates shows at once");
					screen.onClose();
					check(menu.getSlot(21).getItem().is(Items.APPLE) && menu.getSlot(36).getItem().is(Items.DIAMOND)
						&& !SteadyInventory.holding(), "steady 1: closed, the menu holds the server's word and its newer update");
				} else if (scenario == 3) {
					List<ItemStack> last = new ArrayList<>();
					for (var slot : menu.slots) last.add(slot.getItem().copy());
					last.set(31, new ItemStack(Items.COBBLESTONE, 2));
					mc.player.connection.handleContainerContent(
						new ClientboundContainerSetContentPacket(0, menu.getStateId(), last, ItemStack.EMPTY));
					check(menu.getSlot(36).getItem().is(Items.APPLE) && !menu.getSlot(21).hasItem()
						&& menu.getSlot(31).getItem().is(Items.COBBLESTONE) && !SteadyInventory.holding(),
						"steady 3: the last answer goes on screen whole");
				}
			});
			if (scenario == 2) {
				Thread.sleep(700);
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					var menu = mc.player.inventoryMenu;
					check(menu.getSlot(21).getItem().is(Items.APPLE) && !menu.getSlot(36).hasItem() && !SteadyInventory.holding(),
						"steady 2: no answer in time, the screen shows the server's word");
				});
			}
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.gui.screen() != null) mc.gui.screen().onClose();
				setOption("steadyInventory", true);
			});
			// The two real clicks reach the integrated server before the next fixture resets it.
			Thread.sleep(300);
		}
		return "";
	}

	// ---------------------------------------------------------------- hide recipe book

	/**
	 * Hide recipe book on a player whose book is saved open: the inventory shows no book and no
	 * book button, the saved setting stays open, and nothing about the book is sent to the server.
	 * Off, the book and its button come back as they were.
	 */
	private static String hideRecipeBook(final Minecraft mc) throws Exception {
		if (!hasOption("hideRecipeBook")) return "; book=n/a";
		boolean savedOpen = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
		long[] buttons = new long[2];
		try {
			bookPackets = 0;
			for (boolean hide : new boolean[] {false, true}) {
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					if (mc.gui.screen() != null) mc.gui.screen().onClose();
					setOption("hideRecipeBook", hide);
					mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
					mc.gui.setScreen(new InventoryScreen(mc.player));
				});
				// A few ticks: a hidden book must not reopen itself.
				Thread.sleep(250);
				screenshot(mc, "feel-book-" + (hide ? "hidden" : "shown"));
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					var screen = (InventoryScreen) mc.gui.screen();
					var book = ((dev.zymekoh.kohsinventorytweaks.mixin.AbstractRecipeBookScreenAccessor) screen)
						.kohsInventoryTweaks$getRecipeBookComponent();
					buttons[hide ? 1 : 0] = screen.children().stream()
						.filter(child -> child instanceof net.minecraft.client.gui.components.ImageButton).count();
					check(book.isVisible() != hide, (hide ? "hide recipe book: the book stays shut" : "option off: the saved open book shows"));
				});
			}
			check(buttons[1] == buttons[0] - 1, "hide recipe book: its button is gone; buttons " + buttons[0] + " -> " + buttons[1]);
			check(mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING), "hide recipe book: the saved book setting is untouched");
			check(bookPackets == 0, "hide recipe book sends nothing to the server; book packets=" + bookPackets);
		} finally {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				if (mc.gui.screen() != null) mc.gui.screen().onClose();
				setOption("hideRecipeBook", false);
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, savedOpen);
			});
		}
		return "";
	}

	// ---------------------------------------------------------------- enchanted items

	private static String enchantedItems(final Minecraft mc) throws Exception {
		if (!hasOption("fastEnchantedItems")) return "; shine=n/a";
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
		});
		StringBuilder out = new StringBuilder();
		// "full": every slot enchanted, the worst case. "pvp": a crystal kit as players carry it.
		for (String kit : LabSuite.quick() ? new String[] {"pvp"} : new String[] {"full", "pvp"}) {
			giveKit(mc, kit);
			for (String where : new String[] {"inventory", "hud"}) {
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					if (mc.gui.screen() != null) mc.gui.screen().onClose();
					if (where.equals("inventory")) mc.gui.setScreen(new InventoryScreen(mc.player));
				});
				screenshot(mc, "feel-shine-" + kit + "-" + where);
				double[][] runs = new double[2][2];
				// Interleaved, twice each, so a drift in the machine falls on both sides.
				int rounds = LabSuite.quick() ? 2 : 4;
				long window = LabSuite.quick() ? 1_500_000_000L : 3_000_000_000L;
				for (int round = 0; round < rounds; round++) {
					boolean on = round % 2 == 1;
					CloseHotbarRegressionLab.atPoll(mc, () -> setOption("fastEnchantedItems", on));
					Thread.sleep(700);
					long frames0 = DebugCollector.frames();
					long draws0 = atlasDraws;
					long start = System.nanoTime();
					while (System.nanoTime() - start < window) {
						// A little pointer movement keeps the game from counting the player as away.
						CloseHotbarRegressionLab.atPoll(mc, () -> ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(
							mc.getWindow().handle(), mc.getWindow().getScreenWidth() / 2.0 + (System.nanoTime() / 1_000_000 % 7),
							mc.getWindow().getScreenHeight() / 2.0));
						Thread.sleep(250);
					}
					double seconds = (System.nanoTime() - start) / 1.0E9;
					runs[on ? 1 : 0][0] += (DebugCollector.frames() - frames0) / seconds / (rounds / 2.0);
					runs[on ? 1 : 0][1] += (atlasDraws - draws0) / seconds / (rounds / 2.0);
				}
				String name = kit + (where.equals("inventory") ? "Inventory" : "Hud");
				out.append(String.format("; %sFps=%.0f->%.0f; %sAtlasDrawsPerSecond=%.0f->%.0f", name, runs[0][0], runs[1][0],
					name, runs[0][1], runs[1][1]));
				check(runs[1][1] < runs[0][1], name + ": fewer atlas redraws with Faster enchanted items");
			}
		}
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
		});
		return out.toString();
	}

	private static void giveKit(final Minecraft mc, final String kit) throws Exception {
		Item[] gear = {Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.BOW,
			Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.ENCHANTED_BOOK, Items.DIAMOND_SWORD, Items.DIAMOND_AXE,
			Items.DIAMOND_PICKAXE, Items.ELYTRA, Items.FISHING_ROD, Items.SHEARS, Items.FLINT_AND_STEEL, Items.SHIELD,
			Items.NETHERITE_HOE};
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		server.submit(() -> {
			var player = server.getPlayerList().getPlayer(playerId);
			var inventory = player.getInventory();
			for (int index = 0; index < inventory.getContainerSize(); index++) inventory.setItem(index, ItemStack.EMPTY);
			if (kit.equals("full")) {
				for (int index = 0; index < 36; index++) inventory.setItem(index, glint(gear[index % gear.length]));
			} else {
				// Hotbar: four of the nine shine (sword, pickaxe, crystals, bottles).
				ItemStack[] hotbar = {glint(Items.NETHERITE_SWORD), glint(Items.NETHERITE_PICKAXE), new ItemStack(Items.END_CRYSTAL, 64),
					new ItemStack(Items.OBSIDIAN, 64), new ItemStack(Items.GOLDEN_APPLE, 64), new ItemStack(Items.TOTEM_OF_UNDYING),
					new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.EXPERIENCE_BOTTLE, 64), new ItemStack(Items.RESPAWN_ANCHOR, 64)};
				for (int index = 0; index < hotbar.length; index++) inventory.setItem(index, hotbar[index]);
				ItemStack[] bag = {new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.TOTEM_OF_UNDYING),
					new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.END_CRYSTAL, 64), new ItemStack(Items.END_CRYSTAL, 64),
					new ItemStack(Items.OBSIDIAN, 64), new ItemStack(Items.EXPERIENCE_BOTTLE, 64), new ItemStack(Items.EXPERIENCE_BOTTLE, 64),
					new ItemStack(Items.GLOWSTONE, 64), new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.GOLDEN_APPLE, 64),
					glint(Items.NETHERITE_AXE), glint(Items.CROSSBOW)};
				for (int index = 0; index < bag.length; index++) inventory.setItem(9 + index, bag[index]);
				inventory.setItem(40, new ItemStack(Items.TOTEM_OF_UNDYING));
			}
			inventory.setItem(36, glint(Items.NETHERITE_BOOTS));
			inventory.setItem(37, glint(Items.NETHERITE_LEGGINGS));
			inventory.setItem(38, glint(Items.NETHERITE_CHESTPLATE));
			inventory.setItem(39, glint(Items.NETHERITE_HELMET));
			player.inventoryMenu.broadcastFullState();
		}).join();
		Thread.sleep(400);
	}

	private static ItemStack glint(final Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	// ---------------------------------------------------------------- helpers

	@FunctionalInterface
	private interface ScreenStep {
		void run(InventoryScreen screen) throws Exception;
	}

	private static void inInventory(final Minecraft mc, final String name, final ScreenStep step) throws Exception {
		InventoryScreen[] opened = new InventoryScreen[1];
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
			KeyMapping.releaseAll();
			opened[0] = new InventoryScreen(mc.player);
			mc.gui.setScreen(opened[0]);
		});
		screenshot(mc, "feel-" + name + "-start");
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			try {
				step.run(opened[0]);
			} catch (RuntimeException error) {
				throw error;
			} catch (Exception error) {
				throw new IllegalStateException(error);
			}
		});
		screenshot(mc, "feel-" + name + "-end");
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.gui.screen() != null) mc.gui.screen().onClose();
		});
	}

	private static void screenshot(final Minecraft mc, final String name) throws Exception {
		Thread.sleep(150);
		java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
		mc.executeBlocking(() -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
			message -> done.complete(null)));
		done.get(10, java.util.concurrent.TimeUnit.SECONDS);
	}

	/** Clears the inventory and puts {@code count} of {@code item} in each given menu slot (9-35). */
	private static void fixture(final Minecraft mc, final int[][] slots, final Item item) throws Exception {
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		int[] stateBefore = new int[1];
		mc.executeBlocking(() -> stateBefore[0] = mc.player.inventoryMenu.getStateId());
		server.submit(() -> {
			var player = server.getPlayerList().getPlayer(playerId);
			player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				player.getBoundingBox().inflate(24.0)).forEach(net.minecraft.world.entity.Entity::discard);
			var inventory = player.getInventory();
			for (int index = 0; index < inventory.getContainerSize(); index++) inventory.setItem(index, ItemStack.EMPTY);
			for (int[] slot : slots) inventory.setItem(slot[0], new ItemStack(item, slot[1]));
			player.inventoryMenu.broadcastFullState();
		}).join();
		long deadline = System.nanoTime() + 5_000_000_000L;
		boolean[] synced = {false};
		while (!synced[0] && System.nanoTime() < deadline) {
			mc.executeBlocking(() -> synced[0] = mc.player.inventoryMenu.getStateId() != stateBefore[0]
				&& mc.player.getInventory().getItem(slots[0][0]).is(item));
			if (!synced[0]) Thread.sleep(10);
		}
		if (!synced[0]) throw new IllegalStateException("Fixture sync timed out");
	}

	private static void verifyServer(final Minecraft mc, final String label) throws Exception {
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		long deadline = System.nanoTime() + 3_000_000_000L;
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
		check(mismatch == null, label + ": server matches client" + (mismatch == null ? "" : " (" + mismatch + ")"));
	}

	private static int count(final AbstractContainerScreen<?> screen, final int menuSlot) {
		return screen.getMenu().getSlot(menuSlot).getItem().getCount();
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

	private static void press(final Minecraft mc, final int button, final int mods) {
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeButton(mc.getWindow().handle(),
			new MouseButtonInfo(button, mods), GLFW.GLFW_PRESS);
	}

	private static void release(final Minecraft mc, final int button, final int mods) {
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeButton(mc.getWindow().handle(),
			new MouseButtonInfo(button, mods), GLFW.GLFW_RELEASE);
	}

	private static void click(final Minecraft mc, final int button, final int mods) {
		press(mc, button, mods);
		release(mc, button, mods);
	}

	private static void key(final Minecraft mc, final int code, final int action, final int mods) {
		((KeyboardHandlerDebugInvoker) mc.keyboardHandler).kohsInventoryDebug$invokeKeyPress(
			mc.getWindow().handle(), action, new KeyEvent(code, GLFW.glfwGetKeyScancode(code), mods));
	}

	private static InputConstants.Key key(final KeyMapping mapping) {
		return ((dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey();
	}

	private static void setKey(final KeyMapping mapping, final InputConstants.Key key) {
		mapping.setKey(key);
		KeyMapping.resetMapping();
	}

	private static int binding(final KeyMapping mapping) {
		return key(mapping).getValue();
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

	private static Field findField(final Class<?> type, final String name) throws NoSuchFieldException {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			try {
				Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				// Keep looking up the hierarchy.
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static void command(final Minecraft mc, final String command) throws Exception {
		mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
		Thread.sleep(120);
	}

	private static void check(final boolean result, final String label) {
		checks++;
		if (!result) {
			failures++;
			FAILED.add(label);
			DebugCollector.issue("INVENTORY_FEEL_FAIL", label);
		} else {
			DebugCollector.info("INVENTORY_FEEL_OK", label);
		}
	}
}
