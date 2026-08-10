package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class HerziumButton extends Button {
	private final boolean installed;
	private final WidgetClip clip = new WidgetClip();

	public HerziumButton(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final OnPress onPress,
		final boolean installed
	) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.installed = installed;
	}

	public HerziumButton setClipBounds(final int left, final int top, final int right, final int bottom) {
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
	protected void renderContents(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
		this.clip.trackPointer(mouseX, mouseY);
		this.clip.begin(graphics);
		long time = System.nanoTime() / 1_000_000L;
		float pulse = 0.5F + 0.5F * (float) Math.sin(time / 280.0F);
		int fill = this.installed
			? (this.isHoveredOrFocused() ? UiTheme.GLASS_HOVER : 0xED35175A)
			: (this.isHoveredOrFocused() ? 0x75351D4A : 0x49160B27);
		int border = this.installed
			? ((170 + Math.round(pulse * 85)) << 24 | 0xA855F7)
			: 0x575C3B6B;
		if (this.installed) {
			UiRender.glow(
				graphics,
				this.getX(),
				this.getY(),
				this.getWidth(),
				this.getHeight(),
				6,
				18 + Math.round(pulse * 18)
			);
		}
		UiRender.panel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6, fill, border);

		if (this.installed) {
			for (int index = 0; index < 6; index++) {
				double angle = time / 360.0 + index * Math.PI / 3.0;
				int sparkleX = this.getX() + this.getWidth() / 2
					+ (int) Math.round(Math.cos(angle) * Math.max(6, this.getWidth() / 2.0 - 4));
				int sparkleY = this.getY() + this.getHeight() / 2
					+ (int) Math.round(Math.sin(angle) * Math.max(5, this.getHeight() / 2.0 - 2));
				int alpha = 120 + Math.round(pulse * 110);
				graphics.fill(sparkleX - 1, sparkleY - 1, sparkleX + 2, sparkleY + 2, alpha << 24 | 0xE9C7FF);
			}
		}

		var font = Minecraft.getInstance().font;
		if (this.getHeight() >= 30) {
			int iconX = this.getX() + 7;
			int iconY = this.getY() + this.getHeight() / 2 - 4;
			graphics.fill(iconX, iconY + 2, iconX + 8, iconY + 6, this.installed ? UiTheme.ACCENT_DEEP : UiTheme.BORDER_SOFT);
			graphics.fill(iconX + 2, iconY, iconX + 6, iconY + 8, this.installed ? UiTheme.ACCENT : UiTheme.TEXT_DISABLED);
			graphics.drawString(font, this.getMessage(), this.getX() + 23, this.getY() + 6, this.installed ? UiTheme.TEXT : UiTheme.TEXT_DISABLED, false);
			Component description = Component.translatable(this.installed
				? "screen.kohs_inventory_tweaks.herzium.description.installed"
				: "screen.kohs_inventory_tweaks.herzium.description.missing");
			String text = font.plainSubstrByWidth(description.getString(), Math.max(1, this.getWidth() - 30));
			graphics.drawString(font, text, this.getX() + 23, this.getY() + this.getHeight() - 12, UiTheme.TEXT_MUTED, false);
		} else {
			graphics.drawCenteredString(
				font,
				this.getMessage(),
				this.getX() + this.getWidth() / 2,
				this.getY() + (this.getHeight() - 8) / 2,
				this.installed ? UiTheme.TEXT : UiTheme.TEXT_DISABLED
			);
		}
		this.clip.end(graphics);
	}
}
