package dev.zymekoh.kohsinventorydebug;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Puts the local player into a known state before a macro that needs items.
 *
 * <p>Everything goes through the ordinary command path a player could type, so
 * the lab still never calls a container action, slot or packet of its own. The
 * integrated server owns the result, which is what makes the offhand assertions
 * meaningful: an item that reaches the offhand reached it the way the game
 * puts it there.</p>
 */
public final class LabKit {
	/** Hotbar slot the offhand macros select before every swap. */
	public static final int KIT_SLOT = 0;

	private LabKit() {
	}

	/**
	 * Grants the offhand kit and waits for the server to acknowledge it.
	 *
	 * @return whether the selected hotbar slot holds the expected item in time
	 */
	public static boolean prepareOffhandKit(final Minecraft minecraft) throws InterruptedException {
		if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
			DebugCollector.warn("LAB_KIT", "No local player to prepare.");
			return false;
		}
		if (!MacroTestController.isSafeLocalWorld(minecraft)) {
			DebugCollector.warn("LAB_KIT", "Refusing to prepare a kit outside a local singleplayer world.");
			return false;
		}
		// A world saved with cheats off rejects every command silently, and the macro
		// would then read an empty offhand and call it a failed swap. Say so instead.
		if (!commandsAllowed(minecraft)) {
			DebugCollector.warn("LAB_KIT", "World has cheats disabled, so /give and /gamemode are refused."
				+ " Open the lab world with cheats on, or pick a world that has them.");
			return false;
		}
		send(minecraft, "gamemode creative");
		send(minecraft, "clear");
		// A totem is the item a PvP player actually races into the offhand, and a
		// shield is the other one, so the macro swaps exactly what players swap.
		send(minecraft, "give @s minecraft:totem_of_undying 1");
		send(minecraft, "give @s minecraft:shield 1");
		minecraft.execute(() -> {
			if (minecraft.player != null) {
				minecraft.player.getInventory().setSelectedSlot(KIT_SLOT);
			}
		});

		long deadline = System.nanoTime() + 5_000_000_000L;
		while (System.nanoTime() < deadline) {
			if (!anyKitItem(minecraft).isEmpty()) {
				DebugCollector.info("LAB_KIT", "Kit ready; selected=" + describe(selected(minecraft))
					+ "; carried=" + describe(anyKitItem(minecraft))
					+ "; offhand=" + describe(offhand(minecraft)));
				return true;
			}
			Thread.sleep(25);
		}
		DebugCollector.warn("LAB_KIT", "Kit never arrived after 5s with cheats on; the offhand"
			+ " assertions would be meaningless. selected=" + describe(selected(minecraft))
			+ "; carried=" + describe(anyKitItem(minecraft)));
		return false;
	}

	/** Whether the local integrated server will honour a command at all. */
	private static boolean commandsAllowed(final Minecraft minecraft) {
		var server = minecraft.getSingleplayerServer();
		return server != null && server.getWorldData().isAllowCommands();
	}

	/**
	 * The kit item wherever it landed.
	 *
	 * <p>{@code /give} fills the first free slot, which is not reliably the selected
	 * one, so reading only the selected slot reports a missing kit that is actually
	 * sitting two slots over.</p>
	 */
	public static ItemStack anyKitItem(final Minecraft minecraft) {
		if (minecraft == null || minecraft.player == null) {
			return ItemStack.EMPTY;
		}
		for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && (stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.SHIELD))) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	public static ItemStack selected(final Minecraft minecraft) {
		return minecraft == null || minecraft.player == null
			? ItemStack.EMPTY
			: minecraft.player.getInventory().getSelectedItem();
	}

	public static ItemStack offhand(final Minecraft minecraft) {
		return minecraft == null || minecraft.player == null
			? ItemStack.EMPTY
			: minecraft.player.getOffhandItem();
	}

	/** Whether the offhand holds the item this kit expects the swap to move. */
	public static boolean offhandHoldsKitItem(final Minecraft minecraft) {
		ItemStack stack = offhand(minecraft);
		return !stack.isEmpty()
			&& (stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.SHIELD));
	}

	public static String describe(final ItemStack stack) {
		return stack == null || stack.isEmpty() ? "empty" : stack.getCount() + "x" + stack.getItem();
	}

	private static void send(final Minecraft minecraft, final String command) {
		DebugCollector.info("LAB_KIT", "/" + command);
		minecraft.execute(() -> {
			if (minecraft.player != null && minecraft.player.connection != null) {
				minecraft.player.connection.sendCommand(command);
			}
		});
	}
}
