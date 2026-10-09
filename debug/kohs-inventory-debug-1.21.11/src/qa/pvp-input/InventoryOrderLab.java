package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The order of the packets a server receives around an inventory opening, judged the
 * way a server judges it.
 *
 * <p>A Vanilla client opens its inventory inside a tick: the keys are released, the
 * tick tells the server the player let go of them and stopped sprinting, and only a
 * later input poll can click a slot. A server therefore never sees a container click
 * while its last word from the client is "holding forward, sprinting", nor a turn of
 * the head after the first click. Servers check exactly that: this lab's judge follows
 * the inventory rules of TotemGuard, an open-source anti-cheat used on crystal PvP
 * servers (click or close while sprinting or moving, movement, sprint, aim or a world
 * action with the inventory open, a click after a close in one tick).</p>
 *
 * <p>Each trial sprints forward turning the camera, presses the inventory key at a
 * random moment of the tick, flicks to a totem and presses the offhand key a few
 * milliseconds later, as a player re-totems in a fight, and records every packet the
 * client sends. Vanilla trials must never be flagged; neither may a trial with Super
 * Fast Inventory, however early it opened, and its totem must still reach the offhand.</p>
 *
 * <p>Two more kinds of trial follow the totem with a second action. A second shortcut a
 * few milliseconds later must reach the server no closer to the first than the hand made
 * it, unless a full tick still separates them: a pair pressed closer together than it was
 * made is what an automatic totem looks like. And closing the inventory and pressing a
 * hotbar key at once must close it and change the held slot, in that order.
 * Integrated singleplayer only.</p>
 */
public final class InventoryOrderLab {
	private static final int TOTEM_SLOT = 20;
	private static final int[] FAST_DELAYS = {0, 6, 12, 20, 30, 42};
	private static final int[] VANILLA_DELAYS = {0, 12, 30, 60, 90};
	private static final int FAST_REPEATS = 8;
	private static final int VANILLA_REPEATS = 4;
	private static final int[] PAIR_GAPS = {8, 20, 35, 60, 90};
	private static final int PAIR_REPEATS = 4;
	private static final int LEAVE_REPEATS = 6;
	/** One client tick: past it, two actions are apart enough whatever the hand did. */
	private static final long TICK_NANOS = 50_000_000L;
	/** The clock of the lab against the clock of the client: frames, not the mod. */
	private static final long PAIR_TOLERANCE_MS = 3L;

	/** One packet the client sent, reduced to what the judge reads. */
	private record Sent(long nanos, String kind, boolean movement, boolean jump, boolean rotation) {
	}

	private static final List<Sent> SENT = new ArrayList<>();
	private static volatile boolean recording;
	private static float lastYaw = Float.NaN;
	private static float lastPitch = Float.NaN;

	private InventoryOrderLab() {
	}

	/** Called for every packet the client sends; records nothing unless a trial is running. */
	public static void onPacket(final Packet<?> packet) {
		if (!recording) return;
		String kind;
		boolean movement = false;
		boolean jump = false;
		boolean rotation = false;
		if (packet instanceof ServerboundPlayerInputPacket input) {
			Input keys = input.input();
			kind = "input";
			movement = keys.forward() || keys.backward() || keys.left() || keys.right();
			jump = keys.jump();
		} else if (packet instanceof ServerboundMovePlayerPacket move) {
			kind = "flying";
			if (move.hasRotation()) {
				float yaw = move.getYRot(0.0F);
				float pitch = move.getXRot(0.0F);
				rotation = yaw != lastYaw || pitch != lastPitch;
				lastYaw = yaw;
				lastPitch = pitch;
			}
		} else if (packet instanceof ServerboundPlayerCommandPacket command) {
			kind = command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING ? "sprint-start"
				: command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING ? "sprint-stop" : "command";
		} else if (packet instanceof ServerboundContainerClickPacket) {
			kind = "click";
		} else if (packet instanceof ServerboundContainerClosePacket) {
			kind = "close";
		} else if (packet instanceof ServerboundClientTickEndPacket) {
			kind = "tick-end";
		} else if (packet instanceof ServerboundInteractPacket) {
			kind = "interact";
		} else if (packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundUseItemOnPacket) {
			kind = "use";
		} else if (packet instanceof ServerboundSetCarriedItemPacket) {
			kind = "held-slot";
		} else if (packet instanceof ServerboundPlayerActionPacket) {
			kind = "player-action";
		} else {
			return;
		}
		synchronized (SENT) {
			SENT.add(new Sent(System.nanoTime(), kind, movement, jump, rotation));
		}
	}

