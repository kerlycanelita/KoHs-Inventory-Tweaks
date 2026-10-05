package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.TotemGuard;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZMotion;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What Inventory Tweaks draws on a container's slots and adds to its tooltips: the
 * held hotbar slot, key hints, low durability, the swap warning, the change flash,
 * steady tooltips and inventory totals.
 *
 * <p>Everything reads state the player can already see, and every mark is drawn
 * inside the slot it belongs to. Nothing follows the pointer, so nothing can trail
 * behind it, and nothing here clicks, moves or sends anything.</p>
 */
public final class SlotOverlays {
	private static final long FLASH_NANOS = 180_000_000L;
	private static final long STEADY_TOOLTIP_NANOS = 150_000_000L;
	private static final int OFFHAND_HINT = 9;
	// ponytail: one identity map per open screen; per-slot fields if it ever shows in a profile.
	private static final Map<Slot, Seen> SEEN = new IdentityHashMap<>();
	private static final String[] HINTS = new String[OFFHAND_HINT + 1];
	private static @Nullable AbstractContainerScreen<?> screen;
	private static @Nullable Slot hovered;
	private static long hoveredSince;

	/** What a slot held when it last changed, and when that was (0: since the screen opened). */
	private record Seen(Item item, int count, long changedAt) {
	}

	private SlotOverlays() {
	}

	/** Once per frame, after Vanilla picked the hovered slot and before the slots are drawn. */
	public static void beginFrame(final AbstractContainerScreen<?> current) {
		if (current != screen) {
			screen = current;
			SEEN.clear();
			hovered = null;
			// Keys cannot be rebound while a container is open, so one read per screen holds.
			Minecraft minecraft = Minecraft.getInstance();
			for (int index = 0; index < OFFHAND_HINT; index++) {
				HINTS[index] = hint(minecraft.options.keyHotbarSlots[index]);
			}
			HINTS[OFFHAND_HINT] = hint(minecraft.options.keySwapOffhand);
		}
		Slot now = ((AbstractContainerScreenAccessor) current).kohsInventoryTweaks$getHoveredSlot();
		if (now != hovered) {
			hovered = now;
			hoveredSince = System.nanoTime();
		}
	}

	/** Steady tooltips: the pointer only just reached this slot, so its tooltip waits. */
	public static boolean holdsTooltip() {
		return ConfigStore.get().steadyTooltips && enabled()
			&& System.nanoTime() - hoveredSince < STEADY_TOOLTIP_NANOS;
	}

