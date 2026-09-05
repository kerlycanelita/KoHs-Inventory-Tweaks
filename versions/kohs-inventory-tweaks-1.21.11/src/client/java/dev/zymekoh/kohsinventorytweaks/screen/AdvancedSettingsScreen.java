package dev.zymekoh.kohsinventorytweaks.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.ProfilePreset;
import dev.zymekoh.kohsinventorytweaks.input.ConfigMenuKeyBinding;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import dev.zymekoh.kohsinventorytweaks.render.AccessibilityRenderController;
import dev.zymekoh.kohsinventorytweaks.render.VisualPerformanceController;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntSupplier;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

/** Explanatory tool catalogue with a separate, preview-backed editor per feature. */
public final class AdvancedSettingsScreen extends Screen {
	private static final Identifier GENERIC_CONTAINER = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	private enum Tab {
		PLAYER_GLOW("player_glow"),
		ACCESSIBILITY("accessibility"),
		CONTAINERS("containers"),
		PERFORMANCE("performance"),
		SAFETY("safety"),
		KEYBINDS("keybinds");

		private final String id;

		Tab(final String id) {
			this.id = id;
		}

		private String titleKey() {
			return "screen.kohs_inventory_tweaks.advanced.tab." + this.id;
		}
	}
	private static final Tab[] TOOL_TABS = {
		Tab.ACCESSIBILITY,
		Tab.PERFORMANCE,
		Tab.SAFETY
	};

	private record Card(int baseY, int height, int depth, Component title, Component description, boolean sideControl) {
	}

	private record MovingWidget(AbstractWidget widget, int baseY, int height) {
	}

	private final Screen parent;
	private final boolean profileEntry;
	private final boolean directEntry;
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();
	private final List<MovingWidget> movingWidgets = new ArrayList<>();
	private final SmoothScroll smoothScroll = new SmoothScroll();
	private InventoryTweaksConfig working;
	private Tab tab;
	private boolean editing;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int tabX;
	private int tabY;
	private int tabWidth;
	private int tabHeight;
	private int tabGap;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int optionsX;
	private int optionsWidth;
	private int contentHeight;
	private boolean previewVisible;
	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;
	private int bodyTop;
	private int bodyBottom;
	private int footerY;
	private int scroll;
	private int maximumScroll;
	private int logicalBottom;
	private boolean awaitingMenuKey;
	private Component status = Component.empty();
	private int containerPreviewIndex;

	public AdvancedSettingsScreen(final Screen parent) {
		this(parent, Tab.ACCESSIBILITY, false, false, false);
	}

	private AdvancedSettingsScreen(
		final Screen parent,
		final Tab tab,
		final boolean editing,
		final boolean profileEntry,
		final boolean directEntry
	) {
		super(Component.translatable(profileEntry || directEntry
			? tab.titleKey()
			: "screen.kohs_inventory_tweaks.advanced.title"));
		this.parent = parent;
		this.tab = tab;
		this.editing = editing;
		this.profileEntry = profileEntry;
		this.directEntry = directEntry;
		this.working = ConfigStore.get().copy();
		if (this.editing && this.status.getString().isBlank()) {
			this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.saved_live");
		}
	}

	public static AdvancedSettingsScreen playerGlow(final Screen parent) {
		return new AdvancedSettingsScreen(parent, Tab.PLAYER_GLOW, true, false, true);
	}

	@Override
	protected void init() {
		this.cards.clear();
		this.movingWidgets.clear();
		this.working = ConfigStore.get().copy();
		this.calculateLayout();
		this.ensureParticles();
		int next = 0;
		if (this.editing) {
			next = switch (this.tab) {
				case PLAYER_GLOW -> this.addPlayerGlowOptions();
				case ACCESSIBILITY -> this.addAccessibilityOptions();
				case CONTAINERS -> this.addContainerOptions();
				case PERFORMANCE -> this.addPerformanceOptions();
				case SAFETY -> this.addSafetyOptions();
				case KEYBINDS -> this.addKeybindOptions();
			};
		} else {
			this.addTabButtons();
			this.addConfigureButton();
		}
		this.logicalBottom = next + 8;
		this.maximumScroll = Math.max(0, this.logicalBottom - Math.max(1, this.bodyBottom - this.bodyTop));
		this.smoothScroll.setMaximum(this.maximumScroll);
		this.scroll = this.smoothScroll.roundedPosition();
		if (this.editing) {
			this.updateWidgetPositions();
		}
		this.addFooterButtons();
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
		if (this.editing) {
			this.scroll = this.smoothScroll.roundedPosition();
			this.smoothScroll.update();
			this.updateWidgetPositions();
		}
		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}

		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 9, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawString(this.font, this.title, this.panelX + 12, this.panelY + 10, UiTheme.TEXT, false);
		if (this.editing) {
			graphics.drawCenteredString(
				this.font,
				Component.translatable(this.tab.titleKey()),
				this.contentX + this.contentWidth / 2,
				this.contentY + 9,
				UiTheme.ACCENT_BRIGHT
			);
		} else {
			graphics.drawString(
				this.font,
				Component.translatable(this.tab.titleKey()),
				this.contentX + 10,
				this.contentY + 9,
				UiTheme.ACCENT_BRIGHT,
				false
			);
		}
		if (!this.editing) {
			graphics.fill(
				this.contentX,
				this.contentY + 28,
				this.contentX + this.contentWidth,
				this.contentY + 29,
				UiTheme.ACCENT_SOFT
			);
		}
		if (this.editing) {
			this.drawLivePreview(graphics, mouseX, mouseY);
			graphics.enableScissor(this.optionsX + 3, this.bodyTop, this.optionsX + this.optionsWidth - 3, this.bodyBottom);
			for (Card card : this.cards) {
				this.drawCard(graphics, card);
			}
			graphics.disableScissor();
		} else {
			this.drawCatalogueExplanation(graphics);
		}
		super.render(graphics, mouseX, mouseY, a);

