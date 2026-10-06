package dev.zymekoh.kohsinventorytweaks.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * The icons of the interface, as small animated scenes made of Minecraft itself: a
 * real item, real particle sprites and a few lines of light, on a transparent
 * ground.
 *
 * <p>Every scene idles gently and wakes up with {@code energy} (0 at rest, 1 when
 * hovered or selected): it moves faster and sheds more particles. Motion is
 * time-based on its own clock and drawn with its own sprites, so no graphics,
 * particle, glint or motion setting stills it. A scene is decoration over a control
 * that never moves, so nothing here touches a hitbox.</p>
 */
public enum ZScene {
	TWEAKS, CURSOR, ISSUES, CUSTOMIZATION, HIGHLIGHTER, SCALER, VISIBILITY, PERFORMANCE, SAFETY,
	INVENTORY, CHEST, SHULKER, ENDER_CHEST, BARREL, KEYBIND,
	FAST, CENTER, POINTER, MOTION, MASCOT, KOHS,
	ADAPTED, WARNING, CRITICAL;

	private static final float TAU = (float) (Math.PI * 2.0);

	/** Item stacks are built on first use: enum constants exist before the item registry does. */
	private static final class Stacks {
		static final ItemStack TOTEM = stack(Items.TOTEM_OF_UNDYING);
		static final ItemStack RECOVERY_COMPASS = stack(Items.RECOVERY_COMPASS);
		static final ItemStack COMPASS = stack(Items.COMPASS);
		static final ItemStack BARRIER = stack(Items.BARRIER);
		static final ItemStack[] DYES = {
			stack(Items.PURPLE_DYE), stack(Items.MAGENTA_DYE), stack(Items.LIGHT_BLUE_DYE), stack(Items.PINK_DYE)
		};
		static final int[] DYE_COLORS = {0xFFA855F7, 0xFFEC4BB6, 0xFF3FDCFF, 0xFFFF9AD5};
		static final ItemStack GLOW_INK = stack(Items.GLOW_INK_SAC);
		static final ItemStack SPYGLASS = stack(Items.SPYGLASS);
		static final ItemStack ENDER_EYE = stack(Items.ENDER_EYE);
		static final ItemStack CLOCK = stack(Items.CLOCK);
		static final ItemStack SHIELD = glint(stack(Items.SHIELD));
		static final ItemStack BUNDLE = stack(Items.BUNDLE);
		static final ItemStack CHEST = stack(Items.CHEST);
		static final ItemStack SHULKER = stack(Items.PURPLE_SHULKER_BOX);
		static final ItemStack ENDER_CHEST = stack(Items.ENDER_CHEST);
		static final ItemStack BARREL = stack(Items.BARREL);
		static final ItemStack TRIAL_KEY = stack(Items.TRIAL_KEY);
		static final ItemStack OMINOUS_KEY = stack(Items.OMINOUS_TRIAL_KEY);
		static final ItemStack FEATHER = stack(Items.FEATHER);
		static final ItemStack TARGET = stack(Items.TARGET);
		static final ItemStack ICE = stack(Items.ICE);
		static final ItemStack EMERALD = glint(stack(Items.EMERALD));
		static final ItemStack TORCH = stack(Items.REDSTONE_TORCH);
		static final ItemStack AMETHYST = stack(Items.AMETHYST_CLUSTER);

		private static ItemStack stack(final Item item) {
			return new ItemStack(item);
		}

