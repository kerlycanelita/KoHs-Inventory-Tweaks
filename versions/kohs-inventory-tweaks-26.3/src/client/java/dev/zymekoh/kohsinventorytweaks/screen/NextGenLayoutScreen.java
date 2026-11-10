package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Where the player arranges the custom inventory with the mouse: the hotbar as one piece, the other
 * slots one by one, the player model, and the status effects, shown here even when none is active
 * so they can be placed too. Every drop is saved at once.
 */
public final class NextGenLayoutScreen extends Screen {
	private static final int SLOTS = 46;
	/** Width kept free beside the mascot in the bottom-right corner. */
	private static final int MASCOT_ROOM = 48;
	private static final int MODEL = SLOTS;
	private static final int EFFECTS = SLOTS + 1;
	private static final int PIECES = SLOTS + 2;
	private static final int SLOT_SIZE = 16;
	private static final int EFFECT_WIDTH = 100;
	private static final int EFFECT_STEP = 33;
	private static final int EFFECT_HEIGHT = EFFECT_STEP + 32;
	private static final int SNAP = 6;
	private static final int TOP_STRIP = 26;
	private static final int EDGE = 2;
	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_HEIGHT = 166;
	private static final Identifier EFFECT_PANEL = Identifier.withDefaultNamespace("container/inventory/effect_background");
	private static final Identifier SPEED = Identifier.withDefaultNamespace("mob_effect/speed");
	private static final Identifier STRENGTH = Identifier.withDefaultNamespace("mob_effect/strength");

	private record Drag(int[] pieces, int[] startX, int[] startY, double grabX, double grabY) {
	}

	private final Screen parent;
	private final int[] xs = new int[PIECES];
	private final int[] ys = new int[PIECES];
	private int left;
	private int top;
	private boolean snap = true;
	private @Nullable Drag drag;
	private boolean invalid;
	private GlassButton snapButton;

	public NextGenLayoutScreen(final Screen parent) {
		super(text("title"));
		this.parent = parent;
		InventoryTweaksConfig config = ConfigStore.get();
		int[][] slots = NextGenInventory.customSlots(config);
		System.arraycopy(slots[0], 0, this.xs, 0, SLOTS);
		System.arraycopy(slots[1], 0, this.ys, 0, SLOTS);
		this.xs[MODEL] = config.customModelX;
		this.ys[MODEL] = config.customModelY;
		this.xs[EFFECTS] = config.customEffectsX;
		this.ys[EFFECTS] = config.customEffectsY;
	}

	@Override
	protected void init() {
		this.left = (this.width - PANEL_WIDTH) / 2;
		this.top = (this.height - PANEL_HEIGHT) / 2;
		int gap = 4;
		int widths = 96 + 70 + 70 + gap * 2;
		int x = (this.width - widths) / 2;
		this.snapButton = this.addRenderableWidget(new GlassButton(x, 3, 96, 18, this.snapLabel(), button -> {
			this.snap = !this.snap;
			button.setMessage(this.snapLabel());
		}, GlassButton.Variant.TAB));
		this.addRenderableWidget(new GlassButton(x + 96 + gap, 3, 70, 18, text("reset"), button -> this.reset(), GlassButton.Variant.DANGER));
		this.addRenderableWidget(new GlassButton(x + 96 + 70 + gap * 2, 3, 70, 18, Component.translatable("gui.done"), button -> this.onClose(), GlassButton.Variant.PRIMARY));
	}

	private Component snapLabel() {
		return text(this.snap ? "snap.on" : "snap.off");
	}

	private void reset() {
		int[][] slots = NextGenInventory.vanillaSlots();
		System.arraycopy(slots[0], 0, this.xs, 0, SLOTS);
		System.arraycopy(slots[1], 0, this.ys, 0, SLOTS);
		this.xs[MODEL] = 26;
		this.ys[MODEL] = 8;
		this.xs[EFFECTS] = InventoryTweaksConfig.DEFAULT_EFFECTS_X;
		this.ys[EFFECTS] = 0;
		this.save();
	}

	private void save() {
		InventoryTweaksConfig working = ConfigStore.get().copy();
		working.customSlotX = java.util.Arrays.copyOf(this.xs, SLOTS);
		working.customSlotY = java.util.Arrays.copyOf(this.ys, SLOTS);
		working.customModelX = this.xs[MODEL];
		working.customModelY = this.ys[MODEL];
		working.customEffectsX = this.xs[EFFECTS];
		working.customEffectsY = this.ys[EFFECTS];
		ConfigStore.replaceAndSave(working);
	}

