package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiRender {
	private UiRender() {
	}

	public static void panel(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int radius,
		final int fill,
		final int border
	) {
		roundedRect(graphics, x - 1, y + 1, width + 2, height + 2, radius + 1, UiTheme.SHADOW);
		roundedRect(graphics, x, y, width, height, radius, border);
		roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
		int highlightInset = Math.max(2, radius);
		if (width > highlightInset * 2 + 1 && height > 3) {
			graphics.fill(
				x + highlightInset,
				y + 1,
				x + width - highlightInset,
				y + 2,
				withAlpha(UiTheme.ACCENT_BRIGHT, 34)
			);
		}
	}

	public static void glow(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int radius,
		final int alpha
	) {
		if (alpha <= 0) {
			return;
		}
		roundedRect(graphics, x - 2, y - 2, width + 4, height + 4, radius + 2, withAlpha(UiTheme.ACCENT, alpha / 3));
		roundedRect(graphics, x - 1, y - 1, width + 2, height + 2, radius + 1, withAlpha(UiTheme.ACCENT_BRIGHT, alpha));
	}

	public static void roundedRect(
		final GuiGraphicsExtractor graphics,
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

	public static void crosshair(final GuiGraphicsExtractor graphics, final int x, final int y, final boolean custom) {
		int color = custom ? UiTheme.ACCENT : UiTheme.WARNING;
		graphics.fill(x - 7, y - 1, x + 8, y + 2, 0xA0000000);
		graphics.fill(x - 1, y - 7, x + 2, y + 8, 0xA0000000);
		graphics.fill(x - 6, y, x + 7, y + 1, color);
		graphics.fill(x, y - 6, x + 1, y + 7, color);
		graphics.outline(x - 4, y - 4, 9, 9, 0xDFFFFFFF);
	}

	public static int withAlpha(final int color, final int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
	}

	public static void scrollFade(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int top,
		final int width,
		final int height,
		final boolean fadeTop,
		final boolean fadeBottom
	) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int fadeHeight = Math.min(12, Math.max(4, height / 4));
		int opaque = 0xE6160B27;
		int clear = 0x00160B27;
		if (fadeTop) {
			graphics.fillGradient(x, top, x + width, top + fadeHeight, opaque, clear);
		}
		if (fadeBottom) {
			graphics.fillGradient(x, top + height - fadeHeight, x + width, top + height, clear, opaque);
		}
	}
}
