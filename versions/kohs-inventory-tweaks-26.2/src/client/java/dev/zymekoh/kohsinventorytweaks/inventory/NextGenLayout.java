package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.NextGenStyle;
import java.util.EnumMap;
import java.util.Map;

/**
 * Where one Next Gen inventory puts everything, in the Vanilla panel's own coordinates: (0, 0) is
 * the panel's corner and the panel stays centered, so every layout stays around the middle of the
 * screen and follows the GUI Scaler like the inventory it replaces.
 */
final class NextGenLayout {
	static final int SLOTS = 46;
	static final int NO_TITLE = Integer.MIN_VALUE;
	static final int NO_ARROW = Integer.MIN_VALUE;
	static final int MODEL_WIDTH = 49;
	static final int MODEL_HEIGHT = 70;
	private static final int STEP = 18;
	private static final int PANEL_CENTER_X = 88;
	private static final int PANEL_CENTER_Y = 83;
	private static final int MODEL_SIZE = 30;
	private static final Map<NextGenStyle, NextGenLayout> ALL = new EnumMap<>(NextGenStyle.class);

	final int[] xs = new int[SLOTS];
	final int[] ys = new int[SLOTS];
	int titleX = NO_TITLE;
	int titleY;
	int modelX0;
	int modelY0;
	int modelX1;
	int modelY1;
	int modelSize;
	boolean modelBox;
	boolean recipeBook;
	int bookX;
	int bookY;
	int minX;
	int minY;
	int maxX;
	int maxY;
	int plateX;
	int plateY;
	int plateWidth;
	int plateHeight;
	int arrowX = NO_ARROW;
	int arrowY;
	boolean arrowLeft;

	static {
		ALL.put(NextGenStyle.BROKEN, broken());
		ALL.put(NextGenStyle.VISOR, visor());
		ALL.put(NextGenStyle.VERTICAL, vertical());
		ALL.put(NextGenStyle.COMPACT, compact());
		ALL.put(NextGenStyle.RING, ring());
		ALL.put(NextGenStyle.HONEYCOMB, honeycomb());
		ALL.put(NextGenStyle.FAN, fan());
		ALL.put(NextGenStyle.HOTBAR_TOP, hotbarTop());
		ALL.put(NextGenStyle.MIRROR, mirror());
		ALL.put(NextGenStyle.FLIPPED, flipped());
		ALL.put(NextGenStyle.WIDE, wide());
		ALL.put(NextGenStyle.SIDE_HOTBAR, sideHotbar());
		ALL.put(NextGenStyle.CUSTOM, floatingVanilla());
	}

	private NextGenLayout() {
	}

	static NextGenLayout of(final NextGenStyle style) {
		return ALL.get(style);
	}

	/** The layout the player arranged, or Vanilla's arrangement until they do. */
	static NextGenLayout custom(final InventoryTweaksConfig config) {
		NextGenLayout layout = floatingVanilla();
		if (config.customSlotX.length == SLOTS && config.customSlotY.length == SLOTS) {
			System.arraycopy(config.customSlotX, 0, layout.xs, 0, SLOTS);
			System.arraycopy(config.customSlotY, 0, layout.ys, 0, SLOTS);
		}
		layout.model(config.customModelX, config.customModelY, config.customModelX + MODEL_WIDTH, config.customModelY + MODEL_HEIGHT, MODEL_SIZE);
		int minX = layout.modelX0;
		int minY = layout.modelY0;
		int maxX = layout.modelX1;
		int maxY = layout.modelY1;
		for (int index = 0; index < SLOTS; index++) {
			minX = Math.min(minX, layout.xs[index] - 1);
			minY = Math.min(minY, layout.ys[index] - 1);
			maxX = Math.max(maxX, layout.xs[index] + 17);
			maxY = Math.max(maxY, layout.ys[index] + 17);
		}
		layout.bounds(minX, minY, maxX, maxY);
		return layout;
	}

	int width() {
		return this.maxX - this.minX;
	}

	int height() {
		return this.maxY - this.minY;
	}

