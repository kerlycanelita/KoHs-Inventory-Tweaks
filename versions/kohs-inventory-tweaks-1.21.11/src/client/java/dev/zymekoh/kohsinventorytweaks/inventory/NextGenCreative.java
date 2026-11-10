package dev.zymekoh.kohsinventorytweaks.inventory;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.NextGenStyle;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.SlotAccessor;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The Creative inventory's version of each Next Gen layout.
 *
 * <p>The Creative screen has two sets of slots: forty-five items and the hotbar on the item tabs,
 * and armor, inventory and hotbar on the inventory tab, which it rebuilds on every tab change. The
 * tabs along the top and bottom and the scrollbar stay in reach, so a layout rearranges what lies
 * between them. As in survival, a slot keeps its index, so only the screen changes.</p>
 */
public final class NextGenCreative {
	private static final int WIDTH = 195;
	private static final int HEIGHT = 136;
	private static final int ITEM_SLOTS = 45;
	private static final int CUT_X = 98;
	private static final int STEP = 18;
	private static final int SHIFT = NextGenInventory.BROKEN_SHIFT;
	/** The strip under the hotbar is plain on every Creative background, which the torn edges are cut from. */
	private static final int PLAIN_U = 30;
	private static final int PLAIN_V = 130;
	private static final int PLAIN_HEIGHT = 3;
	/** Survival panels are centered on a different size, so a custom position is moved by the difference. */
	private static final int CUSTOM_DX = (WIDTH - 176) / 2;
	private static final int CUSTOM_DY = (HEIGHT - 166) / 2;
	private static final Map<NextGenStyle, Layout> ALL = new EnumMap<>(NextGenStyle.class);

	/** Everything a layout places, in the Creative panel's own coordinates. */
	private static final class Layout {
		private final int[] gridX = new int[ITEM_SLOTS];
		private final int[] gridY = new int[ITEM_SLOTS];
		private final int[] hotbarX = new int[9];
		private final int[] hotbarY = new int[9];
		private final int[] mainX = new int[27];
		private final int[] mainY = new int[27];
		private final int[] bagHotbarX = new int[9];
		private final int[] bagHotbarY = new int[9];
		private final int[] armorX = {54, 54, 108, 108};
		private final int[] armorY = {6, 33, 6, 33};
		private int offhandX = 35;
		private int offhandY = 20;
		private int destroyX = 173;
		private int destroyY = 112;
		private int modelX0 = 73;
		private int modelY0 = 6;
		private int modelX1 = 105;
		private int modelY1 = 49;
		private int modelSize = 20;
		private int scrollX = 175;
		private int minX;
		private int minY;
		private int maxX = WIDTH;
		private int maxY = HEIGHT;
		private int plateWidth = WIDTH;
		private int plateHeight = HEIGHT;
		private int sideX;
		private int sideY;
		private int sideWidth;
		private int sideHeight;
		private boolean broken;
		private boolean disc;
		private boolean hexagons;
		private boolean hologram;
		private boolean clearBackdrop;

		private Layout() {
			for (int index = 0; index < ITEM_SLOTS; index++) {
				this.gridX[index] = 9 + index % 9 * STEP;
				this.gridY[index] = 18 + index / 9 * STEP;
			}
			for (int index = 0; index < 27; index++) {
				this.mainX[index] = 9 + index % 9 * STEP;
				this.mainY[index] = 54 + index / 9 * STEP;
			}
			for (int index = 0; index < 9; index++) {
				this.hotbarX[index] = 9 + index * STEP;
				this.hotbarY[index] = 112;
				this.bagHotbarX[index] = this.hotbarX[index];
				this.bagHotbarY[index] = 112;
			}
		}

		private boolean floating() {
			return this.plateWidth == 0 && !this.disc && !this.broken;
		}

		private void bounds(final int x0, final int y0, final int x1, final int y1) {
			this.minX = x0;
			this.minY = y0;
			this.maxX = x1;
			this.maxY = y1;
		}

		private void side(final int x, final int y, final int width, final int height) {
			this.sideX = x;
			this.sideY = y;
			this.sideWidth = width;
			this.sideHeight = height;
		}

		private void hotbarEverywhere(final int index, final int x, final int y) {
			this.hotbarX[index] = x;
			this.hotbarY[index] = y;
			this.bagHotbarX[index] = x;
			this.bagHotbarY[index] = y;
		}

