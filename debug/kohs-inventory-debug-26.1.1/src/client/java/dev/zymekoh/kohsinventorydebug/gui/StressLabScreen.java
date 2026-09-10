package dev.zymekoh.kohsinventorydebug.gui;

import dev.zymekoh.kohsinventorydebug.MacroTestController;
import dev.zymekoh.kohsinventorydebug.MacroTestController.MacroKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Responsive launcher for the destructive-in-spirit, local-only QA sequences. */
public final class StressLabScreen extends Screen {
	private static final int BACKDROP_TOP = 0xF00B0615;
	private static final int BACKDROP_BOTTOM = 0xF0160828;
	private static final int PANEL = 0xEE1A0D2C;
	private static final int CARD = 0xDD26123B;
	private static final int CARD_HOVER = 0xEE3A1758;
	private static final int CARD_DISABLED = 0xBB160D20;
	private static final int BORDER = 0xCC9A55E8;
	private static final int ACCENT = 0xFFB86BFF;
	private static final int TEXT = 0xFFF7EDFF;
	private static final int MUTED = 0xFFBBA4C9;
	private static final int WARN = 0xFFFFC857;
	private static final int CARD_HEIGHT = 42;
	private static final int GAP = 6;

	private final Screen parent;
	private final List<MacroKind> macros = new ArrayList<>();
	private final List<Particle> particles = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int viewportX;
	private int viewportTop;
	private int viewportWidth;
	private int viewportBottom;
	private int columns;
	private double scroll;
	private double targetScroll;

	public StressLabScreen(final Screen parent) {
		super(Component.translatable("kohs_inventory_debug.lab.title"));
		this.parent = parent;
		for (MacroKind kind : MacroKind.values()) {
			if (kind.aggressive()) {
				this.macros.add(kind);
			}
		}
	}

