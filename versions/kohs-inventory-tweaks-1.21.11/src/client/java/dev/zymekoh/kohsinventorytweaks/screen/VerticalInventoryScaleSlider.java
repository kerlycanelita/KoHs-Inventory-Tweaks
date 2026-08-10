package dev.zymekoh.kohsinventorytweaks.screen;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A true vertical scale control: the top is larger and the bottom is smaller. */
public final class VerticalInventoryScaleSlider extends AbstractSliderButton {
	private static final int TRACK_MARGIN = 7;
	private final double maximumScale;
	private final DoubleConsumer valueConsumer;

	public VerticalInventoryScaleSlider(
		final int x,
		final int y,
		final int width,
		final int height,
		final double maximumScale,
		final double initialScale,
		final DoubleConsumer valueConsumer
	) {
		super(x, y, width, height, Component.empty(), toSliderValue(initialScale, maximumScale));
		this.maximumScale = Math.max(InventoryGuiScaler.MINIMUM_SCALE, maximumScale);
		this.valueConsumer = valueConsumer;
		this.updateMessage();
	}

	@Override
	public void renderWidget(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float a
	) {
		int trackX = this.getX() + this.getWidth() / 2;
		int trackTop = this.getY() + TRACK_MARGIN;
		int trackBottom = this.getY() + this.getHeight() - TRACK_MARGIN;
		int trackHeight = Math.max(1, trackBottom - trackTop);
		int handleY = trackBottom - (int) Math.round(this.value * trackHeight);

		graphics.fill(trackX - 2, trackTop - 2, trackX + 3, trackBottom + 3, 0xA0000000);
		graphics.fill(trackX, trackTop, trackX + 1, trackBottom + 1, UiTheme.BORDER_SOFT);
		graphics.fill(trackX - 1, handleY, trackX + 2, trackBottom + 1, UiTheme.ACCENT_DEEP);
		UiRender.roundedRect(
			graphics,
			trackX - 6,
			handleY - 4,
			13,
			9,
			3,
			this.isHoveredOrFocused() ? UiTheme.ACCENT : UiTheme.ACCENT_SOFT
		);
		this.handleCursor(graphics);
	}

	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		this.setValueFromMouse(event.y());
	}

	@Override
	protected void onDrag(final MouseButtonEvent event, final double dx, final double dy) {
		this.setValueFromMouse(event.y());
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.isUp() || event.isDown()) {
			this.setValue(this.value + (event.isUp() ? 0.025 : -0.025));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void handleCursor(final GuiGraphics graphics) {
		if (this.isHovered()) {
			graphics.requestCursor(this.active ? CursorTypes.RESIZE_NS : CursorTypes.NOT_ALLOWED);
		}
	}

	@Override
	protected void updateMessage() {
		this.setMessage(Component.translatable(
			"screen.kohs_inventory_tweaks.gui_scaler.slider",
			(int) Math.round(this.inventoryScale() * 100.0)
		));
	}

	@Override
	protected void applyValue() {
		this.valueConsumer.accept(this.inventoryScale());
	}

	public int percentage() {
		return (int) Math.round(this.inventoryScale() * 100.0);
	}

	private void setValueFromMouse(final double mouseY) {
		int trackTop = this.getY() + TRACK_MARGIN;
		int trackHeight = Math.max(1, this.getHeight() - TRACK_MARGIN * 2);
		this.setValue(1.0 - (mouseY - trackTop) / trackHeight);
	}

	private double inventoryScale() {
		return InventoryGuiScaler.MINIMUM_SCALE
			+ this.value * (this.maximumScale - InventoryGuiScaler.MINIMUM_SCALE);
	}

	private static double toSliderValue(final double scale, final double maximumScale) {
		double maximum = Math.max(InventoryGuiScaler.MINIMUM_SCALE, maximumScale);
		double range = maximum - InventoryGuiScaler.MINIMUM_SCALE;
		if (range < 0.0001) {
			return 0.0;
		}
		double clamped = Math.max(InventoryGuiScaler.MINIMUM_SCALE, Math.min(maximum, scale));
		return (clamped - InventoryGuiScaler.MINIMUM_SCALE) / range;
	}
}
