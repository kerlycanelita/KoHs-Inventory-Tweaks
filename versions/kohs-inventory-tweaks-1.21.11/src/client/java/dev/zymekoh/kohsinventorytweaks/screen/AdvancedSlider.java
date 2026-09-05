package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.network.chat.Component;

final class AdvancedSlider extends ThemedSlider {
	private final double minimum;
	private final double maximum;
	private final DoubleConsumer consumer;
	private final DoubleFunction<Component> messageFactory;

	AdvancedSlider(
		final int x,
		final int y,
		final int width,
		final double minimum,
		final double maximum,
		final double initialValue,
		final DoubleFunction<Component> messageFactory,
		final DoubleConsumer consumer
	) {
		super(
			x,
			y,
			width,
			20,
			Component.empty(),
			toNormalized(minimum, maximum, initialValue)
		);
		this.minimum = minimum;
		this.maximum = Math.max(minimum, maximum);
		this.consumer = consumer;
		this.messageFactory = messageFactory;
		this.updateMessage();
	}

	@Override
	protected void updateMessage() {
		if (this.messageFactory != null) {
			this.setMessage(this.messageFactory.apply(this.actualValue()));
		}
	}

	@Override
	protected void applyValue() {
		if (this.consumer != null) {
			this.consumer.accept(this.actualValue());
		}
	}

	private double actualValue() {
		return this.minimum + this.value * (this.maximum - this.minimum);
	}

	private static double toNormalized(final double minimum, final double maximum, final double value) {
		if (maximum <= minimum || !Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, (value - minimum) / (maximum - minimum)));
	}
}