		private void gridRows(final int top) {
			for (int index = 0; index < ITEM_SLOTS; index++) {
				this.gridY[index] = top + index / 9 * STEP;
			}
		}

		private void mainRows(final int top) {
			for (int index = 0; index < 27; index++) {
				this.mainY[index] = top + index / 9 * STEP;
			}
		}

		private void moveAllX(final int dx) {
			for (int index = 0; index < ITEM_SLOTS; index++) {
				this.gridX[index] += dx;
			}
			for (int index = 0; index < 27; index++) {
				this.mainX[index] += dx;
			}
			for (int index = 0; index < 9; index++) {
				this.hotbarX[index] += dx;
				this.bagHotbarX[index] += dx;
			}
		}
	}

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
		Layout custom = new Layout();
		custom.plateWidth = 0;
		ALL.put(NextGenStyle.CUSTOM, custom);
	}

	private NextGenCreative() {
	}

	private static int splitX(final int x) {
		return x < CUT_X ? x - SHIFT : x + SHIFT;
	}

	private static Layout broken() {
		Layout layout = new Layout();
		for (int index = 0; index < ITEM_SLOTS; index++) {
			layout.gridX[index] = splitX(layout.gridX[index]);
		}
		for (int index = 0; index < 27; index++) {
			layout.mainX[index] = splitX(layout.mainX[index]);
		}
		for (int index = 0; index < 9; index++) {
			layout.hotbarX[index] = splitX(layout.hotbarX[index]);
			layout.bagHotbarX[index] = splitX(layout.bagHotbarX[index]);
		}
		for (int index = 0; index < 4; index++) {
			layout.armorX[index] = splitX(layout.armorX[index]);
		}
		layout.offhandX = splitX(layout.offhandX);
		layout.destroyX = splitX(layout.destroyX);
		layout.modelX0 -= SHIFT;
		layout.modelX1 -= SHIFT;
		layout.scrollX += SHIFT;
		layout.broken = true;
		layout.plateWidth = 0;
		layout.bounds(-SHIFT, 0, WIDTH + SHIFT, HEIGHT);
		return layout;
	}

	/** Only slots, on rows that curve gently away at their ends. */
	private static Layout visor() {
		Layout layout = new Layout();
		for (int index = 0; index < ITEM_SLOTS; index++) {
			layout.gridY[index] += curve(index % 9);
		}
		for (int index = 0; index < 27; index++) {
			layout.mainY[index] += curve(index % 9);
		}
		for (int index = 0; index < 9; index++) {
			layout.hotbarY[index] += curve(index);
			layout.bagHotbarY[index] += curve(index);
		}
		layout.plateWidth = 0;
		layout.hologram = true;
		layout.clearBackdrop = true;
		return layout;
	}

	private static int curve(final int column) {
		int distance = Math.abs(column - 4);
		return distance * distance / 4;
	}

	/** The hotbar as a column on the left, the items using the row it left free. */
	private static Layout vertical() {
		Layout layout = new Layout();
		for (int index = 0; index < 9; index++) {
			layout.hotbarEverywhere(index, -21, -13 + index * STEP);
		}
		layout.gridRows(27);
		layout.mainRows(63);
		layout.side(-26, -18, 26, 172);
		layout.bounds(-26, -18, WIDTH, 154);
		return layout;
	}

	/** Slots pushed together, and no player model on the inventory tab. */
	private static Layout compact() {
		Layout layout = new Layout();
		for (int index = 0; index < ITEM_SLOTS; index++) {
			layout.gridX[index] = 11 + index % 9 * 17;
			layout.gridY[index] = 24 + index / 9 * 17;
		}
		for (int index = 0; index < 27; index++) {
			layout.mainX[index] = 11 + index % 9 * 17;
			layout.mainY[index] = 48 + index / 9 * 17;
		}
		for (int index = 0; index < 9; index++) {
			layout.hotbarX[index] = 11 + index * 17;
			layout.hotbarY[index] = 110;
			layout.bagHotbarX[index] = 11 + index * 17;
			layout.bagHotbarY[index] = 101;
		}
		for (int index = 0; index < 4; index++) {
			layout.armorX[index] = 11 + index / 2 * STEP;
			layout.armorY[index] = 6 + index % 2 * STEP;
		}
		layout.offhandX = 55;
		layout.offhandY = 15;
		layout.modelSize = 0;
		return layout;
	}

	/** A round plate behind the slots, with the hotbar following its lower edge. */
	private static Layout ring() {
		Layout layout = new Layout();
		for (int index = 0; index < 9; index++) {
			int distance = Math.abs(index - 4);
			layout.hotbarY[index] = 112 - distance * distance / 4;
			layout.bagHotbarY[index] = layout.hotbarY[index];
		}
		layout.plateWidth = 0;
		layout.disc = true;
		int radius = NextGenInventory.RING_RADIUS;
		layout.bounds(WIDTH / 2 - radius, HEIGHT / 2 - radius, WIDTH / 2 + radius, HEIGHT / 2 + radius);
		return layout;
	}

	/** Staggered rows of hexagons. */
	private static Layout honeycomb() {
		Layout layout = new Layout();
		for (int index = 0; index < ITEM_SLOTS; index++) {
			int row = index / 9;
			layout.gridX[index] = (row % 2 == 0 ? 4 : 13) + index % 9 * STEP;
			layout.gridY[index] = 18 + row * NextGenInventory.HEX_ROW_STEP;
		}
		for (int index = 0; index < 27; index++) {
			int row = index / 9;
			layout.mainX[index] = (row % 2 == 0 ? 4 : 13) + index % 9 * STEP;
			layout.mainY[index] = 52 + row * NextGenInventory.HEX_ROW_STEP;
		}
		for (int index = 0; index < 9; index++) {
			layout.hotbarEverywhere(index, 13 + index * STEP, 108);
		}
		layout.hexagons = true;
		return layout;
	}

	/** Rows opening like a fan, floating with no panel. */
	private static Layout fan() {
		Layout layout = new Layout();
		for (int index = 0; index < ITEM_SLOTS; index++) {
			int[] place = fanPlace(index % 9, index / 9);
			layout.gridX[index] = place[0];
			layout.gridY[index] = place[1];
		}
		for (int index = 0; index < 27; index++) {
			int[] place = fanPlace(index % 9, index / 9 + 2);
			layout.mainX[index] = place[0];
			layout.mainY[index] = place[1];
		}
		for (int index = 0; index < 9; index++) {
			int[] place = fanPlace(index, 5);
			layout.hotbarEverywhere(index, place[0], place[1]);
		}
		layout.plateWidth = 0;
		return layout;
	}

	private static int[] fanPlace(final int column, final int row) {
		int radius = 131 + row * STEP;
		double angle = (column - 4) * (double) STEP / radius;
		return new int[] {
			(int) Math.round(88.0 + Math.sin(angle) * radius) - 8,
			(int) Math.round(-99.0 + Math.cos(angle) * radius) - 8
		};
	}

	/** The hotbar above the items instead of below them. */
	private static Layout hotbarTop() {
		Layout layout = new Layout();
		for (int index = 0; index < 9; index++) {
			layout.hotbarY[index] = 18;
			layout.bagHotbarY[index] = 54;
		}
		layout.gridRows(40);
		layout.mainRows(76);
		return layout;
	}

	/** The scrollbar on the left, and the armor and player reflected. */
	private static Layout mirror() {
		Layout layout = new Layout();
		layout.moveAllX(19);
		for (int index = 0; index < 4; index++) {
			layout.armorX[index] = WIDTH - layout.armorX[index] - 16;
		}
		layout.offhandX = WIDTH - layout.offhandX - 16;
		layout.modelX0 = 90;
		layout.modelX1 = 122;
		layout.destroyX = 8;
		layout.scrollX = 8;
		return layout;
	}

	/** Upside down: the hotbar first, and the rows of items reversed. */
	private static Layout flipped() {
		Layout layout = new Layout();
		for (int index = 0; index < ITEM_SLOTS; index++) {
			layout.gridY[index] = 40 + (4 - index / 9) * STEP;
		}
		for (int index = 0; index < 9; index++) {
			layout.hotbarY[index] = 18;
			layout.bagHotbarY[index] = 6;
		}
		layout.mainRows(28);
		for (int index = 0; index < 4; index++) {
			layout.armorY[index] += 78;
		}
		layout.offhandY += 78;
		layout.modelY0 += 78;
		layout.modelY1 += 78;
		layout.destroyY = 6;
		return layout;
	}

	/** A wider panel, with the hotbar as a block to the right of the items. */
	private static Layout wide() {
		Layout layout = new Layout();
		for (int index = 0; index < 9; index++) {
			layout.hotbarEverywhere(index, 199 + index % 3 * STEP, 41 + index / 3 * STEP);
		}
		layout.gridRows(27);
		layout.mainRows(63);
		layout.plateWidth = WIDTH + 66;
		layout.bounds(0, 0, WIDTH + 66, HEIGHT);
		return layout;
	}

	/** The hotbar as a column on the right. */
	private static Layout sideHotbar() {
		Layout layout = new Layout();
		for (int index = 0; index < 9; index++) {
			layout.hotbarEverywhere(index, 199, -13 + index * STEP);
		}
		layout.gridRows(27);
		layout.mainRows(63);
		layout.side(195, -18, 26, 172);
		layout.bounds(0, -18, WIDTH + 26, 154);
		return layout;
	}

	/** The custom layout's slots carried over from where the player placed them in survival. */
	private static Layout custom(final InventoryTweaksConfig config) {
		Layout layout = new Layout();
		layout.plateWidth = 0;
		if (config.customSlotX.length == NextGenLayout.SLOTS && config.customSlotY.length == NextGenLayout.SLOTS) {
			for (int index = 0; index < 27; index++) {
				layout.mainX[index] = config.customSlotX[9 + index] + CUSTOM_DX;
				layout.mainY[index] = config.customSlotY[9 + index] + CUSTOM_DY;
			}
			boolean clearOfItems = true;
			for (int index = 0; index < 9; index++) {
				int x = config.customSlotX[36 + index] + CUSTOM_DX;
				int y = config.customSlotY[36 + index] + CUSTOM_DY;
				layout.bagHotbarX[index] = x;
				layout.bagHotbarY[index] = y;
				clearOfItems &= x + 17 <= 8 || x - 1 >= 172 || y + 17 <= 17 || y - 1 >= 109;
			}
			if (clearOfItems) {
				System.arraycopy(layout.bagHotbarX, 0, layout.hotbarX, 0, 9);
				System.arraycopy(layout.bagHotbarY, 0, layout.hotbarY, 0, 9);
			}
			for (int index = 0; index < 4; index++) {
				layout.armorX[index] = config.customSlotX[5 + index] + CUSTOM_DX;
				layout.armorY[index] = config.customSlotY[5 + index] + CUSTOM_DY;
			}
			layout.offhandX = config.customSlotX[45] + CUSTOM_DX;
			layout.offhandY = config.customSlotY[45] + CUSTOM_DY;
			layout.modelX0 = config.customModelX + CUSTOM_DX;
			layout.modelY0 = config.customModelY + CUSTOM_DY;
			layout.modelX1 = layout.modelX0 + NextGenLayout.MODEL_WIDTH;
			layout.modelY1 = layout.modelY0 + NextGenLayout.MODEL_HEIGHT;
			layout.modelSize = 30;
		}
		return layout;
	}

	private static NextGenStyle styleFor(final CreativeModeInventoryScreen screen) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (config.nextGenStyle == NextGenStyle.OFF || !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return NextGenStyle.OFF;
		}
		Layout layout = ALL.get(config.nextGenStyle);
		return screen.width >= layout.maxX - layout.minX + 8 && screen.height >= layout.maxY - layout.minY + 4
			? config.nextGenStyle
			: NextGenStyle.OFF;
	}

	private static @org.jspecify.annotations.Nullable Layout layoutFor(final CreativeModeInventoryScreen screen) {
		NextGenStyle style = styleFor(screen);
		if (style == NextGenStyle.OFF) {
			return null;
		}
		return style == NextGenStyle.CUSTOM ? custom(ConfigStore.get()) : ALL.get(style);
	}

	public static boolean isFloating(final CreativeModeInventoryScreen screen) {
		Layout layout = layoutFor(screen);
		return layout != null && layout.floating();
	}

	/** Whether the world behind this screen stays undarkened. */
	public static boolean clearsBackdrop(final CreativeModeInventoryScreen screen) {
		Layout layout = layoutFor(screen);
		return layout != null && layout.clearBackdrop;
	}

	/** Moves the slots of the tab that has just been built. */
	public static void apply(final CreativeModeInventoryScreen screen) {
		Layout layout = layoutFor(screen);
		if (layout == null) {
			return;
		}
		List<Slot> slots = screen.getMenu().slots;
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		int left = access.kohsInventoryTweaks$getLeftPos();
		int top = access.kohsInventoryTweaks$getTopPos();
		if (screen.isInventoryOpen()) {
			if (slots.size() < NextGenLayout.SLOTS + 1) {
				return;
			}
			for (int index = 0; index < 4; index++) {
				place(screen, slots.get(5 + index), layout.armorX[index], layout.armorY[index], left, top);
			}
			for (int index = 0; index < 27; index++) {
				place(screen, slots.get(9 + index), layout.mainX[index], layout.mainY[index], left, top);
			}
			for (int index = 0; index < 9; index++) {
				place(screen, slots.get(36 + index), layout.bagHotbarX[index], layout.bagHotbarY[index], left, top);
			}
			place(screen, slots.get(45), layout.offhandX, layout.offhandY, left, top);
			place(screen, slots.get(46), layout.destroyX, layout.destroyY, left, top);
			return;
		}
		if (slots.size() < ITEM_SLOTS + 9) {
			return;
		}
		for (int index = 0; index < ITEM_SLOTS; index++) {
			place(screen, slots.get(index), layout.gridX[index], layout.gridY[index], left, top);
		}
		for (int index = 0; index < 9; index++) {
			place(screen, slots.get(ITEM_SLOTS + index), layout.hotbarX[index], layout.hotbarY[index], left, top);
		}
	}

	private static void place(final CreativeModeInventoryScreen screen, final Slot slot, final int x, final int y, final int left, final int top) {
		((SlotAccessor) slot).kohsInventoryTweaks$setX(Mth.clamp(x, 2 - left, screen.width - 18 - left));
		((SlotAccessor) slot).kohsInventoryTweaks$setY(Mth.clamp(y, 2 - top, screen.height - 18 - top));
	}

	/** Draws this layout in place of Vanilla's background; true if it did. */
	public static boolean drawPanel(
		final GuiGraphics graphics,
		final CreativeModeInventoryScreen screen,
		final RenderPipeline pipeline,
		final Identifier texture,
		final CreativeModeTab tab,
		final int left,
		final int top
	) {
		Layout layout = layoutFor(screen);
		if (layout == null) {
			return false;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		if (layout.broken) {
			drawBroken(graphics, pipeline, texture, left, top);
			return true;
		}
		if (layout.disc) {
			NextGenInventory.drawDisc(graphics, left + WIDTH / 2, top + HEIGHT / 2, config);
		}
		if (layout.plateWidth > 0) {
			NextGenInventory.drawPlate(graphics, pipeline, left, top, layout.plateWidth, layout.plateHeight, config);
		}
		if (layout.sideWidth > 0) {
			NextGenInventory.drawPlate(graphics, pipeline, left + layout.sideX, top + layout.sideY, layout.sideWidth, layout.sideHeight, config);
		}
		boolean inventoryTab = screen.isInventoryOpen();
		if (tab.canScroll()) {
			graphics.fill(left + layout.scrollX - 1, top + 17, left + layout.scrollX + 13, top + 131, 0xFF373737);
			graphics.fill(left + layout.scrollX, top + 18, left + layout.scrollX + 12, top + 130, 0xFF8B8B8B);
		}
		if (tab.getType() == CreativeModeTab.Type.SEARCH) {
			graphics.fill(left + 80, top + 4, left + 170, top + 17, 0xFF000000);
		}
		if (inventoryTab) {
			if (layout.modelSize > 0 && layout.plateWidth > 0) {
				graphics.fill(left + layout.modelX0, top + layout.modelY0, left + layout.modelX1, top + layout.modelY1, 0xFF000000);
			}
			drawTiles(graphics, screen, layout, left, top, config, 5, NextGenLayout.SLOTS + 1);
			Slot destroy = screen.getMenu().slots.get(NextGenLayout.SLOTS);
			drawDestroyMark(graphics, left + destroy.x, top + destroy.y);
		} else {
			drawTiles(graphics, screen, layout, left, top, config, 0, ITEM_SLOTS + 9);
		}
		return true;
	}

	private static void drawTiles(
		final GuiGraphics graphics,
		final CreativeModeInventoryScreen screen,
		final Layout layout,
		final int left,
		final int top,
		final InventoryTweaksConfig config,
		final int from,
		final int to
	) {
		List<Slot> slots = screen.getMenu().slots;
		for (int index = from; index < Math.min(to, slots.size()); index++) {
			Slot slot = slots.get(index);
			int x = left + slot.x;
			int y = top + slot.y;
			if (layout.hologram) {
				NextGenInventory.drawHologramTile(graphics, x, y, false, config);
			} else if (layout.hexagons) {
				NextGenInventory.drawHexagon(graphics, x, y, config);
			} else {
				NextGenInventory.drawSlotTile(graphics, x, y, config);
			}
		}
	}

	private static void drawDestroyMark(final GuiGraphics graphics, final int x, final int y) {
		for (int step = 3; step < 13; step++) {
			graphics.fill(x + step, y + step, x + step + 1, y + step + 2, 0xFFE03A3A);
			graphics.fill(x + 15 - step, y + step, x + 16 - step, y + step + 2, 0xFFE03A3A);
		}
	}

	private static void drawBroken(
		final GuiGraphics graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int left,
		final int top
	) {
		int leftEdge = left - SHIFT + CUT_X;
		int rightEdge = left + SHIFT + CUT_X;
		graphics.blit(pipeline, texture, left - SHIFT, top, 0.0F, 0.0F, CUT_X, HEIGHT, 256, 256);
		graphics.blit(pipeline, texture, rightEdge, top, CUT_X, 0.0F, WIDTH - CUT_X, HEIGHT, 256, 256);
		NextGenInventory.drawFractureEdges(graphics, pipeline, texture, leftEdge, rightEdge, top, HEIGHT, PLAIN_U, PLAIN_V, PLAIN_HEIGHT);
	}

	/** The box this layout draws the player in, in the panel's coordinates, or null to keep Vanilla's. */
	public static int @org.jspecify.annotations.Nullable [] modelBox(final CreativeModeInventoryScreen screen) {
		Layout layout = layoutFor(screen);
		return layout == null ? null : new int[] {layout.modelX0, layout.modelY0, layout.modelX1, layout.modelY1, layout.modelSize};
	}

	/** Where the scrollbar's handle goes along x, in the panel's coordinates. */
	public static int scrollbarX(final CreativeModeInventoryScreen screen) {
		Layout layout = layoutFor(screen);
		return layout == null ? 175 : layout.scrollX;
	}

	/** How far a tab moves along x to stay with its half of a split panel. */
	public static int tabShift(final CreativeModeInventoryScreen screen, final int tabX) {
		Layout layout = layoutFor(screen);
		if (layout == null || !layout.broken) {
			return 0;
		}
		return tabX < CUT_X ? -SHIFT : SHIFT;
	}

	/** Whether a click lands outside the layout, for throwing what the player carries. */
	public static boolean clickedOutside(final CreativeModeInventoryScreen screen, final boolean vanilla, final double mouseX, final double mouseY, final int left, final int top) {
		Layout layout = layoutFor(screen);
		if (layout == null) {
			return vanilla;
		}
		double x = mouseX - left;
		double y = mouseY - top;
		boolean custom = ConfigStore.get().nextGenStyle == NextGenStyle.CUSTOM;
		if (!custom) {
			return x < layout.minX || y < layout.minY || x >= layout.maxX || y >= layout.maxY;
		}
		for (Slot slot : screen.getMenu().slots) {
			if (x >= slot.x - 6 && x < slot.x + 22 && y >= slot.y - 6 && y < slot.y + 22) {
				return false;
			}
		}
		return vanilla;
	}

	/** How far the effect panels move to clear this layout, or null to leave them. */
	public static int @org.jspecify.annotations.Nullable [] effectsOffset(final CreativeModeInventoryScreen screen) {
		Layout layout = layoutFor(screen);
		if (layout == null) {
			return null;
		}
		if (ConfigStore.get().nextGenStyle == NextGenStyle.CUSTOM) {
			InventoryTweaksConfig config = ConfigStore.get();
			return new int[] {config.customEffectsX + CUSTOM_DX - WIDTH - 2, config.customEffectsY + CUSTOM_DY};
		}
		int shift = layout.maxX - WIDTH;
		return shift > 0 ? new int[] {shift, 0} : null;
	}
}
