package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Totem protection: inventory shortcuts never take a totem of undying out of the
 * offhand and never throw one away.
 *
 * <p>It only declines an action the player started. Nothing is clicked, moved or
 * sent in its place, so a declined press leaves the inventory exactly as not pressing
 * would have. Clicks with the pointer stay untouched: picking a totem up by hand is
 * always deliberate.</p>
 */
public final class TotemGuard {
	/** The slot id Vanilla gives a click outside the window, which drops the carried stack. */
	private static final int CLICKED_OUTSIDE = -999;

	private TotemGuard() {
	}

	public static boolean blocks(
		final AbstractContainerScreen<?> screen,
		final @Nullable Slot slot,
		final int slotId,
		final int button,
		final ContainerInput input
	) {
		Player player = Minecraft.getInstance().player;
		if (!ConfigStore.get().totemGuard || player == null || screen instanceof CreativeModeInventoryScreen
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return false;
		}
		return switch (input) {
			// The offhand key over any slot, or a hotbar key over the offhand slot.
			case SWAP -> isTotem(player.getOffhandItem()) && (button == Inventory.SLOT_OFFHAND
				? slot == null || !isTotem(slot.getItem())
				: isOffhand(player, slot) && !isTotem(player.getInventory().getItem(button)));
			case THROW -> slot != null && isTotem(slot.getItem());
			case PICKUP -> slotId == CLICKED_OUTSIDE && isTotem(screen.getMenu().getCarried());
			default -> false;
		};
	}

	/** Whether the offhand key over this empty slot would move the offhand totem into it. */
	public static boolean swapWouldEmptyOffhand(final Player player, final Slot slot) {
		ItemStack offhand = player.getOffhandItem();
		return isTotem(offhand) && !slot.hasItem() && slot.mayPlace(offhand);
	}

	private static boolean isOffhand(final Player player, final @Nullable Slot slot) {
		return slot != null && slot.container == player.getInventory() && slot.getContainerSlot() == Inventory.SLOT_OFFHAND;
	}

	private static boolean isTotem(final ItemStack stack) {
		return stack.is(Items.TOTEM_OF_UNDYING);
	}
}