	/**
	 * What a server would hold against this packet sequence. The state is the server's:
	 * what the client last said about its keys and its sprint, and whether a click has
	 * told it the inventory is open.
	 */
	static List<String> judge(final List<Sent> packets) {
		List<String> flags = new ArrayList<>();
		boolean sprinting = false;
		boolean moving = false;
		boolean jumping = false;
		boolean open = false;
		boolean movementFlagged = false;
		int closes = 0;
		for (Sent packet : packets) {
			switch (packet.kind()) {
				case "input" -> {
					moving = packet.movement();
					jumping = packet.jump();
					if (!moving && !jumping) {
						movementFlagged = false;
					} else if (open && !movementFlagged) {
						flags.add("move with the inventory open");
						movementFlagged = true;
					}
				}
				case "sprint-start" -> {
					sprinting = true;
					if (open) flags.add("sprint with the inventory open");
				}
				case "sprint-stop" -> sprinting = false;
				case "flying" -> {
					if (open && packet.rotation()) flags.add("aim with the inventory open");
				}
				case "click" -> {
					if (sprinting) flags.add("click while sprinting");
					else if (moving) flags.add("click while moving");
					if (closes > 0) flags.add("click after a close in one tick");
					open = true;
				}
				case "close" -> {
					if (sprinting) flags.add("close while sprinting");
					else if (moving) flags.add("close while moving");
					closes++;
					if (closes >= 2) flags.add("two closes in one tick");
					open = false;
					movementFlagged = false;
				}
				case "tick-end" -> {
					if (open && (sprinting || moving || jumping) && !movementFlagged) {
						flags.add(sprinting ? "sprint with the inventory open" : "move with the inventory open");
						movementFlagged = true;
					}
					closes = 0;
				}
				case "interact", "use", "held-slot", "player-action" -> {
					if (open) flags.add(packet.kind() + " with the inventory open");
				}
				default -> {
				}
			}
		}
		return flags;
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
		Random random = new Random(20261008L);
		int fastTrials = 0;
		int fastEarly = 0;
		int fastFlagged = 0;
		int fastLost = 0;
		int vanillaTrials = 0;
		int vanillaFlagged = 0;
		int pairTrials = 0;
		int pairBad = 0;
		long pairWorstMs = Long.MIN_VALUE;
		int leaveTrials = 0;
		int leaveBad = 0;
		try {
			command(mc, "gamemode survival");
			// Room for the inventory beside its recipe book, and the book shut: a narrow screen
			// with the book open hides every slot behind it.
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				mc.options.guiScale().set(3);
				mc.resizeDisplay();
				mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
			});
			// A clear strip to sprint along, looking down it.
			command(mc, "tp @s ~ ~ ~ 0 0");
			command(mc, "fill ~-2 ~-1 ~-2 ~2 ~-1 ~26 minecraft:smooth_stone");
			command(mc, "fill ~-2 ~ ~-2 ~2 ~2 ~26 minecraft:air");
			command(mc, "effect give @s minecraft:saturation 5 10 true");
			Thread.sleep(400);
			double[] start = new double[3];
			mc.executeBlocking(() -> {
				start[0] = mc.player.getX();
				start[1] = mc.player.getY();
				start[2] = mc.player.getZ();
			});
			// KOHS_LAB_QUICK in the environment runs three trials instead of sixty-eight.
			boolean quick = System.getenv("KOHS_LAB_QUICK") != null;
			for (int delay : quick ? new int[] {0, 12} : FAST_DELAYS) {
				for (int repeat = 0; repeat < (quick ? 1 : FAST_REPEATS); repeat++) {
					Trial trial = trial(mc, start, true, delay, 0, false, random);
					fastTrials++;
					if (trial.early()) fastEarly++;
					if (!trial.flags().isEmpty()) fastFlagged++;
					if (!trial.swapped()) fastLost++;
				}
			}
			for (int delay : quick ? new int[] {60} : VANILLA_DELAYS) {
				for (int repeat = 0; repeat < (quick ? 1 : VANILLA_REPEATS); repeat++) {
					Trial trial = trial(mc, start, false, delay, 0, false, random);
					vanillaTrials++;
					if (!trial.flags().isEmpty()) vanillaFlagged++;
				}
			}
			for (int gap : quick ? new int[] {20} : PAIR_GAPS) {
				for (int repeat = 0; repeat < (quick ? 1 : PAIR_REPEATS); repeat++) {
					Trial trial = trial(mc, start, true, 0, gap, false, random);
					pairTrials++;
					pairWorstMs = Math.max(pairWorstMs, trial.closerMs());
					if (!trial.flags().isEmpty() || !trial.swapped() || trial.closerMs() > PAIR_TOLERANCE_MS) pairBad++;
				}
			}
			for (int repeat = 0; repeat < (quick ? 1 : LEAVE_REPEATS); repeat++) {
				Trial trial = trial(mc, start, true, 0, 0, true, random);
				leaveTrials++;
				if (!trial.flags().isEmpty() || !trial.swapped() || !trial.left()) leaveBad++;
			}
			DebugCollector.info("INVENTORY_ORDER_SUMMARY", "fast: trials=" + fastTrials + "; early=" + fastEarly + "; flagged=" + fastFlagged
				+ "; lostSwaps=" + fastLost + "; vanilla: trials=" + vanillaTrials + "; flagged=" + vanillaFlagged
				+ "; pairs: trials=" + pairTrials + "; bad=" + pairBad + "; closestToHandMs=" + pairWorstMs
				+ "; leave: trials=" + leaveTrials + "; bad=" + leaveBad);
			if (fastFlagged > 0 || vanillaFlagged > 0 || fastLost > 0 || pairBad > 0 || leaveBad > 0) {
				throw new IllegalStateException("Inventory order: flagged fast=" + fastFlagged + " vanilla=" + vanillaFlagged
					+ " lostSwaps=" + fastLost + " pairs=" + pairBad + " leave=" + leaveBad);
			}
		} catch (RuntimeException error) {
			throw error;
		} catch (Exception error) {
			throw new IllegalStateException(error);
		} finally {
			recording = false;
			try {
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					if (mc.screen != null) mc.screen.onClose();
					KeyMapping.releaseAll();
					ConfigStore.replaceAndSave(savedConfig);
					mc.options.guiScale().set(savedGui);
					mc.resizeDisplay();
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

	/**
	 * @param closerMs how much closer together the two shortcuts of a pair reached the
	 *     server than the hand made them, or than a tick; zero or less is right
	 * @param left whether the inventory ended closed with the hotbar slot pressed after it held
	 */
	private record Trial(boolean early, boolean swapped, List<String> flags, long closerMs, boolean left) {
	}

	/**
	 * @param follow milliseconds after the offhand key at which a hotbar key follows it over
	 *     the same slot, or zero
	 * @param leave whether the inventory key and then a hotbar key follow, eight milliseconds apart
	 */
	private static Trial trial(final Minecraft mc, final double[] start, final boolean fast, final int delay,
		final int follow, final boolean leave, final Random random) throws Exception {
		int forward = binding(mc.options.keyUp);
		int sprint = binding(mc.options.keySprint);
		int inventory = binding(mc.options.keyInventory);
		int offhand = binding(mc.options.keySwapOffhand);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.screen != null) mc.screen.onClose();
			KeyMapping.releaseAll();
			var config = ConfigStore.get();
			config.superFastInventory = fast;
			config.shortcutsFollowPointer = true;
			config.inventoryGuiScalerEnabled = false;
		});
		command(mc, "tp @s " + start[0] + " " + start[1] + " " + start[2] + " 0 0");
		var server = mc.getSingleplayerServer();
		var playerId = mc.player.getUUID();
		server.submit(() -> {
			var player = server.getPlayerList().getPlayer(playerId);
			var items = player.getInventory();
			for (int index = 0; index < items.getContainerSize(); index++) items.setItem(index, ItemStack.EMPTY);
			items.setItem(TOTEM_SLOT, new ItemStack(Items.TOTEM_OF_UNDYING));
			player.inventoryMenu.broadcastFullState();
		}).join();
		Thread.sleep(250);

		synchronized (SENT) {
			SENT.clear();
		}
		lastYaw = Float.NaN;
		lastPitch = Float.NaN;
		recording = true;
		// Sprint forward, turning the camera a little every frame, as in a fight.
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			key(mc, forward, GLFW.GLFW_PRESS);
			key(mc, sprint, GLFW.GLFW_PRESS);
		});
		double[] turn = {mc.getWindow().getScreenWidth() / 2.0};
		long runUntil = System.nanoTime() + (450L + random.nextInt(50)) * 1_000_000L;
		while (System.nanoTime() < runUntil) {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				turn[0] += 5.0;
				((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), turn[0], mc.getWindow().getScreenHeight() / 2.0);
			});
		}
		boolean[] early = {false};
		long[] made = new long[2];
		int[] wanted = {-1};
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			tap(mc, inventory);
			if (delay == 0) {
				// Decide this press now, as the poll's end would, and act before any frame.
				SuperFastInventoryController.afterInputPoll(mc);
				early[0] = mc.screen instanceof InventoryScreen;
				made[0] = System.nanoTime();
				act(mc, offhand);
			}
		});
		if (delay > 0) {
			mc.executeBlocking(() -> early[0] = mc.screen instanceof InventoryScreen && SuperFastInventoryController.lastOpenWasImmediate());
			Thread.sleep(delay);
			CloseHotbarRegressionLab.atPoll(mc, () -> act(mc, offhand));
		}
		if (follow > 0) {
			Thread.sleep(follow);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				made[1] = System.nanoTime();
				tap(mc, binding(mc.options.keyHotbarSlots[0]));
			});
		}
		if (leave) {
			Thread.sleep(8);
			CloseHotbarRegressionLab.atPoll(mc, () -> tap(mc, inventory));
			Thread.sleep(8);
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				wanted[0] = (mc.player.getInventory().getSelectedSlot() + 1) % 9;
				tap(mc, binding(mc.options.keyHotbarSlots[wanted[0]]));
			});
		}
		Thread.sleep(170);
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			key(mc, forward, GLFW.GLFW_RELEASE);
			key(mc, sprint, GLFW.GLFW_RELEASE);
		});
		Thread.sleep(80);
		recording = false;
		List<Sent> packets;
		synchronized (SENT) {
			packets = new ArrayList<>(SENT);
		}
		List<String> flags = judge(packets);
		boolean[] swapped = {false};
		boolean[] left = {false};
		mc.executeBlocking(() -> {
			swapped[0] = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
			left[0] = mc.screen == null && mc.player.getInventory().getSelectedSlot() == wanted[0];
		});
		long closerMs = 0L;
		if (follow > 0) {
			long[] clicks = packets.stream().filter(packet -> packet.kind().equals("click")).mapToLong(Sent::nanos).toArray();
			// A pair missing a click is as wrong as a pair pressed together.
			closerMs = clicks.length < 2 ? 999L
				: (Math.min(made[1] - made[0], TICK_NANOS) - (clicks[1] - clicks[0])) / 1_000_000L;
		}
		DebugCollector.info("INVENTORY_ORDER_TRIAL", "mode=" + (fast ? "fast" : "vanilla") + "; delay=" + delay + "ms; follow=" + follow
			+ "ms; leave=" + leave + "; early=" + early[0] + "; totemInOffhand=" + swapped[0] + "; closerMs=" + closerMs
			+ "; left=" + left[0] + "; flags=" + flags + "; order=" + compact(packets));
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			if (mc.screen != null) mc.screen.onClose();
			KeyMapping.releaseAll();
		});
		Thread.sleep(120);
		return new Trial(early[0], swapped[0], flags, closerMs, left[0]);
	}

	/** Flick to the totem and press the offhand key; with no inventory yet, the key goes to the world. */
	private static void act(final Minecraft mc, final int offhand) {
		if (mc.screen instanceof InventoryScreen screen) {
			flick(mc, screen, TOTEM_SLOT);
		}
		tap(mc, offhand);
	}

	/** The packets from the last two before the inventory key's effects to the end, as a short string. */
	private static String compact(final List<Sent> packets) {
		int first = packets.size();
		for (int index = 0; index < packets.size(); index++) {
			String kind = packets.get(index).kind();
			if (kind.equals("click") || kind.equals("close") || kind.equals("sprint-stop")) {
				first = index;
				break;
			}
		}
		int from = Math.max(0, first - 6);
		StringBuilder text = new StringBuilder();
		for (int index = from; index < Math.min(packets.size(), from + 22); index++) {
			Sent packet = packets.get(index);
			if (!text.isEmpty()) text.append(' ');
			text.append(packet.kind());
			if (packet.kind().equals("input")) text.append(packet.movement() ? "(move)" : "(none)");
			if (packet.rotation()) text.append("(turn)");
		}
		return text.toString();
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

	private static void key(final Minecraft mc, final int code, final int action) {
		((KeyboardHandlerDebugInvoker) mc.keyboardHandler).kohsInventoryDebug$invokeKeyPress(
			mc.getWindow().handle(), action, new KeyEvent(code, GLFW.glfwGetKeyScancode(code), 0));
	}

	private static void tap(final Minecraft mc, final int code) {
		key(mc, code, GLFW.GLFW_PRESS);
		key(mc, code, GLFW.GLFW_RELEASE);
	}

	private static int binding(final KeyMapping mapping) {
		return ((KeyMappingDebugAccessor) mapping).kohsInventoryDebug$getKey().getValue();
	}

	private static void command(final Minecraft mc, final String command) throws Exception {
		mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
		Thread.sleep(120);
	}
}
