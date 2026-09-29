package dev.zymekoh.kohsinventorytweaks.ui;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Titles, headers and the opening veil shared by every screen, so each one reads as
 * part of the same interface. All of it is decoration drawn around fixed layout:
 * nothing here moves a control or its hitbox.
 */
public final class ZChrome {
	private static @Nullable Component lastTitle;
	private static Component lastHeading = Component.empty();

	private ZChrome() {
	}

	/**
	 * The screen title: bold capitals between two lines of current that end in runes,
	 * with an optional muted subtitle underneath.
	 */
	public static void screenTitle(
		final GuiGraphics graphics,
		final Font font,
		final Component title,
		final @Nullable Component subtitle,
		final int centerX,
		final int y,
		final int maxWidth
	) {
		Component heading = heading(title);
		int titleWidth = font.width(heading);
		graphics.drawCenteredString(font, heading, centerX, y, ZTheme.TEXT);
		int lineLength = Math.max(0, Math.min(96, maxWidth / 2 - titleWidth / 2 - 18));
		if (lineLength >= 12) {
			int lineY = y + 4;
			int leftEnd = centerX - titleWidth / 2 - 8;
			int rightStart = centerX + (titleWidth + 1) / 2 + 8;
			ZDraw.energyLine(graphics, leftEnd - lineLength, lineY, lineLength,
				ZTheme.alpha(ZTheme.VIOLET_BRIGHT, 120), ZTheme.LILAC_PALE, 3.4F);
			ZDraw.energyLine(graphics, rightStart, lineY, lineLength,
				ZTheme.alpha(ZTheme.VIOLET_BRIGHT, 120), ZTheme.LILAC_PALE, 3.4F);
			ZDraw.diamond(graphics, leftEnd - lineLength - 3, lineY, 2, ZTheme.LILAC);
			ZDraw.diamond(graphics, rightStart + lineLength + 2, lineY, 2, ZTheme.LILAC);
		}
		if (subtitle != null) {
			graphics.drawCenteredString(font, subtitle, centerX, y + 13, ZTheme.TEXT_MUTED);
		}
	}

	/** A panel header: rune, title and a line of current along the header's base. */
	public static void panelHeader(
		final GuiGraphics graphics,
		final Font font,
		final Component title,
		final int x,
		final int y,
		final int width,
		final int lineY
	) {
		ZDraw.diamond(graphics, x + 2, y + 3, 2, ZTheme.VIOLET_BRIGHT);
		graphics.drawString(font, title, x + 9, y, ZTheme.TEXT);
		if (width > 16) {
			ZDraw.energyLine(graphics, x, lineY, width, ZTheme.BORDER_SOFT, ZTheme.fade(ZTheme.LILAC, 0.85F), 4.2F);
		}
	}

	/** A section label centred over its column, with a blade underneath. */
	public static void sectionTitle(
		final GuiGraphics graphics,
		final Font font,
		final Component title,
		final int centerX,
		final int y,
		final int width
	) {
		graphics.drawCenteredString(font, title, centerX, y, ZTheme.TEXT_MUTED);
		ZDraw.blade(graphics, centerX, y + 12, Math.max(3, Math.min(44, width / 3)), ZTheme.alpha(ZTheme.LILAC, 170));
	}

	/**
	 * The opening of a screen: a veil lifting off the whole screen while a line of
	 * light spreads from the centre of the top edge. Over by the time the eye settles.
	 */
	public static void openingVeil(final GuiGraphics graphics, final int width, final int height, final float progress) {
		if (progress >= 1.0F) {
			return;
		}
		float eased = ZMotion.easeOutCubic(progress);
		graphics.fill(0, 0, width, height, ZTheme.alpha(ZTheme.VOID, Math.round((1.0F - eased) * 120.0F)));
		int spread = Math.max(1, Math.round(width * eased));
		int alpha = Math.round((1.0F - eased) * 150.0F);
		graphics.fill((width - spread) / 2, 0, (width + spread) / 2, 1, ZTheme.alpha(ZTheme.LILAC_PALE, alpha));
		graphics.fill((width - spread) / 2, 1, (width + spread) / 2, 2, ZTheme.alpha(ZTheme.VIOLET_BRIGHT, alpha / 2));
	}

	/** A panel appearing over the screen: the dim deepens and a slash crosses its header. */
	public static void panelOpening(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final float progress
	) {
		if (progress >= 1.0F || width <= 0 || height <= 0) {
			return;
		}
		float eased = ZMotion.easeOutCubic(progress);
		int head = x + Math.round(width * eased);
		int alpha = Math.round((1.0F - eased) * 190.0F);
		int length = Math.min(width, 64);
		for (int step = 0; step < length; step += 2) {
			int sx = head - step;
			if (sx < x || sx >= x + width) {
				continue;
			}
			int a = alpha * (length - step) / length;
			graphics.fill(sx, y, sx + 2, y + 2, ZTheme.alpha(ZTheme.LILAC_PALE, a));
		}
		graphics.fill(x, y, x + width, y + height, ZTheme.alpha(ZTheme.VOID, Math.round((1.0F - eased) * 70.0F)));
	}

	private static Component heading(final Component title) {
		if (title != lastTitle) {
			lastTitle = title;
			lastHeading = Component.literal(title.getString().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
		}
		return lastHeading;
	}
}
