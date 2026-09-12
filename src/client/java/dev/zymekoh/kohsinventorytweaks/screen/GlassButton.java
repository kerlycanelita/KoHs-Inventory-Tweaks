package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

public final class GlassButton extends Button {
	private static final long ENTRANCE_DURATION_NANOS = 240_000_000L;
	private static final float HOVER_SPEED = 0.18F;

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
	private final long createdAtNanos = System.nanoTime();
	private final WidgetClip clip = new WidgetClip();
	private @Nullable Component subtitle;
	private @Nullable Component narrationLabel;
	private float hoverAmount;

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
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
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
		boolean highlighted = this.isHoveredOrFocused();
		this.hoverAmount += ((highlighted ? 1.0F : 0.0F) - this.hoverAmount) * HOVER_SPEED;
		float entrance = cubicOut(clamp01((System.nanoTime() - this.createdAtNanos) / (float) ENTRANCE_DURATION_NANOS));
		boolean stableSwitch = this.variant == Variant.SWITCH;
		float scale = stableSwitch ? 1.0F : 0.965F + entrance * 0.035F;
		int lift = !stableSwitch && this.active && this.hoverAmount > 0.55F ? -1 : 0;
		int x = this.getX();
		int y = this.getY() + lift;

		int fill = isSelected ? UiTheme.GLASS_SELECTED : UiTheme.GLASS_LIGHT;
		int border = isSelected ? UiTheme.ACCENT_SOFT : UiTheme.BORDER_SOFT;
		int text = UiTheme.TEXT;
		if (this.variant == Variant.SWITCH) {
			fill = isSelected
				? blend(0xF05B2586, 0xFF7C3AED, this.hoverAmount)
				: blend(0xCF24182F, 0xE650244B, this.hoverAmount);
			border = isSelected ? UiTheme.ACCENT_BRIGHT : 0xFFC76A92;
			text = isSelected ? UiTheme.TEXT : 0xFFFFD8E8;
		} else if (this.variant == Variant.PRIMARY) {
			fill = blend(0xF07C3AED, 0xFFA855F7, this.hoverAmount);
			border = UiTheme.ACCENT_BRIGHT;
		} else if (this.variant == Variant.DANGER) {
			fill = blend(0xC650244B, 0xE1692E62, this.hoverAmount);
			border = 0xD9FF78A5;
		} else if (highlighted || this.hoverAmount > 0.01F) {
			fill = blend(fill, UiTheme.GLASS_HOVER, this.hoverAmount);
			border = this.variant == Variant.TAB || this.variant == Variant.TOGGLE
				? UiTheme.ACCENT_SOFT
				: blend(UiTheme.BORDER, UiTheme.ACCENT_SOFT, this.hoverAmount);
		}
		if (!this.active) {
			fill = 0x99211631;
			border = UiTheme.BORDER_SOFT;
			text = UiTheme.TEXT_DISABLED;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(x + this.getWidth() / 2.0F, y + this.getHeight() / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-(x + this.getWidth() / 2.0F), -(y + this.getHeight() / 2.0F));
		if (!stableSwitch && this.active && this.hoverAmount > 0.02F) {
			UiRender.glow(graphics, x, y, this.getWidth(), this.getHeight(), 6, (int) (34 * this.hoverAmount));
		}
		UiRender.panel(graphics, x, y, this.getWidth(), this.getHeight(), 6, fill, border);
		if (this.variant == Variant.SWITCH && this.active) {
			int centerY = y + this.getHeight() / 2;
			int marker = isSelected ? UiTheme.ACCENT_BRIGHT : 0xFFFF789A;
			graphics.fill(x + 6, centerY - 4, x + 10, centerY + 4, marker);
			graphics.fill(x + 10, centerY - 2, x + 12, centerY + 2, marker);
		}
		renderText(graphics, x, y, text);
		graphics.pose().popMatrix();
		this.clip.end(graphics);
	}

	private void renderText(final GuiGraphicsExtractor graphics, final int x, final int y, final int textColor) {
		Font font = Minecraft.getInstance().font;
		boolean showSubtitle = this.subtitle != null && this.getHeight() >= 30 && this.variant != Variant.SWITCH;
		int textInset = showSubtitle ? 23 : (this.variant == Variant.SWITCH ? 20 : 5);
		int maximumTextWidth = Math.max(1, this.getWidth() - textInset - 5);
		Component title = truncate(font, this.getMessage(), maximumTextWidth);
		if (!showSubtitle) {
			graphics.centeredText(font, title, x + this.getWidth() / 2, y + (this.getHeight() - 8) / 2, textColor);
			return;
		}

		int iconX = x + 7;
		int iconY = y + this.getHeight() / 2 - 4;
		graphics.fill(iconX, iconY + 2, iconX + 8, iconY + 6, UiTheme.ACCENT_DEEP);
		graphics.fill(iconX + 2, iconY, iconX + 6, iconY + 8, UiTheme.ACCENT);
		graphics.fill(iconX + 3, iconY + 1, iconX + 5, iconY + 3, UiTheme.ACCENT_BRIGHT);
		graphics.text(font, title, x + textInset, y + 6, textColor, false);
		graphics.text(
			font,
			truncate(font, this.subtitle, maximumTextWidth),
			x + textInset,
			y + this.getHeight() - 12,
			UiTheme.TEXT_MUTED,
			false
		);
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

	private static int blend(final int from, final int to, final float amount) {
		float t = clamp01(amount);
		int alpha = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int red = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
		int green = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
		int blue = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static float cubicOut(final float value) {
		float inverse = 1.0F - value;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float clamp01(final float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}
}
