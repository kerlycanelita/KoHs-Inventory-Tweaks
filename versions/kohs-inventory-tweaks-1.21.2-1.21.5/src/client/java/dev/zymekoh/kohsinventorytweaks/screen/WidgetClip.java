package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Keeps partially visible scrolling widgets rendered and interactive only inside their viewport. */
final class WidgetClip {
	private boolean enabled;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private double pointerX;
	private double pointerY;

	public void set(final int left, final int top, final int right, final int bottom) {
		this.enabled = right > left && bottom > top;
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
	}

	public void begin(final GuiGraphics graphics) {
		if (this.enabled) {
			graphics.enableScissor(this.left, this.top, this.right, this.bottom);
		}
	}

	public void end(final GuiGraphics graphics) {
		if (this.enabled) {
			graphics.disableScissor();
		}
	}

	public boolean contains(final double x, final double y) {
		return !this.enabled || x >= this.left && x < this.right && y >= this.top && y < this.bottom;
	}

	public void trackPointer(final double x, final double y) {
		this.pointerX = x;
		this.pointerY = y;
	}

	public boolean permitsHover(final boolean vanillaHovered) {
		return vanillaHovered && this.contains(this.pointerX, this.pointerY);
	}
}
