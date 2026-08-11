package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class BlockingCompatibilityScreen extends Screen {
	private static final long MOTION_NANOS = 280_000_000L;
	private final List<CompatibilityIssue> issues = CompatibilityIssueManager.blockingIssues();
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private final long openedAtNanos = System.nanoTime();
	private boolean stopping;
	private long stoppingAtNanos;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int bodyX;
	private int bodyTop;
	private int bodyWidth;
	private int bodyHeight;
	private final SmoothScroll smoothScroll = new SmoothScroll();
	private int scroll;
	private int maxScroll;

	public BlockingCompatibilityScreen() {
		super(Component.translatable("screen.kohs_inventory_tweaks.blocking.title"));
	}

	@Override
	protected void init() {
		this.ensureParticles();
		int horizontalMargin = Mth.clamp(this.width / 24, 4, 24);
		int verticalMargin = Mth.clamp(this.height / 24, 4, 18);
		int maximumWidth = Math.max(1, this.width - horizontalMargin * 2);
		int maximumHeight = Math.max(1, this.height - verticalMargin * 2);
		this.panelWidth = Math.min(650, maximumWidth);
		this.panelHeight = Math.min(430, maximumHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		int contentMargin = Mth.clamp(this.panelWidth / 28, 6, 16);
		this.bodyX = this.panelX + contentMargin;
		this.bodyTop = this.panelY + (this.panelHeight < 210 ? 34 : 44);
		this.bodyWidth = Math.max(1, this.panelWidth - contentMargin * 2);
		this.bodyHeight = Math.max(20, this.panelY + this.panelHeight - 42 - this.bodyTop);
		this.prepareText();
		int buttonWidth = Math.max(1, Math.min(220, this.panelWidth - contentMargin * 2));
		this.addRenderableWidget(new GlassButton(
			this.panelX + (this.panelWidth - buttonWidth) / 2,
			this.panelY + this.panelHeight - 29,
			buttonWidth,
			21,
			Component.translatable("screen.kohs_inventory_tweaks.blocking.close"),
			button -> this.beginStop(),
			GlassButton.Variant.DANGER
		));
	}

	@Override
	public void tick() {
		for (FloatingParticle particle : this.particles) {
			particle.tick(this.width, this.height);
		}
		if (this.stopping && System.nanoTime() - this.stoppingAtNanos >= MOTION_NANOS) {
			this.minecraft.stop();
		}
	}

	@Override
	public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
		graphics.fillGradient(0, 0, this.width, this.height, 0xE008030D, 0xF215061F);
	}

	@Override
	public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
		this.smoothScroll.update();
		this.scroll = this.smoothScroll.roundedPosition();
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}
		float progress = this.stopping
			? 1.0F - cubicProgress(this.stoppingAtNanos, MOTION_NANOS)
			: cubicProgress(this.openedAtNanos, MOTION_NANOS);
		float scale = 0.94F + progress * 0.06F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
		UiRender.glow(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 12, 52);
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 11, 0xE8160B27, UiTheme.DANGER);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelY + 11, UiTheme.WARNING);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.blocking.subtitle"),
			this.width / 2,
			this.panelY + 23,
			UiTheme.TEXT_MUTED
		);
		UiRender.panel(graphics, this.bodyX, this.bodyTop, this.bodyWidth, this.bodyHeight, 7, UiTheme.PREVIEW_GLASS, UiTheme.BORDER_SOFT);
		this.drawBody(graphics);
		super.render(graphics, mouseX, mouseY, a);
		graphics.pose().popMatrix();
		if (progress < 1.0F) {
			graphics.fill(0, 0, this.width, this.height, UiRender.withAlpha(0x08030D, Math.round((1.0F - progress) * 118.0F)));
		}
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (x >= this.bodyX && x < this.bodyX + this.bodyWidth && y >= this.bodyTop && y < this.bodyTop + this.bodyHeight) {
			this.smoothScroll.scroll(scrollY, 18.0);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public void onClose() {
		this.beginStop();
	}

	private void prepareText() {
		this.lines.clear();
		int textWidth = Math.max(24, this.bodyWidth - 18);
		this.addWrapped(Component.translatable("screen.kohs_inventory_tweaks.blocking.intro"), textWidth);
		this.lines.add(FormattedCharSequence.EMPTY);
		for (CompatibilityIssue issue : this.issues) {
			this.addWrapped(
				Component.literal("• " + issue.modName() + " (" + issue.modId() + " " + issue.version() + ")"),
				textWidth
			);
			this.addWrapped(
				Component.translatable("screen.kohs_inventory_tweaks.blocking.creator", issue.creators()),
				textWidth
			);
			this.addWrapped(Component.translatable(blockingReasonKey(issue)), textWidth);
			this.addWrapped(
				Component.translatable(
					"screen.kohs_inventory_tweaks.blocking.points",
					String.join(", ", issue.conflictPoints())
				),
				textWidth
			);
			this.lines.add(FormattedCharSequence.EMPTY);
		}
		this.addWrapped(Component.translatable("screen.kohs_inventory_tweaks.blocking.shutdown"), textWidth);
		int contentHeight = this.lines.size() * 10 + 10;
		this.maxScroll = Math.max(0, contentHeight - Math.max(1, this.bodyHeight - 10));
		this.smoothScroll.setMaximum(this.maxScroll);
		this.scroll = this.smoothScroll.roundedPosition();
	}

	private void addWrapped(final Component text, final int width) {
		this.lines.addAll(this.font.split(text, width));
	}

	private static String blockingReasonKey(final CompatibilityIssue issue) {
		return switch (issue.reason()) {
			case REDIRECT_COLLISION -> "screen.kohs_inventory_tweaks.blocking.reason.redirect_collision";
			case CRITICAL_OVERWRITE -> "screen.kohs_inventory_tweaks.blocking.reason.critical_overwrite";
			case DIRECT_MUTATION -> "screen.kohs_inventory_tweaks.blocking.reason.direct_mutation";
		};
	}

	private void drawBody(final GuiGraphics graphics) {
		graphics.enableScissor(this.bodyX + 1, this.bodyTop + 1, this.bodyX + this.bodyWidth - 1, this.bodyTop + this.bodyHeight - 1);
		int y = this.bodyTop + 6 - this.scroll;
		for (FormattedCharSequence line : this.lines) {
			graphics.drawString(this.font, line, this.bodyX + 7, y, UiTheme.TEXT_MUTED);
			y += 10;
		}
		graphics.disableScissor();
		if (this.maxScroll > 0) {
			int trackTop = this.bodyTop + 3;
			int trackHeight = Math.max(1, this.bodyHeight - 6);
			int thumbHeight = Math.max(10, trackHeight * trackHeight / (trackHeight + this.maxScroll));
			int thumbY = trackTop + this.scroll * Math.max(1, trackHeight - thumbHeight) / this.maxScroll;
			graphics.fill(this.bodyX + this.bodyWidth - 5, trackTop, this.bodyX + this.bodyWidth - 3, trackTop + trackHeight, UiTheme.SCROLL_TRACK);
			graphics.fill(this.bodyX + this.bodyWidth - 6, thumbY, this.bodyX + this.bodyWidth - 2, thumbY + thumbHeight, UiTheme.DANGER);
		}
	}

	private void beginStop() {
		if (this.stopping) {
			return;
		}
		this.stopping = true;
		this.stoppingAtNanos = System.nanoTime();
		for (var child : this.children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
				widget.active = false;
			}
		}
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x424C4F434B45444CL);
		int count = Mth.clamp(this.width * this.height / 4300, 44, 78);
		for (int i = 0; i < count; i++) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.11F + random.nextFloat() * 0.25F,
				0.012F + random.nextFloat() * 0.038F,
				1 + random.nextInt(3),
				95 + random.nextInt(135),
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
