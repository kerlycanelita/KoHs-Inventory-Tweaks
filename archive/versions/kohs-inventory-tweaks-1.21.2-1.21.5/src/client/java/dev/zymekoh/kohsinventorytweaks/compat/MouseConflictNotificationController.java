package dev.zymekoh.kohsinventorytweaks.compat;

import dev.zymekoh.kohsinventorytweaks.input.ConfigMenuKeyBinding;
import dev.zymekoh.kohsinventorytweaks.screen.UiRender;
import dev.zymekoh.kohsinventorytweaks.screen.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class MouseConflictNotificationController {
	private static final long FADE_NANOS = 220_000_000L;
	private static final long VISIBLE_NANOS = 4_000_000_000L;
	private static final List<Particle> PARTICLES = createParticles();
	private static boolean started;
	private static boolean acknowledged;
	private static long startedAtNanos;

	private MouseConflictNotificationController() {
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (acknowledged || CompatibilityIssueManager.mousePositionIssues().isEmpty()) {
			return;
		}
		if (!started) {
			if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
				return;
			}
			started = true;
			startedAtNanos = System.nanoTime();
		}
		for (Particle particle : PARTICLES) {
			particle.tick();
		}
		if (System.nanoTime() - startedAtNanos >= VISIBLE_NANOS) {
			acknowledged = true;
		}
	}

	public static boolean consumeIssuesTrackerRoute() {
		if (!isVisible()) {
			return false;
		}
		acknowledged = true;
		return true;
	}

	public static void draw(final GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!isVisible() || minecraft.screen != null) {
			return;
		}

		long elapsed = System.nanoTime() - startedAtNanos;
		float entrance = Mth.clamp(elapsed / (float) FADE_NANOS, 0.0F, 1.0F);
		float exit = Mth.clamp((VISIBLE_NANOS - elapsed) / (float) FADE_NANOS, 0.0F, 1.0F);
		float progress = smooth(Math.min(entrance, exit));
		int alpha = Math.max(0, Math.min(255, Math.round(progress * 255.0F)));

		int guiWidth = minecraft.getWindow().getGuiScaledWidth();
		int guiHeight = minecraft.getWindow().getGuiScaledHeight();
		int width = Math.max(142, Math.min(268, guiWidth - 12));
		Font font = minecraft.font;
		List<CompatibilityIssue> issues = CompatibilityIssueManager.mousePositionIssues();
		CompatibilityIssue primary = primaryIssue(issues);
		String names = issueNames(issues);
		Component summary = Component.translatable(
			primary.severity() == CompatibilityIssue.Severity.DEGRADED
				? "notification.kohs_inventory_tweaks.mouse_conflict.summary.degraded"
				: "notification.kohs_inventory_tweaks.mouse_conflict.summary",
			names
		);
		int iconSize = 18;
		int textXOffset = 10 + iconSize + 6;
		List<FormattedCharSequence> summaryLines = font.split(summary, Math.max(40, width - textXOffset - 8));
		int visibleLines = Math.min(2, summaryLines.size());
		int height = 34 + visibleLines * 10;
		int x = guiWidth - width - 6 + Math.round((1.0F - progress) * 22.0F);
		int y = Math.min(8, Math.max(2, guiHeight - height - 2));

		UiRender.glow(graphics, x, y, width, height, 9, Math.round(44.0F * progress));
		UiRender.panel(
			graphics,
			x,
			y,
			width,
			height,
			8,
			UiRender.withAlpha(UiTheme.GLASS, Math.round(218.0F * progress)),
			UiRender.withAlpha(UiTheme.ACCENT_SOFT, alpha)
		);
		graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
		for (Particle particle : PARTICLES) {
			particle.draw(graphics, x, y, width, height, progress);
		}
		graphics.disableScissor();
		graphics.blit(
			RenderType::guiTextured,
			severityTexture(primary.severity()),
			x + 8,
			y + 7,
			0.0F,
			0.0F,
			iconSize,
			iconSize,
			128,
			128,
			128,
			128
		);

		graphics.drawString(
			font,
			Component.translatable(primary.severity() == CompatibilityIssue.Severity.DEGRADED
				? "notification.kohs_inventory_tweaks.mouse_conflict.title.degraded"
				: "notification.kohs_inventory_tweaks.mouse_conflict.title"),
			x + textXOffset,
			y + 5,
			UiRender.withAlpha(UiTheme.TEXT, alpha),
			false
		);
		int textY = y + 16;
		for (int index = 0; index < visibleLines; index++) {
			graphics.drawString(
				font,
				summaryLines.get(index),
				x + textXOffset,
				textY + index * 10,
				UiRender.withAlpha(UiTheme.TEXT_MUTED, alpha)
			);
		}
		graphics.drawString(
			font,
			Component.translatable(
				"notification.kohs_inventory_tweaks.mouse_conflict.open",
				ConfigMenuKeyBinding.mapping().getTranslatedKeyMessage()
			),
			x + textXOffset,
			y + height - 12,
			UiRender.withAlpha(UiTheme.ACCENT_BRIGHT, alpha),
			false
		);
		float remaining = Mth.clamp((VISIBLE_NANOS - elapsed) / (float) VISIBLE_NANOS, 0.0F, 1.0F);
		int barX = x + 5;
		int barWidth = Math.max(1, width - 10);
		int barY = y + height - 3;
		graphics.fill(barX, barY, barX + barWidth, barY + 2, UiRender.withAlpha(UiTheme.SCROLL_TRACK, alpha));
		graphics.fill(
			barX,
			barY,
			barX + Math.round(barWidth * remaining),
			barY + 2,
			UiRender.withAlpha(severityColor(primary.severity()), alpha)
		);
	}

	private static boolean isVisible() {
		return started && !acknowledged && System.nanoTime() - startedAtNanos < VISIBLE_NANOS;
	}

	private static String issueNames(final List<CompatibilityIssue> issues) {
		if (issues.isEmpty()) {
			return "Unknown";
		}
		String first = issues.getFirst().modName();
		return issues.size() == 1 ? first : first + " +" + (issues.size() - 1);
	}

	private static CompatibilityIssue primaryIssue(final List<CompatibilityIssue> issues) {
		return issues.stream()
			.max(java.util.Comparator.comparingInt(issue -> severityRank(issue.severity())))
			.orElseThrow();
	}

	private static int severityRank(final CompatibilityIssue.Severity severity) {
		return switch (severity) {
			case ADAPTABLE -> 0;
			case DEGRADED -> 1;
			case BLOCKING -> 2;
		};
	}

	private static net.minecraft.resources.ResourceLocation severityTexture(final CompatibilityIssue.Severity severity) {
		String fileName = switch (severity) {
			case ADAPTABLE -> "severity_adapted.png";
			case DEGRADED -> "severity_warning.png";
			case BLOCKING -> "severity_critical.png";
		};
		return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
			"kohs_inventory_tweaks",
			"textures/gui/compatibility/" + fileName
		);
	}

	private static int severityColor(final CompatibilityIssue.Severity severity) {
		return switch (severity) {
			case ADAPTABLE -> UiTheme.ACCENT_BRIGHT;
			case DEGRADED -> UiTheme.WARNING;
			case BLOCKING -> UiTheme.DANGER;
		};
	}

	private static float smooth(final float value) {
		return value * value * (3.0F - 2.0F * value);
	}

	private static List<Particle> createParticles() {
		Random random = new Random(0x4D4F555345L);
		List<Particle> particles = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			particles.add(new Particle(
				random.nextFloat(),
				random.nextFloat(),
				0.012F + random.nextFloat() * 0.026F,
				random.nextFloat() * 6.28318F,
				1 + random.nextInt(2)
			));
		}
		return particles;
	}

	private static final class Particle {
		private final float x;
		private final float y;
		private final float speed;
		private final int size;
		private float phase;

		private Particle(final float x, final float y, final float speed, final float phase, final int size) {
			this.x = x;
			this.y = y;
			this.speed = speed;
			this.phase = phase;
			this.size = size;
		}

		private void tick() {
			this.phase += this.speed;
		}

		private void draw(
			final GuiGraphics graphics,
			final int panelX,
			final int panelY,
			final int panelWidth,
			final int panelHeight,
			final float progress
		) {
			int px = panelX + Math.round(this.x * panelWidth + (float) Math.sin(this.phase) * 5.0F);
			int py = panelY + Math.round(this.y * panelHeight + (float) Math.cos(this.phase * 0.8F) * 4.0F);
			int particleAlpha = Math.round((34.0F + 46.0F * (0.5F + 0.5F * (float) Math.sin(this.phase * 1.7F))) * progress);
			int color = UiRender.withAlpha(UiTheme.ACCENT_BRIGHT, particleAlpha);
			graphics.fill(px - this.size, py, px + this.size + 1, py + 1, color);
			graphics.fill(px, py - this.size, px + 1, py + this.size + 1, color);
		}
	}
}
