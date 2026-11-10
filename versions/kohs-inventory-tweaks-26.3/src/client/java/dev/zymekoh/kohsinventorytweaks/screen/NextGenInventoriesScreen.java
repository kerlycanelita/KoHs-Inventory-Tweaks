package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.HotbarStyle;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.NextGenStyle;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import dev.zymekoh.kohsinventorytweaks.render.NextGenHotbar;
import dev.zymekoh.kohsinventorytweaks.ui.ZBackdrop;
import dev.zymekoh.kohsinventorytweaks.ui.ZChrome;
import dev.zymekoh.kohsinventorytweaks.ui.ZDraw;
import dev.zymekoh.kohsinventorytweaks.ui.ZScene;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

/** The inventory in the middle of the screen and, along the bottom, the style it is drawn in. */
public final class NextGenInventoriesScreen extends Screen {
	private static final String KEY = "screen.kohs_inventory_tweaks.next_gen";
	private static final int DONE_WIDTH = 70;
	private static final int DONE_HEIGHT = 20;
	private static final int ARROW_WIDTH = 24;
	private static final int SELECTOR_HEIGHT = 34;
	private static final int RISK_ICON_ROOM = 16;
	private static final int MASCOT_ROOM = 44;
	private static final int HOTBAR_ROW_HEIGHT = 20;
	private static final int HOTBAR_PREVIEW_HEIGHT = 30;
	private static final int WARNING_WIDTH = 340;
	private static final int WARNING_HEIGHT = 112;

	private final Screen parent;
	private final ZBackdrop backdrop = new ZBackdrop();
	private int margin;
	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;
	private int selectorX;
	private int selectorY;
	private int selectorWidth;
	private int footerY;
	private int hotbarRowY;
	private int hotbarPickerWidth;
	private int warningX;
	private int warningY;
	private int warningWidth;
	private boolean confirmingBars;

	public NextGenInventoriesScreen(final Screen parent) {
		super(Component.translatable(KEY));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.margin = this.width < 420 || this.height < 260 ? 6 : 12;
		int titleSpace = this.height >= 230 ? 30 : 20;
		this.footerY = this.height - this.margin - DONE_HEIGHT;
		this.selectorWidth = Math.min(380, Math.max(1, this.width - this.margin * 2));
		this.selectorX = (this.width - this.selectorWidth) / 2;
		this.selectorY = this.footerY - 6 - SELECTOR_HEIGHT;
		this.previewX = this.margin;
		this.previewY = titleSpace;
		this.previewWidth = Math.max(1, this.width - this.margin * 2);
		this.hotbarRowY = this.selectorY - 4 - HOTBAR_ROW_HEIGHT;
		this.previewHeight = Math.max(1, this.hotbarRowY - 4 - this.previewY);
		this.warningWidth = Math.min(WARNING_WIDTH, Math.max(1, this.width - 24));
		this.warningX = (this.width - this.warningWidth) / 2;
		this.warningY = (this.height - WARNING_HEIGHT) / 2;
		if (this.confirmingBars) {
			this.addBarsWarningButtons();
			return;
		}
		int arrowY = this.selectorY + (SELECTOR_HEIGHT - 22) / 2;
		this.addRenderableWidget(new GlassButton(
			this.selectorX + 5,
			arrowY,
			ARROW_WIDTH,
			22,
			Component.literal("<"),
			button -> this.step(-1),
			GlassButton.Variant.TAB
		).setNarrationLabel(Component.translatable(KEY + ".previous")));
		this.addRenderableWidget(new GlassButton(
			this.selectorX + this.selectorWidth - 5 - ARROW_WIDTH,
			arrowY,
			ARROW_WIDTH,
			22,
			Component.literal(">"),
			button -> this.step(1),
			GlassButton.Variant.TAB
		).setNarrationLabel(Component.translatable(KEY + ".next")));
		if (ConfigStore.get().nextGenStyle == NextGenStyle.CUSTOM) {
			this.addRenderableWidget(new GlassButton(
				this.selectorX + this.selectorWidth / 2 - 60,
				this.selectorY + 16,
				120,
				16,
				Component.translatable(KEY + ".custom.edit"),
				button -> this.minecraft.gui.setScreen(new NextGenLayoutScreen(this)),
				GlassButton.Variant.PRIMARY
			));
		}
		this.addHotbarRow();
		this.addRenderableWidget(new GlassButton(
			this.width - this.margin - DONE_WIDTH,
			this.footerY,
			DONE_WIDTH,
			DONE_HEIGHT,
			Component.translatable("gui.done"),
			button -> this.onClose(),
			GlassButton.Variant.PRIMARY
		));
	}

