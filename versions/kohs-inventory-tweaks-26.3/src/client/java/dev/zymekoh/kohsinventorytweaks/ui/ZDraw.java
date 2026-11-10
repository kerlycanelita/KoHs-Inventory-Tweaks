package dev.zymekoh.kohsinventorytweaks.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawing primitives of the Zymekoh interface.
 *
 * <p>Panels are dark glass with cut corners: rounded enough to read as a surface,
 * sharp enough to feel like metal. Everything is plain fills in GUI space, so the
 * cost stays at a handful of quads per element and nothing allocates per frame.</p>
 */
public final class ZDraw {
	private ZDraw() {
	}

	/** A rectangle with its four corners cut diagonally by {@code cut} pixels. */
	public static void chamfer(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int cut,
		final int color
	) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int c = Math.max(0, Math.min(cut, Math.min(width, height) / 2));
		if (c == 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}
		graphics.fill(x, y + c, x + width, y + height - c, color);
		for (int row = 0; row < c; row++) {
			int inset = c - row;
			graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
			graphics.fill(x + inset, y + height - 1 - row, x + width - inset, y + height - row, color);
		}
	}

	/** Cut size that suits a surface of this size. */
	public static int cutFor(final int width, final int height) {
		return Math.max(2, Math.min(5, Math.min(width, height) / 7));
	}

	/**
	 * Dark glass: drop shadow, border, fill, a thin highlight along the top edge and
	 * a sharper glint on the top-left cut.
	 */
	public static void glass(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int fill,
		final int border
	) {
		int c = cutFor(width, height);
		chamfer(graphics, x - 1, y + 1, width + 2, height + 2, c + 1, ZTheme.SHADOW);
		chamfer(graphics, x, y, width, height, c, border);
		chamfer(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, c - 1), fill);
		if (width > c * 2 + 4 && height > 4) {
			graphics.fill(x + c + 1, y + 1, x + width - c - 1, y + 2, ZTheme.alpha(ZTheme.LILAC_PALE, 40));
			graphics.fill(x + c, y + height - 2, x + width - c, y + height - 1, ZTheme.alpha(0x000000, 70));
		}
		// The glint on the cut: one bright pixel per row along the diagonal.
		int glint = ZTheme.alpha(ZTheme.LILAC_PALE, 150);
		for (int step = 0; step < c; step++) {
			graphics.fill(x + c - step, y + step, x + c - step + 1, y + step + 1, glint);
		}
	}

	/** A soft violet aura around a surface, for hover and emphasis. */
	public static void glow(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int color,
		final int alpha
	) {
		if (alpha <= 0) {
			return;
		}
		int c = cutFor(width, height);
		chamfer(graphics, x - 3, y - 3, width + 6, height + 6, c + 3, ZTheme.alpha(color, alpha / 5));
		chamfer(graphics, x - 2, y - 2, width + 4, height + 4, c + 2, ZTheme.alpha(color, alpha / 3));
		chamfer(graphics, x - 1, y - 1, width + 2, height + 2, c + 1, ZTheme.alpha(color, alpha));
	}

	/**
	 * A soft aura drawn only around a rectangle, never inside it, for frames whose
	 * content must keep its own transparency.
	 */
	public static void halo(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int color,
		final int alpha
	) {
		for (int ring = 1; ring <= 3; ring++) {
			int a = alpha * (4 - ring) / 4;
			if (a <= 0) {
				continue;
			}
			int c = ZTheme.alpha(color, a);
			int left = x - ring;
			int top = y - ring;
			int right = x + width + ring;
			int bottom = y + height + ring;
			graphics.fill(left + 1, top, right - 1, top + 1, c);
			graphics.fill(left + 1, bottom - 1, right - 1, bottom, c);
			graphics.fill(left, top + 1, left + 1, bottom - 1, c);
			graphics.fill(right - 1, top + 1, right, bottom - 1, c);
		}
	}

	/**
	 * A one-pixel line with a bright segment travelling along it, like current
	 * through a blade. Static when motion is reduced.
	 */
	public static void energyLine(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int base,
		final int spark,
		final float periodSeconds
	) {
		if (width <= 0) {
			return;
		}
		graphics.fill(x, y, x + width, y + 1, base);
		if (ZMotion.reduced()) {
			return;
		}
		int travel = width + 48;
		int head = x - 24 + Math.round(ZMotion.cycle(periodSeconds) * travel);
		segment(graphics, head - 18, head - 6, y, x, width, ZTheme.fade(spark, 0.25F));
		segment(graphics, head - 6, head - 2, y, x, width, ZTheme.fade(spark, 0.6F));
		segment(graphics, head - 2, head + 2, y, x, width, spark);
		segment(graphics, head + 2, head + 6, y, x, width, ZTheme.fade(spark, 0.6F));
	}

	private static void segment(
		final GuiGraphicsExtractor graphics,
		final int from,
		final int to,
		final int y,
		final int clipX,
		final int clipWidth,
		final int color
	) {
		int start = Math.max(from, clipX);
		int end = Math.min(to, clipX + clipWidth);
		if (end > start) {
			graphics.fill(start, y, end, y + 1, color);
		}
	}

	/** A small diamond, the rune that marks selection and section heads. */
	public static void diamond(final GuiGraphicsExtractor graphics, final int centerX, final int centerY, final int radius, final int color) {
		for (int row = -radius; row <= radius; row++) {
			int half = radius - Math.abs(row);
			graphics.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
		}
	}

	/** A blade-like separator: thin line tapering to points, with a rune at its centre. */
	public static void blade(final GuiGraphicsExtractor graphics, final int centerX, final int y, final int halfWidth, final int color) {
		if (halfWidth <= 2) {
			return;
		}
		graphics.fill(centerX - halfWidth, y, centerX + halfWidth, y + 1, ZTheme.fade(color, 0.55F));
		graphics.fill(centerX - halfWidth * 2 / 3, y, centerX + halfWidth * 2 / 3, y + 1, color);
		diamond(graphics, centerX, y, 2, color);
	}

	/** Vertical scrollbar: a thin dark track with a bright thumb. */
	public static void scrollbar(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int top,
		final int height,
		final int scroll,
		final int maxScroll
	) {
		if (maxScroll <= 0 || height <= 8) {
			return;
		}
		int thumbHeight = Math.max(10, height * height / (height + maxScroll));
		int travel = Math.max(1, height - thumbHeight);
		int thumbY = top + Math.round(scroll * travel / (float) maxScroll);
		graphics.fill(x + 1, top, x + 2, top + height, ZTheme.SCROLL_TRACK);
		graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ZTheme.VIOLET_BRIGHT);
		graphics.fill(x + 1, thumbY + 1, x + 2, thumbY + thumbHeight - 1, ZTheme.LILAC_PALE);
	}

	/** Top and bottom fades telling the content continues past the viewport. */
	public static void scrollFade(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int top,
		final int width,
		final int height,
		final boolean fadeTop,
		final boolean fadeBottom,
		final int surface
	) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int fadeHeight = Math.min(14, Math.max(4, height / 4));
		int opaque = ZTheme.alpha(surface, 235);
		int clear = ZTheme.alpha(surface, 0);
		if (fadeTop) {
			graphics.fillGradient(x, top, x + width, top + fadeHeight, opaque, clear);
		}
		if (fadeBottom) {
			graphics.fillGradient(x, top + height - fadeHeight, x + width, top + height, clear, opaque);
		}
	}

	/** Four angular brackets around a frame, drawn with the corners of a viewfinder. */
	public static void brackets(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int length,
		final int color
	) {
		int l = Math.max(2, Math.min(length, Math.min(width, height) / 3));
		graphics.fill(x, y, x + l, y + 1, color);
		graphics.fill(x, y, x + 1, y + l, color);
		graphics.fill(x + width - l, y, x + width, y + 1, color);
		graphics.fill(x + width - 1, y, x + width, y + l, color);
		graphics.fill(x, y + height - 1, x + l, y + height, color);
		graphics.fill(x, y + height - l, x + 1, y + height, color);
		graphics.fill(x + width - l, y + height - 1, x + width, y + height, color);
		graphics.fill(x + width - 1, y + height - l, x + width, y + height, color);
	}
}
