package dev.zymekoh.kohsinventorytweaks.screen;

import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class ColorPaletteWidget extends AbstractWidget {
	private static final int COLUMNS = 12;
	private static final int ROWS = 5;
	private static final int[] COLORS = createColors();
	private final IntSupplier color;
	private final IntConsumer onColorChanged;
	private final WidgetClip clip = new WidgetClip();

	public ColorPaletteWidget(
		final int x,
		final int y,
		final int width,
		final int height,
		final Component message,
		final IntSupplier color,
		final IntConsumer onColorChanged
	) {
		super(x, y, width, height, message);
		this.color = color;
		this.onColorChanged = onColorChanged;
	}

	public ColorPaletteWidget setClipBounds(final int left, final int top, final int right, final int bottom) {
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
	protected void renderWidget(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float a
	) {
		this.clip.trackPointer(mouseX, mouseY);
		this.clip.begin(graphics);
		UiRender.panel(
			graphics,
			this.getX(),
			this.getY(),
			this.getWidth(),
			this.getHeight(),
			5,
			UiTheme.GLASS_LIGHT,
			this.isHoveredOrFocused() ? UiTheme.ACCENT_SOFT : UiTheme.BORDER_SOFT
		);

		int previewHeight = 8;
		graphics.fill(
			this.getX() + 4,
			this.getY() + 4,
			this.getRight() - 4,
			this.getY() + previewHeight,
			0xFF000000 | this.color.getAsInt() & 0xFFFFFF
		);
		int gridX = this.getX() + 4;
		int gridY = this.getY() + previewHeight + 3;
		int gridWidth = Math.max(1, this.getWidth() - 8);
		int gridHeight = Math.max(1, this.getHeight() - previewHeight - 7);
		int selected = this.color.getAsInt() & 0xFFFFFF;
		for (int row = 0; row < ROWS; row++) {
			int y0 = gridY + row * gridHeight / ROWS;
			int y1 = gridY + (row + 1) * gridHeight / ROWS;
			for (int column = 0; column < COLUMNS; column++) {
				int x0 = gridX + column * gridWidth / COLUMNS;
				int x1 = gridX + (column + 1) * gridWidth / COLUMNS;
				int swatch = COLORS[row * COLUMNS + column];
				graphics.fill(x0, y0, x1, y1, 0xFF000000 | swatch);
				if (swatch == selected) {
					UiRender.outline(graphics, x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), 0xFFFFFFFF);
				}
			}
		}
		this.clip.end(graphics);
	}

	@Override
	public void onClick(final double mouseX, final double mouseY) {
		int previewHeight = 8;
		int gridX = this.getX() + 4;
		int gridY = this.getY() + previewHeight + 3;
		int gridWidth = Math.max(1, this.getWidth() - 8);
		int gridHeight = Math.max(1, this.getHeight() - previewHeight - 7);
		if (mouseX < gridX || mouseX >= gridX + gridWidth || mouseY < gridY || mouseY >= gridY + gridHeight) {
			return;
		}
		int column = Math.min(COLUMNS - 1, (int) ((mouseX - gridX) * COLUMNS / gridWidth));
		int row = Math.min(ROWS - 1, (int) ((mouseY - gridY) * ROWS / gridHeight));
		this.onColorChanged.accept(COLORS[row * COLUMNS + column]);
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.getMessage());
		output.add(NarratedElementType.HINT, Component.translatable("screen.kohs_inventory_tweaks.palette.hint"));
	}

	private static int[] createColors() {
		int[] colors = new int[COLUMNS * ROWS];
		for (int column = 0; column < COLUMNS; column++) {
			float hue = column / (float) COLUMNS;
			colors[column] = Color.HSBtoRGB(hue, 0.55F, 1.0F) & 0xFFFFFF;
			colors[COLUMNS + column] = Color.HSBtoRGB(hue, 0.9F, 1.0F) & 0xFFFFFF;
			colors[COLUMNS * 2 + column] = Color.HSBtoRGB(hue, 0.82F, 0.78F) & 0xFFFFFF;
			colors[COLUMNS * 3 + column] = Color.HSBtoRGB(hue, 0.72F, 0.52F) & 0xFFFFFF;
			int gray = Math.round(column * 255.0F / (COLUMNS - 1));
			colors[COLUMNS * 4 + column] = gray << 16 | gray << 8 | gray;
		}
		return colors;
	}
}