	/** Drawn after Vanilla's own slot contents, in the slot's local coordinates. */
	public static void drawSlot(
		final GuiGraphics graphics,
		final AbstractContainerScreen<?> owner,
		final Slot slot
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || !enabled()) {
			return;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		if (config.slotFlash) {
			flash(graphics, slot);
		}
		boolean own = slot.container == player.getInventory();
		int index = slot.getContainerSlot();
		if (config.heldSlotMarker && own && index == player.getInventory().getSelectedSlot()) {
			ZDraw.brackets(graphics, slot.x - 1, slot.y - 1, 18, 18, 4, ZTheme.CYAN);
		}
		boolean hinted = false;
		if (config.keyHints && own) {
			String hint = index >= 0 && index < OFFHAND_HINT ? HINTS[index]
				: index == Inventory.SLOT_OFFHAND ? HINTS[OFFHAND_HINT] : null;
			if (hint != null) {
				// A keycap, so a "1" reads as a key and never as part of a count or a percentage.
				graphics.fill(slot.x, slot.y, slot.x + minecraft.font.width(hint) + 1, slot.y + 9, ZTheme.alpha(ZTheme.VOID, 176));
				graphics.drawString(minecraft.font, hint, slot.x + 1, slot.y + 1, ZTheme.LILAC_PALE, false);
				hinted = true;
			}
		}
		if (config.durabilityReadout) {
			// A keycap takes the top-left corner; a damaged tool has no count, so the bottom right is free.
			durability(graphics, minecraft.font, slot.getItem(), slot.x, hinted ? slot.y + 9 : slot.y);
		}
		if (config.swapWarning && slot == hovered && owner.getMenu().getCarried().isEmpty()
			&& TotemGuard.swapWouldEmptyOffhand(player, slot)) {
			// Violet when Totem protection will decline the swap, crimson when it would happen.
			int color = config.totemGuard ? ZTheme.VIOLET_BRIGHT : ZTheme.CRIMSON_BRIGHT;
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ZTheme.alpha(color, config.totemGuard ? 44 : 72));
			graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, ZTheme.alpha(color, 230));
		}
	}

	/** Inventory totals: how many of this item the player carries, unless this stack is all of it. */
	public static @Nullable Component inventoryTotal(final ItemStack stack) {
		Player player = Minecraft.getInstance().player;
		if (player == null || stack.isEmpty() || !ConfigStore.get().itemTotals || !enabled()) {
			return null;
		}
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int index = 0; index < inventory.getContainerSize(); index++) {
			ItemStack held = inventory.getItem(index);
			if (held.is(stack.getItem())) {
				total += held.getCount();
			}
		}
		return total == stack.getCount() ? null
			: Component.translatable("screen.kohs_inventory_tweaks.inventory_total", total).withColor(ZTheme.LILAC & 0xFFFFFF);
	}

	private static void flash(final GuiGraphics graphics, final Slot slot) {
		ItemStack stack = slot.getItem();
		Seen seen = SEEN.get(slot);
		if (seen == null || seen.item() != stack.getItem() || seen.count() != stack.getCount()) {
			seen = new Seen(stack.getItem(), stack.getCount(), seen == null ? 0L : System.nanoTime());
			SEEN.put(slot, seen);
		}
		if (seen.changedAt() == 0L || ZMotion.reduced()) {
			return;
		}
		float progress = ZMotion.progress(seen.changedAt(), FLASH_NANOS);
		if (progress >= 1.0F) {
			return;
		}
		float strength = 1.0F - ZMotion.easeOutCubic(progress);
		graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ZTheme.fade(ZTheme.alpha(ZTheme.LILAC_PALE, 120), strength));
		ZDraw.brackets(graphics, slot.x - 1, slot.y - 1, 18, 18, 5, ZTheme.fade(ZTheme.VIOLET_BRIGHT, strength));
	}

	private static void durability(final GuiGraphics graphics, final Font font, final ItemStack stack, final int x, final int y) {
		if (!stack.isDamageableItem() || !stack.isDamaged()) {
			return;
		}
		int maximum = Math.max(1, stack.getMaxDamage());
		int left = Math.max(0, maximum - stack.getDamageValue());
		int percent = Math.min(99, (int) Math.ceil(left * 100.0 / maximum));
		int color = percent <= 20 ? ZTheme.CRIMSON_BRIGHT : percent <= 50 ? ZTheme.LILAC : ZTheme.TEXT;
		String text = Integer.toString(percent);
		graphics.drawString(font, text, x + 17 - font.width(text), y, color, true);
	}

	private static @Nullable String hint(final KeyMapping mapping) {
		return mapping.isUnbound() ? null : shortLabel(mapping.getTranslatedKeyMessage().getString());
	}

	/** At most three characters: "R" stays "R", "Left Shift" becomes "LS", "Button 4" becomes "B4". */
	public static String shortLabel(final String name) {
		String trimmed = name.strip();
		if (trimmed.length() <= 2) {
			return trimmed.toUpperCase(Locale.ROOT);
		}
		StringBuilder label = new StringBuilder();
		for (String word : trimmed.split("[\\s._-]+")) {
			if (!word.isEmpty()) {
				label.append(Character.isDigit(word.charAt(0)) ? word : word.substring(0, 1).toUpperCase(Locale.ROOT));
			}
		}
		return label.length() <= 3 ? label.toString() : label.substring(0, 3);
	}

	private static boolean enabled() {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}
}