		if (this.editing) {
			UiRender.scrollFade(
				graphics,
				this.optionsX + 3,
				this.bodyTop,
				this.optionsWidth - 6,
				Math.max(1, this.bodyBottom - this.bodyTop),
				this.scroll > 0,
				this.scroll < this.maximumScroll
			);
		this.drawScrollbar(graphics);
		}
		if (!this.status.getString().isBlank()) {
			graphics.drawCenteredString(
				this.font,
				this.font.plainSubstrByWidth(this.status.getString(), Math.max(1, this.panelWidth - 32)),
				this.panelX + this.panelWidth / 2,
				this.footerY - 10,
				UiTheme.TEXT_MUTED
			);
		}
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (this.editing && x >= this.optionsX && x <= this.optionsX + this.optionsWidth
			&& y >= this.bodyTop && y <= this.bodyBottom
			&& this.smoothScroll.scroll(scrollY, 22.0)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.awaitingMenuKey) {
			this.awaitingMenuKey = false;
			if (!event.isEscape()) {
				ConfigMenuKeyBinding.assign(this.minecraft, InputConstants.getKey(event));
				this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.saved");
			}
			this.rebuildWidgets();
			return true;
		}
		if (event.isEscape()) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (this.editing && !this.profileEntry && !this.directEntry) {
			this.editing = false;
			this.status = Component.empty();
			this.smoothScroll.snapTo(0);
			this.rebuildWidgets();
			return;
		}
		if (this.parent instanceof InventoryTweaksScreen screen) {
			screen.refreshConfigurationFromStore();
		} else if (this.parent instanceof ItemHighlighterScreen screen) {
			screen.reloadConfigurationFromStore();
		}
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

