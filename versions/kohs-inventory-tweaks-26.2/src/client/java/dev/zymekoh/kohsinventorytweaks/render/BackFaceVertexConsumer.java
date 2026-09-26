package dev.zymekoh.kohsinventorytweaks.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Re-emits every quad of a model with its winding reversed.
 *
 * <p>Back-face culling then keeps only the far side of the geometry. Drawn as an
 * inflated shell over a model that has already written depth, the far side is
 * hidden wherever the body is in front of it, so only the rim beyond the
 * silhouette remains: an outline that blocks occlude like any other geometry.</p>
 */
final class BackFaceVertexConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final float[] floats = new float[4 * 8];
	private final int[] ints = new int[4 * 3];
	private int count;

	BackFaceVertexConsumer(final VertexConsumer delegate) {
		this.delegate = delegate;
	}

	@Override
	public void addVertex(
		final float x, final float y, final float z, final int color, final float u, final float v,
		final int overlay, final int light, final float normalX, final float normalY, final float normalZ
	) {
		int f = this.count * 8;
		int i = this.count * 3;
		this.floats[f] = x;
		this.floats[f + 1] = y;
		this.floats[f + 2] = z;
		this.floats[f + 3] = u;
		this.floats[f + 4] = v;
		this.floats[f + 5] = -normalX;
		this.floats[f + 6] = -normalY;
		this.floats[f + 7] = -normalZ;
		this.ints[i] = color;
		this.ints[i + 1] = overlay;
		this.ints[i + 2] = light;
		if (++this.count < 4) {
			return;
		}
		this.count = 0;
		// 0-1-2-3 becomes 0-3-2-1: the same quad, facing the other way.
		this.emit(0);
		this.emit(3);
		this.emit(2);
		this.emit(1);
	}

	private void emit(final int vertex) {
		int f = vertex * 8;
		int i = vertex * 3;
		this.delegate.addVertex(
			this.floats[f], this.floats[f + 1], this.floats[f + 2], this.ints[i], this.floats[f + 3], this.floats[f + 4],
			this.ints[i + 1], this.ints[i + 2], this.floats[f + 5], this.floats[f + 6], this.floats[f + 7]
		);
	}

	@Override
	public VertexConsumer addVertex(final float x, final float y, final float z) {
		return this.delegate.addVertex(x, y, z);
	}

	@Override
	public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
		return this.delegate.setColor(red, green, blue, alpha);
	}

	@Override
	public VertexConsumer setColor(final int color) {
		return this.delegate.setColor(color);
	}

	@Override
	public VertexConsumer setUv(final float u, final float v) {
		return this.delegate.setUv(u, v);
	}

	@Override
	public VertexConsumer setUv1(final int u, final int v) {
		return this.delegate.setUv1(u, v);
	}

	@Override
	public VertexConsumer setUv2(final int u, final int v) {
		return this.delegate.setUv2(u, v);
	}

	@Override
	public VertexConsumer setNormal(final float x, final float y, final float z) {
		return this.delegate.setNormal(x, y, z);
	}

	@Override
	public VertexConsumer setLineWidth(final float width) {
		return this.delegate.setLineWidth(width);
	}
}