	private static int widthOf(final int piece) {
		return piece == MODEL ? NextGenInventory.MODEL_WIDTH : piece == EFFECTS ? EFFECT_WIDTH : SLOT_SIZE;
	}

	private static int heightOf(final int piece) {
		return piece == MODEL ? NextGenInventory.MODEL_HEIGHT : piece == EFFECTS ? EFFECT_HEIGHT : SLOT_SIZE;
	}

	private int pieceAt(final double mouseX, final double mouseY) {
		double x = mouseX - this.left;
		double y = mouseY - this.top;
		for (int piece = 0; piece < SLOTS; piece++) {
			if (x >= this.xs[piece] - 1 && x < this.xs[piece] + 17 && y >= this.ys[piece] - 1 && y < this.ys[piece] + 17) {
				return piece;
			}
		}
		for (int piece = MODEL; piece <= EFFECTS; piece++) {
			if (x >= this.xs[piece] && x < this.xs[piece] + widthOf(piece) && y >= this.ys[piece] && y < this.ys[piece] + heightOf(piece)) {
				return piece;
			}
		}
		return -1;
	}

	/** The pieces that move with this one: its whole group, or just itself. */
	private static Component text(final String suffix) {
		return Component.translatable("screen.kohs_inventory_tweaks.next_gen.custom." + suffix);
	}

	private static int[] group(final int piece, final boolean shift) {
		if (piece >= SLOTS) {
			return new int[] {piece};
		}
		boolean hotbar = piece >= 36 && piece <= 44;
		// The hotbar goes as one piece and the rest one by one; Shift swaps the two.
		if (hotbar == shift) {
			return new int[] {piece};
		}
		int from;
		int to;
		if (hotbar) {
			from = 36;
			to = 45;
		} else if (piece >= 9 && piece <= 35) {
			from = 9;
			to = 36;
		} else if (piece >= 5 && piece <= 8 || piece == 45) {
			int[] armor = {5, 6, 7, 8, 45};
			return armor;
		} else {
			from = 0;
			to = 5;
		}
		int[] pieces = new int[to - from];
		for (int index = 0; index < pieces.length; index++) {
			pieces[index] = from + index;
		}
		return pieces;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int piece = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT ? this.pieceAt(event.x(), event.y()) : -1;
		if (piece < 0) {
			return false;
		}
		int[] pieces = group(piece, event.hasShiftDown());
		int[] startX = new int[pieces.length];
		int[] startY = new int[pieces.length];
		for (int index = 0; index < pieces.length; index++) {
			startX[index] = this.xs[pieces[index]];
			startY[index] = this.ys[pieces[index]];
		}
		this.drag = new Drag(pieces, startX, startY, event.x(), event.y());
		this.invalid = false;
		return true;
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		if (this.drag == null) {
			return super.mouseDragged(event, dx, dy);
		}
		int moveX = this.snapped(event.x() - this.drag.grabX());
		int moveY = this.snapped(event.y() - this.drag.grabY());
		for (int index = 0; index < this.drag.pieces().length; index++) {
			int piece = this.drag.pieces()[index];
			moveX = Mth.clamp(moveX, EDGE - this.left - this.drag.startX()[index], this.width - EDGE - widthOf(piece) - this.left - this.drag.startX()[index]);
			moveY = Mth.clamp(moveY, TOP_STRIP - this.top - this.drag.startY()[index], this.height - EDGE - heightOf(piece) - this.top - this.drag.startY()[index]);
		}
		for (int index = 0; index < this.drag.pieces().length; index++) {
			int piece = this.drag.pieces()[index];
			this.xs[piece] = this.drag.startX()[index] + moveX;
			this.ys[piece] = this.drag.startY()[index] + moveY;
		}
		this.invalid = this.overlaps(this.drag.pieces());
		return true;
	}

	private int snapped(final double movement) {
		return this.snap ? Math.round((float) movement / SNAP) * SNAP : (int) Math.round(movement);
	}

