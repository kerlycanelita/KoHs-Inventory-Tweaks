package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.function.DoubleConsumer;
import net.minecraft.network.chat.Component;

public final class CropZoomSlider extends ThemedSlider {
	private static final double MINIMUM_ZOOM = 1.0;
	private static final double MAXIMUM_ZOOM = 4.0;
	private final DoubleConsumer valueConsumer;

	public CropZoomSlider(
		final int x,
		final int y,
		final int width,
		final double initialZoom,
		final DoubleConsumer valueConsumer
	) {
		super(x, y, width, 20, Component.empty(), toSliderValue(initialZoom));
		this.valueConsumer = valueConsumer;
		this.updateMessage();
	}

	@Override
	protected void updateMessage() {
		this.setMessage(Component.translatable(
			"screen.kohs_inventory_tweaks.crop.zoom",
			(int) Math.round(this.zoom() * 100.0)
		));
	}

	@Override
	protected void applyValue() {
		this.valueConsumer.accept(this.zoom());
	}

	private double zoom() {
		return MINIMUM_ZOOM + this.value * (MAXIMUM_ZOOM - MINIMUM_ZOOM);
	}

	private static double toSliderValue(final double zoom) {
		return (Math.max(MINIMUM_ZOOM, Math.min(MAXIMUM_ZOOM, zoom)) - MINIMUM_ZOOM)
			/ (MAXIMUM_ZOOM - MINIMUM_ZOOM);
	}
}

