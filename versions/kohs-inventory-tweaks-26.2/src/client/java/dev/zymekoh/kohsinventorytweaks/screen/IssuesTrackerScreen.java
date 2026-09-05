package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import dev.zymekoh.kohsinventorytweaks.render.VisualPerformanceController;

public final class IssuesTrackerScreen extends Screen {
	private static final long ENTRANCE_NANOS = 280_000_000L;
	private final Screen parent;
	private final List<CompatibilityIssue> issues = CompatibilityIssueManager.issues();
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final ModIconSet icons = new ModIconSet();
	private final long openedAtNanos = System.nanoTime();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int bodyTop;
	private int bodyBottom;
	private final SmoothScroll smoothScroll = new SmoothScroll();
	private int scroll;
	private int maxScroll;

	public IssuesTrackerScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.issues_tracker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.ensureParticles();
		int horizontalMargin = this.width < 420 ? 6 : this.width < 700 ? 16 : 42;
		int verticalMargin = this.height < 280 ? 6 : 18;
		this.panelWidth = Math.min(760, Math.max(1, this.width - horizontalMargin * 2));
		this.panelHeight = Math.min(440, Math.max(1, this.height - verticalMargin * 2));
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.bodyTop = this.panelY + 38;
		this.bodyBottom = this.panelY + this.panelHeight - 38;
		int contentHeight = 0;
		for (CompatibilityIssue issue : this.issues) {
			contentHeight += this.cardHeight(issue) + 8;
		}
		this.maxScroll = Math.max(0, contentHeight - Math.max(1, this.bodyBottom - this.bodyTop - 4));
		this.smoothScroll.setMaximum(this.maxScroll);
		this.scroll = this.smoothScroll.roundedPosition();
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
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		graphics.fillGradient(0, 0, this.width, this.height, UiTheme.BACKDROP_TOP, UiTheme.BACKDROP_BOTTOM);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.smoothScroll.update();
		this.scroll = this.smoothScroll.roundedPosition();
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}
		float progress = cubicProgress(this.openedAtNanos, ENTRANCE_NANOS);
		float scale = 0.965F + progress * 0.035F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.centeredText(this.font, this.title, this.width / 2, this.panelY + 11, UiTheme.TEXT);
		graphics.centeredText(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.summary", this.issues.size()),
			this.width / 2,
			this.panelY + 23,
			UiTheme.TEXT_MUTED
		);
		if (this.issues.isEmpty()) {
			this.drawEmptyState(graphics);
		} else {
			this.drawIssues(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.pose().popMatrix();
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (x >= this.panelX && x < this.panelX + this.panelWidth && y >= this.bodyTop && y < this.bodyBottom) {
			this.smoothScroll.scroll(scrollY, 22.0);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.icons.close();
	}

	private void drawEmptyState(final GuiGraphicsExtractor graphics) {
		int centerX = this.width / 2;
		int centerY = (this.bodyTop + this.bodyBottom) / 2;
		UiRender.glow(graphics, centerX - 17, centerY - 31, 34, 34, 10, 40);
		UiRender.panel(graphics, centerX - 15, centerY - 29, 30, 30, 8, UiTheme.GLASS_SELECTED, UiTheme.ACCENT_SOFT);
		graphics.fill(centerX - 7, centerY - 15, centerX - 2, centerY - 10, UiTheme.ACCENT_BRIGHT);
		graphics.fill(centerX - 3, centerY - 11, centerX + 8, centerY - 6, UiTheme.ACCENT_BRIGHT);
		graphics.centeredText(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.none"),
			centerX,
			centerY + 10,
			UiTheme.TEXT
		);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.none.description"),
			this.panelX + 24,
			centerY + 24,
			this.panelWidth - 48,
			UiTheme.TEXT_MUTED
		);
	}

	private void drawIssues(final GuiGraphicsExtractor graphics) {
		graphics.enableScissor(this.panelX + 8, this.bodyTop, this.panelX + this.panelWidth - 8, this.bodyBottom);
		int y = this.bodyTop + 3 - this.scroll;
		for (CompatibilityIssue issue : this.issues) {
			int height = this.cardHeight(issue);
			this.drawIssueCard(graphics, issue, this.panelX + 12, y, this.panelWidth - 24, height);
			y += height + 8;
		}
		graphics.disableScissor();
		this.drawScrollbar(graphics);
	}

	private void drawIssueCard(
		final GuiGraphicsExtractor graphics,
		final CompatibilityIssue issue,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		int severityColor = CompatibilitySeverityIcons.colorFor(issue.severity());
		UiRender.panel(
			graphics,
			x,
			y,
			width,
			height,
			8,
			UiTheme.GLASS_LIGHT,
			severityColor
		);
		int iconSize = width < 300 ? 30 : 40;
		Identifier icon = this.icons.load(issue.modId());
		if (icon != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x + 9, y + 10, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
		} else {
			UiRender.panel(graphics, x + 9, y + 10, iconSize, iconSize, 6, UiTheme.GLASS_SELECTED, UiTheme.ACCENT_SOFT);
			graphics.centeredText(this.font, Component.literal("!"), x + 9 + iconSize / 2, y + 10 + (iconSize - 8) / 2, UiTheme.WARNING);
		}
		int textX = x + iconSize + 18;
		int textWidth = Math.max(48, width - iconSize - 28);
		Component status = Component.translatable(CompatibilitySeverityIcons.statusKey(issue.severity()));
		int statusWidth = this.font.width(status);
		int severityIconSize = 14;
		int severityIconX = x + width - 8 - severityIconSize;
		int statusX = severityIconX - 4 - statusWidth;
		String displayName = this.font.plainSubstrByWidth(issue.modName(), Math.max(12, statusX - textX - 5));
		graphics.text(this.font, Component.literal(displayName), textX, y + 9, UiTheme.TEXT, false);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			CompatibilitySeverityIcons.textureFor(issue.severity()),
			severityIconX,
			y + 6,
			0.0F,
			0.0F,
			severityIconSize,
			severityIconSize,
			128,
			128,
			128,
			128
		);
		graphics.text(
			this.font,
			status,
			statusX,
			y + 9,
			severityColor,
			false
		);
		graphics.text(
			this.font,
			Component.literal(issue.modId() + "  " + issue.version()),
			textX,
			y + 20,
			UiTheme.TEXT_MUTED,
			false
		);
		graphics.text(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.creator", issue.creators()),
			textX,
			y + 31,
			UiTheme.TEXT_DISABLED,
			false
		);
		int reasonY = y + Math.max(48, iconSize + 15);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable(issue.reason().translationKey()),
			x + 10,
			reasonY,
			width - 20,
			UiTheme.TEXT_MUTED
		);
		String points = String.join(", ", issue.conflictPoints());
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.points", points),
			x + 10,
			reasonY + this.font.split(Component.translatable(issue.reason().translationKey()), width - 20).size() * 10 + 3,
			width - 20,
			UiTheme.TEXT_DISABLED
		);
	}

	private int cardHeight(final CompatibilityIssue issue) {
		int cardWidth = Math.max(1, this.panelWidth - 24);
		int textWidth = Math.max(1, cardWidth - 20);
		int iconSize = cardWidth < 300 ? 30 : 40;
		int reasonTop = Math.max(48, iconSize + 15);
		int reasonLines = this.font.split(Component.translatable(issue.reason().translationKey()), textWidth).size();
		int pointLines = this.font.split(
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.points", String.join(", ", issue.conflictPoints())),
			textWidth
		).size();
		return Math.max(86, reasonTop + reasonLines * 10 + 3 + pointLines * 10 + 8);
	}

	private void drawScrollbar(final GuiGraphicsExtractor graphics) {
		if (this.maxScroll <= 0) {
			return;
		}
		int height = this.bodyBottom - this.bodyTop;
		int thumbHeight = Math.max(14, height * height / (height + this.maxScroll));
		int travel = Math.max(1, height - thumbHeight);
		int y = this.bodyTop + this.scroll * travel / this.maxScroll;
		graphics.fill(this.panelX + this.panelWidth - 7, this.bodyTop, this.panelX + this.panelWidth - 5, this.bodyBottom, UiTheme.SCROLL_TRACK);
		graphics.fill(this.panelX + this.panelWidth - 8, y, this.panelX + this.panelWidth - 4, y + thumbHeight, UiTheme.ACCENT);
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x495353554553L);
		int count = VisualPerformanceController.particleCount(Mth.clamp(this.width * this.height / 6800, 28, 50));
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

	private static float cubicProgress(final long startedAt, final long duration) {
		float linear = Mth.clamp((System.nanoTime() - startedAt) / (float) duration, 0.0F, 1.0F);
		float remaining = 1.0F - linear;
		return 1.0F - remaining * remaining * remaining;
	}
}
