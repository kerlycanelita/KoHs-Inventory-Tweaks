package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.ItemHighlight;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public final class ItemHighlighterScreen extends Screen {
	private static final int CELL_SIZE = 24;
	private static final int SLOT_SIZE = 18;
	private final Screen parent;
	private final Consumer<InventoryTweaksConfig> onSave;
	private final InventoryTweaksConfig working;
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final List<ItemEntry> allItems = new ArrayList<>();
	private final List<ItemEntry> filteredItems = new ArrayList<>();
	private Mode mode = Mode.BASE;
	private String searchValue = "";
	private @Nullable String editingItemId;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentTop;
	private int contentBottom;
	private int selectedX;
	private int selectedY;
	private int selectedWidth;
	private int selectedHeight;
	private int catalogX;
	private int catalogY;
	private int catalogWidth;
	private int catalogHeight;
	private int selectedColumns;
	private int selectedVisibleRows;
	private int catalogColumns;
	private int catalogVisibleRows;
	private int selectedScrollRows;
	private int selectedMaxScrollRows;
	private int catalogScrollRows;
	private int catalogMaxScrollRows;

	private int editorX;
	private int editorY;
	private int editorWidth;
	private int editorHeight;
	private boolean compactEditor;
	private int editorPreviewX;
	private int editorPreviewY;
	private int editorPreviewSize;
	private int backgroundPaletteX;
	private int borderPaletteX;
	private int paletteY;
	private int paletteWidth;
	private int paletteHeight;
	private int editorToggleY;
	private int editorFooterY;

	private enum Mode {
		BASE,
		EDITOR
	}

	public ItemHighlighterScreen(
		final Screen parent,
		final InventoryTweaksConfig initial,
		final Consumer<InventoryTweaksConfig> onSave
	) {
		super(Component.translatable("screen.kohs_inventory_tweaks.item_highlighter"));
		this.parent = parent;
		this.working = initial.copy();
		this.onSave = onSave;
	}

	@Override
	protected void init() {
		this.ensureParticles();
		this.ensureItems();
		this.calculateLayout();
		if (this.mode == Mode.BASE) {
			this.addBaseWidgets();
		} else {
			this.addEditorWidgets();
		}
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
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}
		if (this.mode == Mode.BASE) {
			this.drawBase(graphics, mouseX, mouseY);
		} else {
			// A modal must own the whole visible and interactive layer. In particular,
			// do not extract base item tooltips here: Minecraft renders deferred
			// tooltips after the dim layer, which made them pierce the editor.
			graphics.fill(0, 0, this.width, this.height, 0xA00A0612);
			this.drawEditor(graphics);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.mode == Mode.BASE) {
			int selectedTop = this.selectedY + 24;
			int selectedBottom = this.selectedY + this.selectedHeight - 5;
			UiRender.scrollFade(
				graphics,
				this.selectedX + 3,
				selectedTop,
				this.selectedWidth - 7,
				selectedBottom - selectedTop,
				this.selectedScrollRows > 0,
				this.selectedScrollRows < this.selectedMaxScrollRows
			);
			int catalogTop = this.catalogY + 49;
			int catalogBottom = this.catalogY + this.catalogHeight - 5;
			UiRender.scrollFade(
				graphics,
				this.catalogX + 3,
				catalogTop,
				this.catalogWidth - 7,
				catalogBottom - catalogTop,
				this.catalogScrollRows > 0,
				this.catalogScrollRows < this.catalogMaxScrollRows
			);
		}
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (this.mode != Mode.BASE || event.button() != 0) {
			return false;
		}
		int selectedIndex = this.selectedIndexAt(event.x(), event.y());
		if (selectedIndex >= 0 && selectedIndex < this.working.itemHighlights.size()) {
			this.editingItemId = this.working.itemHighlights.get(selectedIndex).itemId;
			this.mode = Mode.EDITOR;
			this.rebuildWidgets();
			return true;
		}
		int catalogIndex = this.catalogIndexAt(event.x(), event.y());
		if (catalogIndex >= 0 && catalogIndex < this.filteredItems.size()) {
			String itemId = this.filteredItems.get(catalogIndex).itemId();
			if (this.working.findItemHighlight(itemId) == null) {
				this.working.getOrCreateItemHighlight(itemId);
				this.updateScrollBounds();
				this.persistWorking();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (this.mode == Mode.BASE && this.inside(x, y, this.selectedX, this.selectedY, this.selectedWidth, this.selectedHeight)) {
			this.selectedScrollRows = Mth.clamp(
				this.selectedScrollRows - (int) Math.signum(scrollY),
				0,
				this.selectedMaxScrollRows
			);
			return true;
		}
		if (this.mode == Mode.BASE && this.inside(x, y, this.catalogX, this.catalogY, this.catalogWidth, this.catalogHeight)) {
			this.catalogScrollRows = Mth.clamp(
				this.catalogScrollRows - (int) Math.signum(scrollY),
				0,
				this.catalogMaxScrollRows
			);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (!event.isEscape()) {
			return super.keyPressed(event);
		}
		if (this.mode == Mode.EDITOR) {
			this.mode = Mode.BASE;
			this.editingItemId = null;
			this.rebuildWidgets();
		} else {
			this.onClose();
		}
		return true;
	}

	@Override
	public void onClose() {
		// Item selections and editor changes are intentionally durable even when
		// the player leaves with Back/Escape instead of the footer Done button.
		this.persistWorking();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void addBaseWidgets() {
		EditBox search = new EditBox(
			this.font,
			this.catalogX + 8,
			this.catalogY + 25,
			Math.max(40, this.catalogWidth - 16),
			20,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.search")
		);
		search.setHint(Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.search"));
		search.setMaxLength(80);
		search.setValue(this.searchValue);
		search.setResponder(value -> {
			this.searchValue = value;
			this.catalogScrollRows = 0;
			this.filterItems();
		});
		this.addRenderableWidget(search);

		int footerY = this.panelY + this.panelHeight - 27;
		int available = this.panelWidth - 24;
		int gap = 6;
		int buttonWidth = Math.min(126, Math.max(54, (available - gap * 2) / 3));
		this.addRenderableWidget(new GlassButton(
			this.panelX + 12,
			footerY,
			buttonWidth,
			21,
			Component.translatable("gui.done"),
			button -> this.saveAndClose(),
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			this.panelX + 12 + buttonWidth + gap,
			footerY,
			buttonWidth,
			21,
			Component.translatable("screen.kohs_inventory_tweaks.reset_all"),
			button -> {
				this.working.itemHighlights.clear();
				this.selectedScrollRows = 0;
				this.updateScrollBounds();
				this.persistWorking();
			},
			GlassButton.Variant.DANGER
		));
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelWidth - 12 - buttonWidth,
			footerY,
			buttonWidth,
			21,
			Component.translatable("gui.back"),
			button -> this.onClose(),
			GlassButton.Variant.NORMAL
		));
	}

	private void addEditorWidgets() {
		ItemHighlight editing = this.editingHighlight();
		if (editing == null) {
			this.mode = Mode.BASE;
			this.rebuildWidgets();
			return;
		}
		this.addRenderableWidget(new ColorPaletteWidget(
			this.backgroundPaletteX,
			this.paletteY,
			this.paletteWidth,
			this.paletteHeight,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.background_color"),
			() -> editing.backgroundColor,
			value -> {
				editing.backgroundColor = value;
				this.persistWorking();
			}
		));
		this.addRenderableWidget(new ColorPaletteWidget(
			this.borderPaletteX,
			this.paletteY,
			this.paletteWidth,
			this.paletteHeight,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.border_color"),
			() -> editing.borderColor,
			value -> {
				editing.borderColor = value;
				this.persistWorking();
			}
		));

		int gap = 6;
		int toggleWidth = Math.max(60, (this.editorWidth - 28 - gap) / 2);
		this.addRenderableWidget(new GlassButton(
			this.editorX + 14,
			this.editorToggleY,
			toggleWidth,
			20,
			this.toggleLabel("screen.kohs_inventory_tweaks.item_highlighter.hotbar", editing.highlightHotbar),
			button -> {
				editing.highlightHotbar = !editing.highlightHotbar;
				button.setMessage(this.toggleLabel("screen.kohs_inventory_tweaks.item_highlighter.hotbar", editing.highlightHotbar));
				this.persistWorking();
			},
			GlassButton.Variant.TOGGLE,
			() -> editing.highlightHotbar
		));
		GlassButton dynamicButton = new GlassButton(
			this.editorX + 14 + toggleWidth + gap,
			this.editorToggleY,
			this.editorWidth - 28 - toggleWidth - gap,
			20,
			this.toggleLabel("screen.kohs_inventory_tweaks.item_highlighter.dynamic", editing.dynamicHighlight),
			button -> {
				editing.dynamicHighlight = !editing.dynamicHighlight;
				button.setMessage(this.toggleLabel("screen.kohs_inventory_tweaks.item_highlighter.dynamic", editing.dynamicHighlight));
				this.persistWorking();
			},
			GlassButton.Variant.TOGGLE,
			() -> editing.dynamicHighlight
		);
		dynamicButton.setTooltip(Tooltip.create(Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.dynamic.description")));
		dynamicButton.setTooltipDelay(Duration.ofMillis(220));
		this.addRenderableWidget(dynamicButton);

		int footerGap = 5;
		int footerWidth = Math.max(48, (this.editorWidth - 28 - footerGap * 2) / 3);
		this.addRenderableWidget(new GlassButton(
			this.editorX + 14,
			this.editorFooterY,
			footerWidth,
			21,
			Component.translatable("gui.done"),
			button -> {
				this.persistWorking();
				this.mode = Mode.BASE;
				this.editingItemId = null;
				this.rebuildWidgets();
			},
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			this.editorX + 14 + footerWidth + footerGap,
			this.editorFooterY,
			footerWidth,
			21,
			Component.translatable("screen.kohs_inventory_tweaks.reset"),
			button -> {
				editing.reset();
				this.persistWorking();
				this.rebuildWidgets();
			},
			GlassButton.Variant.NORMAL
		));
		this.addRenderableWidget(new GlassButton(
			this.editorX + 14 + (footerWidth + footerGap) * 2,
			this.editorFooterY,
			this.editorWidth - 28 - footerWidth * 2 - footerGap * 2,
			21,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.remove"),
			button -> {
				this.working.removeItemHighlight(editing.itemId);
				this.persistWorking();
				this.mode = Mode.BASE;
				this.editingItemId = null;
				this.updateScrollBounds();
				this.rebuildWidgets();
			},
			GlassButton.Variant.DANGER
		));
	}

	private void drawBase(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.centeredText(this.font, this.title, this.panelX + this.panelWidth / 2, this.panelY + 10, UiTheme.TEXT);
		UiRender.panel(graphics, this.selectedX, this.selectedY, this.selectedWidth, this.selectedHeight, 7, UiTheme.PREVIEW_GLASS, UiTheme.ACCENT_SOFT);
		UiRender.panel(graphics, this.catalogX, this.catalogY, this.catalogWidth, this.catalogHeight, 7, UiTheme.PREVIEW_GLASS, UiTheme.BORDER_SOFT);
		graphics.centeredText(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.selected"),
			this.selectedX + this.selectedWidth / 2,
			this.selectedY + 9,
			UiTheme.TEXT
		);
		graphics.centeredText(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.vanilla_items"),
			this.catalogX + this.catalogWidth / 2,
			this.catalogY + 9,
			UiTheme.TEXT
		);
		this.drawSelectedItems(graphics, mouseX, mouseY);
		this.drawCatalogItems(graphics, mouseX, mouseY);
	}

	private void drawSelectedItems(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		int gridX = this.selectedX + 6;
		int gridY = this.selectedY + 24;
		int gridBottom = this.selectedY + this.selectedHeight - 5;
		graphics.enableScissor(this.selectedX + 3, gridY, this.selectedX + this.selectedWidth - 3, gridBottom);
		for (int visibleRow = 0; visibleRow < this.selectedVisibleRows; visibleRow++) {
			int sourceRow = visibleRow + this.selectedScrollRows;
			for (int column = 0; column < this.selectedColumns; column++) {
				int index = sourceRow * this.selectedColumns + column;
				if (index >= this.working.itemHighlights.size()) {
					break;
				}
				ItemHighlight highlight = this.working.itemHighlights.get(index);
				ItemStack stack = this.stackFor(highlight.itemId);
				int x = gridX + column * CELL_SIZE + 3;
				int y = gridY + visibleRow * CELL_SIZE + 3;
				ItemHighlighterController.drawHighlightLayer(graphics, x, y, SLOT_SIZE, highlight, false);
				graphics.item(stack, x + 1, y + 1);
				ItemHighlighterController.drawHighlightLayer(graphics, x, y, SLOT_SIZE, highlight, true);
				if (this.inside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
					graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
				}
			}
		}
		graphics.disableScissor();
		this.drawScrollbar(graphics, this.selectedX + this.selectedWidth - 4, gridY, gridBottom, this.selectedScrollRows, this.selectedMaxScrollRows);
		if (this.working.itemHighlights.isEmpty()) {
			graphics.textWithWordWrap(
				this.font,
				Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.empty"),
				this.selectedX + 9,
				gridY + 8,
				this.selectedWidth - 18,
				UiTheme.TEXT_MUTED
			);
		}
	}

	private void drawCatalogItems(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
		int gridX = this.catalogX + 7;
		int gridY = this.catalogY + 49;
		int gridBottom = this.catalogY + this.catalogHeight - 5;
		graphics.enableScissor(this.catalogX + 3, gridY, this.catalogX + this.catalogWidth - 3, gridBottom);
		for (int visibleRow = 0; visibleRow < this.catalogVisibleRows; visibleRow++) {
			int sourceRow = visibleRow + this.catalogScrollRows;
			for (int column = 0; column < this.catalogColumns; column++) {
				int index = sourceRow * this.catalogColumns + column;
				if (index >= this.filteredItems.size()) {
					break;
				}
				ItemEntry entry = this.filteredItems.get(index);
				int x = gridX + column * CELL_SIZE + 3;
				int y = gridY + visibleRow * CELL_SIZE + 3;
				boolean selected = this.working.findItemHighlight(entry.itemId()) != null;
				UiRender.roundedRect(graphics, x, y, SLOT_SIZE, SLOT_SIZE, 3, selected ? UiTheme.GLASS_SELECTED : 0xB0271838);
				graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, selected ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);
				graphics.item(entry.stack(), x + 1, y + 1);
				if (this.inside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
					graphics.setTooltipForNextFrame(this.font, entry.stack(), mouseX, mouseY);
				}
			}
		}
		graphics.disableScissor();
		this.drawScrollbar(graphics, this.catalogX + this.catalogWidth - 4, gridY, gridBottom, this.catalogScrollRows, this.catalogMaxScrollRows);
	}

	private void drawEditor(final GuiGraphicsExtractor graphics) {
		ItemHighlight editing = this.editingHighlight();
		if (editing == null) {
			return;
		}
		ItemStack stack = this.stackFor(editing.itemId);
		UiRender.panel(graphics, this.editorX, this.editorY, this.editorWidth, this.editorHeight, 10, UiTheme.GLASS, UiTheme.ACCENT_SOFT);
		graphics.centeredText(this.font, stack.getHoverName(), this.editorX + this.editorWidth / 2, this.editorY + 9, UiTheme.TEXT);

		if (this.editorPreviewSize > 0) {
			graphics.pose().pushMatrix();
			float previewScale = this.editorPreviewSize / (float) SLOT_SIZE;
			graphics.pose().translate(this.editorPreviewX, this.editorPreviewY);
			graphics.pose().scale(previewScale, previewScale);
			ItemHighlighterController.drawHighlightLayer(graphics, 0, 0, SLOT_SIZE, editing, false);
			graphics.item(stack, 1, 1);
			ItemHighlighterController.drawHighlightLayer(graphics, 0, 0, SLOT_SIZE, editing, true);
			graphics.pose().popMatrix();
		}

		graphics.text(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.background_color"),
			this.backgroundPaletteX,
			this.paletteY - 10,
			UiTheme.TEXT_MUTED
		);
		graphics.text(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.item_highlighter.border_color"),
			this.borderPaletteX,
			this.paletteY - 10,
			UiTheme.TEXT_MUTED
		);
	}

	private void calculateLayout() {
		int horizontalMargin = this.width < 560 ? 8 : this.width < 760 ? 24 : 48;
		int verticalMargin = this.height < 300 ? 8 : 24;
		int availableWidth = Math.max(1, this.width - horizontalMargin * 2);
		int availableHeight = Math.max(1, this.height - verticalMargin * 2);
		this.panelWidth = Math.min(920, Math.max(Math.min(220, availableWidth), availableWidth));
		this.panelHeight = Math.min(540, Math.max(Math.min(170, availableHeight), availableHeight));
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.contentTop = this.panelY + 31;
		this.contentBottom = this.panelY + this.panelHeight - 34;

		int contentWidth = this.panelWidth - 24;
		int gap = contentWidth < 420 ? 6 : 10;
		this.selectedWidth = Mth.clamp((int) (contentWidth * 0.28F), Math.min(82, contentWidth / 3), 188);
		this.selectedX = this.panelX + 12;
		this.selectedY = this.contentTop;
		this.selectedHeight = Math.max(90, this.contentBottom - this.contentTop);
		this.catalogX = this.selectedX + this.selectedWidth + gap;
		this.catalogY = this.contentTop;
		this.catalogWidth = Math.max(100, this.panelX + this.panelWidth - 12 - this.catalogX);
		this.catalogHeight = this.selectedHeight;

		this.selectedColumns = Math.max(1, (this.selectedWidth - 12) / CELL_SIZE);
		this.selectedVisibleRows = Math.max(1, (this.selectedHeight - 29) / CELL_SIZE);
		this.catalogColumns = Math.max(2, (this.catalogWidth - 14) / CELL_SIZE);
		this.catalogVisibleRows = Math.max(1, (this.catalogHeight - 54) / CELL_SIZE);
		this.updateScrollBounds();

		this.editorWidth = Math.min(620, Math.max(220, this.panelWidth - 16));
		this.editorHeight = Math.min(330, Math.max(170, this.panelHeight - 12));
		this.editorX = (this.width - this.editorWidth) / 2;
		this.editorY = (this.height - this.editorHeight) / 2;
		this.compactEditor = this.editorHeight < 230 || this.editorWidth < 420;
		boolean previewBesidePalettes = this.editorWidth >= 300;
		this.editorPreviewSize = previewBesidePalettes ? (this.compactEditor ? 32 : 54) : 0;
		this.editorPreviewX = this.editorX + (this.compactEditor ? 14 : 20);
		this.editorPreviewY = this.editorY + 43;

		int paletteAreaX = previewBesidePalettes
			? this.editorPreviewX + this.editorPreviewSize + (this.compactEditor ? 12 : 20)
			: this.editorX + 14;
		int paletteAreaWidth = this.editorX + this.editorWidth - 14 - paletteAreaX;
		int paletteGap = 7;
		this.paletteWidth = Math.max(54, (paletteAreaWidth - paletteGap) / 2);
		this.backgroundPaletteX = paletteAreaX;
		this.borderPaletteX = paletteAreaX + this.paletteWidth + paletteGap;
		this.editorFooterY = this.editorY + this.editorHeight - 28;
		this.editorToggleY = this.editorFooterY - 28;
		this.paletteY = this.editorY + 43;
		this.paletteHeight = Math.max(28, Math.min(this.compactEditor ? 44 : 64, this.editorToggleY - this.paletteY - 5));
	}

	private void ensureItems() {
		if (!this.allItems.isEmpty()) {
			return;
		}
		BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).forEach(item -> {
			try {
				Identifier id = BuiltInRegistries.ITEM.getKey(item);
				ItemStack stack = item.getDefaultInstance();
				if (!stack.isEmpty()) {
					String search = (id + " " + stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
					this.allItems.add(new ItemEntry(id.toString(), stack, search));
				}
			} catch (RuntimeException ignored) {
				// 26.1.2 exposes a few internal registry entries before their components are bound.
			}
		});
		this.allItems.sort(Comparator.comparing(ItemEntry::itemId));
		this.filterItems();
	}

	private void filterItems() {
		String query = this.searchValue.strip().toLowerCase(Locale.ROOT);
		this.filteredItems.clear();
		for (ItemEntry entry : this.allItems) {
			if (query.isEmpty() || entry.searchText().contains(query)) {
				this.filteredItems.add(entry);
			}
		}
		this.updateScrollBounds();
	}

	private void updateScrollBounds() {
		int selectedRows = (this.working.itemHighlights.size() + Math.max(1, this.selectedColumns) - 1) / Math.max(1, this.selectedColumns);
		int catalogRows = (this.filteredItems.size() + Math.max(1, this.catalogColumns) - 1) / Math.max(1, this.catalogColumns);
		this.selectedMaxScrollRows = Math.max(0, selectedRows - Math.max(1, this.selectedVisibleRows));
		this.catalogMaxScrollRows = Math.max(0, catalogRows - Math.max(1, this.catalogVisibleRows));
		this.selectedScrollRows = Mth.clamp(this.selectedScrollRows, 0, this.selectedMaxScrollRows);
		this.catalogScrollRows = Mth.clamp(this.catalogScrollRows, 0, this.catalogMaxScrollRows);
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x4954454DL);
		int count = Mth.clamp(this.width * this.height / 9000, 14, 32);
		for (int i = 0; i < count; i++) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.08F + random.nextFloat() * 0.18F,
				0.008F + random.nextFloat() * 0.025F,
				1 + random.nextInt(2),
				45 + random.nextInt(80),
				random.nextFloat() * 6.28318F
			));
		}
	}

	private int selectedIndexAt(final double mouseX, final double mouseY) {
		int gridX = this.selectedX + 6;
		int gridY = this.selectedY + 24;
		if (!this.inside(mouseX, mouseY, gridX, gridY, this.selectedColumns * CELL_SIZE, this.selectedVisibleRows * CELL_SIZE)) {
			return -1;
		}
		int column = (int) (mouseX - gridX) / CELL_SIZE;
		int row = (int) (mouseY - gridY) / CELL_SIZE;
		return (row + this.selectedScrollRows) * this.selectedColumns + column;
	}

	private int catalogIndexAt(final double mouseX, final double mouseY) {
		int gridX = this.catalogX + 7;
		int gridY = this.catalogY + 49;
		if (!this.inside(mouseX, mouseY, gridX, gridY, this.catalogColumns * CELL_SIZE, this.catalogVisibleRows * CELL_SIZE)) {
			return -1;
		}
		int column = (int) (mouseX - gridX) / CELL_SIZE;
		int row = (int) (mouseY - gridY) / CELL_SIZE;
		return (row + this.catalogScrollRows) * this.catalogColumns + column;
	}

	private void drawScrollbar(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int top,
		final int bottom,
		final int scroll,
		final int maximum
	) {
		if (maximum <= 0 || bottom <= top) {
			return;
		}
		graphics.fill(x, top, x + 2, bottom, UiTheme.SCROLL_TRACK);
		int height = bottom - top;
		int thumb = Math.max(14, height / (maximum + 1));
		int y = top + scroll * Math.max(1, height - thumb) / maximum;
		graphics.fill(x - 1, y, x + 3, y + thumb, UiTheme.ACCENT_SOFT);
	}

	private Component toggleLabel(final String key, final boolean enabled) {
		return Component.translatable(
			key,
			Component.translatable(enabled ? "screen.kohs_inventory_tweaks.enabled" : "screen.kohs_inventory_tweaks.disabled")
		);
	}

	private void saveAndClose() {
		this.persistWorking();
		this.minecraft.setScreen(this.parent);
	}

	private void persistWorking() {
		this.onSave.accept(this.working.copy());
	}

	private @Nullable ItemHighlight editingHighlight() {
		return this.editingItemId == null ? null : this.working.findItemHighlight(this.editingItemId);
	}

	private ItemStack stackFor(final String itemId) {
		Identifier identifier = Identifier.tryParse(itemId);
		Item item = identifier == null ? Items.BARRIER : BuiltInRegistries.ITEM.getValue(identifier);
		try {
			return (item == null ? Items.BARRIER : item).getDefaultInstance();
		} catch (RuntimeException ignored) {
			return Items.BARRIER.getDefaultInstance();
		}
	}

	private boolean inside(final double x, final double y, final int left, final int top, final int width, final int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private record ItemEntry(String itemId, ItemStack stack, String searchText) {
	}

}
