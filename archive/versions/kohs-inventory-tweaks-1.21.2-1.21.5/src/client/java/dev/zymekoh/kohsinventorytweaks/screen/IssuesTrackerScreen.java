package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.compat.StartupFailureRegistry;
import dev.zymekoh.kohsinventorytweaks.compat.StartupFailureRegistry.Failure;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class IssuesTrackerScreen extends Screen {
	private static final long ENTRANCE_NANOS = 280_000_000L;
	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());
	private final Screen parent;
	private final List<CompatibilityIssue> issues = CompatibilityIssueManager.issues();
	private final List<Failure> failures = StartupFailureRegistry.entries();
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final ModIconSet icons = new ModIconSet();
	private final long openedAtNanos = System.nanoTime();
	private final SmoothScroll issueSmoothScroll = new SmoothScroll();
	private final SmoothScroll failureSmoothScroll = new SmoothScroll();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int bodyTop;
	private int bodyBottom;
	private int issueX;
	private int issueY;
	private int issueWidth;
	private int issueHeight;
	private int failureX;
	private int failureY;
	private int failureWidth;
	private int failureHeight;
	private int issueScroll;
	private int failureScroll;
	private int issueMaxScroll;
	private int failureMaxScroll;

	public IssuesTrackerScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.issues_tracker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.ensureParticles();
		int horizontalMargin = this.width < 420 ? 6 : this.width < 700 ? 16 : 42;
		int verticalMargin = this.height < 280 ? 6 : 18;
		this.panelWidth = Math.min(900, Math.max(1, this.width - horizontalMargin * 2));
		this.panelHeight = Math.min(500, Math.max(1, this.height - verticalMargin * 2));
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.bodyTop = this.panelY + 40;
		this.bodyBottom = this.panelY + this.panelHeight - 38;

		int gap = this.panelWidth < 520 ? 6 : 10;
		if (this.panelWidth >= 520) {
			this.issueX = this.panelX + 8;
			this.issueY = this.bodyTop;
			this.issueWidth = Math.max(1, (this.panelWidth - 16 - gap) / 2);
			this.issueHeight = Math.max(1, this.bodyBottom - this.bodyTop);
			this.failureX = this.issueX + this.issueWidth + gap;
			this.failureY = this.bodyTop;
			this.failureWidth = Math.max(1, this.panelX + this.panelWidth - 8 - this.failureX);
			this.failureHeight = this.issueHeight;
		} else {
			int availableHeight = Math.max(2, this.bodyBottom - this.bodyTop - gap);
			this.issueX = this.panelX + 6;
			this.issueY = this.bodyTop;
			this.issueWidth = Math.max(1, this.panelWidth - 12);
			this.issueHeight = Math.max(1, availableHeight / 2);
			this.failureX = this.issueX;
			this.failureY = this.issueY + this.issueHeight + gap;
			this.failureWidth = this.issueWidth;
			this.failureHeight = Math.max(1, this.bodyBottom - this.failureY);
		}

		this.issueMaxScroll = this.calculateIssueMaximum();
		this.failureMaxScroll = this.calculateFailureMaximum();
		this.issueSmoothScroll.setMaximum(this.issueMaxScroll);
		this.failureSmoothScroll.setMaximum(this.failureMaxScroll);
		this.issueScroll = this.issueSmoothScroll.roundedPosition();
		this.failureScroll = this.failureSmoothScroll.roundedPosition();
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelWidth - Math.min(104, this.panelWidth - 16),
			this.panelY + this.panelHeight - 29,
			Math.min(94, this.panelWidth - 16),
			21,
			Component.translatable("gui.back"),
			button -> this.onClose(),
			GlassButton.Variant.PRIMARY
		));
	}

	@Override
	public void tick() {
		for (FloatingParticle particle : this.particles) {
			particle.tick(this.width, this.height);
		}
	}

	@Override
	public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
		graphics.fillGradient(0, 0, this.width, this.height, UiTheme.BACKDROP_TOP, UiTheme.BACKDROP_BOTTOM);
	}

	@Override
	public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
		this.issueSmoothScroll.update();
		this.failureSmoothScroll.update();
		this.issueScroll = this.issueSmoothScroll.roundedPosition();
		this.failureScroll = this.failureSmoothScroll.roundedPosition();
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}
		float progress = cubicProgress(this.openedAtNanos, ENTRANCE_NANOS);
		float scale = 0.965F + progress * 0.035F;
		graphics.pose().pushPose();
		graphics.pose().translate(this.width / 2.0F, this.height / 2.0F, 0.0F);
		graphics.pose().scale(scale, scale, 1.0F);
		graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F, 0.0F);
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelY + 10, UiTheme.TEXT);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.summary", this.issues.size()),
			this.width / 2,
			this.panelY + 22,
			UiTheme.TEXT_MUTED
		);
		this.drawIssueRegion(graphics);
		this.drawFailureRegion(graphics);
		super.render(graphics, mouseX, mouseY, a);
		graphics.pose().popPose();
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (inside(x, y, this.failureX, this.failureY, this.failureWidth, this.failureHeight)) {
			this.failureSmoothScroll.scroll(scrollY, 22.0);
			return true;
		}
		if (inside(x, y, this.issueX, this.issueY, this.issueWidth, this.issueHeight)) {
			this.issueSmoothScroll.scroll(scrollY, 22.0);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.icons.close();
	}

	private void drawIssueRegion(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.issueX, this.issueY, this.issueWidth, this.issueHeight, 7, UiTheme.GLASS_LIGHT, UiTheme.BORDER);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.detected"),
			this.issueX + this.issueWidth / 2,
			this.issueY + 6,
			UiTheme.TEXT
		);
		int contentTop = this.issueY + 20;
		int contentBottom = this.issueY + this.issueHeight - 4;
		if (this.issues.isEmpty()) {
			this.drawEmptyRegion(
				graphics,
				this.issueX,
				contentTop,
				this.issueWidth,
				Math.max(1, contentBottom - contentTop),
				"screen.kohs_inventory_tweaks.issues_tracker.none",
				"screen.kohs_inventory_tweaks.issues_tracker.none.description"
			);
			return;
		}
		graphics.enableScissor(this.issueX + 4, contentTop, this.issueX + this.issueWidth - 4, contentBottom);
		int y = contentTop + 3 - this.issueScroll;
		int cardWidth = Math.max(1, this.issueWidth - 16);
		for (CompatibilityIssue issue : this.issues) {
			int height = this.issueCardHeight(issue, cardWidth);
			this.drawIssueCard(graphics, issue, this.issueX + 7, y, cardWidth, height);
			y += height + 7;
		}
		graphics.disableScissor();
		this.drawScrollbar(graphics, this.issueX + this.issueWidth - 5, contentTop, contentBottom, this.issueScroll, this.issueMaxScroll);
	}

	private void drawFailureRegion(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.failureX, this.failureY, this.failureWidth, this.failureHeight, 7, UiTheme.GLASS_LIGHT, UiTheme.DANGER);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.startup_log", this.failures.size()),
			this.failureX + this.failureWidth / 2,
			this.failureY + 6,
			UiTheme.TEXT
		);
		int contentTop = this.failureY + 20;
		int contentBottom = this.failureY + this.failureHeight - 4;
		if (this.failures.isEmpty()) {
			this.drawEmptyRegion(
				graphics,
				this.failureX,
				contentTop,
				this.failureWidth,
				Math.max(1, contentBottom - contentTop),
				"screen.kohs_inventory_tweaks.issues_tracker.startup_log.none",
				"screen.kohs_inventory_tweaks.issues_tracker.startup_log.none.description"
			);
			return;
		}
		graphics.enableScissor(this.failureX + 4, contentTop, this.failureX + this.failureWidth - 4, contentBottom);
		int y = contentTop + 3 - this.failureScroll;
		int cardWidth = Math.max(1, this.failureWidth - 16);
		for (Failure failure : this.failures) {
			int height = this.failureCardHeight(failure, cardWidth);
			this.drawFailureCard(graphics, failure, this.failureX + 7, y, cardWidth, height);
			y += height + 7;
		}
		graphics.disableScissor();
		this.drawScrollbar(graphics, this.failureX + this.failureWidth - 5, contentTop, contentBottom, this.failureScroll, this.failureMaxScroll);
	}

	private void drawEmptyRegion(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final String titleKey,
		final String descriptionKey
	) {
		int centerY = y + Math.max(2, height / 2 - 12);
		graphics.drawCenteredString(this.font, Component.translatable(titleKey), x + width / 2, centerY, UiTheme.TEXT);
		if (height >= 46 && width >= 120) {
			graphics.drawWordWrap(
				this.font,
				Component.translatable(descriptionKey),
				x + 10,
				centerY + 14,
				Math.max(20, width - 20),
				UiTheme.TEXT_MUTED
			);
		}
	}

	private void drawIssueCard(
		final GuiGraphics graphics,
		final CompatibilityIssue issue,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		int severityColor = CompatibilitySeverityIcons.colorFor(issue.severity());
		UiRender.panel(graphics, x, y, width, height, 7, UiTheme.GLASS, severityColor);
		this.drawModIcon(graphics, issue.modId(), x + 7, y + 8, 28);
		int textX = x + 42;
		int textWidth = Math.max(30, width - 49);
		Component status = Component.translatable(CompatibilitySeverityIcons.statusKey(issue.severity()));
		int severityIconSize = 14;
		int severityIconX = x + width - 7 - severityIconSize;
		int statusX = severityIconX - 3 - this.font.width(status);
		String displayName = this.font.plainSubstrByWidth(issue.modName(), Math.max(12, statusX - textX - 4));
		graphics.drawString(this.font, Component.literal(displayName), textX, y + 7, UiTheme.TEXT, false);
		graphics.blit(
			RenderType::guiTextured,
			CompatibilitySeverityIcons.textureFor(issue.severity()),
			severityIconX,
			y + 4,
			0.0F,
			0.0F,
			severityIconSize,
			severityIconSize,
			128,
			128,
			128,
			128
		);
		graphics.drawString(this.font, status, statusX, y + 7, severityColor, false);
		graphics.drawString(this.font, Component.literal(issue.modId() + "  " + issue.version()), textX, y + 18, UiTheme.TEXT_MUTED, false);
		graphics.drawString(this.font, Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.creator", issue.creators()), textX, y + 29, UiTheme.TEXT_DISABLED, false);
		int reasonY = y + 43;
		Component reason = Component.translatable(issue.reason().translationKey());
		graphics.drawWordWrap(this.font, reason, x + 8, reasonY, width - 16, UiTheme.TEXT_MUTED);
		int pointsY = reasonY + this.font.split(reason, width - 16).size() * 10 + 3;
		graphics.drawWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.points", String.join(", ", issue.conflictPoints())),
			x + 8,
			pointsY,
			width - 16,
			UiTheme.TEXT_DISABLED
		);
	}

	private void drawFailureCard(
		final GuiGraphics graphics,
		final Failure failure,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		UiRender.panel(graphics, x, y, width, height, 7, UiTheme.GLASS, UiTheme.DANGER);
		this.drawModIcon(graphics, failure.modId(), x + 7, y + 8, 28);
		int textX = x + 42;
		int textWidth = Math.max(30, width - 49);
		Component source = Component.translatable("CRASH_REPORT".equals(failure.sourceType())
			? "screen.kohs_inventory_tweaks.issues_tracker.startup_log.crash"
			: "screen.kohs_inventory_tweaks.issues_tracker.startup_log.prevented");
		int severityIconSize = 14;
		int severityIconX = x + width - 7 - severityIconSize;
		int sourceX = severityIconX - 3 - this.font.width(source);
		String displayName = this.font.plainSubstrByWidth(failure.modName(), Math.max(12, sourceX - textX - 4));
		graphics.drawString(this.font, Component.literal(displayName), textX, y + 7, UiTheme.TEXT, false);
		graphics.blit(
			RenderType::guiTextured,
			CompatibilitySeverityIcons.textureFor(CompatibilityIssue.Severity.BLOCKING),
			severityIconX,
			y + 4,
			0.0F,
			0.0F,
			severityIconSize,
			severityIconSize,
			128,
			128,
			128,
			128
		);
		graphics.drawString(this.font, source, sourceX, y + 7, UiTheme.DANGER, false);
		graphics.drawString(this.font, Component.literal(failure.modId() + "  " + failure.modVersion()), textX, y + 18, UiTheme.TEXT_MUTED, false);
		graphics.drawString(
			this.font,
			Component.translatable(
				"screen.kohs_inventory_tweaks.issues_tracker.startup_log.when",
				LOG_TIME.format(failure.lastSeenInstant()),
				failure.minecraftVersion(),
				failure.occurrences()
			),
			textX,
			y + 29,
			UiTheme.TEXT_DISABLED,
			false
		);
		Component reason = this.failureReason(failure);
		int reasonY = y + 43;
		graphics.drawWordWrap(this.font, reason, x + 8, reasonY, width - 16, UiTheme.TEXT_MUTED);
		int detailsY = reasonY + this.font.split(reason, width - 16).size() * 10 + 3;
		graphics.drawWordWrap(this.font, Component.literal(failure.details()), x + 8, detailsY, width - 16, UiTheme.TEXT_DISABLED);
	}

	private void drawModIcon(final GuiGraphics graphics, final String modId, final int x, final int y, final int size) {
		ResourceLocation icon = this.icons.load(modId);
		if (icon != null) {
			graphics.blit(RenderType::guiTextured, icon, x, y, 0.0F, 0.0F, size, size, size, size);
		} else {
			UiRender.panel(graphics, x, y, size, size, 6, UiTheme.GLASS_SELECTED, UiTheme.ACCENT_SOFT);
			graphics.drawCenteredString(this.font, Component.literal("!"), x + size / 2, y + (size - 8) / 2, UiTheme.WARNING);
		}
	}

	private Component failureReason(final Failure failure) {
		if (!failure.reasonCode().isBlank()) {
			try {
				return Component.translatable(CompatibilityIssue.Reason.valueOf(failure.reasonCode()).translationKey());
			} catch (IllegalArgumentException ignored) {
				// Old or external entries retain their literal reason below.
			}
		}
		return Component.literal(failure.reasonText());
	}

	private int calculateIssueMaximum() {
		int cardWidth = Math.max(1, this.issueWidth - 16);
		int contentHeight = 0;
		for (CompatibilityIssue issue : this.issues) {
			contentHeight += this.issueCardHeight(issue, cardWidth) + 7;
		}
		return Math.max(0, contentHeight - Math.max(1, this.issueHeight - 27));
	}

	private int calculateFailureMaximum() {
		int cardWidth = Math.max(1, this.failureWidth - 16);
		int contentHeight = 0;
		for (Failure failure : this.failures) {
			contentHeight += this.failureCardHeight(failure, cardWidth) + 7;
		}
		return Math.max(0, contentHeight - Math.max(1, this.failureHeight - 27));
	}

	private int issueCardHeight(final CompatibilityIssue issue, final int width) {
		int textWidth = Math.max(20, width - 16);
		Component reason = Component.translatable(issue.reason().translationKey());
		Component points = Component.translatable(
			"screen.kohs_inventory_tweaks.issues_tracker.points",
			String.join(", ", issue.conflictPoints())
		);
		return Math.max(
			82,
			43 + this.font.split(reason, textWidth).size() * 10
				+ 3 + this.font.split(points, textWidth).size() * 10 + 8
		);
	}

	private int failureCardHeight(final Failure failure, final int width) {
		int textWidth = Math.max(20, width - 16);
		return Math.max(
			82,
			43 + this.font.split(this.failureReason(failure), textWidth).size() * 10
				+ 3 + this.font.split(Component.literal(failure.details()), textWidth).size() * 10 + 8
		);
	}

	private void drawScrollbar(
		final GuiGraphics graphics,
		final int x,
		final int top,
		final int bottom,
		final int scroll,
		final int maximum
	) {
		if (maximum <= 0) {
			return;
		}
		int height = Math.max(1, bottom - top);
		int thumbHeight = Math.max(12, height * height / (height + maximum));
		int travel = Math.max(1, height - thumbHeight);
		int y = top + scroll * travel / maximum;
		graphics.fill(x, top, x + 2, bottom, UiTheme.SCROLL_TRACK);
		graphics.fill(x - 1, y, x + 3, y + thumbHeight, UiTheme.ACCENT);
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x495353554553L);
		int count = Mth.clamp(this.width * this.height / 6800, 28, 50);
		for (int i = 0; i < count; i++) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.09F + random.nextFloat() * 0.20F,
				0.010F + random.nextFloat() * 0.030F,
				1 + random.nextInt(2),
				78 + random.nextInt(118),
				random.nextFloat() * 6.28318F
			));
		}
	}

	private static boolean inside(
		final double x,
		final double y,
		final int areaX,
		final int areaY,
		final int areaWidth,
		final int areaHeight
	) {
		return x >= areaX && x < areaX + areaWidth && y >= areaY && y < areaY + areaHeight;
	}

	private static float cubicProgress(final long startedAt, final long duration) {
		float linear = Mth.clamp((System.nanoTime() - startedAt) / (float) duration, 0.0F, 1.0F);
		float remaining = 1.0F - linear;
		return 1.0F - remaining * remaining * remaining;
	}
}
