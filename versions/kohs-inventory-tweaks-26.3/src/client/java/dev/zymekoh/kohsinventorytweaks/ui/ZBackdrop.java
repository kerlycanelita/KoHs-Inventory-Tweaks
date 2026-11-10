package dev.zymekoh.kohsinventorytweaks.ui;

import dev.zymekoh.kohsinventorytweaks.render.VisualPerformanceController;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The living background of the configuration screens.
 *
 * <p>Layers, back to front: a deep gradient, a slot-grid sigil turning slowly behind
 * the content, a few embers rising, an occasional diagonal light streak, and a
 * vignette that keeps the eye on the middle. Everything runs on the interface clock,
 * stays far below the brightness of the controls, and holds still when motion is
 * reduced. Ember count follows the menu particle density setting live. State is
 * sized once per screen size; drawing allocates nothing.</p>
 */
public final class ZBackdrop {
	private static final float TAU = (float) (Math.PI * 2.0);
	private static final int RING_DOTS = 72;
	private static final float[] RING_COS = new float[RING_DOTS];
	private static final float[] RING_SIN = new float[RING_DOTS];

	static {
		for (int index = 0; index < RING_DOTS; index++) {
			float angle = index * TAU / RING_DOTS;
			RING_COS[index] = (float) Math.cos(angle);
			RING_SIN[index] = (float) Math.sin(angle);
		}
	}

	private int width = -1;
	private int height = -1;
	private float[] emberX = new float[0];
	private float[] emberY = new float[0];
	private float[] emberRise = new float[0];
	private float[] emberSway = new float[0];
	private float[] emberPhase = new float[0];
	private int[] emberSize = new int[0];
	private int[] emberTint = new int[0];

	/** Draws every layer for a screen of this size; the pointer adds a slight parallax. */
	public void draw(
		final GuiGraphicsExtractor graphics,
		final int width,
		final int height,
		final int mouseX,
		final int mouseY
	) {
		if (width != this.width || height != this.height) {
			this.size(width, height);
		}
		graphics.fillGradient(0, 0, width, height, ZTheme.BACKDROP_TOP, ZTheme.BACKDROP_BOTTOM);
		boolean still = ZMotion.reduced();
		float time = still ? 0.0F : ZMotion.seconds();
		float parallaxX = still ? 0.0F : (mouseX - width * 0.5F) / Math.max(1, width) * -8.0F;
		float parallaxY = still ? 0.0F : (mouseY - height * 0.5F) / Math.max(1, height) * -5.0F;
		this.sigil(graphics, width * 0.5F + parallaxX, height * 0.52F + parallaxY,
			Math.min(width, height) * 0.46F, time);
		this.embers(graphics, time, width, height, still);
		if (!still) {
			this.streak(graphics, time, width, height);
		}
		int band = Math.max(12, height / 6);
		graphics.fillGradient(0, 0, width, band, ZTheme.alpha(ZTheme.VOID, 150), ZTheme.alpha(ZTheme.VOID, 0));
		graphics.fillGradient(0, height - band, width, height, ZTheme.alpha(ZTheme.VOID, 0), ZTheme.alpha(ZTheme.VOID, 170));
	}

	private void size(final int width, final int height) {
		this.width = width;
		this.height = height;
		int count = Math.max(14, Math.min(30, width * height / 11_000));
		Random random = new Random(0x5A594D45L);
		this.emberX = new float[count];
		this.emberY = new float[count];
		this.emberRise = new float[count];
		this.emberSway = new float[count];
		this.emberPhase = new float[count];
		this.emberSize = new int[count];
		this.emberTint = new int[count];
		for (int index = 0; index < count; index++) {
			this.emberX[index] = random.nextFloat() * width;
			this.emberY[index] = random.nextFloat() * height;
			this.emberRise[index] = 4.0F + random.nextFloat() * 10.0F;
			this.emberSway[index] = 2.0F + random.nextFloat() * 6.0F;
			this.emberPhase[index] = random.nextFloat() * TAU;
			this.emberSize[index] = random.nextInt(7) == 0 ? 2 : 1;
			int roll = random.nextInt(10);
			this.emberTint[index] = roll < 6 ? ZTheme.VIOLET_BRIGHT : roll < 8 ? ZTheme.MAGENTA : roll < 9 ? ZTheme.CYAN : ZTheme.LILAC_PALE;
		}
	}

