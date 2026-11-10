package dev.zymekoh.kohsinventorytweaks.inventory;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.NextGenStyle;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.SlotAccessor;
import dev.zymekoh.kohsinventorytweaks.render.InterfaceTheme;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Inventories that put the player's slots somewhere else, always around the middle of the screen.
 *
 * <p>Only the screen changes. A slot keeps its index, so what reaches the server is what a Vanilla
 * inventory sends for the same clicks. Moving a slot does move where the player has to click, and
 * a server may rule against that, which is why the player is warned before entering.</p>
 *
 * <p>{@code Slot.x} and {@code Slot.y} are the only source Minecraft has for drawing a slot and for
 * finding the one under the pointer, so moving them keeps both in agreement. The menu's slots are
 * shared with the Creative inventory, so they go back to where they were when the screen closes.</p>
 */
public final class NextGenInventory {
	public static final int BROKEN_SHIFT = 20;
	public static final int MODEL_WIDTH = NextGenLayout.MODEL_WIDTH;
	public static final int MODEL_HEIGHT = NextGenLayout.MODEL_HEIGHT;
	static final int RING_OUTER = 86;
	static final int RING_INNER = 62;
	static final int RING_RADIUS = 101;
	static final int HEX_ROW_STEP = 17;
	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_HEIGHT = 166;
	private static final int TEXTURE_SIZE = 256;
	/** A click this close to a floating slot still counts as a click on the inventory. */
	private static final int CUSTOM_REACH = 6;
	private static final int SCREEN_EDGE = 2;
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

	/** Between the fifth and sixth inventory columns, which is also the crafting grid's left border. */
	private static final int CUT_X = 97;
	private static final int FRACTURE_DEPTH = 12;
	/** A stretch of the panel with no slots on it, which the broken edges are cut from. */
	private static final int PLAIN_U = 100;
	private static final int PLAIN_V = 60;
	private static final int PLAIN_HEIGHT = 16;
	private static final int[] FRACTURE_Y = {0, 9, 20, 28, 41, 52, 63, 74, 83, 95, 107, 118, 129, 140, 151, 166};
	private static final int[] FRACTURE_REACH = {4, 11, 2, 9, 3, 11, 5, 10, 1, 8, 11, 4, 11, 2, 9, 5};

	private static final int HOLOGRAM = 0x3FDCFF;

	private static final Map<Slot, int[]> MOVED = new IdentityHashMap<>();
	private static final GeneratedSprite DISC = new GeneratedSprite("next_gen_disc", "KoHs ring plate", RING_RADIUS * 2, RING_RADIUS * 2, NextGenInventory::paintDisc);
	private static final GeneratedSprite HEXAGON = new GeneratedSprite("next_gen_hexagon", "KoHs hexagon slot", 18, 20, NextGenInventory::paintHexagon);
	private static @Nullable InventoryScreen movedScreen;
	private static NextGenStyle movedStyle = NextGenStyle.OFF;
	private static @Nullable NextGenLayout movedLayout;
	private static int originalTitleX;
	private static int originalTitleY;

	private NextGenInventory() {
	}

	/** The layout an inventory on a screen of this size gets: too small a window keeps Vanilla's. */
	public static NextGenStyle styleFor(final InventoryTweaksConfig config, final int width, final int height) {
		if (config == null || config.nextGenStyle == NextGenStyle.OFF
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return NextGenStyle.OFF;
		}
		NextGenLayout layout = NextGenLayout.of(config.nextGenStyle);
		return width >= layout.width() + 8 && height >= layout.height() + 4 ? config.nextGenStyle : NextGenStyle.OFF;
	}

	/** The layout this screen is really showing. */
	public static NextGenStyle activeStyle(final AbstractContainerScreen<?> screen) {
		return screen == movedScreen ? movedStyle : NextGenStyle.OFF;
	}

	public static int footprintWidth(final NextGenStyle style) {
		return NextGenLayout.of(style).width();
	}

	public static int footprintHeight(final NextGenStyle style) {
		return NextGenLayout.of(style).height();
	}

	public static boolean allowsRecipeBook(final NextGenStyle style) {
		return style == NextGenStyle.OFF || NextGenLayout.of(style).recipeBook;
	}

	/** Some layouts have no room beside them for the book, so for those it stays shut. */
	public static boolean hidesRecipeBook() {
		return Minecraft.getInstance().gui.screen() instanceof InventoryScreen screen
			&& !allowsRecipeBook(styleFor(ConfigStore.get(), screen.width, screen.height));
	}

