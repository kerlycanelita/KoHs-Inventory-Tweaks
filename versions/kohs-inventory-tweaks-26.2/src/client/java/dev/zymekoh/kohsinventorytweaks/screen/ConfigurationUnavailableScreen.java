package dev.zymekoh.kohsinventorytweaks.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Main-menu guard for configuration features that require a live player,
 * inventory menu, and the resource set loaded for the current connection.
 */
public final class ConfigurationUnavailableScreen extends Screen {
	private final Screen parent;
	private final List<FloatingParticle> particles = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private boolean compact;
	private float snakePhase;

	public ConfigurationUnavailableScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.world_required.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.ensureParticles();
		this.calculateLayout();

		int buttonWidth = Math.min(180, Math.max(96, this.panelWidth - 36));
		this.addRenderableWidget(new GlassButton(
			this.panelX + (this.panelWidth - buttonWidth) / 2,
			this.panelY + this.panelHeight - 34,
			buttonWidth,
			21,
			Component.translatable("gui.back"),
			button -> this.onClose(),
			GlassButton.Variant.PRIMARY
		));
	}

	@Override
	public void tick() {
		this.snakePhase += 0.075F;
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
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}

		UiRender.panel(
			graphics,
			this.panelX,
			this.panelY,
			this.panelWidth,
			this.panelHeight,
			10,
			UiTheme.GLASS,
			UiTheme.BORDER
		);
		graphics.centeredText(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.world_required.title"),
			this.panelX + this.panelWidth / 2,
			this.panelY + 14,
			UiTheme.TEXT
		);

		this.drawPurpleSnake(graphics);

		int textX = this.panelX + (this.compact ? 14 : 26);
		int textWidth = this.panelWidth - (this.compact ? 28 : 52);
		int descriptionY = this.panelY + (this.compact ? 91 : 125);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.world_required.description"),
			textX,
			descriptionY,
			textWidth,
			UiTheme.TEXT
		);
		if (!this.compact) {
			graphics.textWithWordWrap(
				this.font,
				Component.translatable("screen.kohs_inventory_tweaks.world_required.hint"),
				textX,
				this.panelY + 174,
				textWidth,
				UiTheme.TEXT_MUTED
			);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.isEscape()) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void calculateLayout() {
		int horizontalMargin = this.width < 420 ? 10 : this.width < 760 ? 28 : 64;
		int verticalMargin = this.height < 300 ? 9 : this.height < 460 ? 24 : 48;
		int availableWidth = Math.max(1, this.width - horizontalMargin * 2);
		int availableHeight = Math.max(1, this.height - verticalMargin * 2);
		int minWidth = Math.min(260, availableWidth);
		int minHeight = Math.min(188, availableHeight);
		this.panelWidth = Mth.clamp(500, minWidth, availableWidth);
		this.panelHeight = Mth.clamp(254, minHeight, availableHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.compact = this.panelHeight < 226 || this.panelWidth < 350;
	}

	private void drawPurpleSnake(final GuiGraphicsExtractor graphics) {
		int segments = this.compact ? 11 : 15;
		int spacing = this.compact ? 9 : 11;
		int segmentSize = this.compact ? 7 : 9;
		int wave = this.compact ? 7 : 11;
		int totalWidth = (segments - 1) * spacing;
		int startX = this.panelX + (this.panelWidth - totalWidth) / 2;
		int centerY = this.panelY + (this.compact ? 60 : 77);
		int headX = startX;
		int headY = centerY;

		for (int index = 0; index < segments; index++) {
			int x = startX + index * spacing;
			int y = centerY + Math.round(Mth.sin(this.snakePhase + index * 0.72F) * wave);
			int size = Math.max(4, segmentSize - Math.max(0, segments - index - 9) / 3);
			int color = index % 2 == 0 ? 0xFF9D4EDD : 0xFF7130A4;
			UiRender.roundedRect(graphics, x - size / 2, y - size / 2, size, size, size / 2, 0x66000000);
			UiRender.roundedRect(graphics, x - size / 2, y - size / 2 - 1, size, size, size / 2, color);
			headX = x;
			headY = y;
		}

		int headWidth = this.compact ? 14 : 17;
		int headHeight = this.compact ? 11 : 13;
		UiRender.roundedRect(
			graphics,
			headX - headWidth / 2,
			headY - headHeight / 2 - 1,
			headWidth,
			headHeight,
			5,
			0xFFB45CFF
		);
		int eyeY = headY - 3;
		graphics.fill(headX + 2, eyeY, headX + 4, eyeY + 2, 0xFFF9F2FF);
		graphics.fill(headX + 3, eyeY, headX + 4, eyeY + 1, 0xFF170922);

		int tongueLength = 5 + (Mth.sin(this.snakePhase * 1.7F) > 0.35F ? 3 : 0);
		graphics.fill(headX + headWidth / 2 - 1, headY, headX + headWidth / 2 + tongueLength, headY + 1, 0xFFFF69C8);
		graphics.fill(headX + headWidth / 2 + tongueLength - 2, headY - 2, headX + headWidth / 2 + tongueLength, headY, 0xFFFF69C8);
		graphics.fill(headX + headWidth / 2 + tongueLength - 2, headY + 1, headX + headWidth / 2 + tongueLength, headY + 3, 0xFFFF69C8);
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x534E414B45L);
		int count = Mth.clamp(this.width * this.height / 11000, 12, 28);
		for (int index = 0; index < count; index++) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.06F + random.nextFloat() * 0.14F,
				0.008F + random.nextFloat() * 0.022F,
				1 + random.nextInt(2),
				42 + random.nextInt(72),
				random.nextFloat() * Mth.TWO_PI
			));
		}
	}
}