	/** Slot-grid sigil: dotted ring, two counter-turning squares and a nine-slot core. */
	private void sigil(final GuiGraphicsExtractor graphics, final float centerX, final float centerY, final float radius, final float time) {
		var pose = graphics.pose();
		int r = Math.round(radius);
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.rotate(time * 0.035F);
		for (int index = 0; index < RING_DOTS; index++) {
			int x = Math.round(RING_COS[index] * r);
			int y = Math.round(RING_SIN[index] * r);
			boolean major = index % 6 == 0;
			graphics.fill(x, y, x + (major ? 2 : 1), y + (major ? 2 : 1),
				ZTheme.alpha(major ? ZTheme.LILAC : ZTheme.VIOLET_BRIGHT, major ? 46 : 20));
		}
		int outer = Math.round(radius * 0.74F);
		squareOutline(graphics, outer, ZTheme.alpha(ZTheme.VIOLET_BRIGHT, 24));
		pose.popMatrix();

		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.rotate(TAU / 8.0F - time * 0.055F);
		int inner = Math.round(radius * 0.52F);
		squareOutline(graphics, inner, ZTheme.alpha(ZTheme.MAGENTA, 18));
		int tick = Math.max(3, r / 18);
		graphics.fill(-1, -inner - tick, 1, -inner + tick, ZTheme.alpha(ZTheme.CYAN, 40));
		graphics.fill(-1, inner - tick, 1, inner + tick, ZTheme.alpha(ZTheme.CYAN, 40));
		graphics.fill(-inner - tick, -1, -inner + tick, 1, ZTheme.alpha(ZTheme.CYAN, 40));
		graphics.fill(inner - tick, -1, inner + tick, 1, ZTheme.alpha(ZTheme.CYAN, 40));
		pose.popMatrix();

		// The inventory at the heart of the sigil: nine faint slots, breathing.
		int cell = Math.max(6, r / 7);
		int gap = Math.max(2, cell / 4);
		int span = cell * 3 + gap * 2;
		int left = Math.round(centerX) - span / 2;
		int top = Math.round(centerY) - span / 2;
		int breath = Math.round(10 + 8 * ZMotion.pulse(3.2F));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				int x = left + column * (cell + gap);
				int y = top + row * (cell + gap);
				int color = row == 1 && column == 1 ? ZTheme.alpha(ZTheme.CYAN, breath + 8) : ZTheme.alpha(ZTheme.VIOLET_BRIGHT, breath);
				graphics.fill(x, y, x + cell, y + 1, color);
				graphics.fill(x, y + cell - 1, x + cell, y + cell, color);
				graphics.fill(x, y + 1, x + 1, y + cell - 1, color);
				graphics.fill(x + cell - 1, y + 1, x + cell, y + cell - 1, color);
			}
		}
	}

	private static void squareOutline(final GuiGraphicsExtractor graphics, final int half, final int color) {
		graphics.fill(-half, -half, half, -half + 1, color);
		graphics.fill(-half, half - 1, half, half, color);
		graphics.fill(-half, -half + 1, -half + 1, half - 1, color);
		graphics.fill(half - 1, -half + 1, half, half - 1, color);
	}

	private void embers(final GuiGraphicsExtractor graphics, final float time, final int width, final int height, final boolean still) {
		int visible = Math.min(this.emberX.length, VisualPerformanceController.particleCount(this.emberX.length));
		if (still) {
			visible /= 3;
		}
		for (int index = 0; index < visible; index++) {
			float travel = still ? 0.0F : time * this.emberRise[index];
			float y = this.emberY[index] - travel;
			y = ((y % height) + height) % height;
			float x = this.emberX[index] + (still ? 0.0F : (float) Math.sin(time * 0.6F + this.emberPhase[index]) * this.emberSway[index]);
			float flicker = still ? 0.6F : 0.45F + 0.55F * (float) Math.abs(Math.sin(time * 1.3F + this.emberPhase[index]));
			// Fade near the top so an ember never pops out of existence mid-screen.
			float edge = Math.min(1.0F, y / (height * 0.25F));
			int alpha = Math.round(90 * flicker * edge);
			int size = this.emberSize[index];
			int px = Math.round(x);
			int py = Math.round(y);
			graphics.fill(px, py, px + size, py + size, ZTheme.alpha(this.emberTint[index], alpha));
		}
	}

	/** A thin diagonal light that crosses the upper screen every eleven seconds. */
	private void streak(final GuiGraphicsExtractor graphics, final float time, final int width, final int height) {
		float period = 11.0F;
		float active = 0.9F;
		float phase = time % period;
		if (phase > active) {
			return;
		}
		float progress = ZMotion.easeOutCubic(phase / active);
		int length = Math.max(40, width / 6);
		int startX = Math.round(-length + progress * (width + length * 2));
		int startY = Math.round(height * 0.18F);
		for (int step = 0; step < length; step++) {
			float along = step / (float) length;
			int alpha = Math.round(70 * along * (1.0F - progress * 0.6F));
			int x = startX + step;
			int y = startY + Math.round(step * 0.28F);
			graphics.fill(x, y, x + 1, y + 1, ZTheme.alpha(ZTheme.LILAC_PALE, alpha));
		}
	}
}