	/** Two slots on one spot would leave one of them unreachable, so a drop like that is refused. */
	private boolean overlaps(final int[] moved) {
		for (int piece : moved) {
			if (piece >= SLOTS) {
				continue;
			}
			for (int other = 0; other < SLOTS; other++) {
				if (!contains(moved, other) && Math.abs(this.xs[piece] - this.xs[other]) < 18 && Math.abs(this.ys[piece] - this.ys[other]) < 18) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean contains(final int[] pieces, final int piece) {
		for (int candidate : pieces) {
			if (candidate == piece) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.drag == null) {
			return super.mouseReleased(event);
		}
		if (this.invalid) {
			for (int index = 0; index < this.drag.pieces().length; index++) {
				this.xs[this.drag.pieces()[index]] = this.drag.startX()[index];
				this.ys[this.drag.pieces()[index]] = this.drag.startY()[index];
			}
		} else {
			this.save();
		}
		this.drag = null;
		this.invalid = false;
		return true;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		graphics.fillGradient(0, 0, this.width, this.height, 0xA0101010, 0xC0101010);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		InventoryTweaksConfig config = ConfigStore.get();
		LocalPlayer player = this.minecraft.player;
		int hovered = this.drag == null ? this.pieceAt(mouseX, mouseY) : -1;
		this.drawEffects(graphics, hovered == EFFECTS || this.isDragging(EFFECTS));
		this.drawModelBox(graphics, player, mouseX, mouseY, hovered == MODEL || this.isDragging(MODEL));
		int[] hoveredGroup = hovered >= 0 && hovered < SLOTS ? group(hovered, false) : new int[0];
		for (int piece = 0; piece < SLOTS; piece++) {
			int x = this.left + this.xs[piece];
			int y = this.top + this.ys[piece];
			NextGenInventory.drawSlotTile(graphics, x, y, config);
			if (player != null) {
				this.drawSlotContents(graphics, player.inventoryMenu.slots, piece, x, y);
			}
			if (this.isDragging(piece) && this.invalid) {
				graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xA0E03A3A);
			} else if (this.isDragging(piece) || contains(hoveredGroup, piece)) {
				graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x60FFFFFF);
			}
		}
		// Centred, and as narrow on the left as the mascot needs on the right, so no language runs under it.
		List<FormattedCharSequence> hint = this.font.split(text("hint"), Math.max(40, this.width - MASCOT_ROOM * 2));
		int hintY = this.height - 3 - hint.size() * 10;
		for (FormattedCharSequence line : hint) {
			graphics.centeredText(this.font, line, this.width / 2, hintY, ZTheme.TEXT);
			hintY += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private boolean isDragging(final int piece) {
		return this.drag != null && contains(this.drag.pieces(), piece);
	}

	private void drawSlotContents(final GuiGraphicsExtractor graphics, final List<Slot> slots, final int piece, final int x, final int y) {
		if (piece >= slots.size()) {
			return;
		}
		Slot slot = slots.get(piece);
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) {
			Identifier icon = slot.getNoItemIcon();
			if (icon != null) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, 16, 16);
			}
			return;
		}
		graphics.item(stack, x, y, x + y * PANEL_WIDTH);
		graphics.itemDecorations(this.font, stack, x, y);
	}

	private void drawModelBox(final GuiGraphicsExtractor graphics, final @Nullable LocalPlayer player, final int mouseX, final int mouseY, final boolean lit) {
		int x = this.left + this.xs[MODEL];
		int y = this.top + this.ys[MODEL];
		if (player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(
				graphics, x, y, x + NextGenInventory.MODEL_WIDTH, y + NextGenInventory.MODEL_HEIGHT, 30, 0.0625F, mouseX, mouseY, player
			);
		}
		ZDraw.brackets(graphics, x, y, NextGenInventory.MODEL_WIDTH, NextGenInventory.MODEL_HEIGHT, 6, ZTheme.alpha(ZTheme.LILAC, lit ? 255 : 140));
	}

	/** Two made-up effects, so the place for them can be chosen when none is active. */
	private void drawEffects(final GuiGraphicsExtractor graphics, final boolean lit) {
		int x = this.left + this.xs[EFFECTS];
		int y = this.top + this.ys[EFFECTS];
		this.drawEffect(graphics, x, y, SPEED, Component.translatable("effect.minecraft.speed").append(" II"), "1:30");
		this.drawEffect(graphics, x, y + EFFECT_STEP, STRENGTH, Component.translatable("effect.minecraft.strength"), "0:45");
		ZDraw.brackets(graphics, x, y, EFFECT_WIDTH, EFFECT_HEIGHT, 6, ZTheme.alpha(ZTheme.LILAC, lit ? 255 : 140));
	}

	private void drawEffect(final GuiGraphicsExtractor graphics, final int x, final int y, final Identifier icon, final Component name, final String time) {
		int room = Math.max(32, Math.min(EFFECT_WIDTH, this.width - EDGE - x));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_PANEL, x, y, room, 32);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + 7, y + 7, 18, 18);
		if (room == EFFECT_WIDTH) {
			graphics.text(this.font, name, x + 32, y + 7, 0xFFFFFFFF, true);
			graphics.text(this.font, time, x + 32, y + 16, 0xFF7F7F7F, true);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
