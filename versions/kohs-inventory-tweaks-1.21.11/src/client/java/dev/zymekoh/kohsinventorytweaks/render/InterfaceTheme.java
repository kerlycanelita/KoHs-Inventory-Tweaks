package dev.zymekoh.kohsinventorytweaks.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.PartStyle;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.TabStyle;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Carries the inventory's look to what Minecraft draws around it.
 *
 * <p>The panel of a container is one texture, which the customization recomposes. The creative
 * tabs, the recipe book tabs, the scrollbars and the effect panels are separate sprites, so they
 * stayed Vanilla grey beside a customized panel. They share the panel's greys, so multiplying them
 * by the panel's colour is the same operation the panel itself gets; with a background image, the
 * creative tabs can wear the image too.</p>
 *
 * <p>Tooltips and menu buttons follow the same choice, off by default. Tooltip sprites are nearly
 * black, which no tint can colour, so a themed tooltip swaps them for white ones of the same shape.
 * A tooltip with its own {@code tooltip_style} keeps it.</p>
 */
public final class InterfaceTheme {
	/** The dark grey of every container title. */
	private static final int VANILLA_LABEL = -12566464;
	private static final int LIGHT_LABEL = 0xFFF0E8F8;
	private static final double DARK_PANEL_LUMINANCE = 0.45;
	private static final String CREATIVE_TAB = "container/creative_inventory/tab_";
	private static final Identifier FABRIC_ARROWS = Identifier.fromNamespaceAndPath("fabric", "textures/gui/creative_buttons.png");
	private static final Identifier TOOLTIP_BACKGROUND = Identifier.fromNamespaceAndPath(KoHsInventoryTweaksClient.MOD_ID, "tooltip/theme_background");
	private static final Identifier TOOLTIP_FRAME = Identifier.fromNamespaceAndPath(KoHsInventoryTweaksClient.MOD_ID, "tooltip/theme_frame");
	/** Vanilla leaves this much margin and padding around a tooltip's text. */
	private static final int TOOLTIP_INSET = 12;
	private static final int TOOLTIP_DARKEST_CHANNEL = 0x2A;
	private static final int TOOLTIP_FOLLOW_BACKGROUND_ALPHA = 240;
	private static final int TOOLTIP_FOLLOW_FRAME_ALPHA = 230;

	private enum Part {
		CREATIVE_TAB,
		RECIPE_TAB,
		SCROLLBAR,
		EFFECT
	}

	private InterfaceTheme() {
	}

	/** Draws a sprite in the inventory's look when it is one of the themed parts; true if it did. */
	public static boolean drawSprite(
		final GuiGraphics graphics,
		final RenderPipeline pipeline,
		final Identifier sprite,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		if (!"minecraft".equals(sprite.getNamespace())) {
			return false;
		}
		Part part = partOf(sprite.getPath());
		if (part == null) {
			return false;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		if (!partEnabled(part, config) || !tabsThemed(config)) {
			return false;
		}
		if (part == Part.CREATIVE_TAB && config.tabStyle == TabStyle.FOLLOW) {
			Identifier composed = InventoryTextureManager.creativeTabTexture(config, sprite);
			if (composed != null) {
				graphics.blit(pipeline, composed, x, y, 0.0F, 0.0F, width, height, width, height);
				return true;
			}
		}
		graphics.blitSprite(pipeline, sprite, x, y, width, height, tabTint(config));
		return true;
	}

	/** Fabric API's page arrows are one texture of the same greys as the tabs beside them. */
	public static boolean drawFabricArrows(
		final GuiGraphics graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int x,
		final int y,
		final float u,
		final float v,
		final int width,
		final int height,
		final int textureWidth,
		final int textureHeight
	) {
		if (!"fabric".equals(texture.getNamespace()) || !FABRIC_ARROWS.equals(texture)) {
			return false;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		if (!config.themeScrollbars || !tabsThemed(config)) {
			return false;
		}
		graphics.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, tabTint(config));
		return true;
	}

	private static @Nullable Part partOf(final String path) {
		if (path.startsWith("container/")) {
			if (path.startsWith(CREATIVE_TAB)) {
				return Part.CREATIVE_TAB;
			}
			if (path.endsWith("/scroller") || path.endsWith("/scroller_disabled")) {
				return Part.SCROLLBAR;
			}
			// The ambient panel's cyan border is what tells a beacon effect apart, so it stays.
			return path.equals("container/inventory/effect_background") ? Part.EFFECT : null;
		}
		return path.equals("recipe_book/tab") || path.equals("recipe_book/tab_selected") ? Part.RECIPE_TAB : null;
	}

	private static boolean partEnabled(final Part part, final InventoryTweaksConfig config) {
		return switch (part) {
			case CREATIVE_TAB -> config.themeCreativeTabs;
			case RECIPE_TAB -> config.themeRecipeBookTabs;
			case SCROLLBAR -> config.themeScrollbars;
			case EFFECT -> config.themeEffects;
		};
	}

	private static boolean tabsThemed(final InventoryTweaksConfig config) {
		if (config.tabStyle == TabStyle.VANILLA || !customizationAvailable()) {
			return false;
		}
		return config.tabStyle == TabStyle.CUSTOM || InventoryTextureManager.customizesContainerSurfaces(config);
	}

	private static int tabTint(final InventoryTweaksConfig config) {
		return config.tabStyle == TabStyle.CUSTOM
			? (config.tabOpacity & 0xFF) << 24 | config.tabColor & 0xFFFFFF
			: InventoryTextureManager.inventoryTint(config);
	}

	private static boolean customizationAvailable() {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION);
	}

