package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityNoticeController;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public final class CompatibilityNoticeScreen extends Screen {
	private static final long MOTION_NANOS = 280_000_000L;
	private final Screen parent;
	private final List<CompatibilityIssue> issues = CompatibilityIssueManager.issues();
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final ModIconSet icons = new ModIconSet();
	private final long openedAtNanos = System.nanoTime();
	private long closingAtNanos;
	private boolean closing;
	private int frameX;
	private int frameY;
	private int frameWidth;
	private int frameHeight;
	private int bodyTop;
	private int bodyBottom;
	private final SmoothScroll smoothScroll = new SmoothScroll();
	private int scroll;
	private int maxScroll;
	private Button continueButton;
	private Button modsFolderButton;

	public CompatibilityNoticeScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.compatibility_notice.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.ensureParticles();
		int margin = Mth.clamp(this.width / 32, 4, 12);
		this.frameX = margin;
		this.frameY = margin;
		this.frameWidth = Math.max(1, this.width - margin * 2);
		this.frameHeight = Math.max(1, this.height - margin * 2);
		this.bodyTop = this.frameY + (this.height < 250 ? 42 : 52);
		this.bodyBottom = this.frameY + this.frameHeight - 44;
		int contentHeight = 34;
		for (CompatibilityIssue issue : this.issues) {
			contentHeight += this.issueHeight(issue) + 7;
		}
		this.maxScroll = Math.max(0, contentHeight - Math.max(1, this.bodyBottom - this.bodyTop));
		this.smoothScroll.setMaximum(this.maxScroll);
		this.scroll = this.smoothScroll.roundedPosition();

		int gap = 7;
		int available = Math.max(1, this.frameWidth - 16);
		int buttonWidth = Math.max(1, (available - gap) / 2);
		int y = this.frameY + this.frameHeight - 29;
		this.modsFolderButton = this.addRenderableWidget(new GlassButton(
			this.frameX + 8,
			y,
			buttonWidth,
			21,
			Component.translatable("screen.kohs_inventory_tweaks.compatibility_notice.mods_folder"),
			button -> Util.getPlatform().openPath(FabricLoader.getInstance().getGameDir().resolve("mods")),
			GlassButton.Variant.DANGER
		));
		this.continueButton = this.addRenderableWidget(new GlassButton(
			this.frameX + 8 + buttonWidth + gap,
			y,
			Math.max(1, available - buttonWidth - gap),
			21,
			Component.translatable("screen.kohs_inventory_tweaks.compatibility_notice.continue"),
			button -> this.beginClose(),
			GlassButton.Variant.PRIMARY
		));
	}

	@Override
	public void tick() {
		for (FloatingParticle particle : this.particles) {
			particle.tick(this.width, this.height);
		}
		if (this.closing && System.nanoTime() - this.closingAtNanos >= MOTION_NANOS) {
			CompatibilityNoticeController.acknowledge();
			this.minecraft.setScreen(this.parent);
		}
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		graphics.fillGradient(0, 0, this.width, this.height, 0xB00B0612, 0xDA0B0612);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.smoothScroll.update();
		this.scroll = this.smoothScroll.roundedPosition();
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}
		float progress = this.closing
			? 1.0F - cubicProgress(this.closingAtNanos, MOTION_NANOS)
			: cubicProgress(this.openedAtNanos, MOTION_NANOS);
		float scale = 0.94F + progress * 0.06F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
		UiRender.glow(graphics, this.frameX, this.frameY, this.frameWidth, this.frameHeight, 12, 44);
		UiRender.panel(graphics, this.frameX, this.frameY, this.frameWidth, this.frameHeight, 11, 0xD0160B27, UiTheme.DANGER);
		graphics.centeredText(this.font, this.title, this.width / 2, this.frameY + 10, UiTheme.WARNING);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.compatibility_notice.description"),
			this.frameX + 12,
			this.frameY + 24,
			this.frameWidth - 24,
			UiTheme.TEXT_MUTED
		);
		this.drawIssueList(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.pose().popMatrix();
		if (progress < 1.0F) {
			graphics.fill(0, 0, this.width, this.height, UiRender.withAlpha(0x0B0612, Math.round((1.0F - progress) * 96.0F)));
		}
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (x >= this.frameX && x < this.frameX + this.frameWidth && y >= this.bodyTop && y < this.bodyBottom) {
			this.smoothScroll.scroll(scrollY, 20.0);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.isEscape()) {
			this.beginClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.beginClose();
	}

	@Override
	public void removed() {
		this.icons.close();
	}

	private void drawIssueList(final GuiGraphicsExtractor graphics) {
		graphics.enableScissor(this.frameX + 7, this.bodyTop, this.frameX + this.frameWidth - 7, this.bodyBottom);
		int y = this.bodyTop - this.scroll;
		for (CompatibilityIssue issue : this.issues) {
			int height = this.issueHeight(issue);
			UiRender.panel(graphics, this.frameX + 10, y, this.frameWidth - 20, height, 7, UiTheme.GLASS_LIGHT, UiTheme.DANGER);
			Identifier icon = this.icons.load(issue.modId());
			int iconSize = Math.min(36, Math.max(22, height - 18));
			if (icon != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, icon, this.frameX + 18, y + 9, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
			} else {
				UiRender.panel(graphics, this.frameX + 18, y + 9, iconSize, iconSize, 5, UiTheme.GLASS_SELECTED, UiTheme.ACCENT_SOFT);
				graphics.centeredText(this.font, Component.literal("!"), this.frameX + 18 + iconSize / 2, y + 9 + (iconSize - 8) / 2, UiTheme.WARNING);
			}
			int textX = this.frameX + 27 + iconSize;
			int textWidth = Math.max(40, this.frameWidth - iconSize - 47);
			graphics.text(this.font, Component.literal(issue.modName()), textX, y + 8, UiTheme.TEXT, false);
			graphics.text(this.font, Component.literal(issue.modId() + "  " + issue.version()), textX, y + 19, UiTheme.TEXT_MUTED, false);
			graphics.text(
				this.font,
				Component.translatable("screen.kohs_inventory_tweaks.issues_tracker.creator", issue.creators()),
				textX,
				y + 30,
				UiTheme.TEXT_DISABLED,
				false
			);
			graphics.textWithWordWrap(
				this.font,
				Component.translatable(issue.reason().translationKey()),
				this.frameX + 18,
				y + 48,
				this.frameWidth - 36,
				UiTheme.TEXT_MUTED
			);
			y += height + 7;
		}
		graphics.disableScissor();
		if (this.maxScroll > 0) {
			int height = this.bodyBottom - this.bodyTop;
			int thumbHeight = Math.max(14, height * height / (height + this.maxScroll));
			int thumbY = this.bodyTop + this.scroll * Math.max(1, height - thumbHeight) / this.maxScroll;
			graphics.fill(this.frameX + this.frameWidth - 6, this.bodyTop, this.frameX + this.frameWidth - 4, this.bodyBottom, UiTheme.SCROLL_TRACK);
			graphics.fill(this.frameX + this.frameWidth - 7, thumbY, this.frameX + this.frameWidth - 3, thumbY + thumbHeight, UiTheme.ACCENT);
		}
	}

	private int issueHeight(final CompatibilityIssue issue) {
		int width = Math.max(60, this.frameWidth - 36);
		return Math.max(80, 54 + this.font.split(Component.translatable(issue.reason().translationKey()), width).size() * 10);
	}

	private void beginClose() {
		if (this.closing) {
			return;
		}
		this.closing = true;
		this.closingAtNanos = System.nanoTime();
		this.continueButton.active = false;
		this.modsFolderButton.active = false;
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x434F4E464C494354L);
		int count = Mth.clamp(this.width * this.height / 4700, 38, 72);
		for (int i = 0; i < count; i++) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.10F + random.nextFloat() * 0.24F,
				0.012F + random.nextFloat() * 0.036F,
				1 + random.nextInt(3),
				92 + random.nextInt(132),
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
