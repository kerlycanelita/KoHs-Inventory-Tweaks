package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class GlassButton extends Button {
	public enum Variant {
		NORMAL,
		PRIMARY,
		DANGER,
		TAB,
		TOGGLE
	}

	private final Variant variant;
	private final @Nullable BooleanSupplier selected;

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

	@Override
	protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		boolean isSelected = this.selected != null && this.selected.getAsBoolean();
		boolean highlighted = this.isHoveredOrFocused();
		int fill = isSelected ? UiTheme.GLASS_SELECTED : UiTheme.GLASS_LIGHT;
		int border = isSelected ? UiTheme.ACCENT_SOFT : UiTheme.BORDER_SOFT;
		int text = UiTheme.TEXT;
		if (this.variant == Variant.PRIMARY) {
			fill = highlighted ? 0xFF7B38B5 : 0xF2642C99;
			border = 0xFFD29AFF;
		} else if (this.variant == Variant.DANGER) {
			fill = highlighted ? 0xE1692E62 : 0xC650244B;
			border = 0xD9FF78A5;
		} else if (highlighted) {
			fill = UiTheme.GLASS_HOVER;
			border = this.variant == Variant.TAB || this.variant == Variant.TOGGLE ? UiTheme.ACCENT_SOFT : UiTheme.BORDER;
		}
		if (!this.active) {
			fill = 0x99211631;
			border = UiTheme.BORDER_SOFT;
			text = UiTheme.TEXT_DISABLED;
		}

		UiRender.panel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, fill, border);
		graphics.centeredText(
			Minecraft.getInstance().font,
			this.getMessage(),
			this.getX() + this.getWidth() / 2,
			this.getY() + (this.getHeight() - 8) / 2,
			text
		);
	}
}
