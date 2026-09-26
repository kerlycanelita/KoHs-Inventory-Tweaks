package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;

public final class GlassSlider extends ThemedSlider {
	private final String translationKey;
	private final IntConsumer valueConsumer;

	public GlassSlider(
		final int x,
		final int y,
		final int width,
		final String translationKey,
		final int initialValue,
		final IntConsumer valueConsumer
	) {
		super(x, y, width, 20, Component.empty(), clampByte(initialValue) / 255.0);
		this.translationKey = translationKey;
		this.valueConsumer = valueConsumer;
		this.updateMessage();
	}

	@Override
	protected void updateMessage() {
		this.setMessage(Component.translatable(this.translationKey, this.byteValue()));
	}

	@Override
	protected void applyValue() {
		this.valueConsumer.accept(this.byteValue());
	}

	private int byteValue() {
		return clampByte((int) Math.round(this.value * 255.0));
	}

	private static int clampByte(final int value) {
		return Math.max(0, Math.min(255, value));
	}
}

