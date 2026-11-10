package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import java.util.function.DoubleConsumer;
import net.minecraft.network.chat.Component;

public final class InventoryScaleSlider extends ThemedSlider {
	private final double maximumScale;
	private final DoubleConsumer valueConsumer;

	public InventoryScaleSlider(
		final int x,
		final int y,
		final int width,
		final double maximumScale,
		final double initialScale,
		final DoubleConsumer valueConsumer
	) {
		super(x, y, width, 20, Component.empty(), toSliderValue(initialScale, maximumScale));
		this.maximumScale = Math.max(InventoryGuiScaler.MINIMUM_SCALE, maximumScale);
		this.valueConsumer = valueConsumer;
		this.updateMessage();
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
