package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.ui.ZBackdrop;
import dev.zymekoh.kohsinventorytweaks.ui.ZChrome;
import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZMotion;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class IssuesTrackerScreen extends Screen {
	private static final long ENTRANCE_NANOS = 280_000_000L;
	private final Screen parent;
	private final List<CompatibilityIssue> issues = CompatibilityIssueManager.issues();
	private final ZBackdrop backdrop = new ZBackdrop();
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
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.backdrop.draw(graphics, this.width, this.height, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.smoothScroll.update();
		this.scroll = this.smoothScroll.roundedPosition();
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		ZChrome.screenTitle(
			graphics,
			this.font,
			this.title,
			Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.summary", this.issues.size()),
			this.width / 2,
			this.panelY + 9,
			this.panelWidth
		);
		if (this.issues.isEmpty()) {
			this.drawEmptyState(graphics);
		} else {
			this.drawIssues(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		ZChrome.openingVeil(graphics, this.width, this.height, ZMotion.progress(this.openedAtNanos, ENTRANCE_NANOS));
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
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.icons.close();
	}

	private void drawEmptyState(final GuiGraphicsExtractor graphics) {
		int centerX = this.width / 2;
		int centerY = (this.bodyTop + this.bodyBottom) / 2;
		ZDraw.glow(graphics, centerX - 17, centerY - 33, 34, 34, UiTheme.ACCENT, Math.round(28 + 22 * ZMotion.pulse(2.6F)));
		UiRender.panel(graphics, centerX - 17, centerY - 33, 34, 34, 8, UiTheme.GLASS_SELECTED, UiTheme.ACCENT_SOFT);
		CompatibilitySeverityIcons.draw(graphics, CompatibilityIssue.Severity.ADAPTABLE, centerX - 16, centerY - 32, 32);
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
			ZTheme.fade(severityColor, 0.75F)
		);
		// The severity also runs down the card's edge, next to its name and shape.
		graphics.fill(x + 2, y + 6, x + 4, y + height - 6, severityColor);
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
		int severityIconSize = 16;
		int severityIconX = x + width - 8 - severityIconSize;
		int statusX = severityIconX - 4 - statusWidth;
		String displayName = this.font.plainSubstrByWidth(issue.modName(), Math.max(12, statusX - textX - 5));
		graphics.text(this.font, Component.literal(displayName), textX, y + 9, UiTheme.TEXT, false);
		CompatibilitySeverityIcons.draw(graphics, issue.severity(), severityIconX, y + 5, severityIconSize);
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
		ZDraw.scrollbar(graphics, this.panelX + this.panelWidth - 8, this.bodyTop, this.bodyBottom - this.bodyTop,
			this.scroll, this.maxScroll);
		UiRender.scrollFade(graphics, this.panelX + 8, this.bodyTop, this.panelWidth - 16, this.bodyBottom - this.bodyTop,
			this.scroll > 0, this.scroll < this.maxScroll);
	}
}
