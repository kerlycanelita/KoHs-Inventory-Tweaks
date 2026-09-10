package dev.zymekoh.kohsinventorydebug.gui;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import dev.zymekoh.kohsinventorydebug.DebugCollector.DebugEvent;
import dev.zymekoh.kohsinventorydebug.DebugCollector.Level;
import dev.zymekoh.kohsinventorydebug.MacroTestController;
import dev.zymekoh.kohsinventorydebug.MacroTestController.MacroKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class DebugLogScreen extends Screen {
	private static final int BACKDROP_TOP = 0xF00B0615;
	private static final int BACKDROP_BOTTOM = 0xF0160828;
	private static final int PANEL = 0xE91A0D2C;
	private static final int PANEL_LIGHT = 0xE226123B;
	private static final int BORDER = 0xCC9A55E8;
	private static final int ACCENT = 0xFFB86BFF;
	private static final int ACCENT_BRIGHT = 0xFFE4C5FF;
	private static final int TEXT = 0xFFF7EDFF;
	private static final int MUTED = 0xFFBBA4C9;
	private static final int WARN = 0xFFFFC857;
	private static final int ERROR = 0xFFFF6B9D;
	private static final int LINE_HEIGHT = 10;
	private static final int SNAPSHOT_LIMIT = 50_000;

	private final Screen parent;
	private final List<Particle> particles = new ArrayList<>();
	private List<DebugEvent> events = List.of();
	private long observedSequence = -1L;
	private boolean follow = true;
	private double targetScrollFromBottom;
	private double scrollFromBottom;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentX;
	private int contentWidth;
	private int logTop;
	private int logBottom;
	private int copyFeedbackTicks;
	private Button copyButton;
	private Button followButton;

	public DebugLogScreen(final Screen parent) {
		super(Component.translatable("kohs_inventory_debug.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int horizontalMargin = this.width < 420 ? 6 : this.width < 700 ? 14 : 34;
		int verticalMargin = this.height < 280 ? 6 : this.height < 420 ? 12 : 22;
		int maximumWidth = Math.max(220, this.width - horizontalMargin * 2);
		int maximumHeight = Math.max(170, this.height - verticalMargin * 2);
		this.panelWidth = Mth.clamp(this.width < 760 ? maximumWidth : 940, Math.min(320, maximumWidth), maximumWidth);
		this.panelHeight = Mth.clamp(this.height < 440 ? maximumHeight : 560, Math.min(190, maximumHeight), maximumHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		int padding = this.panelWidth < 420 ? 8 : 12;
		this.contentX = this.panelX + padding;
		this.contentWidth = this.panelWidth - padding * 2;

		boolean compact = this.contentWidth < 420;
		int buttonGap = compact ? 3 : 5;
		int buttonHeight = 20;
		int toolbarTop = this.panelY + (compact ? 44 : 39);
		int toolbarRows = compact ? 2 : 1;
		int macroTop = toolbarTop + toolbarRows * (buttonHeight + buttonGap) + 2;
		int macroRows = compact ? 2 : 1;
		this.logTop = macroTop + macroRows * (buttonHeight + buttonGap) + 4;
		this.logBottom = this.panelY + this.panelHeight - 25;

		if (compact) {
			int half = (this.contentWidth - buttonGap) / 2;
			this.copyButton = this.addRenderableWidget(button("kohs_inventory_debug.screen.copy", this.contentX, toolbarTop, half, buttonHeight, ignored -> copyAll()));
			this.addRenderableWidget(button("kohs_inventory_debug.screen.mark", this.contentX + half + buttonGap, toolbarTop, half, buttonHeight, ignored -> mark()));
			this.addRenderableWidget(button("kohs_inventory_debug.screen.clear", this.contentX, toolbarTop + buttonHeight + buttonGap, half, buttonHeight, ignored -> clearLog()));
			this.followButton = this.addRenderableWidget(button(followKey(), this.contentX + half + buttonGap, toolbarTop + buttonHeight + buttonGap, half, buttonHeight, ignored -> toggleFollow()));
		} else {
			int width = (this.contentWidth - buttonGap * 3) / 4;
			this.copyButton = this.addRenderableWidget(button("kohs_inventory_debug.screen.copy", this.contentX, toolbarTop, width, buttonHeight, ignored -> copyAll()));
			this.addRenderableWidget(button("kohs_inventory_debug.screen.mark", this.contentX + (width + buttonGap), toolbarTop, width, buttonHeight, ignored -> mark()));
			this.addRenderableWidget(button("kohs_inventory_debug.screen.clear", this.contentX + (width + buttonGap) * 2, toolbarTop, width, buttonHeight, ignored -> clearLog()));
			this.followButton = this.addRenderableWidget(button(followKey(), this.contentX + (width + buttonGap) * 3, toolbarTop, width, buttonHeight, ignored -> toggleFollow()));
		}
		addMacroButtons(macroTop, buttonHeight, buttonGap, compact);

		int backWidth = Math.min(92, Math.max(58, this.contentWidth / 4));
		this.addRenderableWidget(button(
			"kohs_inventory_debug.screen.back",
			this.panelX + this.panelWidth - padding - backWidth,
			this.panelY + this.panelHeight - 22,
			backWidth,
			18,
			ignored -> this.onClose()
		));
		ensureParticles();
		refreshEvents(true);
	}

	private Button button(
		final String key,
		final int x,
		final int y,
		final int width,
		final int height,
		final Button.OnPress action
	) {
		return Button.builder(Component.translatable(key), action).bounds(x, y, Math.max(1, width), height).build();
	}

	@Override
	public void tick() {
		refreshEvents(false);
		this.scrollFromBottom += (this.targetScrollFromBottom - this.scrollFromBottom) * 0.30;
		if (Math.abs(this.targetScrollFromBottom - this.scrollFromBottom) < 0.01) {
			this.scrollFromBottom = this.targetScrollFromBottom;
		}
		if (this.copyFeedbackTicks > 0 && --this.copyFeedbackTicks == 0 && this.copyButton != null) {
			this.copyButton.setMessage(Component.translatable("kohs_inventory_debug.screen.copy"));
		}
		for (Particle particle : this.particles) {
			particle.y -= particle.speed;
			particle.phase += 0.025F;
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
			int alpha = 45 + (int) (25.0F * (0.5F + 0.5F * Mth.sin(particle.phase)));
			int x = (int) particle.x;
			int y = (int) particle.y;
			graphics.fill(x - 2, y, x + 3, y + 1, (alpha << 24) | 0x00C084FC);
			graphics.fill(x, y - 2, x + 1, y + 3, (alpha << 24) | 0x00E4C5FF);
		}

		panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL, BORDER);
		graphics.centeredText(this.font, this.title, this.width / 2, this.panelY + 9, TEXT);
		graphics.centeredText(this.font, Component.translatable("kohs_inventory_debug.screen.live"), this.width / 2, this.panelY + 20, ACCENT_BRIGHT);
		String status = DebugCollector.status().compact() + "; macro=" + MacroTestController.current();
		graphics.centeredText(this.font, Component.literal(trim(status, this.panelWidth - 18)), this.width / 2, this.panelY + 30, MUTED);

		panel(graphics, this.contentX, this.logTop, this.contentWidth, Math.max(1, this.logBottom - this.logTop), PANEL_LIGHT, 0x99653A86);
		drawLog(graphics);
		graphics.text(
			this.font,
			Component.literal(trim(Component.translatable("kohs_inventory_debug.screen.hint").getString(), Math.max(1, this.contentWidth - 110))),
			this.contentX + 3,
			this.panelY + this.panelHeight - 18,
			MUTED,
			false
		);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawLog(final GuiGraphicsExtractor graphics) {
		int visible = Math.max(1, (this.logBottom - this.logTop - 8) / LINE_HEIGHT);
		int maximumScroll = Math.max(0, this.events.size() - visible);
		this.targetScrollFromBottom = Mth.clamp(this.targetScrollFromBottom, 0.0, maximumScroll);
		this.scrollFromBottom = Mth.clamp(this.scrollFromBottom, 0.0, maximumScroll);
		int end = Math.max(0, this.events.size() - (int) Math.round(this.scrollFromBottom));
		int start = Math.max(0, end - visible);
		int y = this.logTop + 4;

		graphics.enableScissor(this.contentX + 2, this.logTop + 2, this.contentX + this.contentWidth - 2, this.logBottom - 2);
		if (this.events.isEmpty()) {
			graphics.text(this.font, Component.translatable("kohs_inventory_debug.screen.empty"), this.contentX + 6, y, MUTED, false);
		} else {
			for (int index = start; index < end; index++) {
				DebugEvent event = this.events.get(index);
				String line = trim(event.displayLine(), Math.max(1, this.contentWidth - 16));
				graphics.text(this.font, Component.literal(line), this.contentX + 6, y, color(event.level()), false);
				y += LINE_HEIGHT;
			}
		}
		graphics.disableScissor();

		if (maximumScroll > 0) {
			int trackTop = this.logTop + 3;
			int trackHeight = Math.max(1, this.logBottom - this.logTop - 6);
			int thumbHeight = Math.max(8, trackHeight * visible / Math.max(1, this.events.size()));
			int offsetFromTop = maximumScroll - (int) Math.round(this.scrollFromBottom);
			int thumbY = trackTop + (trackHeight - thumbHeight) * offsetFromTop / maximumScroll;
			graphics.fill(this.contentX + this.contentWidth - 4, trackTop, this.contentX + this.contentWidth - 2, trackTop + trackHeight, 0x55401C55);
			graphics.fill(this.contentX + this.contentWidth - 4, thumbY, this.contentX + this.contentWidth - 2, thumbY + thumbHeight, ACCENT);
		}
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (x >= this.contentX && x <= this.contentX + this.contentWidth && y >= this.logTop && y <= this.logBottom) {
			this.follow = false;
			this.followButton.setMessage(Component.translatable(followKey()));
			this.targetScrollFromBottom = Math.max(0.0, this.targetScrollFromBottom + scrollY * 4.0);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void refreshEvents(final boolean force) {
		long sequence = DebugCollector.latestSequence();
		if (!force && sequence == this.observedSequence) {
			return;
		}
		this.observedSequence = sequence;
		this.events = DebugCollector.snapshot(SNAPSHOT_LIMIT);
		if (this.follow) {
			this.targetScrollFromBottom = 0.0;
			this.scrollFromBottom = 0.0;
		}
	}

	private void copyAll() {
		this.minecraft.keyboardHandler.setClipboard(DebugCollector.fullReport());
		this.copyButton.setMessage(Component.translatable("kohs_inventory_debug.screen.copied"));
		this.copyFeedbackTicks = 40;
		DebugCollector.info("UI", "Complete report copied to clipboard.");
	}

	private void mark() {
		DebugCollector.addUserMarker();
		this.follow = true;
		this.followButton.setMessage(Component.translatable(followKey()));
		refreshEvents(true);
	}

	private void clearLog() {
		DebugCollector.clear();
		this.targetScrollFromBottom = 0.0;
		this.scrollFromBottom = 0.0;
		refreshEvents(true);
	}

	private void toggleFollow() {
		this.follow = !this.follow;
		if (this.follow) {
			this.targetScrollFromBottom = 0.0;
		}
		this.followButton.setMessage(Component.translatable(followKey()));
	}

	private void addMacroButtons(final int top, final int height, final int gap, final boolean compact) {
		MacroKind[] kinds = {
			MacroKind.FAST_OPEN_CLOSE,
			MacroKind.INVENTORY_THEN_OFFHAND,
			MacroKind.OFFHAND_THEN_INVENTORY,
			MacroKind.CENTERED_CURSOR,
			MacroKind.FULL_STRESS
		};
		String[] keys = {
			"kohs_inventory_debug.screen.macro_fast",
			"kohs_inventory_debug.screen.macro_inv_off",
			"kohs_inventory_debug.screen.macro_off_inv",
			"kohs_inventory_debug.screen.macro_center",
			"kohs_inventory_debug.screen.macro_full"
		};
		boolean active = MacroTestController.isSafeLocalWorld(this.minecraft) && !MacroTestController.isRunning();
		if (!compact) {
			int width = (this.contentWidth - gap * 5) / 6;
			for (int index = 0; index < kinds.length; index++) {
				MacroKind kind = kinds[index];
				Button macro = this.addRenderableWidget(button(keys[index], this.contentX + index * (width + gap), top, width, height,
					ignored -> startMacro(kind)));
				macro.active = active;
			}
			Button lab = this.addRenderableWidget(button("kohs_inventory_debug.screen.stress_lab",
				this.contentX + 5 * (width + gap), top, width, height, ignored -> this.minecraft.setScreen(new StressLabScreen(this))));
			lab.active = active;
			return;
		}

		int firstWidth = (this.contentWidth - gap * 2) / 3;
		for (int index = 0; index < 3; index++) {
			MacroKind kind = kinds[index];
			Button macro = this.addRenderableWidget(button(keys[index], this.contentX + index * (firstWidth + gap), top, firstWidth, height,
				ignored -> startMacro(kind)));
			macro.active = active;
		}
		int secondWidth = (this.contentWidth - gap * 2) / 3;
		for (int index = 3; index < 5; index++) {
			MacroKind kind = kinds[index];
			Button macro = this.addRenderableWidget(button(keys[index], this.contentX + (index - 3) * (secondWidth + gap),
				top + height + gap, secondWidth, height, ignored -> startMacro(kind)));
			macro.active = active;
		}
		Button lab = this.addRenderableWidget(button("kohs_inventory_debug.screen.stress_lab",
			this.contentX + 2 * (secondWidth + gap), top + height + gap, secondWidth, height,
			ignored -> this.minecraft.setScreen(new StressLabScreen(this))));
		lab.active = active;
	}

	private void startMacro(final MacroKind kind) {
		MacroTestController.start(this.minecraft, kind, this.parent);
	}

	private String followKey() {
		return this.follow ? "kohs_inventory_debug.screen.follow_on" : "kohs_inventory_debug.screen.follow_off";
	}

	private String trim(final String value, final int width) {
		if (this.font.width(value) <= width) {
			return value;
		}
		return this.font.plainSubstrByWidth(value, Math.max(1, width - this.font.width("…"))) + "…";
	}

	private static int color(final Level level) {
		return switch (level) {
			case TRACE -> MUTED;
			case INFO -> TEXT;
			case WARN -> WARN;
			case ERROR -> ERROR;
		};
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
		Random random = new Random(0x4B4F485344454255L ^ this.width * 31L ^ this.height);
		int count = Mth.clamp((this.width * this.height) / 13_000, 16, 54);
		for (int index = 0; index < count; index++) {
			this.particles.add(new Particle(
				random.nextFloat() * this.width,
				random.nextFloat() * this.height,
				0.08F + random.nextFloat() * 0.18F,
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
