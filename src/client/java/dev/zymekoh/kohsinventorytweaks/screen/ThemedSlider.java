package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

abstract class ThemedSlider extends AbstractSliderButton {
	protected ThemedSlider(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final double initialValue
	) {
		super(x, y, width, height, message, initialValue);
	}

	@Override
	public void extractWidgetRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a
	) {
		int fill = this.active
			? (this.isHoveredOrFocused() ? UiTheme.GLASS_HOVER : UiTheme.GLASS_LIGHT)
			: 0x99211631;
		int border = this.isHoveredOrFocused() ? UiTheme.ACCENT_SOFT : UiTheme.BORDER_SOFT;
		if (this.active && this.isHoveredOrFocused()) {
			UiRender.glow(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, 24);
		}
		UiRender.panel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, fill, border);

		int trackX = this.getX() + 6;
		int trackY = this.getY() + this.getHeight() - 5;
		int trackWidth = Math.max(1, this.getWidth() - 12);
		graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 2, UiTheme.SCROLL_TRACK);
		int progress = (int) Math.round(this.value * trackWidth);
		graphics.fill(trackX, trackY, trackX + progress, trackY + 2, UiTheme.ACCENT);
		int handleX = trackX + progress;
		graphics.fill(handleX - 3, trackY - 3, handleX + 4, trackY + 4, UiTheme.ACCENT_BRIGHT);
		graphics.fill(handleX - 2, trackY - 2, handleX + 3, trackY + 3, UiTheme.ACCENT_DEEP);
		graphics.fill(handleX - 1, trackY - 1, handleX + 2, trackY + 2, UiTheme.ACCENT_BRIGHT);

		this.extractScrollingStringOverContents(
			graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE),
			this.getMessage(),
			3
		);
		this.handleCursor(graphics);
	}
}
