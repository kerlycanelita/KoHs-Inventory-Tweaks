package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class FloatingParticle {
	private float originX;
	private float originY;
	private float x;
	private float y;
	private final float speed;
	private final float drift;
	private final int size;
	private final int alpha;
	private final int style;
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
		this.originX = x;
		this.originY = y;
		this.x = x;
		this.y = y;
		this.speed = speed;
		this.drift = drift;
		this.size = size;
		this.alpha = alpha;
		this.phase = phase;
		this.style = Math.floorMod((int) (phase * 1000.0F), 3);
	}

	void tick(final int width, final int height) {
		this.phase += 0.020F + this.speed * 0.05F;
		this.originX = Math.max(4.0F, Math.min(Math.max(4.0F, width - 4.0F), this.originX));
		this.originY = Math.max(4.0F, Math.min(Math.max(4.0F, height - 4.0F), this.originY));
		float horizontalTravel = 3.0F + this.drift * 100.0F;
		float verticalTravel = 10.0F + this.speed * 42.0F;
		this.x = this.originX + (float) Math.sin(this.phase * 0.73F) * horizontalTravel;
		this.y = this.originY + (float) Math.sin(this.phase) * verticalTravel;
	}

	void draw(final GuiGraphicsExtractor graphics) {
		int px = Math.round(this.x);
		int py = Math.round(this.y);
		int pulseAlpha = Math.max(12, Math.min(255, Math.round(this.alpha * (0.72F + 0.28F * (float) Math.sin(this.phase * 1.7F)))));
		if (this.style == 1) {
			int arm = this.size + 2;
			graphics.fill(px - arm, py, px + arm + 1, py + 1, UiRender.withAlpha(UiTheme.ACCENT, pulseAlpha / 2));
			graphics.fill(px, py - arm, px + 1, py + arm + 1, UiRender.withAlpha(UiTheme.ACCENT_BRIGHT, pulseAlpha));
			graphics.fill(px - 1, py - 1, px + 2, py + 2, UiRender.withAlpha(UiTheme.ACCENT_BRIGHT, pulseAlpha / 3));
		} else if (this.style == 2) {
			graphics.fill(px, py, px + 1, py + 1, UiRender.withAlpha(UiTheme.ACCENT_BRIGHT, pulseAlpha));
		} else {
			graphics.fill(px - this.size, py - this.size, px + this.size + 1, py + this.size + 1, UiRender.withAlpha(UiTheme.ACCENT, pulseAlpha / 5));
			graphics.fill(px, py, px + this.size, py + this.size, UiRender.withAlpha(UiTheme.ACCENT_BRIGHT, pulseAlpha));
		}
	}
}