	/** Whether this screen's title is drawn straight over the world, with no panel to darken it. */
	public static boolean drawsOnDarkGlass(final InventoryTweaksConfig config) {
		if (Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen creative) {
			return NextGenCreative.isFloating(creative);
		}
		return Minecraft.getInstance().gui.screen() instanceof InventoryScreen screen
			&& activeStyle(screen) == NextGenStyle.FAN;
	}

	private static NextGenLayout layoutFor(final NextGenStyle style, final InventoryTweaksConfig config) {
		return style == NextGenStyle.CUSTOM ? NextGenLayout.custom(config) : NextGenLayout.of(style);
	}

	/** Moves the slots of a screen that has just been laid out. */
	public static void apply(final InventoryScreen screen) {
		restore(screen);
		Minecraft minecraft = Minecraft.getInstance();
		// A player with infinite materials is sent on to the Creative screen during init.
		if (minecraft.gui.screen() != screen || minecraft.player == null || minecraft.player.hasInfiniteMaterials()) {
			return;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		NextGenStyle style = styleFor(config, screen.width, screen.height);
		List<Slot> slots = screen.getMenu().slots;
		if (style == NextGenStyle.OFF || slots.size() < NextGenLayout.SLOTS) {
			return;
		}
		NextGenLayout layout = layoutFor(style, config);
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		int left = access.kohsInventoryTweaks$getLeftPos();
		int top = access.kohsInventoryTweaks$getTopPos();
		for (int index = 0; index < NextGenLayout.SLOTS; index++) {
			Slot slot = slots.get(index);
			MOVED.put(slot, new int[] {slot.x, slot.y});
			int x = layout.xs[index];
			int y = layout.ys[index];
			if (style == NextGenStyle.CUSTOM) {
				// Placed on a bigger window than this one, a slot must still be reachable.
				x = Mth.clamp(x, SCREEN_EDGE - left, screen.width - SCREEN_EDGE - 16 - left);
				y = Mth.clamp(y, SCREEN_EDGE - top, screen.height - SCREEN_EDGE - 16 - top);
			}
			((SlotAccessor) slot).kohsInventoryTweaks$setX(x);
			((SlotAccessor) slot).kohsInventoryTweaks$setY(y);
		}
		originalTitleX = access.kohsInventoryTweaks$getTitleLabelX();
		originalTitleY = access.kohsInventoryTweaks$getTitleLabelY();
		// A layout without a title sends Vanilla's far off the screen, where it draws nothing.
		access.kohsInventoryTweaks$setTitleLabelX(layout.titleX == NextGenLayout.NO_TITLE ? -10000 : layout.titleX);
		access.kohsInventoryTweaks$setTitleLabelY(layout.titleX == NextGenLayout.NO_TITLE ? -10000 : layout.titleY);
		movedScreen = screen;
		movedStyle = style;
		movedLayout = layout;
	}

	/** Puts back what {@link #apply} moved. */
	public static void restore(final AbstractContainerScreen<?> screen) {
		if (screen != movedScreen) {
			return;
		}
		for (Map.Entry<Slot, int[]> moved : MOVED.entrySet()) {
			((SlotAccessor) moved.getKey()).kohsInventoryTweaks$setX(moved.getValue()[0]);
			((SlotAccessor) moved.getKey()).kohsInventoryTweaks$setY(moved.getValue()[1]);
		}
		MOVED.clear();
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		access.kohsInventoryTweaks$setTitleLabelX(originalTitleX);
		access.kohsInventoryTweaks$setTitleLabelY(originalTitleY);
		movedScreen = null;
		movedStyle = NextGenStyle.OFF;
		movedLayout = null;
	}

	static int brokenX(final int x) {
		return x < CUT_X ? x - BROKEN_SHIFT : x + BROKEN_SHIFT;
	}

	/** Where this layout keeps the recipe book button, or null when Vanilla's place is right. */
	public static @Nullable ScreenPosition recipeButtonPosition(final InventoryScreen screen) {
		NextGenStyle style = styleFor(ConfigStore.get(), screen.width, screen.height);
		if (style == NextGenStyle.OFF) {
			return null;
		}
		NextGenLayout layout = NextGenLayout.of(style);
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		return new ScreenPosition(
			access.kohsInventoryTweaks$getLeftPos() + layout.bookX,
			access.kohsInventoryTweaks$getTopPos() + layout.bookY
		);
	}

	/** Vanilla throws the carried item for a click outside the panel, and moved slots lie outside it. */
	public static boolean clickedOutside(
		final NextGenStyle style,
		final double mouseX,
		final double mouseY,
		final int left,
		final int top
	) {
		if (style == NextGenStyle.RING) {
			double dx = mouseX - (left + PANEL_WIDTH / 2.0);
			double dy = mouseY - (top + 83.0);
			return dx * dx + dy * dy >= (double) (RING_RADIUS * RING_RADIUS);
		}
		NextGenLayout layout = layoutFor(style, ConfigStore.get());
		if (style == NextGenStyle.CUSTOM) {
			return !nearCustomLayout(layout, mouseX - left, mouseY - top);
		}
		return mouseX < left + layout.minX || mouseY < top + layout.minY
			|| mouseX >= left + layout.maxX || mouseY >= top + layout.maxY;
	}

	/** The custom layout can be scattered, so its outside is everywhere that is not near a slot. */
	private static boolean nearCustomLayout(final NextGenLayout layout, final double x, final double y) {
		for (int index = 0; index < NextGenLayout.SLOTS; index++) {
			if (x >= layout.xs[index] - CUSTOM_REACH && x < layout.xs[index] + 16 + CUSTOM_REACH
				&& y >= layout.ys[index] - CUSTOM_REACH && y < layout.ys[index] + 16 + CUSTOM_REACH) {
				return true;
			}
		}
		return x >= layout.modelX0 && x < layout.modelX1 && y >= layout.modelY0 && y < layout.modelY1;
	}

	/** Draws this layout in place of Vanilla's panel; true if it did. */
	public static boolean drawPanel(
		final GuiGraphicsExtractor graphics,
		final InventoryScreen screen,
		final RenderPipeline pipeline,
		final int left,
		final int top
	) {
		NextGenStyle style = activeStyle(screen);
		if (style == NextGenStyle.OFF || style == NextGenStyle.VISOR || movedLayout == null) {
			return false;
		}
		drawBody(graphics, pipeline, style, movedLayout, left, top, ConfigStore.get());
		return true;
	}

	private static void drawBody(
		final GuiGraphicsExtractor graphics,
		final RenderPipeline pipeline,
		final NextGenStyle style,
		final NextGenLayout layout,
		final int left,
		final int top,
		final InventoryTweaksConfig config
	) {
		if (style == NextGenStyle.BROKEN) {
			drawBrokenPanel(graphics, pipeline, InventoryTextureManager.textureFor(config), left, top);
			return;
		}
		if (style == NextGenStyle.RING) {
			drawDisc(graphics, left + PANEL_WIDTH / 2, top + 83, config);
		}
		if (layout.plateWidth > 0) {
			drawPlate(graphics, pipeline, left + layout.plateX, top + layout.plateY, layout.plateWidth, layout.plateHeight, config);
		}
		if (layout.modelBox) {
			graphics.fill(left + layout.modelX0, top + layout.modelY0, left + layout.modelX1, top + layout.modelY1, 0xFF000000);
		}
		if (style == NextGenStyle.HONEYCOMB) {
			drawSlots(graphics, layout, left, top, config, 0, 9);
			drawSlots(graphics, layout, left, top, config, 45, 46);
			for (int index = 9; index < 45; index++) {
				drawHexagon(graphics, left + layout.xs[index], top + layout.ys[index], config);
			}
		} else {
			drawSlots(graphics, layout, left, top, config, 0, NextGenLayout.SLOTS);
		}
		if (layout.arrowX != NextGenLayout.NO_ARROW) {
			drawArrow(graphics, left + layout.arrowX, top + layout.arrowY, layout.arrowLeft, config);
		}
	}

	private static void drawSlots(
		final GuiGraphicsExtractor graphics,
		final NextGenLayout layout,
		final int left,
		final int top,
		final InventoryTweaksConfig config,
		final int from,
		final int to
	) {
		for (int index = from; index < to; index++) {
			drawSlotTile(graphics, left + layout.xs[index], top + layout.ys[index], config);
		}
	}

	/** A slot's background, in Minecraft's sprite or in the customization's colours. */
	public static void drawSlotTile(final GuiGraphicsExtractor graphics, final int x, final int y, final InventoryTweaksConfig config) {
		if (InventoryTextureManager.hasColorCustomization(config)) {
			graphics.fill(x - 1, y - 1, x + 17, y + 17, tinted(0xFF6B477F, config.frameColor, config.frameOpacity));
			graphics.fill(x, y, x + 16, y + 16, tinted(0xFF17101F, config.slotColor, config.slotOpacity));
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x - 1, y - 1, 18, 18);
		}
	}