	/** Draws a themed tooltip box in place of Vanilla's; true if it did. */
	public static boolean drawTooltip(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final @Nullable Identifier style
	) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (style != null || !tooltipThemed(config)
			|| !config.tooltipEverywhere && !(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) {
			return false;
		}
		drawTooltipBox(graphics, x, y, width, height, config);
		return true;
	}

	/** Whether tooltips are themed at all: following the inventory needs an inventory that was customized. */
	public static boolean tooltipThemed(final InventoryTweaksConfig config) {
		if (config.tooltipStyle == PartStyle.VANILLA || !customizationAvailable()) {
			return false;
		}
		return config.tooltipStyle == PartStyle.CUSTOM || InventoryTextureManager.customizesContainerSurfaces(config);
	}

	/** The box of a tooltip whose content is at this position and size. */
	public static void drawTooltipBox(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final InventoryTweaksConfig config
	) {
		int background;
		int frame;
		if (config.tooltipStyle == PartStyle.CUSTOM) {
			int alpha = (config.tooltipOpacity & 0xFF) << 24;
			background = alpha | config.tooltipBackgroundColor & 0xFFFFFF;
			frame = alpha | config.tooltipFrameColor & 0xFFFFFF;
		} else {
			int panel = InventoryTextureManager.panelColor(config);
			background = TOOLTIP_FOLLOW_BACKGROUND_ALPHA << 24 | withBrightestChannel(panel, TOOLTIP_DARKEST_CHANNEL);
			frame = TOOLTIP_FOLLOW_FRAME_ALPHA << 24 | withBrightestChannel(panel, 0xFF);
		}
		int boxX = x - TOOLTIP_INSET;
		int boxY = y - TOOLTIP_INSET;
		int boxWidth = width + TOOLTIP_INSET * 2;
		int boxHeight = height + TOOLTIP_INSET * 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TOOLTIP_BACKGROUND, boxX, boxY, boxWidth, boxHeight, background);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TOOLTIP_FRAME, boxX, boxY, boxWidth, boxHeight, frame);
	}

	/** The colour of a Vanilla button, whose sprite is passed in with this tint. */
	public static int buttonColor(final int vanilla) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (config.buttonStyle == PartStyle.VANILLA || !customizationAvailable()) {
			return vanilla;
		}
		int tint = config.buttonStyle == PartStyle.CUSTOM
			? config.buttonColor & 0xFFFFFF
			: withBrightestChannel(InventoryTextureManager.panelColor(config), 0xFF);
		return vanilla & 0xFF000000
			| (vanilla >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255 << 16
			| (vanilla >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255 << 8
			| (vanilla & 0xFF) * (tint & 0xFF) / 255;
	}

	/** A container title stays readable on a panel made dark: Vanilla's grey would vanish on it. */
	public static int labelColor(final int color) {
		if (color != VANILLA_LABEL || !(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) {
			return color;
		}
		InventoryTweaksConfig config = ConfigStore.get();
		if (NextGenInventory.drawsOnDarkGlass(config)) {
			return LIGHT_LABEL;
		}
		return InventoryTextureManager.customizesContainerSurfaces(config) ? previewLabelColor(config) : color;
	}

	/** The title colour a panel with this configuration gets. */
	public static int previewLabelColor(final InventoryTweaksConfig config) {
		int panel = InventoryTextureManager.panelColor(config);
		double luminance = (0.2126 * (panel >> 16 & 0xFF) + 0.7152 * (panel >> 8 & 0xFF) + 0.0722 * (panel & 0xFF)) / 255.0;
		return luminance < DARK_PANEL_LUMINANCE ? LIGHT_LABEL : VANILLA_LABEL;
	}

	/** The same hue with its brightest channel at this value. */
	private static int withBrightestChannel(final int rgb, final int brightest) {
		int red = rgb >> 16 & 0xFF;
		int green = rgb >> 8 & 0xFF;
		int blue = rgb & 0xFF;
		int maximum = Math.max(1, Math.max(red, Math.max(green, blue)));
		return red * brightest / maximum << 16 | green * brightest / maximum << 8 | blue * brightest / maximum;
	}
}
