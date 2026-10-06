package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZMotion;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import org.jspecify.annotations.Nullable;

/**
 * The interface's button: dark glass with cut corners and a variant-specific light.
 *
 * <p>Hover, focus, selection and press only change light and colour; the geometry is
 * fixed, so the drawn button and its hitbox are always the same rectangle.</p>
 */
public final class GlassButton extends Button {
	private static final long FLASH_NANOS = 140_000_000L;

	public enum Variant {
		NORMAL,
		PRIMARY,
		DANGER,
		TAB,
		TOGGLE,
		SWITCH
	}

	private final Variant variant;
	private final @Nullable BooleanSupplier selected;
	private final WidgetClip clip = new WidgetClip();
	private @Nullable Component subtitle;
	private @Nullable Component narrationLabel;
	private @Nullable KohsTabIcon icon;
	private float hoverAmount;
	private float selectAmount;
	private long lastRenderNanos;
	private long pressedAtNanos;

	public GlassButton(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final OnPress onPress,
		final Variant variant
	) {
		this(x, y, width, height, message, onPress, variant, null);
	}

	public GlassButton(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final OnPress onPress,
		final Variant variant,
		final @Nullable BooleanSupplier selected
	) {
		super(x, y, width, height, message, pressed -> {
			if (pressed instanceof GlassButton glass) {
				glass.pressedAtNanos = System.nanoTime();
			}
			onPress.onPress(pressed);
		}, DEFAULT_NARRATION);
		this.variant = variant;
		this.selected = selected;
	}

	public GlassButton setSubtitle(final Component subtitle) {
		this.subtitle = subtitle;
		return this;
	}

	public GlassButton setNarrationLabel(final Component label) {
		this.narrationLabel = label;
		return this;
	}

	public GlassButton setIcon(final @Nullable KohsTabIcon icon) {
		this.icon = icon;
		return this;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return this.narrationLabel == null ? super.createNarrationMessage()
			: wrapDefaultNarrationMessage(this.narrationLabel.copy().append(": ").append(this.getMessage()));
	}

	public GlassButton setClipBounds(final int left, final int top, final int right, final int bottom) {
		this.clip.set(left, top, right, bottom);
		return this;
	}

	@Override
	public boolean isMouseOver(final double x, final double y) {
		return this.clip.contains(x, y) && super.isMouseOver(x, y);
	}

	@Override
	public boolean isHovered() {
		return this.clip.permitsHover(super.isHovered());
	}

	@Override
	protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.clip.trackPointer(mouseX, mouseY);
		this.clip.begin(graphics);
		boolean isSelected = this.selected != null && this.selected.getAsBoolean();
		boolean highlighted = this.active && this.isHoveredOrFocused();
		long now = System.nanoTime();
		float deltaSeconds = this.lastRenderNanos == 0L
			? 1.0F / 60.0F
			: Math.min(0.1F, Math.max(0.0F, (now - this.lastRenderNanos) / 1_000_000_000.0F));
		this.hoverAmount = ZMotion.approach(this.hoverAmount, highlighted ? 1.0F : 0.0F, 14.0F, deltaSeconds);
		this.selectAmount = this.lastRenderNanos == 0L
			? (isSelected ? 1.0F : 0.0F)
			: ZMotion.approach(this.selectAmount, isSelected ? 1.0F : 0.0F, 18.0F, deltaSeconds);
		this.lastRenderNanos = now;
		float flash = this.pressedAtNanos == 0L ? 0.0F : 1.0F - ZMotion.progress(this.pressedAtNanos, FLASH_NANOS);

		// Geometry never moves: hover, selection and press change light, not position,
		// so what the pointer sees is exactly what it can click.
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();
		int text = this.active ? ZTheme.TEXT : ZTheme.TEXT_DISABLED;
		int fill;
		int border;
		int aura = ZTheme.VIOLET_BRIGHT;
		switch (this.variant) {
			case PRIMARY -> {
				fill = ZTheme.mix(0xF06D28D9, 0xFF9333EA, this.hoverAmount);
				border = ZTheme.mix(ZTheme.LILAC, ZTheme.LILAC_PALE, this.hoverAmount);
			}
			case DANGER -> {
				fill = ZTheme.mix(0xD2360A1E, 0xE8581030, this.hoverAmount);
				border = ZTheme.mix(0xC0C8193F, ZTheme.CRIMSON_BRIGHT, this.hoverAmount);
				aura = ZTheme.CRIMSON_BRIGHT;
			}
			case SWITCH -> {
				fill = ZTheme.mix(ZTheme.mix(ZTheme.SURFACE_LIGHT, ZTheme.SURFACE_HOVER, this.hoverAmount), ZTheme.SURFACE_SELECTED, this.selectAmount);
				border = ZTheme.mix(ZTheme.mix(ZTheme.BORDER_SOFT, ZTheme.BORDER, this.hoverAmount), ZTheme.LILAC, this.selectAmount);
			}
			default -> {
				fill = ZTheme.mix(ZTheme.mix(ZTheme.SURFACE_LIGHT, ZTheme.SURFACE_HOVER, this.hoverAmount), ZTheme.SURFACE_SELECTED, this.selectAmount);
				border = ZTheme.mix(ZTheme.mix(ZTheme.BORDER_SOFT, ZTheme.BORDER_HOT, this.hoverAmount * 0.7F), ZTheme.LILAC, this.selectAmount);
			}
		}
		if (!this.active) {
			fill = 0x9A1A1224;
			border = ZTheme.alpha(ZTheme.BORDER_SOFT, 80);
		}