	/** The hotbar's shape on the left, and the switches for its movement, reactions and the bars beside it. */
	private void addHotbarRow() {
		int gap = 4;
		int toggleWidth = Mth.clamp(this.selectorWidth * 17 / 100, 56, 70);
		this.hotbarPickerWidth = Math.max(60, this.selectorWidth - toggleWidth * 3 - gap * 3);
		this.addRenderableWidget(new GlassButton(
			this.selectorX,
			this.hotbarRowY,
			HOTBAR_ROW_HEIGHT,
			HOTBAR_ROW_HEIGHT,
			Component.literal("<"),
			button -> this.stepHotbar(-1),
			GlassButton.Variant.TAB
		).setNarrationLabel(Component.translatable(KEY + ".hotbar.previous")));
		this.addRenderableWidget(new GlassButton(
			this.selectorX + this.hotbarPickerWidth - HOTBAR_ROW_HEIGHT,
			this.hotbarRowY,
			HOTBAR_ROW_HEIGHT,
			HOTBAR_ROW_HEIGHT,
			Component.literal(">"),
			button -> this.stepHotbar(1),
			GlassButton.Variant.TAB
		).setNarrationLabel(Component.translatable(KEY + ".hotbar.next")));
		int toggleX = this.selectorX + this.hotbarPickerWidth + gap;
		this.addHotbarToggle(toggleX, toggleWidth, ".hotbar.motion", () -> ConfigStore.get().hotbarMotion,
			pressed -> this.change(config -> config.hotbarMotion = !config.hotbarMotion));
		this.addHotbarToggle(toggleX + toggleWidth + gap, toggleWidth, ".hotbar.reactions", () -> ConfigStore.get().hotbarReactions,
			pressed -> this.change(config -> config.hotbarReactions = !config.hotbarReactions));
		this.addHotbarToggle(toggleX + (toggleWidth + gap) * 2, toggleWidth, ".hotbar.bars", () -> ConfigStore.get().hotbarStatusFollow,
			pressed -> this.toggleBars());
	}

	private void addHotbarToggle(final int x, final int width, final String key, final BooleanSupplier selected, final Button.OnPress press) {
		GlassButton button = new GlassButton(
			x,
			this.hotbarRowY,
			width,
			HOTBAR_ROW_HEIGHT,
			Component.translatable(KEY + key),
			press,
			GlassButton.Variant.TOGGLE,
			selected
		);
		button.setTooltip(Tooltip.create(Component.translatable(KEY + key + ".description")));
		button.setTooltipDelay(Duration.ofMillis(220));
		this.addRenderableWidget(button);
	}

	/** Turning the bars on warns first, because other mods may be moving the same elements. */
	private void toggleBars() {
		if (ConfigStore.get().hotbarStatusFollow) {
			this.change(config -> config.hotbarStatusFollow = false);
			return;
		}
		this.confirmingBars = true;
		this.rebuildWidgets();
	}

