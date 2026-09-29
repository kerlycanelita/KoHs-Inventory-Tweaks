package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import net.minecraft.client.gui.GuiGraphics;

public final class UiRender {
	private UiRender() {
	}

	/** Dark glass with cut corners; {@code radius} is kept for callers and ignored. */
	public static void panel(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int radius,
		final int fill,
		final int border
	) {
		ZDraw.glass(graphics, x, y, width, height, fill, border);
	}

	public static void glow(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int radius,
		final int alpha
	) {
		ZDraw.glow(graphics, x, y, width, height, UiTheme.ACCENT, alpha);
	}

	public static void roundedRect(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int radius,
		final int color
	) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		if (r == 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}

		graphics.fill(x + r, y, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int dy = 0; dy < r; dy++) {
			int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy));
			graphics.fill(x + r - dx, y + dy, x + width - r + dx, y + dy + 1, color);
			graphics.fill(x + r - dx, y + height - dy - 1, x + width - r + dx, y + height - dy, color);
		}
	}

	/** The landing marker: cyan for a chosen point, lilac for the vanilla centre. */
	public static void crosshair(final GuiGraphics graphics, final int x, final int y, final boolean custom) {
		int color = custom ? UiTheme.SPEED : UiTheme.ACCENT_BRIGHT;
		graphics.fill(x - 8, y - 1, x + 9, y + 2, 0xB0000000);
		graphics.fill(x - 1, y - 8, x + 2, y + 9, 0xB0000000);
		graphics.fill(x - 7, y, x - 2, y + 1, color);
		graphics.fill(x + 3, y, x + 8, y + 1, color);
		graphics.fill(x, y - 7, x + 1, y - 2, color);
		graphics.fill(x, y + 3, x + 1, y + 8, color);
		graphics.fill(x, y, x + 1, y + 1, ZTheme.TEXT);
		ZDraw.brackets(graphics, x - 5, y - 5, 11, 11, 3, withAlpha(color, 200));
	}

	public static int withAlpha(final int color, final int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
	}

	public static void scrollFade(
		final GuiGraphics graphics,
		final int x,
		final int top,
		final int width,
		final int height,
		final boolean fadeTop,
		final boolean fadeBottom
	) {
		ZDraw.scrollFade(graphics, x, top, width, height, fadeTop, fadeBottom, UiTheme.GLASS);
	}
}