		if (this.active && this.hoverAmount > 0.02F) {
			ZDraw.glow(graphics, x, y, width, height, aura, Math.round(46 * this.hoverAmount));
		}
		ZDraw.glass(graphics, x, y, width, height, fill, border);
		if (this.active) {
			this.drawAccent(graphics, x, y, width, height);
		}
		if (flash > 0.0F) {
			int flashColor = this.variant == Variant.DANGER ? ZTheme.CRIMSON_BRIGHT : ZTheme.LILAC_PALE;
			ZDraw.chamfer(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, ZDraw.cutFor(width, height) - 1),
				ZTheme.alpha(flashColor, Math.round(90 * flash)));
		}
		if (this.variant == Variant.SWITCH) {
			this.renderSwitch(graphics, x, y, text);
		} else {
			this.renderText(graphics, x, y, text);
		}
		this.clip.end(graphics);
	}

	/** The variant's signature light: a blade of current, a selection bar or a rune. */
	private void drawAccent(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height) {
		switch (this.variant) {
			case PRIMARY -> {
				graphics.fill(x + 3, y + height - 3, x + width - 3, y + height - 2, ZTheme.alpha(ZTheme.LILAC_PALE, 90));
				if (this.hoverAmount > 0.05F) {
					ZDraw.energyLine(graphics, x + 3, y + height - 3, width - 6,
						ZTheme.alpha(ZTheme.LILAC_PALE, 0), ZTheme.fade(ZTheme.TEXT, this.hoverAmount), 1.1F);
				}
			}
			case DANGER -> graphics.fill(x + 3, y + height - 3, x + width - 3, y + height - 2,
				ZTheme.alpha(ZTheme.CRIMSON_BRIGHT, Math.round(60 + 110 * this.hoverAmount)));
			case TAB, NORMAL -> {
				// A bar on the leading edge that grows with hover and fills when selected.
				float amount = Math.max(this.selectAmount, this.hoverAmount * 0.65F);
				if (amount > 0.02F && height > 8) {
					int barHeight = Math.max(2, Math.round((height - 8) * amount));
					int top = y + (height - barHeight) / 2;
					graphics.fill(x + 2, top, x + 4, top + barHeight, ZTheme.mix(ZTheme.VIOLET_BRIGHT, ZTheme.LILAC_PALE, this.selectAmount));
				}
				if (this.selectAmount > 0.5F && width > 40) {
					ZDraw.diamond(graphics, x + width - 7, y + height / 2, 2, ZTheme.fade(ZTheme.LILAC_PALE, this.selectAmount));
				}
			}
			case TOGGLE -> {
				if (this.selectAmount > 0.02F) {
					int lineWidth = Math.round((width - 10) * this.selectAmount);
					int left = x + (width - lineWidth) / 2;
					graphics.fill(left, y + height - 3, left + lineWidth, y + height - 2, ZTheme.LILAC_PALE);
				}
			}
			case SWITCH -> {
			}
		}
	}

	/** A track and knob beside the state name, so the state reads by shape and by word. */
	private void renderSwitch(final GuiGraphicsExtractor graphics, final int x, final int y, final int textColor) {
		Font font = Minecraft.getInstance().font;
		int trackWidth = 18;
		int trackHeight = 10;
		int gap = 5;
		int available = Math.max(1, this.getWidth() - 12);
		boolean showTrack = available >= trackWidth + gap + 12;
		int labelSpace = showTrack ? available - trackWidth - gap : available;
		Component label = truncate(font, this.getMessage(), labelSpace);
		int groupWidth = (showTrack ? trackWidth + gap : 0) + font.width(label);
		int left = x + (this.getWidth() - groupWidth) / 2;
		int centerY = y + this.getHeight() / 2;
		if (showTrack) {
			int top = centerY - trackHeight / 2;
			int trackFill = this.active
				? ZTheme.mix(0xE0241634, ZTheme.VIOLET, this.selectAmount)
				: 0xB0201828;
			int trackBorder = this.active
				? ZTheme.mix(ZTheme.alpha(ZTheme.BORDER_SOFT, 200), ZTheme.LILAC_PALE, this.selectAmount)
				: ZTheme.alpha(ZTheme.BORDER_SOFT, 90);
			ZDraw.chamfer(graphics, left, top, trackWidth, trackHeight, 2, trackBorder);
			ZDraw.chamfer(graphics, left + 1, top + 1, trackWidth - 2, trackHeight - 2, 1, trackFill);
			int knobSize = trackHeight - 4;
			int knobX = left + 2 + Math.round((trackWidth - 4 - knobSize) * this.selectAmount);
			int knob = this.active ? ZTheme.mix(ZTheme.SILVER, ZTheme.TEXT, this.selectAmount) : ZTheme.TEXT_DISABLED;
			graphics.fill(knobX, top + 2, knobX + knobSize, top + 2 + knobSize, knob);
			if (this.active && this.selectAmount > 0.5F) {
				graphics.fill(knobX + 1, top + 3, knobX + knobSize - 1, top + 1 + knobSize, ZTheme.fade(ZTheme.CYAN, this.selectAmount));
			}
			left += trackWidth + gap;
		}
		graphics.text(font, label, left, centerY - 4, textColor, false);
	}

	private void renderText(final GuiGraphicsExtractor graphics, final int x, final int y, final int textColor) {
		Font font = Minecraft.getInstance().font;
		boolean showSubtitle = this.subtitle != null && this.getHeight() >= 30;
		int iconSize = showSubtitle || this.getHeight() >= 22 ? 16 : Math.max(8, Math.min(12, this.getHeight() - 6));
		int textInset = this.icon == null ? (showSubtitle ? 23 : 5) : iconSize + 10;
		int maximumTextWidth = Math.max(1, this.getWidth() - textInset - 5);
		if (!showSubtitle && this.getHeight() >= 22 && font.width(this.getMessage()) > maximumTextWidth
			&& wordsFit(font, this.getMessage().getString(), maximumTextWidth)) {
			// Two whole lines read better than one cut with an ellipsis; a word cut in
			// half reads worse than both, so a word too long for the line truncates.
			List<FormattedCharSequence> lines = font.split(this.getMessage(), maximumTextWidth);
			if (lines.size() == 2) {
				int lineWidth = Math.max(font.width(lines.get(0)), font.width(lines.get(1)));
				int contentWidth = lineWidth + (this.icon == null ? 0 : iconSize + 4);
				int contentX = x + (this.getWidth() - contentWidth) / 2;
				if (this.icon != null) {
					this.drawIcon(graphics, contentX, y + (this.getHeight() - iconSize) / 2, iconSize);
					contentX += iconSize + 4;
				}
				int textY = y + (this.getHeight() - 18) / 2;
				graphics.text(font, lines.get(0), contentX, textY, textColor, false);
				graphics.text(font, lines.get(1), contentX, textY + 10, textColor, false);
				return;
			}
		}
		Component title = truncate(font, this.getMessage(), maximumTextWidth);
		if (!showSubtitle) {
			int titleWidth = font.width(title);
			int contentWidth = titleWidth + (this.icon == null ? 0 : iconSize + 4);
			int contentX = x + (this.getWidth() - contentWidth) / 2;
			if (this.icon != null) {
				this.drawIcon(graphics, contentX, y + (this.getHeight() - iconSize) / 2, iconSize);
				contentX += iconSize + 4;
			}
			graphics.text(font, title, contentX, y + (this.getHeight() - 8) / 2, textColor, false);
			return;
		}
		if (this.icon != null) {
			this.drawIcon(graphics, x + 7, y + (this.getHeight() - iconSize) / 2, iconSize);
		} else {
			ZDraw.diamond(graphics, x + 11, y + this.getHeight() / 2, 3, this.active ? ZTheme.VIOLET_BRIGHT : ZTheme.TEXT_DISABLED);
		}
		graphics.text(font, title, x + textInset, y + 6, textColor, false);
		graphics.text(
			font,
			truncate(font, this.subtitle, maximumTextWidth),
			x + textInset,
			y + this.getHeight() - 12,
			ZTheme.TEXT_MUTED,
			false
		);
	}

	private void drawIcon(final GuiGraphicsExtractor graphics, final int x, final int y, final int size) {
		this.icon.draw(graphics, x, y, size, Math.max(this.hoverAmount, this.selectAmount), this.active);
	}

	private static boolean wordsFit(final Font font, final String text, final int width) {
		for (String word : text.split(" ")) {
			if (font.width(word) > width) {
				return false;
			}
		}
		return true;
	}

	private static Component truncate(final Font font, final Component component, final int maximumWidth) {
		if (font.width(component) <= maximumWidth) {
			return component;
		}
		String ellipsis = "\u2026";
		return Component.literal(
			font.plainSubstrByWidth(component.getString(), Math.max(1, maximumWidth - font.width(ellipsis))) + ellipsis
		);
	}
}