	private void addBarsWarningButtons() {
		int gap = 8;
		int buttonWidth = Math.min(150, (this.warningWidth - 28 - gap) / 2);
		int y = this.warningY + WARNING_HEIGHT - 30;
		int startX = this.warningX + (this.warningWidth - buttonWidth * 2 - gap) / 2;
		this.addRenderableWidget(new GlassButton(startX, y, buttonWidth, 20, Component.translatable(KEY + ".hotbar.bars.warning.enable"), button -> {
			this.change(config -> config.hotbarStatusFollow = true);
			this.confirmingBars = false;
			this.rebuildWidgets();
		}, GlassButton.Variant.DANGER));
		this.addRenderableWidget(new GlassButton(startX + buttonWidth + gap, y, buttonWidth, 20, Component.translatable("gui.back"), button -> {
			this.confirmingBars = false;
			this.rebuildWidgets();
		}, GlassButton.Variant.NORMAL));
	}

	private void stepHotbar(final int direction) {
		this.change(config -> {
			HotbarStyle[] styles = HotbarStyle.values();
			config.hotbarStyle = styles[Math.floorMod(config.hotbarStyle.ordinal() + direction, styles.length)];
		});
	}

	private void change(final Consumer<InventoryTweaksConfig> change) {
		InventoryTweaksConfig working = ConfigStore.get().copy();
		change.accept(working);
		ConfigStore.replaceAndSave(working);
	}

