package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Draws the inventory darkness while preserving small depth-safe windows around
 * players that the local player can already see. The ordinary world render remains
 * authoritative: this never redraws an entity and therefore cannot reveal pixels
 * rejected by Minecraft's depth test.
 */
public final class VisiblePlayerBackdropController {
	private static final int MAX_REVEALS = 6;
	private static final int BACKDROP_RGB = 0x101010;

	private VisiblePlayerBackdropController() {
	}

	public static void draw(
		final GuiGraphicsExtractor graphics,
		final InventoryScreen screen,
		final InventoryTweaksConfig config,
		final int topAlpha,
		final int bottomAlpha
	) {
		List<Reveal> reveals = collectReveals(screen, config);
		int depthIntensity = clamp(config.visiblePlayerDepthIntensity, 0, 255);
		if (reveals.isEmpty() || depthIntensity == 0) {
			graphics.fillGradient(0, 0, screen.width, screen.height, color(topAlpha), color(bottomAlpha));
			return;
		}

		double intensity = depthIntensity / 255.0;
		if (config.visiblePlayerGlowPulse && !InventoryAnimationController.suppressAllInventoryAnimations()) {
			intensity *= 0.92 + 0.08 * Math.sin(System.nanoTime() / 240_000_000.0);
		}

		TreeSet<Integer> xEdges = new TreeSet<>();
		TreeSet<Integer> yEdges = new TreeSet<>();
		xEdges.add(0);
		xEdges.add(screen.width);
		yEdges.add(0);
		yEdges.add(screen.height);
		for (Reveal reveal : reveals) {
			reveal.addEdges(xEdges, yEdges, screen.width, screen.height);
		}

		List<Integer> xs = new ArrayList<>(xEdges);
		List<Integer> ys = new ArrayList<>(yEdges);
		for (int xi = 0; xi + 1 < xs.size(); xi++) {
			int left = xs.get(xi);
			int right = xs.get(xi + 1);
			if (right <= left) {
				continue;
			}
			double sampleX = (left + right) * 0.5;
			for (int yi = 0; yi + 1 < ys.size(); yi++) {
				int top = ys.get(yi);
				int bottom = ys.get(yi + 1);
				if (bottom <= top) {
					continue;
				}
				double sampleY = (top + bottom) * 0.5;
				double revealFactor = 0.0;
				for (Reveal reveal : reveals) {
					revealFactor = Math.max(revealFactor, reveal.factorAt(sampleX, sampleY));
				}
				double darkness = Math.max(0.0, 1.0 - revealFactor * intensity);
				int cellTopAlpha = (int) Math.round(alphaAt(topAlpha, bottomAlpha, top, screen.height) * darkness);
				int cellBottomAlpha = (int) Math.round(alphaAt(topAlpha, bottomAlpha, bottom, screen.height) * darkness);
				graphics.fillGradient(left, top, right, bottom, color(cellTopAlpha), color(cellBottomAlpha));
			}
		}
	}

	private static List<Reveal> collectReveals(final InventoryScreen screen, final InventoryTweaksConfig config) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!config.visiblePlayerGlowEnabled || minecraft.level == null || minecraft.player == null) {
			return List.of();
		}

		Camera camera = minecraft.gameRenderer.getMainCamera();
		Vec3 cameraPosition = camera.position();
		Vector3fc forward = camera.forwardVector();
		Vector3fc leftVector = camera.leftVector();
		Vector3fc up = camera.upVector();
		double focal = screen.height
			/ (2.0 * Math.tan(Math.toRadians(Math.max(20.0F, camera.getFov())) * 0.5));
		double maximumDistanceSq = (double) config.visiblePlayerGlowDistance * config.visiblePlayerGlowDistance;
		Player localPlayer = minecraft.player;

		return minecraft.level.players()
			.stream()
			.filter(player -> player != localPlayer && player.isAlive() && !player.isInvisible() && !player.isSpectator())
			.filter(player -> player.distanceToSqr(localPlayer) <= maximumDistanceSq)
			.filter(player -> localPlayer.hasLineOfSight(
				player,
				ClipContext.Block.VISUAL,
				ClipContext.Fluid.NONE,
				player.getEyeY()
			))
			.sorted(Comparator.comparingDouble(player -> player.distanceToSqr(localPlayer)))
			.limit(MAX_REVEALS)
			.map(player -> project(screen, player, cameraPosition, forward, leftVector, up, focal))
			.filter(java.util.Objects::nonNull)
			.toList();
	}

	private static Reveal project(
		final InventoryScreen screen,
		final Player player,
		final Vec3 cameraPosition,
		final Vector3fc forward,
		final Vector3fc left,
		final Vector3fc up,
		final double focal
	) {
		double dx = player.getX() - cameraPosition.x;
		double dy = player.getY() + player.getBbHeight() * 0.52 - cameraPosition.y;
		double dz = player.getZ() - cameraPosition.z;
		double depth = dx * forward.x() + dy * forward.y() + dz * forward.z();
		if (depth <= 0.05) {
			return null;
		}

		double horizontal = dx * left.x() + dy * left.y() + dz * left.z();
		double vertical = dx * up.x() + dy * up.y() + dz * up.z();
		double projectedX = screen.width * 0.5 - horizontal * focal / depth;
		double projectedY = screen.height * 0.5 - vertical * focal / depth;
		double halfWidth = Math.max(3.0, player.getBbWidth() * focal / depth * 0.68);
		double halfHeight = Math.max(7.0, player.getBbHeight() * focal / depth * 0.58);
		int innerLeft = (int) Math.floor(projectedX - halfWidth);
		int innerTop = (int) Math.floor(projectedY - halfHeight);
		int innerRight = (int) Math.ceil(projectedX + halfWidth);
		int innerBottom = (int) Math.ceil(projectedY + halfHeight);
		if (innerRight < 0 || innerLeft > screen.width || innerBottom < 0 || innerTop > screen.height) {
			return null;
		}
		int outerPadding = Math.max(8, (int) Math.round(Math.min(halfWidth, halfHeight) * 0.42));
		return new Reveal(innerLeft, innerTop, innerRight, innerBottom, outerPadding);
	}

	private static int alphaAt(final int top, final int bottom, final int y, final int height) {
		if (height <= 0) {
			return bottom;
		}
		double progress = Math.max(0.0, Math.min(1.0, y / (double) height));
		return clamp((int) Math.round(top + (bottom - top) * progress), 0, 255);
	}

	private static int color(final int alpha) {
		return clamp(alpha, 0, 255) << 24 | BACKDROP_RGB;
	}

	private static int clamp(final int value, final int minimum, final int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private record Reveal(int left, int top, int right, int bottom, int outerPadding) {
		private void addEdges(
			final TreeSet<Integer> xs,
			final TreeSet<Integer> ys,
			final int screenWidth,
			final int screenHeight
		) {
			for (int padding : new int[]{outerPadding, 0}) {
				xs.add(clamp(left - padding, 0, screenWidth));
				xs.add(clamp(right + padding, 0, screenWidth));
				ys.add(clamp(top - padding, 0, screenHeight));
				ys.add(clamp(bottom + padding, 0, screenHeight));
			}
		}

		private double factorAt(final double x, final double y) {
			if (inside(x, y, 0)) {
				return 1.0;
			}
			return inside(x, y, outerPadding) ? 0.28 : 0.0;
		}

		private boolean inside(final double x, final double y, final int padding) {
			return x >= left - padding && x <= right + padding
				&& y >= top - padding && y <= bottom + padding;
		}
	}
}
