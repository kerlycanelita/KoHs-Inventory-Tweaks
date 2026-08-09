package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class HerziumButton extends Button {
	private final boolean installed;

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

	@Override
	protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		long time = System.nanoTime() / 1_000_000L;
		float pulse = 0.5F + 0.5F * (float) Math.sin(time / 280.0F);
		int fill = this.installed
			? (this.isHoveredOrFocused() ? 0xFF7135A4 : 0xED4C1E78)
			: (this.isHoveredOrFocused() ? 0x75351D4A : 0x4920162B);
		int border = this.installed
			? ((170 + Math.round(pulse * 85)) << 24 | 0xB760FF)
			: 0x575C3B6B;
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

		graphics.centeredText(
			Minecraft.getInstance().font,
			this.getMessage(),
			this.getX() + this.getWidth() / 2,
			this.getY() + (this.getHeight() - 8) / 2,
			this.installed ? 0xFFFFFFFF : 0xFF816D8C
		);
	}
}
