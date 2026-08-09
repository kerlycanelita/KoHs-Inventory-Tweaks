package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class FloatingParticle {
	private float x;
	private float y;
	private final float speed;
	private final float drift;
	private final int size;
	private final int alpha;
	private float phase;

	FloatingParticle(
		final float x,
		final float y,
		final float speed,
		final float drift,
		final int size,
		final int alpha,
		final float phase
	) {
		this.x = x;
		this.y = y;
		this.speed = speed;
		this.drift = drift;
		this.size = size;
		this.alpha = alpha;
		this.phase = phase;
	}

	void tick(final int width, final int height) {
		this.phase += 0.045F;
		this.y -= this.speed;
		this.x += Math.sin(this.phase) * this.drift;
		if (this.y < -8.0F) {
			this.y = height + 8.0F;
		}
		if (this.x < -8.0F) {
			this.x = width + 8.0F;
		} else if (this.x > width + 8.0F) {
			this.x = -8.0F;
		}
	}

	void draw(final GuiGraphicsExtractor graphics) {
		int px = Math.round(this.x);
		int py = Math.round(this.y);
		graphics.fill(px - this.size, py - this.size, px + this.size + 1, py + this.size + 1, UiRender.withAlpha(UiTheme.ACCENT, this.alpha / 4));
		graphics.fill(px, py, px + this.size, py + this.size, UiRender.withAlpha(UiTheme.ACCENT, this.alpha));
	}
}
