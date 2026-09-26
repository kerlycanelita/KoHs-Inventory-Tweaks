package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shows a control's description once the pointer rests on it, instead of printing
 * it inside the control.
 *
 * <p>Option cards stay one line tall and never truncate their explanation; the
 * full text appears in a tooltip after a short, time-based delay so sweeping the
 * pointer across a list does not flash every description.</p>
 */
final class HoverDescription {
	private static final long DELAY_NANOS = 250_000_000L;

	private Object target;
	private long since;

	/** Call once per frame with what is under the pointer, or null for nothing. */
	boolean settled(final Object hovered) {
		long now = System.nanoTime();
		if (hovered != this.target) {
			this.target = hovered;
			this.since = now;
		}
		return hovered != null && now - this.since >= DELAY_NANOS;
	}

	static void show(
		final GuiGraphics graphics,
		final Font font,
		final Component text,
		final int mouseX,
		final int mouseY,
		final int maximumWidth
	) {
		graphics.setTooltipForNextFrame(font.split(text, Math.max(120, maximumWidth)), mouseX, mouseY);
	}
}