		private static ItemStack glint(final ItemStack stack) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			return stack;
		}
	}

	/** Draws the scene into a {@code size} square at {@code x, y}. */
	public void draw(final GuiGraphicsExtractor graphics, final int x, final int y, final int size, final float energy) {
		float e = ZMotion.clamp01(energy);
		float t = ZMotion.seconds();
		float k = size / 16.0F;
		float cx = x + size * 0.5F;
		float cy = y + size * 0.5F;
		switch (this) {
			case TWEAKS -> {
				streaks(graphics, x - 2, y + 2, size + 4, size - 4, t, 0.9F + 1.6F * e, ZTheme.CYAN, 3);
				slot(graphics, x, y, size);
				float push = (float) Math.sin(t * 7.0F) * k * (0.6F + e);
				item(graphics, Stacks.TOTEM, cx + push, cy, 0.78F * k, 0.0F);
				sparks(graphics, x + size - 3 * k, cy, k, t, e, ZTheme.CYAN);
			}
			case CURSOR, CENTER -> {
				float pulse = 0.5F + 0.5F * (float) Math.sin(t * 4.0F);
				int inset = Math.round((1.5F - pulse - e) * k);
				ZDraw.brackets(graphics, x + inset, y + inset, size - inset * 2, size - inset * 2,
					Math.max(2, Math.round(4 * k)), ZTheme.fade(ZTheme.CYAN, 0.55F + 0.45F * e));
				float wobble = (float) Math.sin(t * 2.2F) * (0.14F + 0.2F * e);
				item(graphics, this == CURSOR ? Stacks.RECOVERY_COMPASS : Stacks.COMPASS, cx, cy, 0.82F * k, wobble);
			}
			case ISSUES, CRITICAL -> {
				float pulse = 0.5F + 0.5F * (float) Math.sin(t * 3.2F);
				ZDraw.halo(graphics, x + 2, y + 2, size - 4, size - 4, ZTheme.CRIMSON_BRIGHT, Math.round(70 + 110 * pulse * (0.4F + e)));
				float shake = e > 0.5F ? (float) Math.sin(t * 40.0F) * k * 0.8F : 0.0F;
				item(graphics, Stacks.BARRIER, cx + shake, cy, 0.86F * k, 0.0F);
			}
			case CUSTOMIZATION -> {
				int index = (int) (t / Math.max(0.25F, 0.9F - 0.5F * e)) % Stacks.DYES.length;
				float pop = 1.0F + 0.08F * (float) Math.max(0.0, Math.sin(t * 9.0F));
				rising(graphics, x, y, size, t, e, "glitter", Stacks.DYE_COLORS[index], 3);
				item(graphics, Stacks.DYES[index], cx, cy, 0.86F * k * pop, 0.0F);
			}
			case HIGHLIGHTER -> {
				float pulse = 0.5F + 0.5F * (float) Math.sin(t * 2.6F);
				ZDraw.halo(graphics, x + 3, y + 3, size - 6, size - 6, ZTheme.CYAN, Math.round(60 + 120 * pulse * (0.5F + e)));
				orbit(graphics, cx, cy, size * 0.48F, t * (1.0F + 1.5F * e), "glow", 0xFF7DF9FF, k, 2);
				item(graphics, Stacks.GLOW_INK, cx, cy, 0.8F * k, 0.0F);
			}
			case SCALER -> {
				float zoom = 0.5F + 0.5F * (float) Math.sin(t * (1.6F + 1.6F * e));
				int inset = Math.round((zoom * 2.0F) * k);
				ZDraw.brackets(graphics, x + inset, y + inset, size - inset * 2, size - inset * 2,
					Math.max(2, Math.round(4 * k)), ZTheme.fade(ZTheme.LILAC, 0.6F + 0.4F * e));
				item(graphics, Stacks.SPYGLASS, cx, cy, (0.68F + 0.16F * (1.0F - zoom)) * k, -0.78F);
			}
			case VISIBILITY -> {
				orbit(graphics, cx, cy, size * 0.46F, t * (0.8F + e), "glitter", 0xFFEC4BB6, k, 3);
				float bob = (float) Math.sin(t * 2.0F) * k;
				item(graphics, Stacks.ENDER_EYE, cx, cy + bob, 0.8F * k, 0.0F);
			}
			case PERFORMANCE -> {
				orbit(graphics, cx, cy, size * 0.5F, t * (2.0F + 3.0F * e), "spark", 0xFF3FDCFF, k, 1);
				item(graphics, Stacks.CLOCK, cx, cy, 0.8F * k, 0.0F);
			}
			case SAFETY -> {
				float bob = (float) Math.sin(t * 1.8F) * 0.7F * k;
				rising(graphics, x, y, size, t, e * 0.6F, "glitter", ZTheme.VIOLET_BRIGHT, 2);
				item(graphics, Stacks.SHIELD, cx, cy + bob, 0.82F * k, 0.0F);
				twinkle(graphics, x, y, size, t, e);
			}
			case INVENTORY, CHEST, SHULKER, ENDER_CHEST, BARREL -> {
				float hop = (float) -Math.abs(Math.sin(t * (2.0F + 5.0F * e))) * k * (0.5F + 1.5F * e);
				ItemStack stack = switch (this) {
					case INVENTORY -> Stacks.BUNDLE;
					case CHEST -> Stacks.CHEST;
					case SHULKER -> Stacks.SHULKER;
					case ENDER_CHEST -> Stacks.ENDER_CHEST;
					default -> Stacks.BARREL;
				};
				if (this == ENDER_CHEST || this == SHULKER) {
					rising(graphics, x, y, size, t, e, this == ENDER_CHEST ? "glitter" : "spark",
						this == ENDER_CHEST ? 0xFFEC4BB6 : 0xFF3FDCFF, 2);
				}
				item(graphics, stack, cx, cy + hop, 0.86F * k, 0.0F);
			}
			case KEYBIND -> {
				float swing = (float) Math.sin(t * 2.4F) * (0.18F + 0.22F * e);
				item(graphics, e > 0.5F ? Stacks.OMINOUS_KEY : Stacks.TRIAL_KEY, cx, cy, 0.82F * k, swing);
			}
			case FAST -> {
				streaks(graphics, x - 2, y + 1, size + 4, size - 2, t, 1.2F + 2.0F * e, ZTheme.CYAN, 4);
				float dash = (float) Math.sin(t * 9.0F) * k * (0.8F + e);
				item(graphics, Stacks.FEATHER, cx + dash, cy, 0.8F * k, -0.35F);
			}
			case POINTER -> {
				float pulse = (t * (0.9F + e)) % 1.0F;
				ring(graphics, cx, cy, size * (0.3F + 0.25F * pulse), ZTheme.fade(ZTheme.CRIMSON_BRIGHT, 1.0F - pulse));
				item(graphics, Stacks.TARGET, cx, cy, 0.72F * k, 0.0F);
			}
			case MOTION -> {
				// Reduce visual motion: ice, and snow falling slowly past it.
				rising(graphics, x, y, size, -t * 0.35F, 0.6F, "glitter", 0xFFF6F0FB, 2);
				item(graphics, Stacks.ICE, cx, cy, 0.76F * k, 0.0F);
			}
			case MASCOT -> ZMascot.drawPortrait(graphics, x, y, size, e);
			case KOHS -> {
				// An amethyst cluster in an orbit of enchanting glyphs: the creator's tab.
				for (int index = 0; index < 3; index++) {
					float angle = t * (1.2F + 1.4F * e) + index * TAU / 3.0F;
					sprite(graphics, "sga_" + (char) ('k' + index * 3), cx + (float) Math.cos(angle) * size * 0.46F,
						cy + (float) Math.sin(angle) * size * 0.3F, 5 * k, 8, index == 1 ? 0xFFFF8AD8 : ZTheme.LILAC);
				}
				float bob = (float) Math.sin(t * 2.0F) * 0.6F * k;
				item(graphics, Stacks.AMETHYST, cx, cy + bob, 0.84F * k, 0.0F);
				twinkle(graphics, x, y, size, t, e);
			}
			case ADAPTED -> {
				rising(graphics, x, y, size, t, 0.5F + 0.5F * e, "glitter", 0xFF7DFFB0, 2);
				item(graphics, Stacks.EMERALD, cx, cy, 0.82F * k, 0.0F);
				twinkle(graphics, x, y, size, t, e);
			}
			case WARNING -> {
				float blink = 0.55F + 0.45F * (float) Math.sin(t * 5.0F);
				ZDraw.halo(graphics, x + 3, y + 2, size - 6, size - 4, ZTheme.CRIMSON_BRIGHT, Math.round(140 * blink));
				item(graphics, Stacks.TORCH, cx, cy, 0.86F * k, 0.0F);
			}
		}
	}

	private static void item(
		final GuiGraphicsExtractor graphics,
		final ItemStack stack,
		final float cx,
		final float cy,
		final float scale,
		final float angle
	) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		if (angle != 0.0F) {
			pose.rotate(angle);
		}
		pose.scale(scale, scale);
		pose.translate(-8.0F, -8.0F);
		graphics.item(stack, 0, 0);
		pose.popMatrix();
	}

	/** Two sparkles taking turns over an item: a shine that no glint setting turns off. */
	private static void twinkle(final GuiGraphicsExtractor graphics, final int x, final int y, final int size, final float t, final float e) {
		for (int index = 0; index < 2; index++) {
			float life = fract(t * (0.7F + 0.6F * e) + index * 0.5F);
			float alpha = (float) Math.sin(life * Math.PI);
			sprite(graphics, "glint", x + size * (index == 0 ? 0.3F : 0.72F), y + size * (index == 0 ? 0.28F : 0.64F),
				size * (0.26F + 0.14F * alpha), 8, ZTheme.fade(0xFFFFFFFF, alpha));
		}
	}

	/** A Vanilla-looking slot with a see-through floor. */
	private static void slot(final GuiGraphicsExtractor graphics, final int x, final int y, final int size) {
		graphics.fill(x, y, x + size, y + size, 0x608B8B8B);
		graphics.fill(x, y, x + size, y + 1, 0xC0373737);
		graphics.fill(x, y, x + 1, y + size, 0xC0373737);
		graphics.fill(x, y + size - 1, x + size, y + size, 0xC0FFFFFF);
		graphics.fill(x + size - 1, y, x + size, y + size, 0xC0FFFFFF);
	}

	/** Lines of speed racing left to right, fading in and out at the edges. */
	private static void streaks(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final float t,
		final float speed,
		final int color,
		final int lanes
	) {
		for (int lane = 0; lane < lanes; lane++) {
			float phase = fract(t * speed + lane * 0.37F);
			int length = Math.max(3, Math.round(width * (0.25F + 0.12F * lane)));
			int sx = x + Math.round(phase * (width + length)) - length;
			int sy = y + Math.round((lane + 0.5F) * height / lanes);
			float fade = (float) Math.sin(phase * Math.PI);
			int left = Math.max(x, sx);
			int right = Math.min(x + width, sx + length);
			if (right > left) {
				graphics.fill(left, sy, right, sy + 1, ZTheme.fade(color, 0.85F * fade));
			}
		}
	}

	/** Sparks spat out at the leading edge, more of them with energy. */
	private static void sparks(final GuiGraphicsExtractor graphics, final float x, final float y, final float k, final float t, final float e, final int color) {
		int count = e > 0.4F ? 2 : 1;
		for (int index = 0; index < count; index++) {
			float life = fract(t * (1.4F + e) + index * 0.5F);
			int frame = Math.min(7, (int) (life * 8));
			sprite(graphics, "spark_" + frame, x + life * 4 * k, y + (index == 0 ? -2 : 2) * k * life, 6 * k, 8,
				ZTheme.fade(color, 1.0F - life));
		}
	}

	/** Particles rising from the bottom of the square and fading near its top. */
	private static void rising(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int size,
		final float t,
		final float energy,
		final String particle,
		final int color,
		final int count
	) {
		int visible = Math.max(1, Math.round(count * (0.5F + energy)));
		for (int index = 0; index < visible; index++) {
			float life = fract(t * (0.5F + 0.5F * energy) + index / (float) visible);
			float px = x + size * (0.18F + 0.64F * fract(index * 0.618F + 0.13F));
			float py = y + size * (0.95F - life);
			String name = particle.equals("glitter") || particle.equals("spark") ? particle + "_" + Math.min(7, (int) (life * 8)) : particle;
			sprite(graphics, name, px, py, size * 0.32F, 8, ZTheme.fade(color, (float) Math.sin(life * Math.PI)));
		}
	}

	/** Particles circling the centre. */
	private static void orbit(
		final GuiGraphicsExtractor graphics,
		final float cx,
		final float cy,
		final float radius,
		final float turns,
		final String particle,
		final int color,
		final float k,
		final int count
	) {
		for (int index = 0; index < count; index++) {
			float angle = turns * TAU * 0.25F + index * TAU / count;
			float px = cx + (float) Math.cos(angle) * radius;
			float py = cy + (float) Math.sin(angle) * radius * 0.6F;
			String name = particle.equals("glitter") || particle.equals("spark")
				? particle + "_" + (int) (fract(turns + index * 0.3F) * 7) : particle;
			sprite(graphics, name, px, py, 5 * k, 8, color);
		}
	}

	/** A pixel ring, about {@code radius} across, for pulses. */
	private static void ring(final GuiGraphicsExtractor graphics, final float cx, final float cy, final float radius, final int color) {
		int r = Math.max(2, Math.round(radius));
		int x0 = Math.round(cx);
		int y0 = Math.round(cy);
		int steps = Math.max(12, r * 6);
		for (int step = 0; step < steps; step++) {
			double angle = step * Math.PI * 2.0 / steps;
			int px = x0 + (int) Math.round(Math.cos(angle) * r);
			int py = y0 + (int) Math.round(Math.sin(angle) * r);
			graphics.fill(px, py, px + 1, py + 1, color);
		}
	}

	/** One particle sprite, {@code size} across, centred on {@code cx, cy} and tinted. */
	public static void sprite(
		final GuiGraphicsExtractor graphics,
		final String name,
		final float cx,
		final float cy,
		final float size,
		final int textureSize,
		final int color
	) {
		if ((color >>> 24) < 4 || size <= 0.5F) {
			return;
		}
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - size * 0.5F, cy - size * 0.5F);
		pose.scale(size / textureSize, size / textureSize);
		graphics.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/particle/" + name + ".png"),
			0, 0, 0.0F, 0.0F, textureSize, textureSize, textureSize, textureSize, textureSize, textureSize, color);
		pose.popMatrix();
	}

	private static float fract(final float value) {
		return value - (float) Math.floor(value);
	}
}