	private void step(final int direction) {
		this.change(config -> {
			NextGenStyle[] styles = NextGenStyle.values();
			config.nextGenStyle = styles[Math.floorMod(config.nextGenStyle.ordinal() + direction, styles.length)];
		});
		this.rebuildWidgets();
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.backdrop.draw(graphics, this.width, this.height, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		InventoryTweaksConfig config = ConfigStore.get();
		NextGenStyle style = config.nextGenStyle;
		ZChrome.screenTitle(graphics, this.font, this.title, null, this.width / 2, this.height >= 230 ? 10 : 5, this.width - 24);
		this.drawPreview(graphics, config, style, mouseX, mouseY);

		ZDraw.glass(graphics, this.selectorX, this.selectorY, this.selectorWidth, SELECTOR_HEIGHT, ZTheme.SURFACE, ZTheme.BORDER);
		String id = style.name().toLowerCase(Locale.ROOT);
		int textWidth = Math.max(1, this.selectorWidth - (ARROW_WIDTH + 12) * 2);
		int center = this.selectorX + this.selectorWidth / 2;
		boolean custom = style == NextGenStyle.CUSTOM;
		graphics.centeredText(this.font, Component.translatable(KEY + ".style." + id), center, this.selectorY + (custom ? 4 : 6), ZTheme.TEXT);
		if (!custom) {
			boolean fits = style == NextGenStyle.OFF || NextGenInventory.styleFor(config, this.width, this.height) == style;
			Component description = Component.translatable(fits ? KEY + ".style." + id + ".description" : KEY + ".too_small");
			graphics.centeredText(
				this.font,
				this.font.plainSubstrByWidth(description.getString(), textWidth),
				center,
				this.selectorY + 20,
				fits ? ZTheme.TEXT_MUTED : ZTheme.CRIMSON_BRIGHT
			);
		}

		graphics.centeredText(
			this.font,
			Component.translatable(KEY + ".hotbar", Component.translatable(KEY + ".hotbar." + config.hotbarStyle.name().toLowerCase(Locale.ROOT))),
			this.selectorX + this.hotbarPickerWidth / 2,
			this.hotbarRowY + 6,
			ZTheme.TEXT
		);
		this.drawRisk(graphics);
		if (this.confirmingBars) {
			this.drawBarsWarning(graphics);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void drawBarsWarning(final GuiGraphicsExtractor graphics) {
		graphics.fill(0, 0, this.width, this.height, ZTheme.MODAL_DIM);
		ZDraw.glass(graphics, this.warningX, this.warningY, this.warningWidth, WARNING_HEIGHT, ZTheme.SURFACE, ZTheme.CRIMSON_BRIGHT);
		Component title = Component.translatable(KEY + ".hotbar.bars.warning.title");
		int titleWidth = this.font.width(title);
		int titleX = this.warningX + (this.warningWidth - titleWidth - 18) / 2;
		ZScene.WARNING.draw(graphics, titleX, this.warningY + 7, 14, 0.5F);
		graphics.text(this.font, title, titleX + 18, this.warningY + 11, ZTheme.TEXT, false);
		graphics.textWithWordWrap(
			this.font,
			Component.translatable(KEY + ".hotbar.bars.warning.description"),
			this.warningX + 14,
			this.warningY + 28,
			this.warningWidth - 28,
			ZTheme.TEXT
		);
	}

	/** The reminder stays on the page, on two lines if it needs them, clear of the mascot beside Done. */
	private void drawRisk(final GuiGraphicsExtractor graphics) {
		int room = Math.max(40, this.width - this.margin * 2 - DONE_WIDTH - 8 - RISK_ICON_ROOM - MASCOT_ROOM);
		List<FormattedCharSequence> lines = this.font.split(Component.translatable(KEY + ".risk"), room);
		int shown = Math.min(2, lines.size());
		int widest = 0;
		for (int index = 0; index < shown; index++) {
			widest = Math.max(widest, this.font.width(lines.get(index)));
		}
		int x = this.margin + Math.max(0, (room - widest) / 2);
		int textY = this.footerY + (DONE_HEIGHT - shown * 10) / 2 + 1;
		ZScene.WARNING.draw(graphics, x, this.footerY + 4, 12, 0.4F);
		for (int index = 0; index < shown; index++) {
			graphics.text(this.font, lines.get(index), x + RISK_ICON_ROOM, textY + index * 10, ZTheme.CRIMSON_BRIGHT, false);
		}
	}

	private void drawPreview(
		final GuiGraphicsExtractor graphics,
		final InventoryTweaksConfig config,
		final NextGenStyle style,
		final int mouseX,
		final int mouseY
	) {
		ZDraw.brackets(graphics, this.previewX, this.previewY, this.previewWidth, this.previewHeight, 9, ZTheme.alpha(ZTheme.LILAC, 150));
		int inset = 6;
		int areaX = this.previewX + inset;
		int areaY = this.previewY + inset;
		int areaWidth = Math.max(1, this.previewWidth - inset * 2);
		int strip = config.hotbarStyle == HotbarStyle.VERTICAL ? 0 : HOTBAR_PREVIEW_HEIGHT;
		int fullHeight = Math.max(1, this.previewHeight - inset * 2);
		int areaHeight = Math.max(1, fullHeight - strip);
		NextGenHotbar.drawPreview(graphics, this.font, areaX, areaY, areaWidth, fullHeight, config);
		if (style != NextGenStyle.OFF && NextGenInventory.styleFor(config, this.width, this.height) == style) {
			NextGenInventory.drawPreview(graphics, this.font, style, areaX, areaY, areaWidth, areaHeight, mouseX, mouseY, config);
			return;
		}
		float scale = Math.min(1.6F, Math.min(
			areaWidth / (float) InventoryPreviewRenderer.INVENTORY_WIDTH,
			areaHeight / (float) InventoryPreviewRenderer.INVENTORY_HEIGHT
		));
		InventoryPreviewRenderer.drawPlayerInventory(
			graphics,
			this.font,
			areaX + (areaWidth - Math.round(InventoryPreviewRenderer.INVENTORY_WIDTH * scale)) / 2,
			areaY + (areaHeight - Math.round(InventoryPreviewRenderer.INVENTORY_HEIGHT * scale)) / 2,
			scale,
			mouseX,
			mouseY,
			true,
			config
		);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (!this.confirmingBars && (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT)) {
			this.step(event.key() == InputConstants.KEY_LEFT ? -1 : 1);
			return true;
		}
		if (this.confirmingBars && event.isEscape()) {
			this.confirmingBars = false;
			this.rebuildWidgets();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (this.parent instanceof InventoryTweaksScreen screen) {
			screen.refreshConfigurationFromStore();
		}
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