	static void drawHexagon(final GuiGraphicsExtractor graphics, final int x, final int y, final InventoryTweaksConfig config) {
		HEXAGON.prepare();
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, HEXAGON.id, x - 1, y - 2, 0.0F, 0.0F, 18, 20, 18, 20,
			(config.slotOpacity & 0xFF) << 24 | config.slotColor & 0xFFFFFF
		);
	}

	static void drawDisc(final GuiGraphicsExtractor graphics, final int centerX, final int centerY, final InventoryTweaksConfig config) {
		DISC.prepare();
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, DISC.id, centerX - RING_RADIUS, centerY - RING_RADIUS,
			0.0F, 0.0F, RING_RADIUS * 2, RING_RADIUS * 2, RING_RADIUS * 2, RING_RADIUS * 2,
			InventoryTextureManager.inventoryTint(config)
		);
	}

	private static int tinted(final int base, final int color, final int opacity) {
		int alpha = (base >>> 24) * Mth.clamp(opacity, 0, 255) / 255;
		int red = (base >> 16 & 0xFF) * (color >> 16 & 0xFF) / 255;
		int green = (base >> 8 & 0xFF) * (color >> 8 & 0xFF) / 255;
		int blue = (base & 0xFF) * (color & 0xFF) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	/** A panel in Minecraft's own shape, wearing the inventory's colour, image and opacity. */
	static void drawPlate(
		final GuiGraphicsExtractor graphics,
		final RenderPipeline pipeline,
		final int x,
		final int y,
		final int width,
		final int height,
		final InventoryTweaksConfig config
	) {
		int alpha = Mth.clamp(config.frameOpacity, 0, 255) << 24;
		int panel = InventoryTextureManager.panelColor(config) & 0xFFFFFF;
		int light = alpha | mix(panel, 0xFFFFFF, 3, 4);
		int dark = alpha | mix(panel, 0x000000, 4, 7);
		int edge = alpha;
		int right = x + width;
		int bottom = y + height;
		graphics.fill(x + 2, y, right - 3, y + 1, edge);
		graphics.fill(x + 3, bottom - 1, right - 2, bottom, edge);
		graphics.fill(x, y + 2, x + 1, bottom - 3, edge);
		graphics.fill(right - 1, y + 3, right, bottom - 2, edge);
		graphics.fill(x + 1, y + 1, x + 2, y + 2, edge);
		graphics.fill(right - 3, y + 1, right - 1, y + 3, edge);
		graphics.fill(x + 1, bottom - 3, x + 3, bottom - 1, edge);
		graphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, edge);
		Identifier surface = InventoryTextureManager.plainSurface(config);
		if (surface == null) {
			graphics.fill(x + 3, y + 3, right - 3, bottom - 3, alpha | panel);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 3.0F, y + 3.0F);
			graphics.pose().scale((width - 6) / (float) PANEL_WIDTH, (height - 6) / (float) PANEL_HEIGHT);
			graphics.blit(pipeline, surface, 0, 0, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
			graphics.pose().popMatrix();
		}
		graphics.fill(x + 2, y + 1, right - 3, y + 3, light);
		graphics.fill(x + 1, y + 2, x + 3, bottom - 3, light);
		graphics.fill(x + 3, bottom - 3, right - 2, bottom - 1, dark);
		graphics.fill(right - 3, y + 3, right - 1, bottom - 3, dark);
	}

	private static int mix(final int color, final int other, final int parts, final int whole) {
		int red = ((color >> 16 & 0xFF) * (whole - parts) + (other >> 16 & 0xFF) * parts) / whole;
		int green = ((color >> 8 & 0xFF) * (whole - parts) + (other >> 8 & 0xFF) * parts) / whole;
		int blue = ((color & 0xFF) * (whole - parts) + (other & 0xFF) * parts) / whole;
		return red << 16 | green << 8 | blue;
	}

	private static void drawArrow(final GuiGraphicsExtractor graphics, final int x, final int y, final boolean pointsLeft, final InventoryTweaksConfig config) {
		int color = Mth.clamp(config.frameOpacity, 0, 255) << 24 | mix(InventoryTextureManager.panelColor(config) & 0xFFFFFF, 0x000000, 2, 7);
		if (pointsLeft) {
			graphics.fill(x + 6, y + 4, x + 14, y + 7, color);
			for (int step = 0; step < 6; step++) {
				graphics.fill(x + 5 - step, y + step, x + 6 - step, y + 11 - step, color);
			}
			return;
		}
		graphics.fill(x, y + 4, x + 8, y + 7, color);
		for (int step = 0; step < 6; step++) {
			graphics.fill(x + 8 + step, y + step, x + 9 + step, y + 11 - step, color);
		}
	}

	private static void drawBrokenPanel(
		final GuiGraphicsExtractor graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int left,
		final int top
	) {
		int leftEdge = left - BROKEN_SHIFT + CUT_X;
		int rightEdge = left + BROKEN_SHIFT + CUT_X;
		graphics.blit(pipeline, texture, left - BROKEN_SHIFT, top, 0.0F, 0.0F, CUT_X, PANEL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
		graphics.blit(pipeline, texture, rightEdge, top, CUT_X, 0.0F, PANEL_WIDTH - CUT_X, PANEL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
		drawFractureEdges(graphics, pipeline, texture, leftEdge, rightEdge, top, PANEL_HEIGHT, PLAIN_U, PLAIN_V, PLAIN_HEIGHT);
	}

	/** The torn edges of a split panel, cut from a stretch of the panel with no slots on it. */
	static void drawFractureEdges(
		final GuiGraphicsExtractor graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int leftEdge,
		final int rightEdge,
		final int top,
		final int height,
		final int plainU,
		final int plainV,
		final int plainHeight
	) {
		int row = 0;
		while (row < height) {
			boolean border = row < 3 || row >= height - 3;
			int rowHeight = border ? 1 : 2;
			int v = border ? row : plainV + row % plainHeight;
			int reach = fractureReach(row * PANEL_HEIGHT / height);
			int other = FRACTURE_DEPTH - reach;
			int y = top + row;
			graphics.blit(pipeline, texture, leftEdge, y, plainU, v, reach, rowHeight, TEXTURE_SIZE, TEXTURE_SIZE);
			graphics.fill(leftEdge + reach, y, leftEdge + reach + 1, y + rowHeight, 0xFF000000);
			graphics.blit(pipeline, texture, rightEdge - other, y, plainU, v, other, rowHeight, TEXTURE_SIZE, TEXTURE_SIZE);
			graphics.fill(rightEdge - other - 1, y, rightEdge - other, y + rowHeight, 0xFF000000);
			row += rowHeight;
		}
	}

	private static int fractureReach(final int row) {
		for (int index = 1; index < FRACTURE_Y.length; index++) {
			if (row <= FRACTURE_Y[index]) {
				int from = FRACTURE_REACH[index - 1];
				int span = Math.max(1, FRACTURE_Y[index] - FRACTURE_Y[index - 1]);
				int reach = from + (FRACTURE_REACH[index] - from) * (row - FRACTURE_Y[index - 1]) / span;
				return Mth.clamp(reach, 1, FRACTURE_DEPTH - 1);
			}
		}
		return FRACTURE_REACH[FRACTURE_REACH.length - 1];
	}

	/** The box this layout draws the player in, in the panel's coordinates, or null to keep Vanilla's. */
	public static int @Nullable [] modelBox(final InventoryScreen screen) {
		if (activeStyle(screen) == NextGenStyle.OFF || movedLayout == null) {
			return null;
		}
		return new int[] {movedLayout.modelX0, movedLayout.modelY0, movedLayout.modelX1, movedLayout.modelY1, movedLayout.modelSize};
	}

	/** How far the effect panels move to clear this layout, or null to leave them where Vanilla puts them. */
	public static int @Nullable [] effectsOffset(final InventoryScreen screen) {
		NextGenStyle style = activeStyle(screen);
		if (style == NextGenStyle.OFF) {
			return null;
		}
		if (style == NextGenStyle.CUSTOM) {
			InventoryTweaksConfig config = ConfigStore.get();
			return new int[] {config.customEffectsX - InventoryTweaksConfig.DEFAULT_EFFECTS_X, config.customEffectsY};
		}
		int shift = NextGenLayout.of(style).maxX - PANEL_WIDTH;
		return shift > 0 ? new int[] {shift, 0} : null;
	}

	/** Draws the visor's slots and player in place of Vanilla's background; true if it did. */
	public static boolean drawVisorBackground(
		final GuiGraphicsExtractor graphics,
		final InventoryScreen screen,
		final int mouseX,
		final int mouseY
	) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (activeStyle(screen) != NextGenStyle.VISOR || player == null) {
			return false;
		}
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		int left = access.kohsInventoryTweaks$getLeftPos();
		int top = access.kohsInventoryTweaks$getTopPos();
		NextGenLayout layout = NextGenLayout.of(NextGenStyle.VISOR);
		drawVisorSlots(graphics, layout, left, top, ConfigStore.get());
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			graphics,
			left + layout.modelX0,
			top + layout.modelY0,
			left + layout.modelX1,
			top + layout.modelY1,
			layout.modelSize,
			0.0625F,
			mouseX,
			mouseY,
			player
		);
		return true;
	}

	/** Only the slots float over the world: no panel, no darkening, nothing else drawn behind them. */
	private static void drawVisorSlots(
		final GuiGraphicsExtractor graphics,
		final NextGenLayout layout,
		final int left,
		final int top,
		final InventoryTweaksConfig config
	) {
		for (int index = 0; index < NextGenLayout.SLOTS; index++) {
			drawHologramTile(graphics, left + layout.xs[index], top + layout.ys[index], index >= 36 && index <= 44, config);
		}
	}

	private static int alpha(final int opacity, final int strength) {
		return opacity * strength / 255;
	}

	/** One floating slot: only its outline, narrower than the cell so neighbours never join into a sheet. */
	public static void drawHologramTile(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final boolean strong,
		final InventoryTweaksConfig config
	) {
		int fallback = config.frameColor == 0xFFFFFF ? HOLOGRAM : config.frameColor & 0xFFFFFF;
		int color = config.slotColor == 0xFFFFFF ? fallback : config.slotColor & 0xFFFFFF;
		int opacity = Mth.clamp(config.slotOpacity, 0, 255);
		int edge = alpha(opacity, strong ? 170 : 120) << 24 | color;
		graphics.fill(x, y, x + 16, y + 1, edge);
		graphics.fill(x, y + 15, x + 16, y + 16, edge);
		graphics.fill(x, y + 1, x + 1, y + 15, edge);
		graphics.fill(x + 15, y + 1, x + 16, y + 15, edge);
		int corner = alpha(opacity, 240) << 24 | color;
		graphics.fill(x, y, x + 3, y + 1, corner);
		graphics.fill(x, y + 1, x + 1, y + 3, corner);
		graphics.fill(x + 13, y + 15, x + 16, y + 16, corner);
		graphics.fill(x + 15, y + 13, x + 16, y + 15, corner);
	}

	/** Shows the layout at a size that fits this area, with the player's real items. */
	public static void drawPreview(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final NextGenStyle style,
		final int areaX,
		final int areaY,
		final int areaWidth,
		final int areaHeight,
		final int mouseX,
		final int mouseY,
		final InventoryTweaksConfig config
	) {
		NextGenLayout layout = layoutFor(style, config);
		LocalPlayer player = Minecraft.getInstance().player;
		float scale = Math.min(1.6F, Math.min(areaWidth / (float) Math.max(1, layout.width()), areaHeight / (float) Math.max(1, layout.height())));
		float originX = areaX + (areaWidth - layout.width() * scale) / 2.0F - layout.minX * scale;
		float originY = areaY + (areaHeight - layout.height() * scale) / 2.0F - layout.minY * scale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(originX, originY);
		graphics.pose().scale(scale, scale);
		if (style == NextGenStyle.VISOR) {
			// A strip of sky stands in for the world the visor floats over.
			graphics.fillGradient(layout.minX, layout.minY, layout.maxX, layout.maxY, 0xFF3A6A94, 0xFF16242F);
			drawVisorSlots(graphics, layout, 0, 0, config);
		} else {
			drawBody(graphics, RenderPipelines.GUI_TEXTURED, style, layout, 0, 0, config);
			if (layout.titleX != NextGenLayout.NO_TITLE) {
				int label = style == NextGenStyle.FAN ? ZTheme.TEXT
					: InventoryTextureManager.customizesContainerSurfaces(config) ? InterfaceTheme.previewLabelColor(config) : 0xFF404040;
				graphics.text(font, Component.translatable("container.crafting"), layout.titleX, layout.titleY, label, false);
			}
		}
		if (player != null) {
			List<Slot> slots = player.inventoryMenu.slots;
			for (int index = 0; index < Math.min(NextGenLayout.SLOTS, slots.size()); index++) {
				Slot slot = slots.get(index);
				if (!slot.getItem().isEmpty()) {
					int itemX = layout.xs[index];
					int itemY = layout.ys[index];
					graphics.item(slot.getItem(), itemX, itemY, itemX + itemY * PANEL_WIDTH);
					graphics.itemDecorations(font, slot.getItem(), itemX, itemY);
				}
			}
		}
		graphics.pose().popMatrix();
		if (player == null || layout.modelSize <= 0) {
			return;
		}
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			graphics,
			Math.round(originX + layout.modelX0 * scale),
			Math.round(originY + layout.modelY0 * scale),
			Math.round(originX + layout.modelX1 * scale),
			Math.round(originY + layout.modelY1 * scale),
			Math.max(8, Math.round(layout.modelSize * scale)),
			0.0625F,
			mouseX,
			mouseY,
			player
		);
	}

	/** Slot positions for the custom layout's editor: the player's, or Vanilla's until they place one. */
	public static int[][] customSlots(final InventoryTweaksConfig config) {
		NextGenLayout layout = NextGenLayout.custom(config);
		return new int[][] {layout.xs.clone(), layout.ys.clone()};
	}

	/** Vanilla's slot positions, for putting the custom layout back. */
	public static int[][] vanillaSlots() {
		NextGenLayout layout = NextGenLayout.of(NextGenStyle.CUSTOM);
		return new int[][] {layout.xs.clone(), layout.ys.clone()};
	}

	@FunctionalInterface
	private interface Painter {
		void paint(NativeImage image, int width, int height);
	}

	/** A small picture made once in code and kept for the whole session. */
	private static final class GeneratedSprite {
		private final Identifier id;
		private final String label;
		private final int width;
		private final int height;
		private final Painter painter;
		private @Nullable DynamicTexture texture;

		private GeneratedSprite(final String name, final String label, final int width, final int height, final Painter painter) {
			this.id = Identifier.fromNamespaceAndPath(KoHsInventoryTweaksClient.MOD_ID, "dynamic/" + name);
			this.label = label;
			this.width = width;
			this.height = height;
			this.painter = painter;
		}

		private void prepare() {
			if (this.texture != null) {
				return;
			}
			NativeImage image = new NativeImage(this.width, this.height, true);
			this.painter.paint(image, this.width, this.height);
			this.texture = new DynamicTexture(() -> this.label, image);
			Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
			this.texture.upload();
		}
	}

	/** A round plate in the Vanilla panel's greys, so a tint gives it the inventory's colour. */
	private static void paintDisc(final NativeImage image, final int width, final int height) {
		float center = width / 2.0F;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float dx = x + 0.5F - center;
				float dy = y + 0.5F - center;
				float distance = center - (float) Math.sqrt(dx * dx + dy * dy);
				image.setPixel(x, y, distance < 0.0F ? 0 : discPixel(distance, dx + dy));
			}
		}
	}

	private static int discPixel(final float inside, final float towardLight) {
		if (inside < 1.2F) {
			return 0xFF000000;
		}
		if (inside < 3.2F) {
			return towardLight < 0.0F ? 0xFFFFFFFF : 0xFF555555;
		}
		return 0xFFC6C6C6;
	}

	/** A hexagon with Minecraft's slot shading: dark along the top left, light along the bottom right. */
	private static void paintHexagon(final NativeImage image, final int width, final int height) {
		for (int y = 0; y < height; y++) {
			int shoulder = Math.min(y, height - 1 - y);
			int inset = shoulder >= 5 ? 0 : (5 - shoulder) * 8 / 5;
			for (int x = 0; x < width; x++) {
				boolean inside = x >= inset && x < width - inset;
				if (!inside) {
					image.setPixel(x, y, 0);
					continue;
				}
				boolean edge = x == inset || x == width - 1 - inset || y == 0 || y == height - 1
					|| shoulder < 5 && (x <= inset + 1 || x >= width - 2 - inset);
				boolean lightSide = x + y > (width + height) / 2;
				image.setPixel(x, y, edge ? (lightSide ? 0xFFFFFFFF : 0xFF373737) : 0xFF8B8B8B);
			}
		}
	}
}
