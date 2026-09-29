package dev.zymekoh.kohsinventorytweaks.ui;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;

/**
 * Time-based motion for the interface: the same animation at 30 and at 500 FPS.
 *
 * <p>Three speeds give the screen its rhythm: slow atmosphere (seconds), medium
 * breathing of active elements (under a second) and fast feedback to the hand
 * (tens of milliseconds). With Reduce inventory visual motion on, the atmosphere
 * holds still while hover and click feedback remain.</p>
 */
public final class ZMotion {
	/** Wraps hourly so the float keeps sub-millisecond precision. */
	private static final long WRAP_NANOS = 3_600_000_000_000L;

	private ZMotion() {
	}

	/** Seconds on the interface clock. */
	public static float seconds() {
		return (System.nanoTime() % WRAP_NANOS) / 1_000_000_000.0F;
	}

	/** Linear 0-1 progress of an animation that started at {@code startNanos}. */
	public static float progress(final long startNanos, final long durationNanos) {
		if (durationNanos <= 0L) {
			return 1.0F;
		}
		return clamp01((System.nanoTime() - startNanos) / (float) durationNanos);
	}

	/** Whether the atmosphere should hold still. */
	public static boolean reduced() {
		return ConfigStore.get().reduceInventoryMotion;
	}

	public static float easeOutCubic(final float t) {
		float inverse = 1.0F - clamp01(t);
		return 1.0F - inverse * inverse * inverse;
	}

	public static float smoothstep(final float t) {
		float x = clamp01(t);
		return x * x * (3.0F - 2.0F * x);
	}

	/** A restrained overshoot: sharp and heavy, never a cartoon bounce. */
	public static float easeOutBack(final float t) {
		float x = clamp01(t) - 1.0F;
		float overshoot = 1.15F;
		return 1.0F + (overshoot + 1.0F) * x * x * x + overshoot * x * x;
	}

	/** 0-1 sine wave with the given period in seconds; 0.5 when motion is reduced. */
	public static float pulse(final float periodSeconds) {
		if (reduced()) {
			return 0.5F;
		}
		return 0.5F + 0.5F * (float) Math.sin(seconds() * (Math.PI * 2.0) / Math.max(0.01F, periodSeconds));
	}

	/** 0-1 position within a repeating cycle; 0 when motion is reduced. */
	public static float cycle(final float periodSeconds) {
		if (reduced()) {
			return 0.0F;
		}
		float period = Math.max(0.01F, periodSeconds);
		return (seconds() % period) / period;
	}

	/** Exponential approach, frame-rate independent. */
	public static float approach(final float current, final float target, final float ratePerSecond, final float deltaSeconds) {
		float blend = 1.0F - (float) Math.exp(-ratePerSecond * Math.max(0.0F, deltaSeconds));
		return current + (target - current) * blend;
	}

	public static float clamp01(final float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}
}