	@Override
	protected void init() {
		int horizontalMargin = this.width < 420 ? 6 : this.width < 700 ? 14 : 34;
		int verticalMargin = this.height < 280 ? 6 : this.height < 420 ? 12 : 22;
		int maximumWidth = Math.max(220, this.width - horizontalMargin * 2);
		int maximumHeight = Math.max(170, this.height - verticalMargin * 2);
		this.panelWidth = Mth.clamp(this.width < 760 ? maximumWidth : 900, Math.min(300, maximumWidth), maximumWidth);
		this.panelHeight = Mth.clamp(this.height < 440 ? maximumHeight : 540, Math.min(190, maximumHeight), maximumHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		int padding = this.panelWidth < 420 ? 8 : 12;
		this.viewportX = this.panelX + padding;
		this.viewportWidth = this.panelWidth - padding * 2;
		this.viewportTop = this.panelY + (this.panelHeight < 250 ? 44 : 52);
		this.viewportBottom = this.panelY + this.panelHeight - 29;
		this.columns = this.viewportWidth >= 500 ? 2 : 1;
		this.targetScroll = Mth.clamp(this.targetScroll, 0.0, maximumScroll());
		this.scroll = Mth.clamp(this.scroll, 0.0, maximumScroll());

		int backWidth = Math.min(96, Math.max(64, this.viewportWidth / 4));
		this.addRenderableWidget(Button.builder(
			Component.translatable("kohs_inventory_debug.screen.back"),
			ignored -> this.onClose()
		).bounds(this.panelX + this.panelWidth - padding - backWidth, this.panelY + this.panelHeight - 23, backWidth, 19).build());
		ensureParticles();
	}

	@Override
	public void tick() {
		this.scroll += (this.targetScroll - this.scroll) * 0.28;
		if (Math.abs(this.targetScroll - this.scroll) < 0.02) {
			this.scroll = this.targetScroll;
		}
		for (Particle particle : this.particles) {
			particle.y -= particle.speed;
			particle.phase += 0.028F;
			if (particle.y < -8) {
				particle.y = this.height + 8;
			}
		}
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		graphics.fillGradient(0, 0, this.width, this.height, BACKDROP_TOP, BACKDROP_BOTTOM);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		for (Particle particle : this.particles) {
			int alpha = 42 + (int) (28.0F * (0.5F + 0.5F * Mth.sin(particle.phase)));
			int x = (int) particle.x;
			int y = (int) particle.y;
			graphics.fill(x - 2, y, x + 3, y + 1, (alpha << 24) | 0x00C084FC);
			graphics.fill(x, y - 2, x + 1, y + 3, (alpha << 24) | 0x00E4C5FF);
		}

		panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL, BORDER);
		graphics.centeredText(this.font, this.title, this.width / 2, this.panelY + 9, TEXT);
		graphics.centeredText(this.font, Component.translatable("kohs_inventory_debug.lab.warning"), this.width / 2,
			this.panelY + 22, WARN);
		graphics.centeredText(this.font, Component.translatable("kohs_inventory_debug.lab.boundary"), this.width / 2,
			this.panelY + 33, MUTED);

		graphics.enableScissor(this.viewportX, this.viewportTop, this.viewportX + this.viewportWidth, this.viewportBottom);
		boolean active = MacroTestController.isSafeLocalWorld(this.minecraft) && !MacroTestController.isRunning();
		int cardWidth = (this.viewportWidth - (this.columns - 1) * GAP) / this.columns;
		for (int index = 0; index < this.macros.size(); index++) {
			int column = index % this.columns;
			int row = index / this.columns;
			int x = this.viewportX + column * (cardWidth + GAP);
			int y = this.viewportTop + row * (CARD_HEIGHT + GAP) - (int) Math.round(this.scroll);
			boolean hovered = active && mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT
				&& mouseY >= this.viewportTop && mouseY < this.viewportBottom;
			drawCard(graphics, this.macros.get(index), x, y, cardWidth, active, hovered);
		}
		graphics.disableScissor();
		drawScrollbar(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawCard(
		final GuiGraphicsExtractor graphics,
		final MacroKind kind,
		final int x,
		final int y,
		final int width,
		final boolean active,
		final boolean hovered
	) {
		int fill = !active ? CARD_DISABLED : hovered ? CARD_HOVER : CARD;
		int border = hovered ? ACCENT : 0x99653A86;
		graphics.fill(x, y, x + width, y + CARD_HEIGHT, border);
		graphics.fill(x + 1, y + 1, x + width - 1, y + CARD_HEIGHT - 1, fill);
		graphics.fill(x + 1, y + 1, x + 4, y + CARD_HEIGHT - 1, active ? ACCENT : 0xFF60466E);
		graphics.text(this.font, Component.translatable(kind.titleKey()), x + 9, y + 7, active ? TEXT : MUTED, false);
		String description = Component.translatable(kind.descriptionKey()).getString();
		graphics.text(this.font, Component.literal(trim(description, width - 16)), x + 9, y + 23, MUTED, false);
	}

	private void drawScrollbar(final GuiGraphicsExtractor graphics) {
		int maximum = maximumScroll();
		if (maximum <= 0) {
			return;
		}
		int trackHeight = Math.max(1, this.viewportBottom - this.viewportTop);
		int contentHeight = contentHeight();
		int thumbHeight = Math.max(10, trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
		int thumbY = this.viewportTop + (int) Math.round((trackHeight - thumbHeight) * this.scroll / maximum);
		graphics.fill(this.viewportX + this.viewportWidth - 3, this.viewportTop, this.viewportX + this.viewportWidth - 1,
			this.viewportBottom, 0x55401C55);
		graphics.fill(this.viewportX + this.viewportWidth - 3, thumbY, this.viewportX + this.viewportWidth - 1,
			thumbY + thumbHeight, ACCENT);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0 || !MacroTestController.isSafeLocalWorld(this.minecraft) || MacroTestController.isRunning()) {
			return false;
		}
		if (event.x() < this.viewportX || event.x() >= this.viewportX + this.viewportWidth
			|| event.y() < this.viewportTop || event.y() >= this.viewportBottom) {
			return false;
		}
		int cardWidth = (this.viewportWidth - (this.columns - 1) * GAP) / this.columns;
		for (int index = 0; index < this.macros.size(); index++) {
			int column = index % this.columns;
			int row = index / this.columns;
			int x = this.viewportX + column * (cardWidth + GAP);
			int y = this.viewportTop + row * (CARD_HEIGHT + GAP) - (int) Math.round(this.scroll);
			if (event.x() >= x && event.x() < x + cardWidth && event.y() >= y && event.y() < y + CARD_HEIGHT) {
				MacroTestController.start(this.minecraft, this.macros.get(index), this);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (x >= this.viewportX && x <= this.viewportX + this.viewportWidth && y >= this.viewportTop && y <= this.viewportBottom) {
			this.targetScroll = Mth.clamp(this.targetScroll - scrollY * 28.0, 0.0, maximumScroll());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private int contentHeight() {
		int rows = (this.macros.size() + this.columns - 1) / this.columns;
		return Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP);
	}

	private int maximumScroll() {
		return Math.max(0, contentHeight() - Math.max(1, this.viewportBottom - this.viewportTop));
	}

	private String trim(final String value, final int width) {
		if (this.font.width(value) <= width) {
			return value;
		}
		return this.font.plainSubstrByWidth(value, Math.max(1, width - this.font.width("…"))) + "…";
	}

	private static void panel(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int fill,
		final int border
	) {
		graphics.fill(x - 1, y + 2, x + width + 1, y + height + 2, 0x78000000);
		graphics.fill(x, y, x + width, y + height, border);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
		graphics.fill(x + 4, y + 1, x + width - 4, y + 2, 0x55E4C5FF);
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x5354524553534C41L ^ this.width * 31L ^ this.height);
		int count = Mth.clamp((this.width * this.height) / 12_000, 18, 60);
		for (int index = 0; index < count; index++) {
			this.particles.add(new Particle(
				random.nextFloat() * this.width,
				random.nextFloat() * this.height,
				0.08F + random.nextFloat() * 0.19F,
				random.nextFloat() * Mth.TWO_PI
			));
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static final class Particle {
		private final float x;
		private float y;
		private final float speed;
		private float phase;

		private Particle(final float x, final float y, final float speed, final float phase) {
			this.x = x;
			this.y = y;
			this.speed = speed;
			this.phase = phase;
		}
	}
}
