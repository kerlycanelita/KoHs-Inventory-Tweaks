package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.util.Mth;

/**
 * Small frame-rate-independent scroll model shared by every custom screen.
 * Input changes the target immediately while rendering eases the visible
 * position toward it, so fast wheels remain responsive without hard jumps.
 */
final class SmoothScroll {
	private static final double RESPONSE = 14.0;
	private double position;
	private double target;
	private int maximum;
	private long updatedAtNanos = System.nanoTime();

	public void setMaximum(final int maximum) {
		this.maximum = Math.max(0, maximum);
		this.target = Mth.clamp(this.target, 0.0, this.maximum);
		this.position = Mth.clamp(this.position, 0.0, this.maximum);
	}

	public boolean scroll(final double wheelDelta, final double distance) {
		if (this.maximum <= 0 || wheelDelta == 0.0) {
			return false;
		}
		this.update();
		double next = Mth.clamp(this.target - wheelDelta * distance, 0.0, this.maximum);
		if (Math.abs(next - this.target) < 0.001) {
			return false;
		}
		this.target = next;
		return true;
	}

	public double update() {
		long now = System.nanoTime();
		double elapsedSeconds = Math.min(0.08, Math.max(0.0, (now - this.updatedAtNanos) / 1_000_000_000.0));
		this.updatedAtNanos = now;
		if (Math.abs(this.target - this.position) <= 0.01) {
			this.position = this.target;
			return this.position;
		}
		double blend = 1.0 - Math.exp(-RESPONSE * elapsedSeconds);
		this.position += (this.target - this.position) * blend;
		if (Math.abs(this.target - this.position) <= 0.01) {
			this.position = this.target;
		}
		return this.position;
	}

	public int roundedPosition() {
		return Mth.clamp((int) Math.round(this.position), 0, this.maximum);
	}

	public boolean canScrollUp() {
		return this.position > 0.01 || this.target > 0.01;
	}

	public boolean canScrollDown() {
		return this.position < this.maximum - 0.01 || this.target < this.maximum - 0.01;
	}

	public void snapTo(final double value) {
		this.target = Mth.clamp(value, 0.0, this.maximum);
		this.position = this.target;
		this.updatedAtNanos = System.nanoTime();
	}
}

