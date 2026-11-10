package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.NautilusInventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Resolves the slot a shortcut acts on from the latest delivered pointer.
 *
 * <p>Vanilla recomputes the hovered slot while it renders the screen. A number
 * key, the offhand swap or the drop key pressed after the pointer moved but before
 * the next frame acts on the slot that was under the pointer one frame earlier: a
 * quick flick to a totem and F can swap the wrong item. Refreshing the target at
 * the moment of the key press makes the shortcut act where the player is
 * pointing. Nothing is clicked or sent here; Vanilla still handles the key.</p>
 *
 * <p>Only Vanilla's own container screens are covered, by exact class: their
 * hovered slot follows the standard {@link AbstractContainerScreen} rules. The
 * creative inventory and modded subclasses keep Vanilla's render-time target.</p>
 */
public final class SlotTargeting {
	private static final Set<Class<?>> SCREENS = Set.of(
		InventoryScreen.class, ContainerScreen.class, ShulkerBoxScreen.class,
		DispenserScreen.class, HopperScreen.class, CraftingScreen.class, CrafterScreen.class,
		FurnaceScreen.class, BlastFurnaceScreen.class, SmokerScreen.class,
		BrewingStandScreen.class, EnchantmentScreen.class, AnvilScreen.class,
		GrindstoneScreen.class, StonecutterScreen.class, LoomScreen.class,
		CartographyTableScreen.class, SmithingScreen.class, BeaconScreen.class,
		MerchantScreen.class, HorseInventoryScreen.class, NautilusInventoryScreen.class
	);

	/**
	 * Lab switch for KoHs Inventory Debug: keeps Vanilla's last-rendered target so a
	 * lab can measure what the refresh changes. Never set by the mod itself.
	 */
	public static volatile boolean labVanillaTargeting;

	private SlotTargeting() {
	}

	/** Whether shortcuts on this screen resolve against the latest pointer. */
	public static boolean covers(final AbstractContainerScreen<?> screen) {
		return SCREENS.contains(screen.getClass());
	}

	/**
	 * Points the screen's hovered slot at whatever lies under {@code x, y}.
	 *
	 * @param screenCoordinates whether the position is in screen space, as the
	 *     pointer is, rather than already converted into the scaled inventory surface
	 */
	public static void refresh(
		final AbstractContainerScreen<?> screen,
		final double x,
		final double y,
		final boolean screenCoordinates
	) {
		Minecraft minecraft = Minecraft.getInstance();
		if (labVanillaTargeting || !ConfigStore.get().superFastInventory || !ConfigStore.get().shortcutsFollowPointer || !covers(screen) || minecraft.gui.screen() != screen
			|| minecraft.gui.overlay() != null || !minecraft.isWindowActive()
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return;
		}
		double scale = screenCoordinates ? InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get()) : 1.0;
		var access = (AbstractContainerScreenAccessor) screen;
		Slot previous = access.kohsInventoryTweaks$getHoveredSlot();
		Slot current = access.kohsInventoryTweaks$findHoveredSlot(
			InventoryGuiScaler.toInventoryCoordinate(x, screen.width, scale),
			InventoryGuiScaler.toInventoryCoordinate(y, screen.height, scale));
		if (previous != current) {
			access.kohsInventoryTweaks$setHoveredSlot(current);
			// Preserve Vanilla's bundle/slot cleanup when leaving the previous target.
			if (previous != null) {
				access.kohsInventoryTweaks$stopHovering(previous);
			}
		}
	}

	/** {@link #refresh} from the pointer as the mouse handler last received it. */
	public static void refreshFromPointer(final AbstractContainerScreen<?> screen) {
		Minecraft minecraft = Minecraft.getInstance();
		refresh(screen,
			minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
			minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()), true);
	}
}