	private void calculateLayout() {
		int horizontalMargin = this.width < 560 ? 6 : this.width < 760 ? 28 : 54;
		int verticalMargin = this.height < 320 ? 6 : this.height < 460 ? 22 : 38;
		int maximumWidth = Math.max(1, this.width - horizontalMargin * 2);
		int maximumHeight = Math.max(1, this.height - verticalMargin * 2);
		this.panelWidth = Math.min(900, maximumWidth);
		this.panelHeight = Math.min(520, maximumHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		int gap = this.panelWidth < 500 ? 5 : 10;
		this.tabX = this.panelX + 9;
		this.tabY = this.panelY + 38;
		this.tabWidth = this.editing ? 0 : Math.min(Mth.clamp(this.panelWidth / 4, 58, 180), Math.max(28, this.panelWidth / 3));
		this.contentX = this.editing ? this.panelX + 9 : this.tabX + this.tabWidth + gap;
		this.contentY = this.tabY;
		this.contentWidth = Math.max(1, this.panelX + this.panelWidth - 9 - this.contentX);
		this.footerY = this.panelY + this.panelHeight - 31;
		this.contentHeight = Math.max(1, this.footerY - this.contentY - 12);
		this.previewVisible = this.editing
			&& this.needsLivePreview()
			&& this.contentHeight >= 178
			&& this.contentWidth >= 560;
		if (this.previewVisible) {
			int editorGap = this.contentWidth < 700 ? 14 : 18;
			this.optionsWidth = Mth.clamp((int) (this.contentWidth * 0.42F), 220, 340);
			this.previewWidth = Math.max(220, this.contentWidth - this.optionsWidth - editorGap);
			this.previewX = this.contentX;
			this.optionsX = this.previewX + this.previewWidth + editorGap;
			this.previewY = this.contentY + 34;
			this.previewHeight = Math.max(1, this.contentHeight - 42);
			this.bodyTop = this.contentY + 34;
		} else {
			this.previewWidth = 0;
			this.previewHeight = 0;
			this.optionsWidth = this.editing ? Math.min(680, Math.max(1, this.contentWidth - 18)) : this.contentWidth;
			this.optionsX = this.contentX + (this.contentWidth - this.optionsWidth) / 2;
			this.previewX = this.contentX;
			this.previewY = this.contentY;
			this.bodyTop = Math.min(this.contentY + 34, this.contentY + Math.max(0, this.contentHeight - 1));
		}
		this.bodyBottom = Math.max(this.bodyTop + 1, this.contentY + this.contentHeight - 8);
		int availableTabs = Math.max(70, this.contentHeight - 4);
		this.tabGap = availableTabs < 120 ? 1 : availableTabs < 150 ? 2 : 4;
		this.tabHeight = Math.max(10, Math.min(31,
			(availableTabs - (TOOL_TABS.length - 1) * this.tabGap) / TOOL_TABS.length));
	}

	private boolean needsLivePreview() {
		return this.tab == Tab.PLAYER_GLOW
			|| this.tab == Tab.ACCESSIBILITY
			|| this.tab == Tab.CONTAINERS;
	}

	private void addTabButtons() {
		for (int index = 0; index < TOOL_TABS.length; index++) {
			Tab next = TOOL_TABS[index];
			GlassButton button = new GlassButton(
				this.tabX,
				this.tabY + 2 + index * (this.tabHeight + this.tabGap),
				this.tabWidth,
				this.tabHeight,
				Component.translatable(next.titleKey()),
				pressed -> {
					this.tab = next;
					this.status = Component.empty();
					this.rebuildWidgets();
				},
				GlassButton.Variant.TAB,
				() -> this.tab == next
			);
			button.setTooltip(Tooltip.create(Component.translatable(
				"screen.kohs_inventory_tweaks.advanced.tab." + next.id + ".summary"
			)));
			button.setTooltipDelay(Duration.ofMillis(220));
			this.addRenderableWidget(button);
		}
	}

	private void addConfigureButton() {
		int width = Math.min(210, Math.max(86, this.contentWidth - 36));
		boolean tiny = this.contentHeight < 140;
		int height = tiny ? 20 : 25;
		int bottomMargin = tiny ? 4 : 13;
		this.addRenderableWidget(new GlassButton(
			this.contentX + (this.contentWidth - width) / 2,
			this.contentY + this.contentHeight - height - bottomMargin,
			width,
			height,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.configure"),
			button -> {
				this.editing = true;
				this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.saved_live");
				this.smoothScroll.snapTo(0);
				this.rebuildWidgets();
			},
			GlassButton.Variant.PRIMARY
		));
	}

	private int addAccessibilityOptions() {
		int y = 8;
		y = this.addToggle(y, 0,
			"screen.kohs_inventory_tweaks.advanced.access.focus",
			"screen.kohs_inventory_tweaks.advanced.access.focus.description",
			this.working.accessibilitySlotFocusEnabled,
			config -> config.accessibilitySlotFocusEnabled = !config.accessibilitySlotFocusEnabled);
		if (this.working.accessibilitySlotFocusEnabled) {
			y = this.addToggle(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.focus_pulse",
				"screen.kohs_inventory_tweaks.advanced.access.focus_pulse.description",
				this.working.accessibilitySlotFocusPulse,
				config -> config.accessibilitySlotFocusPulse = !config.accessibilitySlotFocusPulse);
			y = this.addIntSlider(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.focus_opacity",
				"screen.kohs_inventory_tweaks.advanced.opacity.description",
				0, 255, this.working.accessibilitySlotFocusOpacity,
				value -> this.working.accessibilitySlotFocusOpacity = value,
				"screen.kohs_inventory_tweaks.advanced.value");
			y = this.addColorPalette(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.focus_color",
				"screen.kohs_inventory_tweaks.advanced.color.description",
				() -> this.working.accessibilitySlotFocusColor,
				value -> this.working.accessibilitySlotFocusColor = value);
		}
		return y;
	}

	private int addPlayerGlowOptions() {
		int y = 8;
		y = this.addToggle(y, 0,
			"screen.kohs_inventory_tweaks.advanced.access.player_glow",
			"screen.kohs_inventory_tweaks.advanced.access.player_glow.description",
			this.working.visiblePlayerGlowEnabled,
			config -> config.visiblePlayerGlowEnabled = !config.visiblePlayerGlowEnabled);
		if (this.working.visiblePlayerGlowEnabled) {
			y = this.addInfo(y, 1,
				Component.translatable("screen.kohs_inventory_tweaks.advanced.access.player_glow.safe"),
				Component.translatable("screen.kohs_inventory_tweaks.advanced.access.player_glow.safe.description"));
			y = this.addToggle(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.player_highlight",
				"screen.kohs_inventory_tweaks.advanced.access.player_highlight.description",
				this.working.visiblePlayerHighlightEnabled,
				config -> config.visiblePlayerHighlightEnabled = !config.visiblePlayerHighlightEnabled);
			if (this.working.visiblePlayerHighlightEnabled) {
				y = this.addIntSlider(y, 2,
					"screen.kohs_inventory_tweaks.advanced.access.player_glow.intensity",
					"screen.kohs_inventory_tweaks.advanced.access.player_glow.intensity.description",
					0, 255, this.working.visiblePlayerGlowIntensity,
					value -> this.working.visiblePlayerGlowIntensity = value,
					"screen.kohs_inventory_tweaks.advanced.value");
				y = this.addColorPalette(y, 2,
					"screen.kohs_inventory_tweaks.advanced.access.player_glow.color",
					"screen.kohs_inventory_tweaks.advanced.color.description",
					() -> this.working.visiblePlayerGlowColor,
					value -> this.working.visiblePlayerGlowColor = value);
			}
			y = this.addToggle(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.player_light_glow",
				"screen.kohs_inventory_tweaks.advanced.access.player_light_glow.description",
				this.working.visiblePlayerLightGlowEnabled,
				config -> config.visiblePlayerLightGlowEnabled = !config.visiblePlayerLightGlowEnabled);
			if (this.working.visiblePlayerLightGlowEnabled) {
				y = this.addIntSlider(y, 2,
					"screen.kohs_inventory_tweaks.advanced.access.player_light_glow.intensity",
					"screen.kohs_inventory_tweaks.advanced.access.player_light_glow.intensity.description",
					0, 255, this.working.visiblePlayerGlowBrightness,
					value -> this.working.visiblePlayerGlowBrightness = value,
					"screen.kohs_inventory_tweaks.advanced.value");
			}
			y = this.addIntSlider(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.player_glow.distance",
				"screen.kohs_inventory_tweaks.advanced.access.player_glow.distance.description",
				4, 96, this.working.visiblePlayerGlowDistance,
				value -> this.working.visiblePlayerGlowDistance = value,
				"screen.kohs_inventory_tweaks.advanced.blocks");
			y = this.addToggle(y, 1,
				"screen.kohs_inventory_tweaks.advanced.access.player_glow.pulse",
				"screen.kohs_inventory_tweaks.advanced.access.player_glow.pulse.description",
				this.working.visiblePlayerGlowPulse,
				config -> config.visiblePlayerGlowPulse = !config.visiblePlayerGlowPulse);
		}
		return y;
	}

	private int addContainerOptions() {
		int y = 8;
		y = this.addToggle(y, 0,
			"screen.kohs_inventory_tweaks.advanced.containers.scaler",
			"screen.kohs_inventory_tweaks.advanced.containers.scaler.description",
			this.working.inventoryGuiScalerEnabled,
			config -> config.inventoryGuiScalerEnabled = !config.inventoryGuiScalerEnabled);
		if (!this.working.inventoryGuiScalerEnabled) {
			return y;
		}
		y = this.addToggle(y, 1,
			"screen.kohs_inventory_tweaks.advanced.containers.profiles",
			"screen.kohs_inventory_tweaks.advanced.containers.profiles.description",
			this.working.containerProfilesEnabled,
			config -> config.containerProfilesEnabled = !config.containerProfilesEnabled);
		if (!this.working.containerProfilesEnabled) {
			y = this.addToggle(y, 2,
				"screen.kohs_inventory_tweaks.advanced.containers.global",
				"screen.kohs_inventory_tweaks.advanced.containers.global.description",
				this.working.affectAllContainers,
				config -> config.affectAllContainers = !config.affectAllContainers);
			return y;
		}
		y = this.addContainerScale(y, "chest", this.working.chestContainerScaleEnabled, this.working.chestContainerScale,
			config -> {
				config.chestContainerScaleEnabled = !config.chestContainerScaleEnabled;
				this.containerPreviewIndex = 0;
			},
			value -> {
				this.working.chestContainerScale = value;
				this.containerPreviewIndex = 0;
			});
		y = this.addContainerScale(y, "shulker", this.working.shulkerContainerScaleEnabled, this.working.shulkerContainerScale,
			config -> {
				config.shulkerContainerScaleEnabled = !config.shulkerContainerScaleEnabled;
				this.containerPreviewIndex = 1;
			},
			value -> {
				this.working.shulkerContainerScale = value;
				this.containerPreviewIndex = 1;
			});
		y = this.addContainerScale(y, "ender_chest", this.working.enderChestContainerScaleEnabled, this.working.enderChestContainerScale,
			config -> {
				config.enderChestContainerScaleEnabled = !config.enderChestContainerScaleEnabled;
				this.containerPreviewIndex = 2;
			},
			value -> {
				this.working.enderChestContainerScale = value;
				this.containerPreviewIndex = 2;
			});
		y = this.addContainerScale(y, "barrel", this.working.barrelContainerScaleEnabled, this.working.barrelContainerScale,
			config -> {
				config.barrelContainerScaleEnabled = !config.barrelContainerScaleEnabled;
				this.containerPreviewIndex = 3;
			},
			value -> {
				this.working.barrelContainerScale = value;
				this.containerPreviewIndex = 3;
			});
		return y;
	}

	private int addContainerScale(
		final int y,
		final String id,
		final boolean enabled,
		final double value,
		final Consumer<InventoryTweaksConfig> toggle,
		final DoubleConsumer slider
	) {
		int next = this.addToggle(y, 2,
			"screen.kohs_inventory_tweaks.advanced.containers." + id,
			"screen.kohs_inventory_tweaks.advanced.containers.entry.description",
			enabled,
			toggle);
		if (enabled) {
			next = this.addDoubleSlider(next, 3,
				"screen.kohs_inventory_tweaks.advanced.containers.scale",
				"screen.kohs_inventory_tweaks.advanced.containers.scale.description",
				0.65, 3.15, value, slider,
				"screen.kohs_inventory_tweaks.advanced.scale");
		}
		return next;
	}

	private int addPerformanceOptions() {
		int y = 8;
		y = this.addIntSlider(y, 0,
			"screen.kohs_inventory_tweaks.advanced.performance.particles",
			"screen.kohs_inventory_tweaks.advanced.performance.particles.description",
			0, 200, this.working.menuParticleDensity,
			value -> this.working.menuParticleDensity = value,
			"screen.kohs_inventory_tweaks.advanced.percent");
		boolean limited = this.working.animatedBackgroundFps < 60;
		y = this.addToggle(y, 0,
			"screen.kohs_inventory_tweaks.advanced.performance.limit_background",
			"screen.kohs_inventory_tweaks.advanced.performance.limit_background.description",
			limited,
			config -> config.animatedBackgroundFps = config.animatedBackgroundFps < 60 ? 60 : 30);
		if (limited) {
			y = this.addIntSlider(y, 1,
				"screen.kohs_inventory_tweaks.advanced.performance.background_fps",
				"screen.kohs_inventory_tweaks.advanced.performance.background_fps.description",
				1, 60, this.working.animatedBackgroundFps,
				value -> this.working.animatedBackgroundFps = value,
				"screen.kohs_inventory_tweaks.advanced.fps");
			y = this.addToggle(y, 1,
				"screen.kohs_inventory_tweaks.advanced.performance.pause_background",
				"screen.kohs_inventory_tweaks.advanced.performance.pause_background.description",
				this.working.pauseAnimatedBackgroundWhenUnfocused,
				config -> config.pauseAnimatedBackgroundWhenUnfocused = !config.pauseAnimatedBackgroundWhenUnfocused);
		}
		y = this.addToggle(y, 0,
			"screen.kohs_inventory_tweaks.advanced.performance.unfocused_particles",
			"screen.kohs_inventory_tweaks.advanced.performance.unfocused_particles.description",
			this.working.reduceParticlesWhenUnfocused,
			config -> config.reduceParticlesWhenUnfocused = !config.reduceParticlesWhenUnfocused);
		return y;
	}

	private int addSafetyOptions() {
		int y = 8;
		y = this.addToggle(y, 0,
			"screen.kohs_inventory_tweaks.advanced.safety.backups",
			"screen.kohs_inventory_tweaks.advanced.safety.backups.description",
			this.working.automaticBackups,
			config -> config.automaticBackups = !config.automaticBackups);
		if (this.working.automaticBackups) {
			y = this.addIntSlider(y, 1,
				"screen.kohs_inventory_tweaks.advanced.safety.retention",
				"screen.kohs_inventory_tweaks.advanced.safety.retention.description",
				1, 10, this.working.backupRetention,
				value -> this.working.backupRetention = value,
				"screen.kohs_inventory_tweaks.advanced.copies");
			y = this.addAction(y, 1,
				Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.restore"),
				Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.restore.description"),
				Component.translatable("screen.kohs_inventory_tweaks.advanced.restore"),
				button -> {
					boolean restored = ConfigStore.restoreLatestBackup();
					this.status = Component.translatable(restored
						? "screen.kohs_inventory_tweaks.advanced.safety.restore.ok"
						: "screen.kohs_inventory_tweaks.advanced.safety.restore.none");
					this.rebuildWidgets();
				});
		}
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.undo"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.undo.description"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.undo"),
			button -> this.applyHistory(true));
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.redo"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.redo.description"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.redo"),
			button -> this.applyHistory(false));
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.export"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.export.description"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.export"),
			button -> this.exportSnapshot());
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.import"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.import.description"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.import"),
			button -> this.importSnapshot());
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.folder"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.folder.description"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.open"),
			button -> {
				try {
					java.nio.file.Files.createDirectories(ConfigStore.exportsDirectory());
					Util.getPlatform().openFile(ConfigStore.exportsDirectory().toFile());
				} catch (Exception ignored) {
				}
			});
		return y;
	}

	private int addKeybindOptions() {
		int y = 8;
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.menu"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.menu.description"),
			this.awaitingMenuKey
				? Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.press")
				: ConfigMenuKeyBinding.mapping().getTranslatedKeyMessage(),
			button -> {
				this.awaitingMenuKey = true;
				button.setMessage(Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.press"));
			});
		int conflicts = ConfigMenuKeyBinding.conflictCount(this.minecraft);
		y = this.addInfo(y, 1,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.conflicts", conflicts),
			Component.translatable(conflicts == 0
				? "screen.kohs_inventory_tweaks.advanced.keybind.conflicts.none"
				: "screen.kohs_inventory_tweaks.advanced.keybind.conflicts.found"));
		y = this.addAction(y, 0,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.reset"),
			Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.reset.description"),
			Component.translatable("screen.kohs_inventory_tweaks.reset"),
			button -> {
				ConfigMenuKeyBinding.reset(this.minecraft);
				this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.keybind.saved");
				this.rebuildWidgets();
			});
		return y;
	}

	private int addToggle(
		final int y,
		final int depth,
		final String titleKey,
		final String descriptionKey,
		final boolean enabled,
		final Consumer<InventoryTweaksConfig> mutation
	) {
		Component label = Component.translatable(enabled
			? "screen.kohs_inventory_tweaks.enabled"
			: "screen.kohs_inventory_tweaks.disabled");
		return this.addAction(
			y,
			depth,
			Component.literal(enabled ? "▾ " : "▸ ").append(Component.translatable(titleKey)),
			Component.translatable(descriptionKey),
			label,
			button -> this.mutate(mutation, true, true),
			GlassButton.Variant.SWITCH,
			enabled
		);
	}

	private int addColorPalette(
		final int y,
		final int depth,
		final String titleKey,
		final String descriptionKey,
		final IntSupplier color,
		final java.util.function.IntConsumer consumer
	) {
		this.cards.add(new Card(
			y,
			116,
			depth,
			Component.translatable(titleKey),
			Component.translatable(descriptionKey),
			false
		));
		int indent = depth * 11;
		int x = this.optionsX + 10 + indent;
		int width = Math.max(1, this.optionsWidth - 20 - indent);
		ColorPaletteWidget palette = new ColorPaletteWidget(
			x,
			this.bodyTop + y + 52 - this.scroll,
			width,
			60,
			Component.translatable(titleKey),
			color,
			value -> {
				consumer.accept(value);
				this.markCustom();
				this.persist(false);
			}
		);
		palette.setTooltip(Tooltip.create(Component.translatable(descriptionKey)));
		palette.setTooltipDelay(Duration.ofMillis(220));
		this.registerMovingWidget(palette, y + 52, 60);
		return y + 128;
	}

	private int addInfo(final int y, final int depth, final Component title, final Component description) {
		this.cards.add(new Card(y, 54, depth, title, description, false));
		return y + 64;
	}

	private int addAction(
		final int y,
		final int depth,
		final Component title,
		final Component description,
		final Component action,
		final net.minecraft.client.gui.components.Button.OnPress onPress
	) {
		return this.addAction(y, depth, title, description, action, onPress, GlassButton.Variant.NORMAL, false);
	}

	private int addAction(
		final int y,
		final int depth,
		final Component title,
		final Component description,
		final Component action,
		final net.minecraft.client.gui.components.Button.OnPress onPress,
		final GlassButton.Variant variant,
		final boolean selected
	) {
		this.cards.add(new Card(y, 54, depth, title, description, true));
		int indent = depth * 11;
		int width = Math.max(1, Math.min(Math.max(1, this.optionsWidth - 20), Math.max(42, Math.min(112, this.optionsWidth / 3))));
		GlassButton button = new GlassButton(
			this.optionsX + this.optionsWidth - width - 10,
			this.bodyTop + y + 16 - this.scroll,
			width,
			22,
			action,
			onPress,
			variant,
			variant == GlassButton.Variant.SWITCH ? () -> selected : null
		);
		button.setTooltip(Tooltip.create(description));
		button.setTooltipDelay(Duration.ofMillis(220));
		this.registerMovingWidget(button, y + 16, 22);
		return y + 64;
	}

	private int addIntSlider(
		final int y,
		final int depth,
		final String titleKey,
		final String descriptionKey,
		final int minimum,
		final int maximum,
		final int value,
		final java.util.function.IntConsumer consumer,
		final String valueKey
	) {
		return this.addDoubleSlider(
			y, depth, titleKey, descriptionKey, minimum, maximum, value,
			next -> consumer.accept((int) Math.round(next)), valueKey
		);
	}

	private int addDoubleSlider(
		final int y,
		final int depth,
		final String titleKey,
		final String descriptionKey,
		final double minimum,
		final double maximum,
		final double value,
		final DoubleConsumer consumer,
		final String valueKey
	) {
		this.cards.add(new Card(y, 76, depth, Component.translatable(titleKey), Component.translatable(descriptionKey), false));
		int indent = depth * 11;
		int x = this.optionsX + 10 + indent;
		int width = Math.max(1, this.optionsWidth - 20 - indent);
		AdvancedSlider slider = new AdvancedSlider(
			x,
			this.bodyTop + y + 50 - this.scroll,
			width,
			minimum,
			maximum,
			value,
			next -> Component.translatable(valueKey, formatSliderValue(next, maximum)),
			next -> {
				consumer.accept(next);
				this.markCustom();
				this.persist(false);
			}
		);
		this.registerMovingWidget(slider, y + 50, 20);
		return y + 86;
	}

	private void registerMovingWidget(final AbstractWidget widget, final int baseY, final int height) {
		if (widget instanceof GlassButton button) {
			button.setClipBounds(this.optionsX + 3, this.bodyTop, this.optionsX + this.optionsWidth - 3, this.bodyBottom);
		} else if (widget instanceof ThemedSlider slider) {
			slider.setClipBounds(this.optionsX + 3, this.bodyTop, this.optionsX + this.optionsWidth - 3, this.bodyBottom);
		} else if (widget instanceof ColorPaletteWidget palette) {
			palette.setClipBounds(this.optionsX + 3, this.bodyTop, this.optionsX + this.optionsWidth - 3, this.bodyBottom);
		}
		this.addRenderableWidget(widget);
		this.movingWidgets.add(new MovingWidget(widget, baseY, height));
	}

	private void addFooterButtons() {
		int buttonWidth = Math.min(120, Math.max(55, (this.panelWidth - 27) / 3));
		if (this.editing) {
			this.addRenderableWidget(new GlassButton(
				this.panelX + 9,
				this.footerY,
				buttonWidth,
				22,
				Component.translatable("screen.kohs_inventory_tweaks.advanced.reset_tab"),
				button -> this.resetCurrentTab(),
				GlassButton.Variant.DANGER
			));
		}
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelWidth - 9 - buttonWidth,
			this.footerY,
			buttonWidth,
			22,
			Component.translatable("gui.back"),
			button -> this.onClose(),
			GlassButton.Variant.PRIMARY
		));
	}

	private void drawCatalogueExplanation(final GuiGraphics graphics) {
		int x = this.contentX + 15;
		int width = Math.max(30, this.contentWidth - 30);
		if (this.contentHeight < 140) {
			int configureY = this.contentY + this.contentHeight - 24;
			int noteY = this.contentY + 31;
			int noteHeight = Math.max(18, configureY - noteY - 4);
			UiRender.panel(graphics, x, noteY, width, noteHeight, 6, 0xB9221235, UiTheme.BORDER_SOFT);
			graphics.drawString(this.font, this.font.plainSubstrByWidth(
				Component.translatable("screen.kohs_inventory_tweaks.advanced.saved_live").getString(), Math.max(1, width - 18)
			), x + 9, noteY + Math.max(4, (noteHeight - 8) / 2), UiTheme.ACCENT_BRIGHT, false);
			return;
		}
		boolean compact = this.contentHeight < 230;
		int textY;
		if (compact) {
			textY = this.contentY + 38;
		} else {
			int iconY = this.contentY + 37;
			UiRender.glow(graphics, x, iconY, Math.min(52, width), 42, 9, 42);
			UiRender.panel(graphics, x, iconY, Math.min(52, width), 42, 8, 0xD32A1244, UiTheme.ACCENT_SOFT);
			graphics.drawCenteredString(this.font, Component.literal("✦"), x + Math.min(52, width) / 2, iconY + 16, UiTheme.ACCENT_BRIGHT);
			textY = iconY + 55;
		}
		List<net.minecraft.util.FormattedCharSequence> summary = this.font.split(
			Component.translatable("screen.kohs_inventory_tweaks.advanced.tab." + this.tab.id + ".summary"),
			width
		);
		int noteY = this.contentY + this.contentHeight - 78;
		int maximumLines = Math.max(1, (noteY - textY - 6) / 10);
		for (int index = 0; index < Math.min(maximumLines, summary.size()); index++) {
			graphics.drawString(this.font, summary.get(index), x, textY + index * 10, UiTheme.TEXT, false);
		}
		UiRender.panel(graphics, x, noteY, width, 34, 6, 0xB9221235, UiTheme.BORDER_SOFT);
		graphics.drawString(this.font, this.font.plainSubstrByWidth(
			Component.translatable("screen.kohs_inventory_tweaks.advanced.saved_live").getString(), Math.max(1, width - 18)
		), x + 9, noteY + 7, UiTheme.ACCENT_BRIGHT, false);
		graphics.drawString(this.font, this.font.plainSubstrByWidth(
			Component.translatable("screen.kohs_inventory_tweaks.advanced.catalogue.hint").getString(), Math.max(1, width - 18)
		), x + 9, noteY + 19, UiTheme.TEXT_MUTED, false);
	}

	private void drawCard(final GuiGraphics graphics, final Card card) {
		int y = this.bodyTop + card.baseY - this.scroll;
		if (y + card.height < this.bodyTop || y > this.bodyBottom) {
			return;
		}
		int indent = card.depth * 11;
		int x = this.optionsX + 7 + indent;
		int width = Math.max(20, this.optionsWidth - 14 - indent);
		if (card.depth > 0) {
			int branchX = x - 7;
			graphics.fill(branchX, y - 6, branchX + 1, y + card.height / 2, UiTheme.ACCENT_SOFT);
			graphics.fill(branchX, y + card.height / 2, x - 2, y + card.height / 2 + 1, UiTheme.ACCENT_SOFT);
		}
		graphics.fill(x + 3, y + card.height - 1, x + width - 3, y + card.height, 0x578A4FB7);
		int reserved = card.sideControl ? Math.max(70, Math.min(122, this.optionsWidth / 3 + 10)) : 0;
		int textWidth = Math.max(20, width - reserved - 10);
		graphics.drawString(
			this.font,
			this.font.plainSubstrByWidth(card.title.getString(), textWidth),
			x + 8,
				y + 8,
			UiTheme.TEXT,
			false
		);
		List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(card.description, textWidth);
		for (int index = 0; index < Math.min(2, lines.size()); index++) {
			graphics.drawString(this.font, lines.get(index), x + 8, y + 24 + index * 9, UiTheme.TEXT_MUTED, false);
		}
	}

	private void drawLivePreview(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		if (!this.previewVisible) {
			return;
		}
		UiRender.panel(
			graphics,
			this.previewX,
			this.previewY,
			this.previewWidth,
			this.previewHeight,
			7,
			UiTheme.PREVIEW_GLASS,
			UiTheme.BORDER_SOFT
		);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.advanced.preview"),
			this.previewX + this.previewWidth / 2,
			this.previewY + 7,
			UiTheme.TEXT_MUTED
		);
		if (this.tab == Tab.CONTAINERS) {
			this.drawContainerScalePreview(graphics);
		} else if (this.tab == Tab.PLAYER_GLOW) {
			this.drawPlayerGlowPreview(graphics, mouseX, mouseY);
		} else {
			this.drawInventoryPreview(graphics, mouseX, mouseY);
		}
	}

	private void drawInventoryPreview(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		int areaX = this.previewX + 6;
		int areaY = this.previewY + 21;
		int areaWidth = Math.max(1, this.previewWidth - 12);
		int areaHeight = Math.max(1, this.previewHeight - 27);
		float scale = Math.min(areaWidth / 176.0F, areaHeight / 166.0F);
		scale = Math.max(0.12F, Math.min(1.0F, scale));
		int width = Math.round(176 * scale);
		int height = Math.round(166 * scale);
		int x = areaX + (areaWidth - width) / 2;
		int y = areaY + (areaHeight - height) / 2;
		this.drawInventoryAt(graphics, x, y, scale, mouseX, mouseY);
	}

	private void drawInventoryAt(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final int mouseX,
		final int mouseY
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			InventoryTextureManager.textureFor(this.working),
			0,
			0,
			0.0F,
			0.0F,
			176,
			166,
			256,
			256
		);
		graphics.pose().popMatrix();
		if (this.minecraft.player != null) {
			int entityX0 = x + Math.round(26 * scale);
			int entityY0 = y + Math.round(8 * scale);
			int entityX1 = x + Math.round(75 * scale);
			int entityY1 = y + Math.round(78 * scale);
			InventoryScreen.renderEntityInInventoryFollowsMouse(
				graphics,
				entityX0,
				entityY0,
				entityX1,
				entityY1,
				Math.max(1, Math.round(30 * scale)),
				0.0625F,
				mouseX,
				mouseY,
				this.minecraft.player
			);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(scale, scale);
			double localMouseX = (mouseX - x) / Math.max(0.001F, scale);
			double localMouseY = (mouseY - y) / Math.max(0.001F, scale);
			Slot hovered = null;
			String hoveredDynamic = null;
			for (Slot slot : this.minecraft.player.inventoryMenu.slots) {
				if (slot.isActive()
					&& localMouseX >= slot.x && localMouseX < slot.x + 16
					&& localMouseY >= slot.y && localMouseY < slot.y + 16) {
					hovered = slot;
					InventoryTweaksConfig.ItemHighlight highlight = ItemHighlighterController.highlightFor(this.working, slot.getItem());
					if (highlight != null && highlight.dynamicHighlight) {
						hoveredDynamic = highlight.itemId;
					}
					break;
				}
			}
			for (Slot slot : this.minecraft.player.inventoryMenu.slots) {
				if (!slot.isActive() || slot.getItem().isEmpty()) {
					continue;
				}
				InventoryTweaksConfig.ItemHighlight highlight = ItemHighlighterController.highlightFor(this.working, slot.getItem());
				boolean drawHighlight = highlight != null
					&& (!highlight.dynamicHighlight || highlight.itemId.equals(hoveredDynamic));
				if (drawHighlight) {
					ItemHighlighterController.drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, false);
				}
				graphics.renderItem(slot.getItem(), slot.x, slot.y, slot.x + slot.y * 176);
				graphics.renderItemDecorations(this.font, slot.getItem(), slot.x, slot.y);
				if (drawHighlight) {
					ItemHighlighterController.drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, true);
				}
				if (slot == hovered) {
					AccessibilityRenderController.drawPreviewFocus(graphics, slot.x, slot.y, this.working);
				}
			}
			graphics.pose().popMatrix();
		}
	}

	private void drawPlayerGlowPreview(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		int areaX = this.previewX + 7;
		int areaY = this.previewY + 21;
		int areaWidth = Math.max(1, this.previewWidth - 14);
		int areaHeight = Math.max(1, this.previewHeight - 28);
		graphics.fillGradient(areaX, areaY, areaX + areaWidth, areaY + areaHeight, 0x85230D3C, 0xB10C0816);

		float inventoryScale = Math.min((areaWidth - 100.0F) / 176.0F, (areaHeight - 16.0F) / 166.0F);
		inventoryScale = Math.max(0.25F, Math.min(1.0F, inventoryScale));
		int inventoryWidth = Math.round(176 * inventoryScale);
		int inventoryHeight = Math.round(166 * inventoryScale);
		int inventoryX = areaX + (areaWidth - inventoryWidth) / 2;
		int inventoryY = areaY + (areaHeight - inventoryHeight) / 2;
		UiRender.glow(graphics, inventoryX, inventoryY, inventoryWidth, inventoryHeight, 9, 30);
		this.drawInventoryAt(graphics, inventoryX, inventoryY, inventoryScale, mouseX, mouseY);

		int modelRight = inventoryX - 6;
		int modelLeft = areaX + 2;
		int modelWidth = Math.max(0, modelRight - modelLeft);
		int color = this.working.visiblePlayerGlowColor & 0xFFFFFF;
		if (modelWidth >= 32 && this.working.visiblePlayerGlowEnabled) {
			int haloAlpha = this.working.visiblePlayerLightGlowEnabled
				? 18 + this.working.visiblePlayerGlowBrightness / 5 + this.working.visiblePlayerDepthIntensity / 4
				: 10 + this.working.visiblePlayerDepthIntensity / 4;
			UiRender.roundedRect(
				graphics,
				modelLeft,
				areaY + 3,
				modelWidth,
				Math.max(24, areaHeight - 6),
				12,
				UiRender.withAlpha(color, Math.min(132, haloAlpha))
			);
		}
		if (modelWidth >= 32 && this.minecraft.player != null) {
			AnimatedPlayerPreview.draw(
				graphics,
				this.minecraft.player,
				modelLeft,
				areaY + 3,
				modelRight,
				areaY + areaHeight - 3,
				Math.max(22, Math.min(58, areaHeight - 15)),
				this.working
			);
		}
		if (modelWidth >= 48) {
			graphics.drawCenteredString(
				this.font,
				this.font.plainSubstrByWidth(
					Component.translatable("screen.kohs_inventory_tweaks.advanced.preview.world_layer").getString(),
					Math.max(1, modelWidth - 6)
				),
				modelLeft + modelWidth / 2,
				areaY + 5,
				UiTheme.ACCENT_BRIGHT
			);
		}
	}

	private void drawContainerScalePreview(final GuiGraphics graphics) {
		String[] keys = {"chest", "shulker", "ender_chest", "barrel"};
		boolean[] enabled = {
			this.working.chestContainerScaleEnabled,
			this.working.shulkerContainerScaleEnabled,
			this.working.enderChestContainerScaleEnabled,
			this.working.barrelContainerScaleEnabled
		};
		double[] scales = {
			this.working.chestContainerScale,
			this.working.shulkerContainerScale,
			this.working.enderChestContainerScale,
			this.working.barrelContainerScale
		};
		int index = Math.floorMod(this.containerPreviewIndex, keys.length);
		double selectedScale = this.working.containerProfilesEnabled
			? enabled[index] ? scales[index] : 1.0
			: this.working.affectAllContainers ? this.working.inventoryGuiScale : 1.0;
		int areaX = this.previewX + 6;
		int areaY = this.previewY + 31;
		int areaWidth = Math.max(1, this.previewWidth - 12);
		int areaHeight = Math.max(1, this.previewHeight - 42);
		int imageHeight = 168;
		float base = Math.min(areaWidth / (176.0F * 3.15F), areaHeight / (imageHeight * 3.15F));
		float renderScale = Math.max(0.10F, base * (float) selectedScale);
		int width = Math.round(176 * renderScale);
		int height = Math.round(imageHeight * renderScale);
		int x = areaX + (areaWidth - width) / 2;
		int y = areaY + (areaHeight - height) / 2;
		Identifier texture = InventoryTextureManager.containerTextureFor(this.working, GENERIC_CONTAINER, imageHeight);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(renderScale, renderScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 176, 71, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 71, 0.0F, 126.0F, 176, 96, 256, 256);
		graphics.pose().popMatrix();
		graphics.drawCenteredString(
			this.font,
			Component.translatable(
				"screen.kohs_inventory_tweaks.advanced.preview.container",
				Component.translatable("screen.kohs_inventory_tweaks.advanced.containers." + keys[index]),
				Math.round(selectedScale * 100.0)
			),
			this.previewX + this.previewWidth / 2,
			this.previewY + 19,
			UiTheme.TEXT_MUTED
		);
	}

	private void drawScrollbar(final GuiGraphics graphics) {
		if (this.maximumScroll <= 0) {
			return;
		}
		int trackHeight = Math.max(8, this.bodyBottom - this.bodyTop);
		int thumbHeight = Math.max(12, trackHeight * trackHeight / Math.max(trackHeight, trackHeight + this.maximumScroll));
		int travel = Math.max(1, trackHeight - thumbHeight);
		int y = this.bodyTop + this.scroll * travel / this.maximumScroll;
		graphics.fill(this.optionsX + this.optionsWidth - 5, this.bodyTop, this.optionsX + this.optionsWidth - 3, this.bodyBottom, UiTheme.SCROLL_TRACK);
		graphics.fill(this.optionsX + this.optionsWidth - 6, y, this.optionsX + this.optionsWidth - 2, y + thumbHeight, UiTheme.ACCENT);
	}

	private void updateWidgetPositions() {
		this.scroll = this.smoothScroll.roundedPosition();
		for (MovingWidget moving : this.movingWidgets) {
			int y = this.bodyTop + moving.baseY - this.scroll;
			moving.widget.setY(y);
			moving.widget.visible = y + moving.height > this.bodyTop && y < this.bodyBottom;
		}
	}

	private void mutate(
		final Consumer<InventoryTweaksConfig> mutation,
		final boolean markCustom,
		final boolean rebuild
	) {
		mutation.accept(this.working);
		if (markCustom) {
			this.markCustom();
		}
		this.persist(rebuild);
	}

	private void markCustom() {
		this.working.activeProfile = ProfilePreset.CUSTOM;
	}

	private void persist(final boolean rebuild) {
		ConfigStore.replaceAndSave(this.working);
		this.working = ConfigStore.get().copy();
		InventoryTextureManager.invalidateConfiguration();
		this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.saved_live");
		if (rebuild) {
			this.rebuildWidgets();
		}
	}

	private void resetCurrentTab() {
		switch (this.tab) {
			case ACCESSIBILITY -> {
				this.working.accessibilitySlotFocusEnabled = false;
				this.working.accessibilitySlotFocusPulse = true;
				this.working.accessibilitySlotFocusOpacity = 210;
				this.working.accessibilitySlotFocusColor = 0xD7A3FF;
			}
			case PLAYER_GLOW -> {
				this.working.visiblePlayerGlowEnabled = false;
				this.working.visiblePlayerHighlightEnabled = true;
				this.working.visiblePlayerLightGlowEnabled = true;
				this.working.visiblePlayerGlowIntensity = 120;
				this.working.visiblePlayerGlowBrightness = 96;
				this.working.visiblePlayerDepthIntensity = 220;
				this.working.visiblePlayerGlowDistance = 32;
				this.working.visiblePlayerGlowPulse = true;
				this.working.visiblePlayerGlowColor = 0xB86BFF;
			}
			case CONTAINERS -> {
				this.working.containerProfilesEnabled = false;
				this.working.chestContainerScaleEnabled = true;
				this.working.chestContainerScale = 1.0;
				this.working.shulkerContainerScaleEnabled = true;
				this.working.shulkerContainerScale = 1.0;
				this.working.enderChestContainerScaleEnabled = true;
				this.working.enderChestContainerScale = 1.0;
				this.working.barrelContainerScaleEnabled = true;
				this.working.barrelContainerScale = 1.0;
			}
			case PERFORMANCE -> {
				this.working.menuParticleDensity = 100;
				this.working.animatedBackgroundFps = 30;
				this.working.pauseAnimatedBackgroundWhenUnfocused = true;
				this.working.reduceParticlesWhenUnfocused = true;
			}
			case SAFETY -> {
				this.working.automaticBackups = true;
				this.working.backupRetention = 5;
			}
			case KEYBINDS -> ConfigMenuKeyBinding.reset(this.minecraft);
		}
		if (this.tab != Tab.KEYBINDS) {
			this.markCustom();
		}
		this.persist(true);
	}

	private void applyHistory(final boolean undo) {
		boolean applied = undo ? ConfigStore.undo() : ConfigStore.redo();
		this.status = Component.translatable(applied
			? "screen.kohs_inventory_tweaks.advanced.safety.history.ok"
			: "screen.kohs_inventory_tweaks.advanced.safety.history.empty");
		this.working = ConfigStore.get().copy();
		InventoryTextureManager.invalidateConfiguration();
		this.rebuildWidgets();
	}

	private void exportSnapshot() {
		try {
			Path exported = ConfigStore.exportSnapshot();
			this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.export.ok", exported.getFileName().toString());
		} catch (Exception exception) {
			this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.file.error");
		}
	}

	private void importSnapshot() {
		try {
			String selected;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.json"));
				filters.flip();
				selected = TinyFileDialogs.tinyfd_openFileDialog(
					"Import KoHs Inventory Tweaks configuration",
					ConfigStore.exportsDirectory().toAbsolutePath().toString(),
					filters,
					"JSON configuration",
					false
				);
			}
			if (selected == null || selected.isBlank()) {
				return;
			}
			ConfigStore.importSnapshot(Path.of(selected));
			this.working = ConfigStore.get().copy();
			InventoryTextureManager.invalidateConfiguration();
			this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.import.ok");
			this.rebuildWidgets();
		} catch (Exception exception) {
			this.status = Component.translatable("screen.kohs_inventory_tweaks.advanced.safety.file.error");
		}
	}

	private static String formatSliderValue(final double value, final double maximum) {
		if (maximum <= 2.0) {
			return Math.round(value * 100.0) + "%";
		}
		return Integer.toString((int) Math.round(value));
	}

	private void ensureParticles() {
		int desired = VisualPerformanceController.particleCount(Mth.clamp(this.width * this.height / 7000, 26, 48));
		if (this.particles.size() > desired) {
			this.particles.subList(desired, this.particles.size()).clear();
		}
		Random random = new Random(0x414456414E434544L);
		while (this.particles.size() < desired) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.08F + random.nextFloat() * 0.24F,
				0.12F + random.nextFloat() * 0.30F,
				1 + random.nextInt(3),
				72 + random.nextInt(112),
				random.nextFloat() * 6.28F
			));
		}
	}
}