	private void model(final int x0, final int y0, final int x1, final int y1, final int size) {
		this.modelX0 = x0;
		this.modelY0 = y0;
		this.modelX1 = x1;
		this.modelY1 = y1;
		this.modelSize = size;
	}

	private void bounds(final int x0, final int y0, final int x1, final int y1) {
		this.minX = x0;
		this.minY = y0;
		this.maxX = x1;
		this.maxY = y1;
	}

	private void slot(final int index, final int x, final int y) {
		this.xs[index] = x;
		this.ys[index] = y;
	}

	private void plate(final int x, final int y, final int width, final int height) {
		this.plateX = x;
		this.plateY = y;
		this.plateWidth = width;
		this.plateHeight = height;
		this.bounds(x, y, x + width, y + height);
	}

	private void arrow(final int x, final int y, final boolean left) {
		this.arrowX = x;
		this.arrowY = y;
		this.arrowLeft = left;
	}

	private void title(final int x, final int y) {
		this.titleX = x;
		this.titleY = y;
	}

	private void book(final int x, final int y) {
		this.recipeBook = true;
		this.bookX = x;
		this.bookY = y;
	}

	/** Minecraft's own arrangement on a panel of Minecraft's own shape. */
	private static NextGenLayout vanilla() {
		NextGenLayout layout = new NextGenLayout();
		layout.slot(0, 154, 28);
		for (int index = 0; index < 4; index++) {
			layout.slot(1 + index, 98 + index % 2 * STEP, 18 + index / 2 * STEP);
			layout.slot(5 + index, 8, 8 + index * STEP);
		}
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, 8 + index % 9 * STEP, 84 + index / 9 * STEP);
		}
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, 8 + index * STEP, 142);
		}
		layout.slot(45, 77, 62);
		layout.title(97, 6);
		layout.model(26, 8, 75, 78, MODEL_SIZE);
		layout.modelBox = true;
		layout.book(104, 61);
		layout.plate(0, 0, 176, 166);
		layout.arrow(135, 30, false);
		return layout;
	}

	/** The same arrangement with nothing behind it, as the start of the custom layout. */
	private static NextGenLayout floatingVanilla() {
		NextGenLayout layout = vanilla();
		layout.plateWidth = 0;
		layout.modelBox = false;
		layout.arrowX = NO_ARROW;
		layout.titleX = NO_TITLE;
		layout.recipeBook = false;
		return layout;
	}

	private static NextGenLayout broken() {
		NextGenLayout layout = vanilla();
		for (int index = 0; index < SLOTS; index++) {
			layout.xs[index] = NextGenInventory.brokenX(layout.xs[index]);
		}
		layout.titleX = NextGenInventory.brokenX(layout.titleX);
		layout.model(26 - NextGenInventory.BROKEN_SHIFT, 8, 75 - NextGenInventory.BROKEN_SHIFT, 78, MODEL_SIZE);
		layout.recipeBook = false;
		layout.plateWidth = 0;
		layout.modelBox = false;
		layout.arrowX = NO_ARROW;
		layout.bounds(-NextGenInventory.BROKEN_SHIFT, 0, 176 + NextGenInventory.BROKEN_SHIFT, 166);
		return layout;
	}

	/** Slots floating at the middle of the screen: armor and crafting at the sides, the hotbar as a smile. */
	private static NextGenLayout visor() {
		NextGenLayout layout = floatingVanilla();
		int eyeTop = 18;
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, 4 + index % 2 * STEP, eyeTop + index / 2 * STEP);
			layout.slot(1 + index, 138 + index % 2 * STEP, eyeTop + index / 2 * STEP);
		}
		layout.slot(45, 42, eyeTop + 9);
		layout.slot(0, 118, eyeTop + 9);
		for (int index = 0; index < 27; index++) {
			int distance = Math.abs(index % 9 - 4);
			layout.slot(9 + index, 8 + index % 9 * STEP, 80 + index / 9 * STEP + distance * distance / 2);
		}
		for (int index = 0; index < 9; index++) {
			int distance = Math.abs(index - 4);
			layout.slot(36 + index, 8 + index * STEP, 154 - distance * distance / 2);
		}
		layout.model(63, -16, 113, 54, MODEL_SIZE);
		layout.bounds(0, -18, 176, 174);
		return layout;
	}

	/** Tall columns: the inventory turned on its side, with the player and crafting beside it. */
	private static NextGenLayout vertical() {
		NextGenLayout layout = new NextGenLayout();
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, 8, 2 + index * STEP);
			layout.slot(1 + index, 8 + index % 2 * STEP, 108 + index / 2 * STEP);
		}
		layout.slot(0, 68, 117);
		layout.slot(45, 8, 76);
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, 96 + index / 9 * STEP, 2 + index % 9 * STEP);
		}
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, 154, 2 + index * STEP);
		}
		layout.title(8, 98);
		layout.model(26, 2, 75, 72, MODEL_SIZE);
		layout.modelBox = true;
		layout.book(28, 76);
		layout.plate(0, -6, 176, 176);
		layout.arrow(47, 120, false);
		return layout;
	}

	/** The smallest panel: no player model, armor and crafting along the top. */
	private static NextGenLayout compact() {
		NextGenLayout layout = new NextGenLayout();
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, 8 + index % 2 * STEP, 26 + index / 2 * STEP);
			layout.slot(1 + index, 100 + index % 2 * STEP, 26 + index / 2 * STEP);
		}
		layout.slot(0, 152, 35);
		layout.slot(45, 50, 35);
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, 8 + index % 9 * STEP, 66 + index / 9 * STEP);
		}
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, 8 + index * STEP, 124);
		}
		layout.book(72, 35);
		layout.plate(0, 18, 176, 130);
		layout.arrow(137, 38, false);
		return layout;
	}

	/** Two rings around the player: the inventory outside; hotbar, armor and crafting inside. */
	private static NextGenLayout ring() {
		NextGenLayout layout = new NextGenLayout();
		int outer = NextGenInventory.RING_OUTER;
		int inner = NextGenInventory.RING_INNER;
		for (int index = 0; index < 27; index++) {
			layout.ringSlot(9 + index, outer, index, 27);
		}
		layout.ringSlot(45, inner, 0, 19);
		for (int index = 0; index < 4; index++) {
			layout.ringSlot(1 + index, inner, 1 + index, 19);
			layout.ringSlot(8 - index, inner, 15 + index, 19);
		}
		layout.ringSlot(0, inner, 5, 19);
		for (int index = 0; index < 9; index++) {
			layout.ringSlot(36 + index, inner, 14 - index, 19);
		}
		int reach = NextGenInventory.RING_RADIUS;
		layout.model(PANEL_CENTER_X - 25, PANEL_CENTER_Y - 35, PANEL_CENTER_X + 25, PANEL_CENTER_Y + 35, MODEL_SIZE);
		layout.bounds(PANEL_CENTER_X - reach, PANEL_CENTER_Y - reach, PANEL_CENTER_X + reach, PANEL_CENTER_Y + reach);
		return layout;
	}

	private void ringSlot(final int index, final int radius, final int position, final int positions) {
		double angle = -Math.PI / 2.0 + position * Math.PI * 2.0 / positions;
		this.slot(
			index,
			(int) Math.round(PANEL_CENTER_X + Math.cos(angle) * radius) - 8,
			(int) Math.round(PANEL_CENTER_Y + Math.sin(angle) * radius) - 8
		);
	}

	/** The Vanilla top half over four staggered rows of hexagons. */
	private static NextGenLayout honeycomb() {
		NextGenLayout layout = vanilla();
		for (int index = 0; index < 36; index++) {
			int row = index / 9;
			layout.slot(9 + index, (row % 2 == 0 ? 4 : 13) + index % 9 * STEP, 88 + row * NextGenInventory.HEX_ROW_STEP);
		}
		return layout;
	}

	/** Rows opening downward from the player like a fan, with no panel behind them. */
	private static NextGenLayout fan() {
		NextGenLayout layout = new NextGenLayout();
		int[] radii = {60, 84, 108, 132};
		for (int index = 0; index < 36; index++) {
			int radius = radii[index / 9];
			double angle = (index % 9 - 4) * 20.0 / radius;
			layout.slot(
				9 + index,
				(int) Math.round(PANEL_CENTER_X + Math.sin(angle) * radius) - 8,
				(int) Math.round(10.0 + Math.cos(angle) * radius) - 8
			);
		}
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, -22, 6 + index * STEP);
			layout.slot(1 + index, 170 + index % 2 * STEP, 6 + index / 2 * STEP);
		}
		layout.slot(45, -22, 82);
		layout.slot(0, 179, 48);
		layout.title(168, -6);
		layout.model(63, -12, 63 + MODEL_WIDTH, -12 + MODEL_HEIGHT, MODEL_SIZE);
		layout.bounds(-26, -14, 210, 154);
		return layout;
	}

	/** Vanilla with the hotbar above the inventory instead of below it. */
	private static NextGenLayout hotbarTop() {
		NextGenLayout layout = vanilla();
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, 8 + index * STEP, 84);
		}
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, 8 + index % 9 * STEP, 106 + index / 9 * STEP);
		}
		return layout;
	}

	/** Vanilla reflected: crafting on the left, the player and armor on the right. */
	private static NextGenLayout mirror() {
		NextGenLayout layout = vanilla();
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, 152, 8 + index * STEP);
			layout.slot(1 + index, 62 - index % 2 * STEP, 18 + index / 2 * STEP);
		}
		layout.slot(0, 6, 28);
		layout.slot(45, 83, 62);
		layout.title(44, 6);
		layout.model(101, 8, 150, 78, MODEL_SIZE);
		layout.book(52, 61);
		layout.arrow(25, 30, true);
		return layout;
	}

	/** The inventory on top and the player and crafting below it. */
	private static NextGenLayout flipped() {
		NextGenLayout layout = vanilla();
		int down = 84;
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, 8 + index * STEP, 8);
		}
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, 8 + index % 9 * STEP, 30 + index / 9 * STEP);
		}
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, 8, 8 + down + index * STEP);
			layout.slot(1 + index, 98 + index % 2 * STEP, 18 + down + index / 2 * STEP);
		}
		layout.slot(0, 154, 28 + down);
		layout.slot(45, 77, 62 + down);
		layout.title(97, 6 + down);
		layout.model(26, 8 + down, 75, 78 + down, MODEL_SIZE);
		layout.book(104, 61 + down);
		layout.arrow(135, 30 + down, false);
		return layout;
	}

	/** A wider panel: the player beside the inventory rather than above it. */
	private static NextGenLayout wide() {
		NextGenLayout layout = new NextGenLayout();
		int left = -47;
		int top = 16;
		int grid = left + 104;
		for (int index = 0; index < 4; index++) {
			layout.slot(5 + index, left + 8, top + 8 + index * STEP);
			layout.slot(1 + index, grid + index % 2 * STEP, top + 8 + index / 2 * STEP);
		}
		layout.slot(0, grid + 56, top + 17);
		layout.slot(45, left + 8, top + 84);
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, grid + index % 9 * STEP, top + 52 + index / 9 * STEP);
		}
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, grid + index * STEP, top + 110);
		}
		layout.title(left + 182, top + 21);
		layout.model(left + 26, top + 8, left + 75, top + 78, MODEL_SIZE);
		layout.modelBox = true;
		layout.plate(left, top, 270, 134);
		layout.arrow(grid + 38, top + 20, false);
		return layout;
	}

	/** Vanilla with the hotbar as a column down its right side. */
	private static NextGenLayout sideHotbar() {
		NextGenLayout layout = vanilla();
		for (int index = 0; index < 27; index++) {
			layout.slot(9 + index, 8 + index % 9 * STEP, 93 + index / 9 * STEP);
		}
		for (int index = 0; index < 9; index++) {
			layout.slot(36 + index, 178, 4 + index * STEP);
		}
		layout.plate(0, 0, 198, 166);
		return layout;
	}
}
