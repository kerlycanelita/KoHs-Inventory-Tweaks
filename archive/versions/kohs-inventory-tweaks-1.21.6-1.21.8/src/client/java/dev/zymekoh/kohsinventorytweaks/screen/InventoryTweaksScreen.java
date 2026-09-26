package dev.zymekoh.kohsinventorytweaks.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.CursorPoint;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.TextureSource;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.cursor.CursorTarget;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.input.ConfigMenuKeyBinding;
import dev.zymekoh.kohsinventorytweaks.media.BackgroundMediaManager;
import dev.zymekoh.kohsinventorytweaks.media.BackgroundMediaManager.CropSettings;
import dev.zymekoh.kohsinventorytweaks.media.BackgroundMediaManager.PreparedMedia;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class InventoryTweaksScreen extends Screen {
	private static final ResourceLocation CONTAINER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final ResourceLocation CROP_PREVIEW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
		"kohs_inventory_tweaks",
		"dynamic/background_crop_preview"
	);
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int SCREEN_MARGIN = 8;
	private static final long ENTRANCE_DURATION_NANOS = 320_000_000L;
	private static final long MODAL_ENTRANCE_DURATION_NANOS = 240_000_000L;
	private static final int CUSTOM_LAYER_CARD_HEIGHT = 114;
	private static final int CUSTOM_SLOT_CARD_Y = CUSTOM_LAYER_CARD_HEIGHT + 8;
	private static final int CUSTOM_BACKGROUND_CARD_Y = CUSTOM_SLOT_CARD_Y + CUSTOM_LAYER_CARD_HEIGHT + 8;
	private static final int CUSTOM_BACKGROUND_CARD_HEIGHT = 126;
	private static final int CUSTOM_BACKDROP_CARD_Y = CUSTOM_BACKGROUND_CARD_Y + CUSTOM_BACKGROUND_CARD_HEIGHT + 8;
	private static final int CUSTOM_BACKDROP_CARD_HEIGHT = 78;
	private static final int CUSTOM_CONTENT_HEIGHT = CUSTOM_BACKDROP_CARD_Y + CUSTOM_BACKDROP_CARD_HEIGHT;
	private static final CustomizationPreview[] CUSTOMIZATION_PREVIEWS = CustomizationPreview.values();

	private final Screen parent;
	private final List<FloatingParticle> particles = new ArrayList<>();
	private final List<AbstractWidget> mainLeftScrollingWidgets = new ArrayList<>();
	private final List<AbstractWidget> mainRightScrollingWidgets = new ArrayList<>();
	private final List<AbstractWidget> customizationScrollingWidgets = new ArrayList<>();
	private final long entranceStartedAtNanos = System.nanoTime();
	private InventoryTweaksConfig working;
	private Modal modal = Modal.NONE;
	private Modal animatedModal = Modal.NONE;
	private long modalOpenedAtNanos = System.nanoTime();
	private Modal warningReturnModal = Modal.NONE;
	private CursorTarget selectedTarget = CursorTarget.INVENTORY;
	private boolean selectingPosition;

	private int mainPreviewX;
	private int mainPreviewY;
	private int mainPreviewWidth;
	private int mainPreviewHeight;
	private float mainPreviewScale;
	private int mainKeybindX;
	private int mainKeybindY;
	private int mainKeybindWidth;
	private GlassButton menuKeyButton;
	private boolean awaitingMenuKey;
	private int cursorCardX;
	private int cursorCardY;
	private int tweakCardX;
	private int tweakCardY;
	private int issuesCardX;
	private int issuesCardY;
	private int customizationCardX;
	private int customizationCardY;
	private int itemHighlighterCardX;
	private int itemHighlighterCardY;
	private int guiScalerCardX;
	private int guiScalerCardY;
	private int mainCardWidth;
	private int mainCardHeight;
	private int textureSelectorX;
	private int textureSelectorY;
	private int textureSelectorWidth;
	private boolean compactMain;
	private int mainLeftRailX;
	private int mainLeftRailY;
	private int mainLeftRailWidth;
	private int mainLeftRailHeight;
	private int mainRightRailX;
	private int mainRightRailY;
	private int mainRightRailWidth;
	private int mainRightRailHeight;
	private final SmoothScroll mainLeftSmoothScroll = new SmoothScroll();
	private final SmoothScroll mainRightSmoothScroll = new SmoothScroll();
	private int mainLeftScroll;
	private int mainRightScroll;
	private int mainLeftMaxScroll;
	private int mainRightMaxScroll;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int panelPadding;
	private int headerHeight;
	private int footerHeight;
	private int contentTop;
	private int contentBottom;
	private int sidebarX;
	private int sidebarWidth;
	private int targetTabY;
	private int targetTabHeight;
	private int targetTabGap;
	private int chestSelectorY;
	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;
	private float previewScale;
	private int cursorToggleX;
	private int cursorToggleY;
	private int cursorToggleWidth;
	private int cursorToggleHeight;
	private boolean compactModal;
	private int tweakOptionsX;
	private int tweakOptionsWidth;
	private int tweakFirstCardY;
	private int tweakCardHeight;
	private int tweakCardGap;
	private int customizationPreviewX;
	private int customizationPreviewY;
	private int customizationPreviewWidth;
	private int customizationPreviewHeight;
	private float customizationPreviewScale;
	private int customizationOptionsX;
	private int customizationOptionsY;
	private int customizationOptionsWidth;
	private int customizationOptionsHeight;
	private final SmoothScroll customizationSmoothScroll = new SmoothScroll();
	private int customizationScroll;
	private int customizationMaxScroll;
	private int customizationSelectorX;
	private int customizationSelectorY;
	private int customizationSelectorWidth;
	private CustomizationPreview customizationPreviewTarget = CustomizationPreview.INVENTORY;
	private boolean customizationPreviewVisible;
	private boolean backgroundBusy;
	private Component backgroundStatus = Component.empty();
	private PreparedMedia cropMedia;
	private DynamicTexture cropPreviewTexture;
	private double cropFocusX = 0.5;
	private double cropFocusY = 0.5;
	private double cropZoom = 1.0;
	private boolean cropDragging;
	private Component cropStatus = Component.empty();
	private int cropWorkspaceX;
	private int cropWorkspaceY;
	private int cropWorkspaceWidth;
	private int cropWorkspaceHeight;
	private int cropFrameX;
	private int cropFrameY;
	private int cropFrameWidth;
	private int cropFrameHeight;
	private int cropControlsY;

	private int warningX;
	private int warningY;
	private int warningWidth;
	private int warningHeight;
	private int guiScalerToggleX;
	private int guiScalerToggleY;
	private int guiScalerToggleWidth;
	private int guiScalerSliderX;
	private int guiScalerSliderY;
	private int guiScalerSliderWidth;
	private int guiScalerPreviewX;
	private int guiScalerPreviewY;
	private int guiScalerPreviewWidth;
	private int guiScalerPreviewHeight;
	private float guiScalerPreviewScale;
	private double guiScalerActualScale = 1.0;
	private boolean guiScalerPreviewFitted;

	private enum Modal {
		NONE,
		CURSOR,
		TWEAKS,
		CUSTOMIZATION,
		CROP,
		GUI_SCALER,
		GUI_SCALER_WARNING,
		CUSTOMIZATION_WARNING,
		ANIMATIONS_WARNING,
		WARNING
	}

	public InventoryTweaksScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.title"));
		this.parent = parent;
		// The main preview and child screens must start from the persisted values.
		// Previously Item Highlighter could receive a fresh default config when it
		// was the first feature opened, making saved items look missing and later
		// overwriting unrelated settings with defaults.
		this.working = ConfigStore.get().copy();
	}

	@Override
	protected void init() {
		this.mainLeftScrollingWidgets.clear();
		this.mainRightScrollingWidgets.clear();
		this.customizationScrollingWidgets.clear();
		this.ensureParticles();
		this.calculateMainLayout();
		this.calculateModalLayout();
		Modal visibleModal = this.modal == Modal.ANIMATIONS_WARNING ? Modal.TWEAKS : this.modal;
		if (visibleModal != this.animatedModal) {
			this.animatedModal = visibleModal;
			this.modalOpenedAtNanos = System.nanoTime();
		}
		switch (this.modal) {
			case NONE -> this.addMainButtons();
			case CURSOR -> this.addCursorModalButtons();
			case TWEAKS -> this.addTweaksModalButtons();
			case CUSTOMIZATION -> this.addCustomizationModalButtons();
			case CROP -> this.addCropModalButtons();
			case GUI_SCALER -> this.addGuiScalerModalButtons();
			case GUI_SCALER_WARNING -> this.addGuiScalerWarningButtons();
			case CUSTOMIZATION_WARNING -> this.addCustomizationWarningButtons();
			case ANIMATIONS_WARNING -> this.addAnimationsWarningButtons();
			case WARNING -> this.addWarningButtons();
		}
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
		this.updateSmoothWidgetPositions();
		float entrance = this.entranceProgress();
		float entranceScale = 0.965F + entrance * 0.035F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
		graphics.pose().scale(entranceScale, entranceScale);
		graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);

		for (FloatingParticle particle : this.particles) {
			particle.draw(graphics);
		}
		this.drawMainScreen(graphics, mouseX, mouseY);

		if (this.modal != Modal.NONE) {
			graphics.fill(0, 0, this.width, this.height, UiTheme.MODAL_DIM);
			float modalEntrance = this.modalEntranceProgress();
			float modalScale = 0.965F + modalEntrance * 0.035F;
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.width / 2.0F, this.height / 2.0F);
			graphics.pose().scale(modalScale, modalScale);
			graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
			Modal visibleModal = this.modal == Modal.WARNING
				? this.warningReturnModal
				: this.modal == Modal.ANIMATIONS_WARNING ? Modal.TWEAKS : this.modal;
			if (visibleModal == Modal.CURSOR) {
				this.drawCursorModal(graphics);
			} else if (visibleModal == Modal.TWEAKS) {
				this.drawTweaksModal(graphics);
			} else if (visibleModal == Modal.CUSTOMIZATION) {
				this.drawCustomizationModal(graphics, mouseX, mouseY);
			} else if (visibleModal == Modal.CROP) {
				this.drawCropModal(graphics);
			} else if (visibleModal == Modal.GUI_SCALER) {
				this.drawGuiScalerModal(graphics, mouseX, mouseY);
			}
			if (this.modal == Modal.WARNING) {
				graphics.fill(0, 0, this.width, this.height, 0x70000000);
				this.drawWarning(graphics);
			} else if (this.modal == Modal.GUI_SCALER_WARNING) {
				this.drawGuiScalerWarning(graphics);
			} else if (this.modal == Modal.CUSTOMIZATION_WARNING) {
				this.drawCustomizationWarning(graphics);
			} else if (this.modal == Modal.ANIMATIONS_WARNING) {
				this.drawAnimationsWarning(graphics);
			}
			super.render(graphics, mouseX, mouseY, a);
			this.drawActiveScrollFades(graphics);
			graphics.pose().popMatrix();
		} else {
			super.render(graphics, mouseX, mouseY, a);
			this.drawActiveScrollFades(graphics);
		}
		graphics.pose().popMatrix();

		if (entrance < 1.0F) {
			int veilAlpha = Math.round((1.0F - entrance) * 112.0F);
			graphics.fill(0, 0, this.width, this.height, UiRender.withAlpha(0x120824, veilAlpha));
			int glowAlpha = Math.round((1.0F - entrance) * 96.0F);
			int glowWidth = Math.max(1, Math.round(this.width * entrance));
			graphics.fill(
				(this.width - glowWidth) / 2,
				0,
				(this.width + glowWidth) / 2,
				2,
				UiRender.withAlpha(UiTheme.ACCENT, glowAlpha)
			);
		}
	}

	@Override
	public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (this.modal == Modal.CURSOR
			&& button == 0
			&& this.isSelectedCursorEnabled()
			&& this.isInsidePreview(mouseX, mouseY)) {
			this.selectingPosition = true;
			this.updateSelectedPosition(mouseX, mouseY);
			return true;
		}
		if (this.modal == Modal.CROP
			&& !this.backgroundBusy
			&& button == 0
			&& this.isInsideCropFrame(mouseX, mouseY)) {
			this.cropDragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(
		final double mouseX,
		final double mouseY,
		final int button,
		final double dx,
		final double dy
	) {
		if (this.modal == Modal.CURSOR && this.selectingPosition && button == 0) {
			this.updateSelectedPosition(mouseX, mouseY);
			return true;
		}
		if (this.modal == Modal.CROP && this.cropDragging && !this.backgroundBusy && button == 0) {
			this.moveCropBy(dx, dy);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
		boolean persistCursorPosition = this.selectingPosition;
		this.selectingPosition = false;
		this.cropDragging = false;
		if (persistCursorPosition) {
			this.persistWorking();
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (this.modal == Modal.NONE) {
			if (this.isInsideMainRail(x, y, true) && this.mainLeftMaxScroll > 0) {
				this.mainLeftSmoothScroll.scroll(scrollY, 20.0);
				return true;
			}
			if (this.isInsideMainRail(x, y, false) && this.mainRightMaxScroll > 0) {
				this.mainRightSmoothScroll.scroll(scrollY, 20.0);
				return true;
			}
		}
		if (this.modal == Modal.CUSTOMIZATION
			&& this.isInsideCustomizationOptions(x, y)
			&& this.customizationMaxScroll > 0) {
			this.customizationSmoothScroll.scroll(scrollY, 18.0);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
		if (this.awaitingMenuKey) {
			this.awaitingMenuKey = false;
			if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
				ConfigMenuKeyBinding.assign(this.minecraft, InputConstants.getKey(keyCode, scanCode));
			}
			if (this.menuKeyButton != null) {
				this.menuKeyButton.setMessage(this.menuKeybindLabel());
			}
			return true;
		}
		if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (this.modal == Modal.NONE) {
			this.onClose();
		} else if (this.modal == Modal.WARNING) {
			this.modal = this.warningReturnModal;
			this.rebuildWidgets();
		} else if (this.modal == Modal.GUI_SCALER_WARNING) {
			this.modal = Modal.NONE;
			this.rebuildWidgets();
		} else if (this.modal == Modal.CUSTOMIZATION_WARNING) {
			this.modal = Modal.NONE;
			this.rebuildWidgets();
		} else if (this.modal == Modal.ANIMATIONS_WARNING) {
			this.modal = Modal.TWEAKS;
			this.rebuildWidgets();
		}
		return true;
	}

	@Override
	public void onClose() {
		this.releaseCropPreview();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void addMainButtons() {
		this.menuKeyButton = this.addRenderableWidget(new GlassButton(
			this.mainKeybindX,
			this.mainKeybindY,
			this.mainKeybindWidth,
			20,
			this.menuKeybindLabel(),
			button -> {
				this.awaitingMenuKey = true;
				button.setMessage(Component.translatable("screen.kohs_inventory_tweaks.keybind.prompt"));
			},
			GlassButton.Variant.TAB
		));
		this.menuKeyButton.setTooltip(Tooltip.create(Component.translatable(
			"screen.kohs_inventory_tweaks.keybind.description"
		)));
		this.menuKeyButton.setTooltipDelay(Duration.ofMillis(220));
		this.addMainFeatureButton(
			this.cursorCardX,
			this.cursorCardY,
			true,
			CompatibilityFeature.CURSOR_LANDING,
			"screen.kohs_inventory_tweaks.cursor_landing",
			"screen.kohs_inventory_tweaks.cursor_landing.description",
			button -> this.openModal(Modal.CURSOR),
			GlassButton.Variant.NORMAL
		);
		this.addMainFeatureButton(
			this.tweakCardX,
			this.tweakCardY,
			true,
			CompatibilityFeature.INVENTORY_TWEAKS,
			"screen.kohs_inventory_tweaks.inventory_tweaks",
			"screen.kohs_inventory_tweaks.inventory_tweaks.description",
			button -> this.openModal(Modal.TWEAKS),
			GlassButton.Variant.NORMAL
		);
		this.addMainFeatureButton(
			this.issuesCardX,
			this.issuesCardY,
			true,
			null,
			"screen.kohs_inventory_tweaks.issues_tracker",
			"screen.kohs_inventory_tweaks.issues_tracker.description",
			button -> this.minecraft.setScreen(new IssuesTrackerScreen(this)),
			GlassButton.Variant.NORMAL
		);
		this.addMainFeatureButton(
			this.customizationCardX,
			this.customizationCardY,
			false,
			CompatibilityFeature.CUSTOMIZATION,
			"screen.kohs_inventory_tweaks.customization",
			"screen.kohs_inventory_tweaks.customization.description",
			button -> this.openCustomizationWarning(),
			GlassButton.Variant.NORMAL
		);
		this.addMainFeatureButton(
			this.itemHighlighterCardX,
			this.itemHighlighterCardY,
			false,
			CompatibilityFeature.ITEM_HIGHLIGHTER,
			"screen.kohs_inventory_tweaks.item_highlighter",
			"screen.kohs_inventory_tweaks.item_highlighter.description",
			button -> this.minecraft.setScreen(new ItemHighlighterScreen(
				this,
				this.working,
				updated -> {
					this.working = updated.copy();
					ConfigStore.replaceAndSave(updated);
				}
			)),
			GlassButton.Variant.NORMAL
		);
		this.addMainFeatureButton(
			this.guiScalerCardX,
			this.guiScalerCardY,
			false,
			CompatibilityFeature.GUI_SCALER,
			"screen.kohs_inventory_tweaks.gui_scaler",
			"screen.kohs_inventory_tweaks.gui_scaler.description",
			button -> this.openGuiScaler(),
			GlassButton.Variant.NORMAL
		);
		int textureGap = 4;
		int textureButtonWidth = Math.max(1, (this.textureSelectorWidth - textureGap) / 2);
		this.addRenderableWidget(new GlassButton(
			this.textureSelectorX,
			this.textureSelectorY,
			textureButtonWidth,
			19,
			Component.translatable("screen.kohs_inventory_tweaks.texture.applied"),
			button -> this.setTextureSource(TextureSource.APPLIED),
			GlassButton.Variant.TOGGLE,
			() -> ConfigStore.get().inventoryTextureSource == TextureSource.APPLIED
		));
		this.addRenderableWidget(new GlassButton(
			this.textureSelectorX + textureButtonWidth + textureGap,
			this.textureSelectorY,
			this.textureSelectorWidth - textureButtonWidth - textureGap,
			19,
			Component.translatable("screen.kohs_inventory_tweaks.texture.vanilla"),
			button -> this.setTextureSource(TextureSource.VANILLA),
			GlassButton.Variant.TOGGLE,
			() -> ConfigStore.get().inventoryTextureSource == TextureSource.VANILLA
		));
		this.addRenderableWidget(new GlassButton(
			Math.max(SCREEN_MARGIN, this.width - SCREEN_MARGIN - 70),
			Math.max(SCREEN_MARGIN, this.height - SCREEN_MARGIN - 20),
			70,
			20,
			Component.translatable("gui.done"),
			button -> this.onClose(),
			GlassButton.Variant.PRIMARY
		));
	}

	private void addMainFeatureButton(
		final int x,
		final int y,
		final boolean leftRail,
		final CompatibilityFeature feature,
		final String titleKey,
		final String descriptionKey,
		final Button.OnPress onPress,
		final GlassButton.Variant variant
	) {
		int railY = leftRail ? this.mainLeftRailY : this.mainRightRailY;
		int railHeight = leftRail ? this.mainLeftRailHeight : this.mainRightRailHeight;
		int railX = leftRail ? this.mainLeftRailX : this.mainRightRailX;
		int railWidth = leftRail ? this.mainLeftRailWidth : this.mainRightRailWidth;
		int visibleTop = railY + 22;
		int visibleBottom = railY + railHeight - 5;
		boolean available = feature == null || CompatibilityIssueManager.isFeatureAvailable(feature);
		Component description = Component.translatable(available
			? descriptionKey
			: "screen.kohs_inventory_tweaks.compatibility.feature_disabled");
		GlassButton button = new GlassButton(
			x,
			y,
			this.mainCardWidth,
			this.mainCardHeight,
			Component.translatable(titleKey),
			onPress,
			variant
		).setSubtitle(description)
			.setClipBounds(railX + 2, visibleTop, railX + railWidth - 2, visibleBottom);
		button.active = available;
		button.setTooltip(Tooltip.create(description));
		button.setTooltipDelay(Duration.ofMillis(220));
		button.visible = y + this.mainCardHeight > visibleTop && y < visibleBottom;
		this.addRenderableWidget(button);
		(leftRail ? this.mainLeftScrollingWidgets : this.mainRightScrollingWidgets).add(button);
	}

	private void addCursorModalButtons() {
		CursorTarget[] tabs = {
			CursorTarget.INVENTORY,
			CursorTarget.CHEST_SINGLE,
			CursorTarget.SHULKER,
			CursorTarget.ENDER_CHEST,
			CursorTarget.BARREL
		};
		for (int i = 0; i < tabs.length; i++) {
			CursorTarget target = tabs[i];
			int y = this.targetTabY + i * (this.targetTabHeight + this.targetTabGap);
			this.addRenderableWidget(new GlassButton(
				this.sidebarX,
				y,
				this.sidebarWidth,
				this.targetTabHeight,
				Component.translatable(target == CursorTarget.CHEST_SINGLE
					? "screen.kohs_inventory_tweaks.target.chest"
					: target.translationKey()),
				button -> this.selectTarget(target),
				GlassButton.Variant.TAB,
				() -> target == CursorTarget.CHEST_SINGLE ? this.isChestTarget() : this.selectedTarget == target
			));
		}

		if (this.isChestTarget()) {
			int gap = 3;
			int width = (this.sidebarWidth - gap) / 2;
			this.addRenderableWidget(new GlassButton(
				this.sidebarX,
				this.chestSelectorY,
				width,
				19,
				Component.translatable("screen.kohs_inventory_tweaks.chest.single"),
				button -> this.selectTarget(CursorTarget.CHEST_SINGLE),
				GlassButton.Variant.TOGGLE,
				() -> this.selectedTarget == CursorTarget.CHEST_SINGLE
			));
			this.addRenderableWidget(new GlassButton(
				this.sidebarX + width + gap,
				this.chestSelectorY,
				this.sidebarWidth - width - gap,
				19,
				Component.translatable("screen.kohs_inventory_tweaks.chest.double"),
				button -> this.selectTarget(CursorTarget.CHEST_DOUBLE),
				GlassButton.Variant.TOGGLE,
				() -> this.selectedTarget == CursorTarget.CHEST_DOUBLE
			));
		}

		if (this.selectedTarget != CursorTarget.INVENTORY) {
			this.addRenderableWidget(new GlassButton(
				this.cursorToggleX,
				this.cursorToggleY,
				this.cursorToggleWidth,
				this.cursorToggleHeight,
				this.cursorToggleLabel(),
				button -> {
					this.working.setCursorEnabled(this.selectedTarget, !this.isSelectedCursorEnabled());
					button.setMessage(this.cursorToggleLabel());
					this.persistWorking();
				},
				GlassButton.Variant.SWITCH,
				this::isSelectedCursorEnabled
			));
		}

		this.addFooterButtons(Modal.CURSOR);
	}

	private void addTweaksModalButtons() {
		int toggleWidth = Mth.clamp(this.tweakOptionsWidth / 3, this.compactModal ? 64 : 82, 138);
		this.addTweakToggle(
			this.tweakOptionsX + this.tweakOptionsWidth - toggleWidth - 10,
			this.tweakFirstCardY + Math.max(7, (this.tweakCardHeight - 24) / 2),
			toggleWidth,
			this.centerFixLabel(),
			"screen.kohs_inventory_tweaks.center_mouse_fix.description",
			button -> {
				this.working.centerMouseFix = !this.working.centerMouseFix;
				button.setMessage(this.centerFixLabel());
				this.persistWorking();
			},
			() -> this.working.centerMouseFix
		);
		this.addTweakToggle(
			this.tweakOptionsX + this.tweakOptionsWidth - toggleWidth - 10,
			this.tweakFirstCardY + this.tweakCardHeight + this.tweakCardGap + Math.max(7, (this.tweakCardHeight - 24) / 2),
			toggleWidth,
			this.superFastInventoryLabel(),
			"screen.kohs_inventory_tweaks.super_fast_inventory.description",
			button -> {
				this.working.superFastInventory = !this.working.superFastInventory;
				button.setMessage(this.superFastInventoryLabel());
				this.persistWorking();
			},
			() -> this.working.superFastInventory
		);
		this.addTweakToggle(
			this.tweakOptionsX + this.tweakOptionsWidth - toggleWidth - 10,
			this.tweakFirstCardY + (this.tweakCardHeight + this.tweakCardGap) * 2
				+ Math.max(7, (this.tweakCardHeight - 24) / 2),
			toggleWidth,
			this.removeAnimationsLabel(),
			"screen.kohs_inventory_tweaks.remove_animations.description",
			button -> {
				if (this.working.removeAllInventoryAnimations) {
					this.working.removeAllInventoryAnimations = false;
					this.persistWorking();
					button.setMessage(this.removeAnimationsLabel());
				} else {
					this.modal = Modal.ANIMATIONS_WARNING;
					this.rebuildWidgets();
				}
			},
			() -> this.working.removeAllInventoryAnimations
		);
		this.addFooterButtons(Modal.TWEAKS);
	}

	private void addTweakToggle(
		final int x,
		final int y,
		final int width,
		final Component label,
		final String descriptionKey,
		final Button.OnPress onPress,
		final BooleanSupplier selected
	) {
		GlassButton button = new GlassButton(x, y, width, 24, label, onPress, GlassButton.Variant.SWITCH, selected);
		button.setTooltip(Tooltip.create(Component.translatable(descriptionKey)));
		button.setTooltipDelay(Duration.ofMillis(220));
		this.addRenderableWidget(button);
	}

	private void addCustomizationModalButtons() {
		int arrowWidth = Math.min(24, Math.max(18, this.customizationSelectorWidth / 6));
		int selectorGap = 3;
		int labelWidth = Math.max(1, this.customizationSelectorWidth - arrowWidth * 2 - selectorGap * 2);
		GlassButton previousButton = new GlassButton(
			this.customizationSelectorX,
			this.customizationSelectorY,
			arrowWidth,
			20,
			Component.literal("\u2190"),
			button -> {
				this.cycleCustomizationPreview(-1);
				this.rebuildWidgets();
			},
			GlassButton.Variant.TAB
		);
		previousButton.setTooltip(Tooltip.create(Component.translatable(
			"screen.kohs_inventory_tweaks.customization.preview.previous"
		)));
		previousButton.setTooltipDelay(Duration.ofMillis(220));
		this.addRenderableWidget(previousButton);

		GlassButton previewButton = new GlassButton(
			this.customizationSelectorX + arrowWidth + selectorGap,
			this.customizationSelectorY,
			labelWidth,
			20,
			this.customizationPreviewLabel(),
			button -> {
				this.cycleCustomizationPreview(1);
				this.rebuildWidgets();
			},
			GlassButton.Variant.TAB
		);
		previewButton.setTooltip(Tooltip.create(Component.translatable(
			"screen.kohs_inventory_tweaks.customization.preview.tooltip"
		)));
		previewButton.setTooltipDelay(Duration.ofMillis(220));
		this.addRenderableWidget(previewButton);

		GlassButton nextButton = new GlassButton(
			this.customizationSelectorX + arrowWidth + selectorGap + labelWidth + selectorGap,
			this.customizationSelectorY,
			arrowWidth,
			20,
			Component.literal("\u2192"),
			button -> {
				this.cycleCustomizationPreview(1);
				this.rebuildWidgets();
			},
			GlassButton.Variant.TAB
		);
		nextButton.setTooltip(Tooltip.create(Component.translatable(
			"screen.kohs_inventory_tweaks.customization.preview.next"
		)));
		nextButton.setTooltipDelay(Duration.ofMillis(220));
		this.addRenderableWidget(nextButton);

		int contentY = this.customizationOptionsY + 22 - this.customizationScroll;
		int controlX = this.customizationOptionsX + 8;
		int controlWidth = Math.max(24, this.customizationOptionsWidth - 16);

		this.addCustomizationPalette(
			contentY + 22,
			controlX,
			controlWidth,
			() -> this.working.frameColor,
			value -> this.working.frameColor = value
		);
		this.addCustomizationSlider(contentY + 90, controlX, controlWidth, "screen.kohs_inventory_tweaks.color.opacity", this.working.frameOpacity,
			value -> this.working.frameOpacity = value);

		int slotY = contentY + CUSTOM_SLOT_CARD_Y;
		this.addCustomizationPalette(
			slotY + 22,
			controlX,
			controlWidth,
			() -> this.working.slotColor,
			value -> this.working.slotColor = value
		);
		this.addCustomizationSlider(slotY + 90, controlX, controlWidth, "screen.kohs_inventory_tweaks.color.opacity", this.working.slotOpacity,
			value -> this.working.slotOpacity = value);

		int backgroundY = contentY + CUSTOM_BACKGROUND_CARD_Y;
		int gap = 5;
		int buttonWidth = Math.max(1, (controlWidth - gap) / 2);
		this.addCustomizationButton(
			controlX,
			backgroundY + 25,
			buttonWidth,
			Component.translatable(this.backgroundBusy
				? "screen.kohs_inventory_tweaks.background.importing"
				: "screen.kohs_inventory_tweaks.background.choose"),
			button -> this.chooseBackgroundFile(),
			!this.backgroundBusy,
			GlassButton.Variant.PRIMARY
		);
		this.addCustomizationButton(
			controlX + buttonWidth + gap,
			backgroundY + 25,
			Math.max(1, controlWidth - buttonWidth - gap),
			Component.translatable("screen.kohs_inventory_tweaks.background.remove"),
			button -> {
				this.working.customBackgroundFile = null;
				this.backgroundStatus = Component.translatable("screen.kohs_inventory_tweaks.background.none");
				this.persistWorking();
				this.rebuildWidgets();
			},
			!this.backgroundBusy && this.working.customBackgroundFile != null,
			GlassButton.Variant.DANGER
		);
		this.addCustomizationSlider(
			backgroundY + 51,
			controlX,
			controlWidth,
			"screen.kohs_inventory_tweaks.background.opacity",
			this.working.backgroundOpacity,
			value -> this.working.backgroundOpacity = value
		);

		int backdropY = contentY + CUSTOM_BACKDROP_CARD_Y;
		this.addCustomizationSlider(
			backdropY + 49,
			controlX,
			controlWidth,
			"screen.kohs_inventory_tweaks.backdrop.opacity",
			this.working.inventoryBackdropOpacity,
			value -> this.working.inventoryBackdropOpacity = value
		);
		this.addFooterButtons(Modal.CUSTOMIZATION);
	}

	private void addCustomizationPalette(
		final int y,
		final int x,
		final int width,
		final java.util.function.IntSupplier color,
		final java.util.function.IntConsumer consumer
	) {
		ColorPaletteWidget palette = new ColorPaletteWidget(
			x,
			y,
			width,
			64,
			Component.translatable("screen.kohs_inventory_tweaks.palette"),
			color,
			value -> {
				consumer.accept(value);
				this.persistWorking();
			}
		).setClipBounds(
			this.customizationOptionsX,
			this.customizationOptionsY + 21,
			this.customizationOptionsX + this.customizationOptionsWidth,
			this.customizationOptionsY + this.customizationOptionsHeight
		);
		palette.visible = y + 64 > this.customizationOptionsY + 21
			&& y < this.customizationOptionsY + this.customizationOptionsHeight;
		this.addRenderableWidget(palette);
		this.customizationScrollingWidgets.add(palette);
	}

	private void addCropModalButtons() {
		int available = Math.max(1, this.panelWidth - this.panelPadding * 2);
		int gap = available < 120 ? 2 : 6;
		int centerWidth = Math.min(86, Math.max(1, available / 4));
		int sliderWidth = Math.max(1, available - centerWidth - gap);
		CropZoomSlider zoomSlider = new CropZoomSlider(
			this.panelX + this.panelPadding,
			this.cropControlsY,
			sliderWidth,
			this.cropZoom,
			value -> {
				this.cropZoom = value;
				this.clampCropFocus();
			}
		);
		zoomSlider.active = !this.backgroundBusy;
		this.addRenderableWidget(zoomSlider);

		GlassButton centerButton = new GlassButton(
			this.panelX + this.panelPadding + sliderWidth + gap,
			this.cropControlsY,
			Math.max(1, available - sliderWidth - gap),
			20,
			Component.translatable("screen.kohs_inventory_tweaks.crop.center"),
			button -> {
				this.cropFocusX = 0.5;
				this.cropFocusY = 0.5;
			},
			GlassButton.Variant.NORMAL
		);
		centerButton.active = !this.backgroundBusy;
		this.addRenderableWidget(centerButton);

		int footerY = this.panelY + this.panelHeight - this.footerHeight + 5;
		int buttonWidth = Math.min(150, Math.max(1, (available - gap) / 2));
		GlassButton applyButton = new GlassButton(
			this.panelX + this.panelPadding,
			footerY,
			buttonWidth,
			22,
			Component.translatable(this.backgroundBusy
				? "screen.kohs_inventory_tweaks.crop.processing"
				: "screen.kohs_inventory_tweaks.crop.apply"),
			button -> this.applyCrop(),
			GlassButton.Variant.PRIMARY
		);
		applyButton.active = !this.backgroundBusy && this.cropMedia != null;
		this.addRenderableWidget(applyButton);

		GlassButton cancelButton = new GlassButton(
			this.panelX + this.panelPadding + buttonWidth + gap,
			footerY,
			buttonWidth,
			22,
			Component.translatable("gui.cancel"),
			button -> this.cancelCrop(),
			GlassButton.Variant.NORMAL
		);
		cancelButton.active = !this.backgroundBusy;
		this.addRenderableWidget(cancelButton);
	}

	private void addGuiScalerModalButtons() {
		this.addRenderableWidget(new GlassButton(
			this.guiScalerToggleX,
			this.guiScalerToggleY,
			this.guiScalerToggleWidth,
			22,
			this.guiScalerToggleLabel(),
			button -> {
				this.working.inventoryGuiScalerEnabled = !this.working.inventoryGuiScalerEnabled;
				this.persistWorking();
				this.calculateGuiScalerLayout();
				this.rebuildWidgets();
			},
			GlassButton.Variant.SWITCH,
			() -> this.working.inventoryGuiScalerEnabled
		));

		double maximumScale = InventoryGuiScaler.maximumScaleFor(this.width, this.height);
		this.addRenderableWidget(new InventoryScaleSlider(
			this.guiScalerSliderX,
			this.guiScalerSliderY,
			this.guiScalerSliderWidth,
			maximumScale,
			this.working.inventoryGuiScale,
			value -> {
				this.working.inventoryGuiScale = value;
				this.persistWorking();
				this.calculateGuiScalerLayout();
			}
		));
		this.addGuiScalerActions();
	}

	private void addGuiScalerActions() {
		int gap = 6;
		int available = this.panelWidth - this.panelPadding * 2;
		int buttonWidth = Math.min(150, Math.max(1, (available - gap) / 2));
		int actionY = this.panelY + this.panelHeight - this.footerHeight + 5;
		int startX = this.panelX + (this.panelWidth - buttonWidth * 2 - gap) / 2;
		this.addRenderableWidget(new GlassButton(
			startX,
			actionY,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.save_exit"),
			button -> this.saveAndCloseModal(),
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			startX + buttonWidth + gap,
			actionY,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.reset"),
			button -> {
				this.working.resetGuiScaler();
				this.persistWorking();
				this.rebuildWidgets();
			},
			GlassButton.Variant.DANGER
		));
	}

	private void addGuiScalerWarningButtons() {
		int gap = 8;
		int buttonWidth = Math.min(168, Math.max(1, (this.warningWidth - 28 - gap) / 2));
		int y = this.warningY + this.warningHeight - 32;
		int startX = this.warningX + (this.warningWidth - buttonWidth * 2 - gap) / 2;
		this.addRenderableWidget(new GlassButton(
			startX,
			y,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.gui_scaler.warning.accept"),
			button -> {
				this.openGuiScalerCalibration();
			},
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			startX + buttonWidth + gap,
			y,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.gui_scaler.warning.dismiss"),
			button -> {
				InventoryTweaksConfig saved = ConfigStore.get().copy();
				saved.guiScalerWarningDismissed = true;
				ConfigStore.replaceAndSave(saved);
				this.working.guiScalerWarningDismissed = true;
				this.openGuiScalerCalibration();
			},
			GlassButton.Variant.NORMAL
		));
	}

	private void addCustomizationWarningButtons() {
		int gap = 8;
		int buttonWidth = Math.min(168, Math.max(1, (this.warningWidth - 28 - gap) / 2));
		int y = this.warningY + this.warningHeight - 32;
		int startX = this.warningX + (this.warningWidth - buttonWidth * 2 - gap) / 2;
		this.addRenderableWidget(new GlassButton(
			startX,
			y,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.customization.warning.continue"),
			button -> this.openModal(Modal.CUSTOMIZATION),
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			startX + buttonWidth + gap,
			y,
			buttonWidth,
			22,
			Component.translatable("gui.back"),
			button -> {
				this.modal = Modal.NONE;
				this.rebuildWidgets();
			},
			GlassButton.Variant.NORMAL
		));
	}

	private void addAnimationsWarningButtons() {
		int gap = 8;
		int buttonWidth = Math.min(168, Math.max(1, (this.warningWidth - 28 - gap) / 2));
		int y = this.warningY + this.warningHeight - 32;
		int startX = this.warningX + (this.warningWidth - buttonWidth * 2 - gap) / 2;
		this.addRenderableWidget(new GlassButton(
			startX,
			y,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.remove_animations.warning.enable"),
			button -> {
				this.working.removeAllInventoryAnimations = true;
				this.persistWorking();
				this.modal = Modal.TWEAKS;
				this.rebuildWidgets();
			},
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			startX + buttonWidth + gap,
			y,
			buttonWidth,
			22,
			Component.translatable("gui.cancel"),
			button -> {
				this.modal = Modal.TWEAKS;
				this.rebuildWidgets();
			},
			GlassButton.Variant.NORMAL
		));
	}

	private void addCustomizationSlider(
		final int y,
		final int x,
		final int width,
		final String translationKey,
		final int initialValue,
		final java.util.function.IntConsumer consumer
	) {
		GlassSlider slider = new GlassSlider(x, y, width, translationKey, initialValue, value -> {
			consumer.accept(value);
			this.persistWorking();
		});
		slider.setClipBounds(
			this.customizationOptionsX,
			this.customizationOptionsY + 21,
			this.customizationOptionsX + this.customizationOptionsWidth,
			this.customizationOptionsY + this.customizationOptionsHeight
		);
		slider.visible = y + 20 > this.customizationOptionsY + 21
			&& y < this.customizationOptionsY + this.customizationOptionsHeight;
		this.addRenderableWidget(slider);
		this.customizationScrollingWidgets.add(slider);
	}

	private void addCustomizationButton(
		final int x,
		final int y,
		final int width,
		final Component label,
		final Button.OnPress onPress,
		final boolean active,
		final GlassButton.Variant variant
	) {
		GlassButton button = new GlassButton(x, y, width, 20, label, onPress, variant);
		button.active = active;
		button.setClipBounds(
			this.customizationOptionsX,
			this.customizationOptionsY + 21,
			this.customizationOptionsX + this.customizationOptionsWidth,
			this.customizationOptionsY + this.customizationOptionsHeight
		);
		button.visible = y + 20 > this.customizationOptionsY + 21
			&& y < this.customizationOptionsY + this.customizationOptionsHeight;
		this.addRenderableWidget(button);
		this.customizationScrollingWidgets.add(button);
	}

	private void addFooterButtons(final Modal modalType) {
		int footerY = this.panelY + this.panelHeight - this.footerHeight + 5;
		int available = this.panelWidth - this.panelPadding * 2;
		int gap = 6;
		int buttonWidth = Math.min(126, (available - gap) / 2);
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelPadding,
			footerY,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.save_exit"),
			button -> this.saveAndCloseModal(),
			GlassButton.Variant.PRIMARY
		));
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelPadding + buttonWidth + gap,
			footerY,
			buttonWidth,
			22,
			Component.translatable(modalType == Modal.CURSOR
				? "screen.kohs_inventory_tweaks.reset_all"
				: "screen.kohs_inventory_tweaks.reset"),
			button -> {
				switch (modalType) {
					case CURSOR -> this.working.resetCursorPositions();
					case TWEAKS -> {
						this.working.centerMouseFix = true;
						this.working.superFastInventory = true;
						this.working.removeAllInventoryAnimations = false;
					}
					case CUSTOMIZATION -> {
						this.working.resetCustomization();
						this.backgroundStatus = Component.translatable("screen.kohs_inventory_tweaks.background.none");
						InventoryTextureManager.invalidateConfiguration();
					}
					case GUI_SCALER -> this.working.resetGuiScaler();
					default -> {
					}
				}
				this.persistWorking();
				this.rebuildWidgets();
			},
			GlassButton.Variant.DANGER
		));
		this.addRenderableWidget(new GlassButton(
			this.panelX + this.panelWidth - this.panelPadding - 72,
			this.panelY + 6,
			72,
			20,
			Component.translatable("gui.back"),
			button -> this.attemptCloseModal(),
			GlassButton.Variant.NORMAL
		));
	}

	private void addWarningButtons() {
		int gap = 8;
		int buttonWidth = Math.min(150, (this.warningWidth - 28 - gap) / 2);
		int y = this.warningY + this.warningHeight - 32;
		int startX = this.warningX + (this.warningWidth - buttonWidth * 2 - gap) / 2;
		this.addRenderableWidget(new GlassButton(
			startX,
			y,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.exit_anyway"),
			button -> {
				this.working = ConfigStore.get().copy();
				InventoryTextureManager.invalidateConfiguration();
				this.modal = Modal.NONE;
				this.rebuildWidgets();
			},
			GlassButton.Variant.DANGER
		));
		this.addRenderableWidget(new GlassButton(
			startX + buttonWidth + gap,
			y,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.go_back"),
			button -> {
				this.modal = this.warningReturnModal;
				this.rebuildWidgets();
			},
			GlassButton.Variant.NORMAL
		));
	}

	private void drawMainScreen(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, UiTheme.TEXT);
		if (!this.compactMain && this.height >= 230) {
			graphics.drawCenteredString(
				this.font,
				Component.translatable("screen.kohs_inventory_tweaks.subtitle"),
				this.width / 2,
				23,
				UiTheme.TEXT_MUTED
			);
		}
		this.drawMainRail(
			graphics,
			this.mainLeftRailX,
			this.mainLeftRailY,
			this.mainLeftRailWidth,
			this.mainLeftRailHeight,
			Component.translatable("screen.kohs_inventory_tweaks.section.behavior"),
			this.mainLeftScroll,
			this.mainLeftMaxScroll
		);
		this.drawMainRail(
			graphics,
			this.mainRightRailX,
			this.mainRightRailY,
			this.mainRightRailWidth,
			this.mainRightRailHeight,
			Component.translatable("screen.kohs_inventory_tweaks.section.appearance"),
			this.mainRightScroll,
			this.mainRightMaxScroll
		);

		UiRender.panel(
			graphics,
			this.mainPreviewX - 5,
			this.mainPreviewY - 5,
			this.mainPreviewWidth + 10,
			this.mainPreviewHeight + 10,
			8,
			UiTheme.PREVIEW_GLASS,
			UiTheme.BORDER_SOFT
		);

		this.drawPlayerInventory(
			graphics,
			this.mainPreviewX,
			this.mainPreviewY,
			this.mainPreviewScale,
			mouseX,
			mouseY,
			true,
			ConfigStore.get()
		);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.texture_source"),
			this.textureSelectorX + this.textureSelectorWidth / 2,
			this.textureSelectorY - 11,
			UiTheme.TEXT_MUTED
		);
	}

	private void drawMainRail(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final Component title,
		final int scroll,
		final int maxScroll
	) {
		graphics.drawCenteredString(this.font, title, x + width / 2, y + 5, UiTheme.TEXT_MUTED);
		int accentWidth = Math.min(32, Math.max(8, width / 3));
		graphics.fill(x + (width - accentWidth) / 2, y + 17, x + (width + accentWidth) / 2, y + 18, UiTheme.ACCENT_SOFT);
		if (maxScroll > 0) {
			int trackTop = y + 24;
			int trackHeight = Math.max(8, height - 31);
			int thumbHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + maxScroll));
			int travel = Math.max(1, trackHeight - thumbHeight);
			int thumbY = trackTop + scroll * travel / maxScroll;
			graphics.fill(x + width - 4, trackTop, x + width - 2, trackTop + trackHeight, UiTheme.SCROLL_TRACK);
			graphics.fill(x + width - 5, thumbY, x + width - 1, thumbY + thumbHeight, UiTheme.ACCENT);
		}
	}

	private void drawActiveScrollFades(final GuiGraphics graphics) {
		if (this.modal == Modal.NONE) {
			UiRender.scrollFade(
				graphics,
				this.mainLeftRailX + 3,
				this.mainLeftRailY + 21,
				this.mainLeftRailWidth - 7,
				this.mainLeftRailHeight - 25,
				this.mainLeftScroll > 0,
				this.mainLeftScroll < this.mainLeftMaxScroll
			);
			UiRender.scrollFade(
				graphics,
				this.mainRightRailX + 3,
				this.mainRightRailY + 21,
				this.mainRightRailWidth - 7,
				this.mainRightRailHeight - 25,
				this.mainRightScroll > 0,
				this.mainRightScroll < this.mainRightMaxScroll
			);
		} else if (this.modal == Modal.CUSTOMIZATION) {
			UiRender.scrollFade(
				graphics,
				this.customizationOptionsX + 2,
				this.customizationOptionsY + 21,
				this.customizationOptionsWidth - 5,
				this.customizationOptionsHeight - 23,
				this.customizationScroll > 0,
				this.customizationScroll < this.customizationMaxScroll
			);
		}
	}

	private void drawCursorModal(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.cursor_landing"),
			this.panelX + this.panelPadding,
			this.panelY + 10,
			UiTheme.TEXT
		);

		UiRender.panel(
			graphics,
			this.previewX - 5,
			this.previewY - 5,
			this.previewWidth + 10,
			this.previewHeight + 10,
			7,
			UiTheme.PREVIEW_GLASS,
			UiTheme.BORDER_SOFT
		);
		if (this.selectedTarget == CursorTarget.INVENTORY) {
			this.drawPlayerInventory(graphics, this.previewX, this.previewY, this.previewScale, -1000, -1000, false, this.working);
		} else {
			this.drawContainerPreview(graphics, this.previewX, this.previewY, this.previewScale, this.selectedTarget, this.working);
		}

		boolean cursorEnabled = this.isSelectedCursorEnabled();
		CursorPoint point = cursorEnabled ? this.working.getPosition(this.selectedTarget) : null;
		double x = point == null ? 0.5 : point.x();
		double y = point == null ? 0.5 : point.y();
		int markerX = this.previewX + (int) Math.round(x * Math.max(0, this.previewWidth - 1));
		int markerY = this.previewY + (int) Math.round(y * Math.max(0, this.previewHeight - 1));
		UiRender.crosshair(graphics, markerX, markerY, point != null);

		if (!this.compactModal) {
			String coordinates = !cursorEnabled
				? Component.translatable("screen.kohs_inventory_tweaks.cursor_disabled_vanilla").getString()
				: point == null
				? Component.translatable("screen.kohs_inventory_tweaks.vanilla_center").getString()
				: String.format(Locale.ROOT, "X %.3f  ·  Y %.3f", point.x(), point.y());
			graphics.drawCenteredString(this.font, coordinates, this.previewX + this.previewWidth / 2, this.previewY + this.previewHeight + 9, UiTheme.TEXT_MUTED);
		}
	}

	private void drawTweaksModal(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.inventory_tweaks"),
			this.panelX + this.panelPadding,
			this.panelY + 10,
			UiTheme.TEXT
		);

		int toggleWidth = Mth.clamp(this.tweakOptionsWidth / 3, this.compactModal ? 64 : 82, 138);
		int textWidth = Math.max(36, this.tweakOptionsWidth - toggleWidth - 32);
		this.drawTweakCard(
			graphics,
			this.tweakOptionsX,
			this.tweakFirstCardY,
			this.tweakOptionsWidth,
			this.tweakCardHeight,
			textWidth,
			"screen.kohs_inventory_tweaks.center_mouse_fix",
			"screen.kohs_inventory_tweaks.center_mouse_fix.description"
		);
		this.drawTweakCard(
			graphics,
			this.tweakOptionsX,
			this.tweakFirstCardY + this.tweakCardHeight + this.tweakCardGap,
			this.tweakOptionsWidth,
			this.tweakCardHeight,
			textWidth,
			"screen.kohs_inventory_tweaks.super_fast_inventory",
			"screen.kohs_inventory_tweaks.super_fast_inventory.description"
		);
		this.drawTweakCard(
			graphics,
			this.tweakOptionsX,
			this.tweakFirstCardY + (this.tweakCardHeight + this.tweakCardGap) * 2,
			this.tweakOptionsWidth,
			this.tweakCardHeight,
			textWidth,
			"screen.kohs_inventory_tweaks.remove_animations",
			"screen.kohs_inventory_tweaks.remove_animations.description"
		);
	}

	private void drawTweakCard(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final int textWidth,
		final String titleKey,
		final String descriptionKey
	) {
		UiRender.panel(graphics, x, y, width, height, 8, UiTheme.GLASS_LIGHT, UiTheme.BORDER_SOFT);
		List<FormattedCharSequence> titleLines = this.font.split(Component.translatable(titleKey), textWidth);
		int maximumTitleLines = Math.max(1, Math.min(2, (height - 9) / 10));
		int drawnTitleLines = Math.min(titleLines.size(), maximumTitleLines);
		for (int index = 0; index < drawnTitleLines; index++) {
			graphics.drawString(this.font, titleLines.get(index), x + 10, y + 7 + index * 10, UiTheme.TEXT);
		}
		int descriptionY = y + 9 + drawnTitleLines * 10;
		int descriptionLines = Math.max(0, (y + height - 5 - descriptionY) / 10);
		if (descriptionLines > 0) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable(descriptionKey), textWidth);
			for (int index = 0; index < Math.min(lines.size(), descriptionLines); index++) {
				graphics.drawString(this.font, lines.get(index), x + 10, descriptionY + index * 10, UiTheme.TEXT_MUTED);
			}
		}
	}

	private void drawGuiScalerModal(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY
	) {
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.gui_scaler"),
			this.panelX + this.panelPadding,
			this.panelY + 10,
			UiTheme.TEXT
		);
		if (!this.compactModal) {
			graphics.drawWordWrap(
				this.font,
				Component.translatable("screen.kohs_inventory_tweaks.gui_scaler.description"),
				this.panelX + this.panelPadding,
				this.guiScalerToggleY + 28,
				this.panelWidth - this.panelPadding * 2,
				UiTheme.TEXT_MUTED
			);
		}

		UiRender.panel(
			graphics,
			this.guiScalerPreviewX - 5,
			this.guiScalerPreviewY - 5,
			this.guiScalerPreviewWidth + 10,
			this.guiScalerPreviewHeight + 10,
			7,
			UiTheme.PREVIEW_GLASS,
			this.working.inventoryGuiScalerEnabled ? UiTheme.ACCENT_SOFT : UiTheme.BORDER_SOFT
		);
		this.drawPlayerInventory(
			graphics,
			this.guiScalerPreviewX,
			this.guiScalerPreviewY,
			this.guiScalerPreviewScale,
			mouseX,
			mouseY,
			true,
			this.working
		);
		String previewLabelKey;
		if (this.compactModal) {
			previewLabelKey = this.guiScalerPreviewFitted
				? "screen.kohs_inventory_tweaks.gui_scaler.preview_fitted_compact"
				: "screen.kohs_inventory_tweaks.gui_scaler.preview_actual_compact";
		} else {
			previewLabelKey = this.guiScalerPreviewFitted
				? "screen.kohs_inventory_tweaks.gui_scaler.preview_fitted"
				: "screen.kohs_inventory_tweaks.gui_scaler.preview_actual";
		}
		graphics.drawCenteredString(
			this.font,
			Component.translatable(previewLabelKey, (int) Math.round(this.guiScalerActualScale * 100.0)),
			this.guiScalerSliderX + this.guiScalerSliderWidth / 2,
			this.guiScalerSliderY - 11,
			this.guiScalerPreviewFitted ? UiTheme.WARNING : UiTheme.TEXT_MUTED
		);
	}

	private void drawGuiScalerWarning(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.warningX, this.warningY, this.warningWidth, this.warningHeight, 10, UiTheme.GLASS, UiTheme.WARNING);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.gui_scaler.warning.title"),
			this.warningX + this.warningWidth / 2,
			this.warningY + 14,
			UiTheme.WARNING
		);
		graphics.drawWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.gui_scaler.warning.description"),
			this.warningX + 14,
			this.warningY + 34,
			this.warningWidth - 28,
			UiTheme.TEXT
		);
	}

	private void drawCustomizationWarning(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.warningX, this.warningY, this.warningWidth, this.warningHeight, 10, UiTheme.GLASS, UiTheme.WARNING);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.customization.warning.title"),
			this.warningX + this.warningWidth / 2,
			this.warningY + 14,
			UiTheme.WARNING
		);
		graphics.drawWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.customization.warning.description"),
			this.warningX + 14,
			this.warningY + 34,
			this.warningWidth - 28,
			UiTheme.TEXT
		);
	}

	private void drawAnimationsWarning(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.warningX, this.warningY, this.warningWidth, this.warningHeight, 10, UiTheme.GLASS, UiTheme.WARNING);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.remove_animations.warning.title"),
			this.warningX + this.warningWidth / 2,
			this.warningY + 14,
			UiTheme.WARNING
		);
		graphics.drawWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.remove_animations.warning.description"),
			this.warningX + 14,
			this.warningY + 34,
			this.warningWidth - 28,
			UiTheme.TEXT
		);
	}

	private void drawCustomizationModal(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.customization"),
			this.panelX + this.panelPadding,
			this.panelY + 10,
			UiTheme.TEXT
		);

		this.drawCustomizationRail(
			graphics,
			this.customizationOptionsX,
			this.customizationOptionsY,
			this.customizationOptionsWidth,
			this.customizationOptionsHeight,
			Component.translatable("screen.kohs_inventory_tweaks.customization.right")
		);

		if (this.customizationPreviewVisible) {
			UiRender.panel(
				graphics,
				this.customizationPreviewX - 5,
				this.customizationPreviewY - 5,
				this.customizationPreviewWidth + 10,
				this.customizationPreviewHeight + 10,
				7,
				UiTheme.PREVIEW_GLASS,
				UiTheme.ACCENT_SOFT
			);
			this.drawBackdropPreview(
				graphics,
				this.customizationPreviewX,
				this.customizationPreviewY,
				this.customizationPreviewWidth,
				this.customizationPreviewHeight
			);
			switch (this.customizationPreviewTarget.kind()) {
				case PLAYER -> this.drawPlayerInventory(
					graphics,
					this.customizationPreviewX,
					this.customizationPreviewY,
					this.customizationPreviewScale,
					mouseX,
					mouseY,
					true,
					this.working
				);
				case GENERIC -> this.drawContainerPreview(
					graphics,
					this.customizationPreviewX,
					this.customizationPreviewY,
					this.customizationPreviewScale,
					this.customizationPreviewTarget,
					this.working
				);
				case SURFACE -> this.drawSurfacePreview(
					graphics,
					this.customizationPreviewX,
					this.customizationPreviewY,
					this.customizationPreviewScale,
					this.customizationPreviewTarget,
					this.working
				);
				case BUNDLE -> this.drawBundlePreview(
					graphics,
					this.customizationPreviewX,
					this.customizationPreviewY,
					this.customizationPreviewScale,
					this.working
				);
			}
			graphics.drawCenteredString(
				this.font,
				Component.translatable("screen.kohs_inventory_tweaks.customization.affects_all"),
				this.customizationPreviewX + this.customizationPreviewWidth / 2,
				this.customizationPreviewY + this.customizationPreviewHeight + 8,
				UiTheme.TEXT_MUTED
			);
		}

		graphics.enableScissor(
			this.customizationOptionsX,
			this.customizationOptionsY + 21,
			this.customizationOptionsX + this.customizationOptionsWidth,
			this.customizationOptionsY + this.customizationOptionsHeight
		);
		int contentY = this.customizationOptionsY + 22 - this.customizationScroll;
		this.drawCustomizationLayerCard(
			graphics,
			this.customizationOptionsX + 2,
			this.customizationOptionsWidth - 6,
			contentY,
			Component.translatable("screen.kohs_inventory_tweaks.frame_layer"),
			this.working.frameColor,
			this.working.frameOpacity
		);
		this.drawCustomizationLayerCard(
			graphics,
			this.customizationOptionsX + 2,
			this.customizationOptionsWidth - 6,
			contentY + CUSTOM_SLOT_CARD_Y,
			Component.translatable("screen.kohs_inventory_tweaks.slot_layer"),
			this.working.slotColor,
			this.working.slotOpacity
		);
		this.drawBackgroundCard(
			graphics,
			this.customizationOptionsX + 2,
			this.customizationOptionsWidth - 6,
			contentY + CUSTOM_BACKGROUND_CARD_Y
		);
		this.drawBackdropCard(
			graphics,
			this.customizationOptionsX + 2,
			this.customizationOptionsWidth - 6,
			contentY + CUSTOM_BACKDROP_CARD_Y
		);
		graphics.disableScissor();

		this.drawPixelScrollbar(
			graphics,
			this.customizationOptionsX + this.customizationOptionsWidth - 3,
			this.customizationOptionsY + 22,
			this.customizationOptionsY + this.customizationOptionsHeight - 3,
			this.customizationScroll,
			this.customizationMaxScroll,
			CUSTOM_CONTENT_HEIGHT
		);
	}

	private void drawCropModal(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 10, UiTheme.GLASS, UiTheme.BORDER);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.crop.title"),
			this.panelX + this.panelPadding,
			this.panelY + 10,
			UiTheme.TEXT
		);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.crop.ratio"),
			this.panelX + this.panelWidth - this.panelPadding - 70,
			this.panelY + 10,
			UiTheme.TEXT_MUTED
		);

		UiRender.panel(
			graphics,
			this.cropWorkspaceX,
			this.cropWorkspaceY,
			this.cropWorkspaceWidth,
			this.cropWorkspaceHeight,
			7,
			0xE5120821,
			UiTheme.BORDER_SOFT
		);

		if (this.cropMedia != null && this.cropPreviewTexture != null) {
			double scale = this.cropDisplayScale();
			int imageWidth = Math.max(1, (int) Math.round(this.cropMedia.previewWidth() * scale));
			int imageHeight = Math.max(1, (int) Math.round(this.cropMedia.previewHeight() * scale));
			int imageX = (int) Math.round(this.cropFrameX + this.cropFrameWidth / 2.0 - this.cropFocusX * imageWidth);
			int imageY = (int) Math.round(this.cropFrameY + this.cropFrameHeight / 2.0 - this.cropFocusY * imageHeight);

			graphics.enableScissor(
				this.cropWorkspaceX + 1,
				this.cropWorkspaceY + 1,
				this.cropWorkspaceX + this.cropWorkspaceWidth - 1,
				this.cropWorkspaceY + this.cropWorkspaceHeight - 1
			);
			graphics.pose().pushMatrix();
			graphics.pose().translate(imageX, imageY);
			graphics.pose().scale((float) scale, (float) scale);
			graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				CROP_PREVIEW_TEXTURE,
				0,
				0,
				0.0F,
				0.0F,
				this.cropMedia.previewWidth(),
				this.cropMedia.previewHeight(),
				this.cropMedia.previewWidth(),
				this.cropMedia.previewHeight()
			);
			graphics.pose().popMatrix();
			this.drawCropDim(graphics);
			graphics.disableScissor();
		}

		UiRender.outline(graphics, this.cropFrameX - 1, this.cropFrameY - 1, this.cropFrameWidth + 2, this.cropFrameHeight + 2, 0xD0000000);
		UiRender.outline(graphics, this.cropFrameX, this.cropFrameY, this.cropFrameWidth, this.cropFrameHeight, UiTheme.ACCENT);
		this.drawCropCorners(graphics);

		Component helper = this.cropStatus.getString().isBlank()
			? Component.translatable("screen.kohs_inventory_tweaks.crop.drag_hint")
			: this.cropStatus;
		graphics.drawCenteredString(
			this.font,
			helper,
			this.panelX + this.panelWidth / 2,
			this.cropControlsY - 11,
			this.cropStatus.getString().isBlank() ? UiTheme.TEXT_MUTED : UiTheme.WARNING
		);
	}

	private void drawCropDim(final GuiGraphics graphics) {
		int workspaceRight = this.cropWorkspaceX + this.cropWorkspaceWidth;
		int workspaceBottom = this.cropWorkspaceY + this.cropWorkspaceHeight;
		int frameRight = this.cropFrameX + this.cropFrameWidth;
		int frameBottom = this.cropFrameY + this.cropFrameHeight;
		int dim = 0xA8000000;
		graphics.fill(this.cropWorkspaceX, this.cropWorkspaceY, workspaceRight, this.cropFrameY, dim);
		graphics.fill(this.cropWorkspaceX, frameBottom, workspaceRight, workspaceBottom, dim);
		graphics.fill(this.cropWorkspaceX, this.cropFrameY, this.cropFrameX, frameBottom, dim);
		graphics.fill(frameRight, this.cropFrameY, workspaceRight, frameBottom, dim);
	}

	private void drawCropCorners(final GuiGraphics graphics) {
		int length = Math.max(5, Math.min(12, this.cropFrameWidth / 8));
		int thickness = 2;
		int right = this.cropFrameX + this.cropFrameWidth;
		int bottom = this.cropFrameY + this.cropFrameHeight;
		graphics.fill(this.cropFrameX, this.cropFrameY, this.cropFrameX + length, this.cropFrameY + thickness, UiTheme.TEXT);
		graphics.fill(this.cropFrameX, this.cropFrameY, this.cropFrameX + thickness, this.cropFrameY + length, UiTheme.TEXT);
		graphics.fill(right - length, this.cropFrameY, right, this.cropFrameY + thickness, UiTheme.TEXT);
		graphics.fill(right - thickness, this.cropFrameY, right, this.cropFrameY + length, UiTheme.TEXT);
		graphics.fill(this.cropFrameX, bottom - thickness, this.cropFrameX + length, bottom, UiTheme.TEXT);
		graphics.fill(this.cropFrameX, bottom - length, this.cropFrameX + thickness, bottom, UiTheme.TEXT);
		graphics.fill(right - length, bottom - thickness, right, bottom, UiTheme.TEXT);
		graphics.fill(right - thickness, bottom - length, right, bottom, UiTheme.TEXT);
	}

	private void drawCustomizationLayerCard(
		final GuiGraphics graphics,
		final int x,
		final int width,
		final int y,
		final Component title,
		final int color,
		final int opacity
	) {
		graphics.fill(x + 6, y, x + width - 6, y + 1, UiTheme.BORDER_SOFT);
		graphics.drawString(this.font, title, x + 8, y + 8, UiTheme.TEXT);
		int swatchX = x + width - 27;
		graphics.fill(swatchX, y + 6, swatchX + 19, y + 21, 0xFF303030);
		graphics.fill(swatchX + 2, y + 8, swatchX + 17, y + 19, UiRender.withAlpha(color, opacity));
	}

	private void drawBackgroundCard(
		final GuiGraphics graphics,
		final int x,
		final int width,
		final int y
	) {
		graphics.fill(x + 6, y, x + width - 6, y + 1, UiTheme.BORDER_SOFT);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.custom_background"),
			x + 8,
			y + 8,
			UiTheme.TEXT
		);
		Component status = this.backgroundStatus.getString().isBlank()
			? this.currentBackgroundStatus()
			: this.backgroundStatus;
		graphics.drawWordWrap(this.font, status, x + 8, y + 76, Math.max(70, width - 16), UiTheme.TEXT_MUTED);
		graphics.drawWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.background.limits"),
			x + 8,
			y + 99,
			Math.max(70, width - 16),
			UiTheme.TEXT_DISABLED
		);
	}

	private void drawBackdropCard(
		final GuiGraphics graphics,
		final int x,
		final int width,
		final int y
	) {
		graphics.fill(x + 6, y, x + width - 6, y + 1, UiTheme.BORDER_SOFT);
		graphics.drawString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.backdrop.title"),
			x + 8,
			y + 7,
			UiTheme.TEXT
		);
		List<FormattedCharSequence> description = this.font.split(
			Component.translatable("screen.kohs_inventory_tweaks.backdrop.description"),
			Math.max(70, width - 16)
		);
		for (int line = 0; line < Math.min(2, description.size()); line++) {
			graphics.drawString(this.font, description.get(line), x + 8, y + 20 + line * 10, UiTheme.TEXT_MUTED);
		}
	}

	private void drawBackdropPreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height
	) {
		int opacity = Mth.clamp(this.working.inventoryBackdropOpacity, 0, 255);
		int topAlpha = Math.min(255, opacity * 192 / 208);
		graphics.fillGradient(
			x,
			y,
			x + width,
			y + height,
			topAlpha << 24 | 0x101010,
			opacity << 24 | 0x101010
		);
	}

	private void drawCustomizationRail(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final Component title
	) {
		UiRender.panel(graphics, x, y, width, height, 8, UiTheme.PREVIEW_GLASS, UiTheme.BORDER_SOFT);
		graphics.drawCenteredString(this.font, title, x + width / 2, y + 7, UiTheme.TEXT_MUTED);
		graphics.fill(x + 7, y + 18, x + width - 7, y + 19, UiTheme.ACCENT_SOFT);
	}

	private void drawPixelScrollbar(
		final GuiGraphics graphics,
		final int x,
		final int top,
		final int bottom,
		final int scroll,
		final int maximum,
		final int contentHeight
	) {
		if (maximum <= 0 || bottom <= top) {
			return;
		}
		int height = bottom - top;
		int thumbHeight = Math.max(14, height * height / Math.max(height, contentHeight));
		int thumbY = top + scroll * Math.max(1, height - thumbHeight) / maximum;
		graphics.fill(x, top, x + 2, bottom, UiTheme.SCROLL_TRACK);
		graphics.fill(x - 1, thumbY, x + 3, thumbY + thumbHeight, UiTheme.ACCENT_SOFT);
	}

	private void drawWarning(final GuiGraphics graphics) {
		UiRender.panel(graphics, this.warningX, this.warningY, this.warningWidth, this.warningHeight, 10, UiTheme.GLASS, UiTheme.WARNING);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.unsaved.title"),
			this.warningX + this.warningWidth / 2,
			this.warningY + 14,
			UiTheme.WARNING
		);
		graphics.drawWordWrap(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.unsaved.description"),
			this.warningX + 14,
			this.warningY + 34,
			this.warningWidth - 28,
			UiTheme.TEXT
		);
	}

	private void drawPlayerInventory(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final int mouseX,
		final int mouseY,
		final boolean followMouse,
		final InventoryTweaksConfig visualConfig
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			InventoryTextureManager.textureFor(visualConfig),
			0,
			0,
			0.0F,
			0.0F,
			INVENTORY_WIDTH,
			INVENTORY_HEIGHT,
			256,
			256
		);
		if (this.minecraft.player != null) {
			int entityX0 = x + Math.round(26 * scale);
			int entityY0 = y + Math.round(8 * scale);
			int entityX1 = x + Math.round(75 * scale);
			int entityY1 = y + Math.round(78 * scale);
			float entityMouseX = followMouse ? mouseX : (entityX0 + entityX1) * 0.5F;
			float entityMouseY = followMouse ? mouseY : (entityY0 + entityY1) * 0.5F;
			graphics.pose().pushMatrix();
			graphics.pose().scale(1.0F / scale, 1.0F / scale);
			graphics.pose().translate(-x, -y);
			InventoryScreen.renderEntityInInventoryFollowsMouse(
				graphics,
				entityX0,
				entityY0,
				entityX1,
				entityY1,
				Math.max(1, Math.round(30 * scale)),
				0.0625F,
				entityMouseX,
				entityMouseY,
				this.minecraft.player
			);
			graphics.pose().popMatrix();
			String hoveredDynamicItem = null;
			if (followMouse && scale > 0.0F) {
				double localMouseX = (mouseX - x) / scale;
				double localMouseY = (mouseY - y) / scale;
				for (Slot slot : this.minecraft.player.inventoryMenu.slots) {
					if (slot.isActive()
						&& localMouseX >= slot.x
						&& localMouseX < slot.x + 16
						&& localMouseY >= slot.y
						&& localMouseY < slot.y + 16) {
						InventoryTweaksConfig.ItemHighlight hoveredHighlight = visualConfig.findItemHighlight(
							BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()
						);
						if (hoveredHighlight != null && hoveredHighlight.dynamicHighlight) {
							hoveredDynamicItem = hoveredHighlight.itemId;
						}
						break;
					}
				}
			}
			for (Slot slot : this.minecraft.player.inventoryMenu.slots) {
				if (slot.isActive() && !slot.getItem().isEmpty()) {
					InventoryTweaksConfig.ItemHighlight highlight = visualConfig.findItemHighlight(
						BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()
					);
					boolean renderHighlight = highlight != null
						&& (!highlight.dynamicHighlight || highlight.itemId.equals(hoveredDynamicItem));
					if (renderHighlight) {
						ItemHighlighterController.drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, false);
					}
					graphics.renderItem(slot.getItem(), slot.x, slot.y, slot.x + slot.y * INVENTORY_WIDTH);
					graphics.renderItemDecorations(this.font, slot.getItem(), slot.x, slot.y);
					if (renderHighlight) {
						ItemHighlighterController.drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, true);
					}
				}
			}
		}
		graphics.pose().popMatrix();
	}

	private void drawContainerPreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final CursorTarget target,
		final InventoryTweaksConfig visualConfig
	) {
		if (target == CursorTarget.SHULKER) {
			this.drawSurfacePreview(
				graphics,
				x,
				y,
				scale,
				CustomizationPreview.SHULKER_BOX,
				visualConfig
			);
			return;
		}
		this.drawGenericContainerPreview(
			graphics,
			x,
			y,
			scale,
			target.containerRows(),
			target.translationKey(),
			visualConfig
		);
	}

	private void drawContainerPreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final CustomizationPreview target,
		final InventoryTweaksConfig visualConfig
	) {
		this.drawGenericContainerPreview(
			graphics,
			x,
			y,
			scale,
			target.containerRows(),
			target.translationKey(),
			visualConfig
		);
	}

	private void drawGenericContainerPreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final int rows,
		final String translationKey,
		final InventoryTweaksConfig visualConfig
	) {
		int topHeight = rows * 18 + 17;
		int imageHeight = 114 + rows * 18;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		ResourceLocation texture = InventoryTextureManager.containerTextureFor(visualConfig, CONTAINER_TEXTURE, imageHeight);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 176, topHeight, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, topHeight, 0.0F, 126.0F, 176, 96, 256, 256);
		graphics.drawString(this.font, Component.translatable(translationKey), 8, 6, 0xFF404040, false);
		graphics.drawString(this.font, Component.translatable("container.inventory"), 8, imageHeight - 94, 0xFF404040, false);
		graphics.pose().popMatrix();
	}

	private void drawSurfacePreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final CustomizationPreview target,
		final InventoryTweaksConfig visualConfig
	) {
		ResourceLocation texture = InventoryTextureManager.previewTextureFor(
			visualConfig,
			target.texture(),
			target.previewWidth(),
			target.previewHeight(),
			target.textureWidth(),
			target.textureHeight(),
			target.slots()
		);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			texture,
			0,
			0,
			0.0F,
			0.0F,
			target.previewWidth(),
			target.previewHeight(),
			target.textureWidth(),
			target.textureHeight()
		);
		graphics.pose().popMatrix();
	}

	private void drawBundlePreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final InventoryTweaksConfig visualConfig
	) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.fill(0, 0, 104, 88, previewTint(0xFF2B183C, visualConfig.frameColor, visualConfig.frameOpacity));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 4; column++) {
				int slotX = 5 + column * 25;
				int slotY = 7 + row * 25;
				graphics.fill(
					slotX,
					slotY,
					slotX + 23,
					slotY + 23,
					previewTint(0xFF6B477F, visualConfig.frameColor, visualConfig.frameOpacity)
				);
				graphics.fill(
					slotX + 3,
					slotY + 3,
					slotX + 20,
					slotY + 20,
					previewTint(0xFF17101F, visualConfig.slotColor, visualConfig.slotOpacity)
				);
			}
		}
		graphics.pose().popMatrix();
	}

	private static int previewTint(final int base, final int tint, final int opacity) {
		int alpha = (base >>> 24) * Mth.clamp(opacity, 0, 255) / 255;
		int red = (base >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
		int green = (base >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
		int blue = (base & 0xFF) * (tint & 0xFF) / 255;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private void calculateMainLayout() {
		this.compactMain = this.width < 560 || this.height < 300;
		int margin = this.width < 360 ? 4 : 8;
		int gap = this.compactMain ? 5 : 12;
		int titleSpace = this.height >= 230 ? 38 : 27;
		int footerSpace = this.height >= 120 ? 34 : 24;
		int absoluteMaxRail = Math.max(1, (this.width - margin * 2 - gap * 2 - 48) / 2);
		int minimumRail = Math.min(this.compactMain ? 68 : 108, absoluteMaxRail);
		int maximumRail = Math.max(minimumRail, Math.min(this.compactMain ? 126 : 184, absoluteMaxRail));
		int preferredPreviewWidth = this.compactMain
			? Mth.clamp((int) (this.width * 0.40F), 92, 150)
			: INVENTORY_WIDTH;
		int preferredRail = (this.width - margin * 2 - gap * 2 - preferredPreviewWidth) / 2;
		int railWidth = Mth.clamp(preferredRail, minimumRail, maximumRail);

		this.mainLeftRailX = margin;
		this.mainLeftRailY = titleSpace;
		this.mainLeftRailWidth = railWidth;
		this.mainLeftRailHeight = Math.max(28, this.height - titleSpace - footerSpace);
		this.mainRightRailWidth = railWidth;
		this.mainRightRailHeight = this.mainLeftRailHeight;
		this.mainRightRailX = this.width - margin - railWidth;
		this.mainRightRailY = titleSpace;

		int centerLeft = this.mainLeftRailX + railWidth + gap;
		int centerRight = this.mainRightRailX - gap;
		int centerWidth = Math.max(24, centerRight - centerLeft);
		this.textureSelectorWidth = railWidth;
		this.textureSelectorX = this.mainLeftRailX;
		this.textureSelectorY = Math.max(titleSpace + 24, this.height - margin - 19);

		int previewTop = titleSpace + 4;
		this.mainKeybindY = previewTop;
		this.mainKeybindWidth = Math.max(20, Math.min(190, centerWidth - 10));
		this.mainKeybindX = centerLeft + (centerWidth - this.mainKeybindWidth) / 2;
		previewTop = this.mainKeybindY + 25;
		int previewBottom = this.height - margin - 3;
		int availableHeight = Math.max(24, previewBottom - previewTop);
		int availableWidth = Math.max(24, centerWidth - 10);
		this.mainPreviewScale = Math.min(1.0F, Math.min(
			availableHeight / (float) INVENTORY_HEIGHT,
			availableWidth / (float) INVENTORY_WIDTH
		));
		this.mainPreviewScale = Math.max(0.18F, this.mainPreviewScale);
		this.mainPreviewWidth = Math.round(INVENTORY_WIDTH * this.mainPreviewScale);
		this.mainPreviewHeight = Math.round(INVENTORY_HEIGHT * this.mainPreviewScale);
		this.mainPreviewX = centerLeft + (centerWidth - this.mainPreviewWidth) / 2;
		this.mainPreviewY = previewTop + Math.max(0, (availableHeight - this.mainPreviewHeight) / 2);

		this.mainCardWidth = Math.max(1, railWidth - 10);
		this.mainCardHeight = this.compactMain ? 23 : 36;
		int cardGap = this.compactMain ? 5 : 7;
		int leftRailContentHeight = this.mainCardHeight * 3 + cardGap * 2;
		int rightRailContentHeight = this.mainCardHeight * 3 + cardGap * 2;
		int railVisibleHeight = Math.max(1, this.mainLeftRailHeight - 27);
		this.mainLeftMaxScroll = Math.max(0, leftRailContentHeight - railVisibleHeight);
		this.mainRightMaxScroll = Math.max(0, rightRailContentHeight - railVisibleHeight);
		this.mainLeftSmoothScroll.setMaximum(this.mainLeftMaxScroll);
		this.mainRightSmoothScroll.setMaximum(this.mainRightMaxScroll);
		this.mainLeftScroll = this.mainLeftSmoothScroll.roundedPosition();
		this.mainRightScroll = this.mainRightSmoothScroll.roundedPosition();
		int railContentTop = this.mainLeftRailY + 22
			+ Math.max(0, (railVisibleHeight - Math.max(leftRailContentHeight, rightRailContentHeight)) / 2);
		this.cursorCardX = this.mainLeftRailX + 5;
		this.cursorCardY = railContentTop - this.mainLeftScroll;
		this.tweakCardX = this.cursorCardX;
		this.tweakCardY = this.cursorCardY + this.mainCardHeight + cardGap;
		this.issuesCardX = this.cursorCardX;
		this.issuesCardY = this.tweakCardY + this.mainCardHeight + cardGap;
		this.customizationCardX = this.mainRightRailX + 5;
		this.customizationCardY = railContentTop - this.mainRightScroll;
		this.itemHighlighterCardX = this.customizationCardX;
		this.itemHighlighterCardY = this.customizationCardY + this.mainCardHeight + cardGap;
		this.guiScalerCardX = this.customizationCardX;
		this.guiScalerCardY = this.itemHighlighterCardY + this.mainCardHeight + cardGap;
	}

	private boolean isInsideMainRail(final double x, final double y, final boolean left) {
		int railX = left ? this.mainLeftRailX : this.mainRightRailX;
		int railY = left ? this.mainLeftRailY : this.mainRightRailY;
		int railWidth = left ? this.mainLeftRailWidth : this.mainRightRailWidth;
		int railHeight = left ? this.mainLeftRailHeight : this.mainRightRailHeight;
		return x >= railX && x < railX + railWidth && y >= railY && y < railY + railHeight;
	}

	private void calculateModalLayout() {
		int margin = this.width < 420 || this.height < 260 ? 6 : 12;
		int maxWidth = Math.max(1, this.width - margin * 2);
		int maxHeight = Math.max(1, this.height - margin * 2);
		this.panelWidth = Math.min(720, maxWidth);
		this.panelHeight = Math.min(430, maxHeight);
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.compactModal = this.panelWidth < 500 || this.panelHeight < 300;
		this.panelPadding = this.compactModal ? 8 : 14;
		this.headerHeight = this.compactModal ? 30 : 38;
		this.footerHeight = this.compactModal ? 34 : 40;
		this.contentTop = this.panelY + this.headerHeight;
		this.contentBottom = this.panelY + this.panelHeight - this.footerHeight;

		int contentHeight = Math.max(60, this.contentBottom - this.contentTop);
		this.sidebarX = this.panelX + this.panelPadding;
		this.sidebarWidth = Mth.clamp(this.panelWidth / 4, 76, 146);
		this.targetTabGap = this.compactModal ? 2 : 5;
		int chestArea = 22;
		this.targetTabHeight = Mth.clamp((contentHeight - chestArea - this.targetTabGap * 4) / 5, 16, 27);
		this.targetTabY = this.contentTop;
		this.chestSelectorY = this.targetTabY + 5 * this.targetTabHeight + 4 * this.targetTabGap + 3;

		int previewAreaX = this.sidebarX + this.sidebarWidth + (this.compactModal ? 8 : 18);
		int previewAreaRight = this.panelX + this.panelWidth - this.panelPadding;
		int previewAreaWidth = Math.max(60, previewAreaRight - previewAreaX);
		int nativeWidth = this.selectedTarget.previewWidth();
		int nativeHeight = this.selectedTarget.previewHeight();
		int toggleSpace = this.selectedTarget == CursorTarget.INVENTORY ? 0 : (this.compactModal ? 22 : 25);
		int coordinateSpace = this.compactModal ? 0 : 17;
		this.previewScale = Math.min(1.0F, Math.min(
			(previewAreaWidth - 10) / (float) nativeWidth,
			(contentHeight - toggleSpace - coordinateSpace - 10) / (float) nativeHeight
		));
		this.previewScale = Math.max(0.28F, this.previewScale);
		this.previewWidth = Math.round(nativeWidth * this.previewScale);
		this.previewHeight = Math.round(nativeHeight * this.previewScale);
		this.previewX = previewAreaX + (previewAreaWidth - this.previewWidth) / 2;
		this.previewY = this.contentTop + toggleSpace
			+ Math.max(0, (contentHeight - toggleSpace - coordinateSpace - this.previewHeight) / 2);
		this.cursorToggleWidth = Math.min(132, Math.max(88, previewAreaWidth));
		this.cursorToggleHeight = this.compactModal ? 19 : 21;
		this.cursorToggleX = previewAreaX + (previewAreaWidth - this.cursorToggleWidth) / 2;
		this.cursorToggleY = this.contentTop;
		this.calculateTweaksLayout();
		this.calculateCustomizationLayout();
		this.calculateCropLayout();
		this.calculateGuiScalerLayout();

		this.warningWidth = Math.min(420, Math.max(1, this.panelWidth - 36));
		this.warningHeight = Math.min(this.compactModal ? 124 : 150, Math.max(1, this.panelHeight - 24));
		this.warningX = (this.width - this.warningWidth) / 2;
		this.warningY = (this.height - this.warningHeight) / 2;
	}

	private void calculateTweaksLayout() {
		int contentWidth = Math.max(1, this.panelWidth - this.panelPadding * 2);
		int contentHeight = Math.max(1, this.contentBottom - this.contentTop);
		this.tweakOptionsWidth = Math.min(contentWidth, 620);
		this.tweakOptionsX = this.panelX + (this.panelWidth - this.tweakOptionsWidth) / 2;

		this.tweakCardGap = this.compactModal ? 4 : 8;
		int maximumCardHeight = Math.max(24, (contentHeight - this.tweakCardGap * 2) / 3);
		int minimumCardHeight = Math.min(this.compactModal ? 42 : 48, maximumCardHeight);
		this.tweakCardHeight = Mth.clamp(maximumCardHeight, minimumCardHeight, 68);
		int cardsHeight = this.tweakCardHeight * 3 + this.tweakCardGap * 2;
		this.tweakFirstCardY = this.contentTop + Math.max(0, (contentHeight - cardsHeight) / 2);
	}

	private void calculateGuiScalerLayout() {
		int contentWidth = Math.max(1, this.panelWidth - this.panelPadding * 2);
		this.guiScalerToggleWidth = Math.min(220, Math.max(116, contentWidth / 2));
		this.guiScalerToggleX = this.panelX + (this.panelWidth - this.guiScalerToggleWidth) / 2;
		this.guiScalerToggleY = this.contentTop;

		this.guiScalerActualScale = this.working.inventoryGuiScalerEnabled
			? Math.min(
				InventoryGuiScaler.clampConfiguredScale(this.working.inventoryGuiScale),
				InventoryGuiScaler.maximumScaleFor(this.width, this.height)
			)
			: 1.0;
		this.guiScalerSliderWidth = Math.min(390, Math.max(80, contentWidth - 32));
		this.guiScalerSliderX = this.panelX + (this.panelWidth - this.guiScalerSliderWidth) / 2;
		this.guiScalerSliderY = Math.max(this.guiScalerToggleY + 50, this.contentBottom - 22);
		int previewTop = this.guiScalerToggleY + (this.compactModal ? 29 : 50);
		int previewBottom = this.guiScalerSliderY - 17;
		int previewAreaX = this.panelX + this.panelPadding;
		int availableWidth = contentWidth;
		availableWidth = Math.max(20, availableWidth);
		int availableHeight = Math.max(20, previewBottom - previewTop);
		float fittedScale = Math.min(
			(float) this.guiScalerActualScale,
			Math.min(availableWidth / (float) INVENTORY_WIDTH, availableHeight / (float) INVENTORY_HEIGHT)
		);
		this.guiScalerPreviewScale = Math.max(0.12F, fittedScale);
		this.guiScalerPreviewFitted = this.guiScalerPreviewScale + 0.001F < this.guiScalerActualScale;
		this.guiScalerPreviewWidth = Math.round(INVENTORY_WIDTH * this.guiScalerPreviewScale);
		this.guiScalerPreviewHeight = Math.round(INVENTORY_HEIGHT * this.guiScalerPreviewScale);
		this.guiScalerPreviewX = previewAreaX + (availableWidth - this.guiScalerPreviewWidth) / 2;
		this.guiScalerPreviewY = previewTop + Math.max(0, (availableHeight - this.guiScalerPreviewHeight) / 2);
	}

	private void calculateCustomizationLayout() {
		int contentWidth = Math.max(1, this.panelWidth - this.panelPadding * 2);
		int contentHeight = Math.max(1, this.contentBottom - this.contentTop);
		int gap = this.compactModal ? 7 : 14;
		int minimumOptionsWidth = Math.min(this.compactModal ? 120 : 170, Math.max(48, contentWidth / 2));
		int maximumPreviewArea = Math.max(1, contentWidth - gap - minimumOptionsWidth);
		int preferredPreviewArea = (int) (contentWidth * 0.45F);
		int previewAreaWidth = Math.min(maximumPreviewArea, Math.max(Math.min(90, maximumPreviewArea), preferredPreviewArea));
		int previewAreaX = this.panelX + this.panelPadding;

		this.customizationOptionsX = previewAreaX + previewAreaWidth + gap;
		this.customizationOptionsY = this.contentTop;
		this.customizationOptionsWidth = Math.max(1, contentWidth - previewAreaWidth - gap);
		this.customizationOptionsHeight = contentHeight;

		this.customizationSelectorWidth = Math.min(196, Math.max(1, previewAreaWidth - 4));
		this.customizationSelectorX = previewAreaX + (previewAreaWidth - this.customizationSelectorWidth) / 2;
		this.customizationSelectorY = this.contentTop;

		int nativeWidth = this.customizationPreviewTarget.previewWidth();
		int nativeHeight = this.customizationPreviewTarget.previewHeight();
		int previewTop = this.contentTop + 27;
		int previewAreaHeight = Math.max(18, contentHeight - 42);
		this.customizationPreviewVisible = previewAreaWidth >= 48 && previewAreaHeight >= 28;
		if (this.customizationPreviewVisible) {
			this.customizationPreviewScale = Math.min(1.0F, Math.min(
				Math.max(1, previewAreaWidth - 10) / (float) nativeWidth,
				previewAreaHeight / (float) nativeHeight
			));
			this.customizationPreviewScale = Math.max(0.10F, this.customizationPreviewScale);
			this.customizationPreviewWidth = Math.round(nativeWidth * this.customizationPreviewScale);
			this.customizationPreviewHeight = Math.round(nativeHeight * this.customizationPreviewScale);
			this.customizationPreviewX = previewAreaX + (previewAreaWidth - this.customizationPreviewWidth) / 2;
			this.customizationPreviewY = previewTop
				+ Math.max(0, (previewAreaHeight - this.customizationPreviewHeight) / 2);
		} else {
			this.customizationPreviewX = 0;
			this.customizationPreviewY = 0;
			this.customizationPreviewWidth = 0;
			this.customizationPreviewHeight = 0;
			this.customizationPreviewScale = 0.0F;
		}

		int visibleOptionsContent = Math.max(1, contentHeight - 25);
		this.customizationMaxScroll = Math.max(0, CUSTOM_CONTENT_HEIGHT - visibleOptionsContent + 4);
		this.customizationSmoothScroll.setMaximum(this.customizationMaxScroll);
		this.customizationScroll = this.customizationSmoothScroll.roundedPosition();
	}

	private void calculateCropLayout() {
		int availableWidth = Math.max(1, this.panelWidth - this.panelPadding * 2);
		this.cropControlsY = Math.max(this.contentTop, this.contentBottom - 20);
		this.cropWorkspaceX = this.panelX + this.panelPadding;
		this.cropWorkspaceY = this.contentTop;
		this.cropWorkspaceWidth = availableWidth;
		this.cropWorkspaceHeight = Math.max(1, this.cropControlsY - this.cropWorkspaceY - 16);

		int frameAvailableWidth = Math.max(1, this.cropWorkspaceWidth - (this.compactModal ? 12 : 28));
		int frameAvailableHeight = Math.max(1, this.cropWorkspaceHeight - (this.compactModal ? 8 : 18));
		double scale = Math.min(
			frameAvailableWidth / (double) INVENTORY_WIDTH,
			frameAvailableHeight / (double) INVENTORY_HEIGHT
		);
		this.cropFrameWidth = Math.max(1, (int) Math.floor(INVENTORY_WIDTH * scale));
		this.cropFrameHeight = Math.max(1, (int) Math.floor(INVENTORY_HEIGHT * scale));
		this.cropFrameX = this.cropWorkspaceX + (this.cropWorkspaceWidth - this.cropFrameWidth) / 2;
		this.cropFrameY = this.cropWorkspaceY + (this.cropWorkspaceHeight - this.cropFrameHeight) / 2;
		this.clampCropFocus();
	}

	private void ensureParticles() {
		if (!this.particles.isEmpty()) {
			return;
		}
		Random random = new Random(0x4B4F4853L);
		int count = Mth.clamp(this.width * this.height / 7600, 24, 42);
		for (int i = 0; i < count; i++) {
			this.particles.add(new FloatingParticle(
				random.nextFloat() * Math.max(1, this.width),
				random.nextFloat() * Math.max(1, this.height),
				0.08F + random.nextFloat() * 0.18F,
				0.008F + random.nextFloat() * 0.025F,
				1 + random.nextInt(2),
				72 + random.nextInt(112),
				random.nextFloat() * 6.28318F
			));
		}
	}

	private float entranceProgress() {
		float linear = Mth.clamp(
			(System.nanoTime() - this.entranceStartedAtNanos) / (float) ENTRANCE_DURATION_NANOS,
			0.0F,
			1.0F
		);
		float remaining = 1.0F - linear;
		return 1.0F - remaining * remaining * remaining;
	}

	private float modalEntranceProgress() {
		float linear = Mth.clamp(
			(System.nanoTime() - this.modalOpenedAtNanos) / (float) MODAL_ENTRANCE_DURATION_NANOS,
			0.0F,
			1.0F
		);
		float remaining = 1.0F - linear;
		return 1.0F - remaining * remaining * remaining;
	}

	private void updateSmoothWidgetPositions() {
		if (this.modal == Modal.NONE) {
			this.mainLeftSmoothScroll.update();
			this.mainRightSmoothScroll.update();
			int nextLeft = this.mainLeftSmoothScroll.roundedPosition();
			int nextRight = this.mainRightSmoothScroll.roundedPosition();
			moveWidgets(
				this.mainLeftScrollingWidgets,
				this.mainLeftScroll - nextLeft,
				this.mainLeftRailY + 22,
				this.mainLeftRailY + this.mainLeftRailHeight - 5
			);
			moveWidgets(
				this.mainRightScrollingWidgets,
				this.mainRightScroll - nextRight,
				this.mainRightRailY + 22,
				this.mainRightRailY + this.mainRightRailHeight - 5
			);
			this.mainLeftScroll = nextLeft;
			this.mainRightScroll = nextRight;
		} else if (this.modal == Modal.CUSTOMIZATION) {
			this.customizationSmoothScroll.update();
			int next = this.customizationSmoothScroll.roundedPosition();
			moveWidgets(
				this.customizationScrollingWidgets,
				this.customizationScroll - next,
				this.customizationOptionsY + 21,
				this.customizationOptionsY + this.customizationOptionsHeight
			);
			this.customizationScroll = next;
		}
	}

	private static void moveWidgets(
		final List<AbstractWidget> widgets,
		final int deltaY,
		final int visibleTop,
		final int visibleBottom
	) {
		for (AbstractWidget widget : widgets) {
			if (deltaY != 0) {
				widget.setY(widget.getY() + deltaY);
			}
			widget.visible = widget.getBottom() > visibleTop && widget.getY() < visibleBottom;
		}
	}

	private void openModal(final Modal nextModal) {
		if (nextModal == Modal.CURSOR
			&& !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CURSOR_LANDING)) {
			return;
		}
		this.working = ConfigStore.get().copy();
		if (nextModal == Modal.CUSTOMIZATION) {
			this.customizationSmoothScroll.snapTo(0.0);
			this.customizationScroll = 0;
			this.backgroundStatus = Component.empty();
			InventoryTextureManager.invalidateConfiguration();
		}
		this.modal = nextModal;
		this.rebuildWidgets();
	}

	private void openCustomizationWarning() {
		this.working = ConfigStore.get().copy();
		this.modal = Modal.CUSTOMIZATION_WARNING;
		this.rebuildWidgets();
	}

	private void openGuiScaler() {
		this.working = ConfigStore.get().copy();
		if (this.working.guiScalerWarningDismissed) {
			this.openGuiScalerCalibration();
			return;
		}
		this.modal = Modal.GUI_SCALER_WARNING;
		this.rebuildWidgets();
	}

	private void openGuiScalerCalibration() {
		this.modal = Modal.NONE;
		this.minecraft.setScreen(new GuiScalerScreen(this));
	}

	void reloadConfigurationFromStore() {
		this.working = ConfigStore.get().copy();
		this.modal = Modal.NONE;
	}

	private void selectTarget(final CursorTarget target) {
		this.selectedTarget = target;
		this.calculateModalLayout();
		this.rebuildWidgets();
	}

	private void saveAndCloseModal() {
		this.persistWorking();
		this.modal = Modal.NONE;
		this.rebuildWidgets();
	}

	private void persistWorking() {
		ConfigStore.replaceAndSave(this.working);
		this.working = ConfigStore.get().copy();
		InventoryTextureManager.invalidateConfiguration();
	}

	private void attemptCloseModal() {
		if (this.working.sameValues(ConfigStore.get())) {
			this.modal = Modal.NONE;
		} else {
			this.warningReturnModal = this.modal;
			this.modal = Modal.WARNING;
		}
		this.rebuildWidgets();
	}

	private boolean isChestTarget() {
		return this.selectedTarget == CursorTarget.CHEST_SINGLE || this.selectedTarget == CursorTarget.CHEST_DOUBLE;
	}

	private boolean isInsidePreview(final double x, final double y) {
		return x >= this.previewX && x < this.previewX + this.previewWidth && y >= this.previewY && y < this.previewY + this.previewHeight;
	}

	private void updateSelectedPosition(final double mouseX, final double mouseY) {
		double x = (mouseX - this.previewX) / Math.max(1.0, this.previewWidth - 1.0);
		double y = (mouseY - this.previewY) / Math.max(1.0, this.previewHeight - 1.0);
		this.working.setPosition(this.selectedTarget, new CursorPoint(x, y));
	}

	private boolean isSelectedCursorEnabled() {
		return this.working.isCursorEnabled(this.selectedTarget);
	}

	private Component cursorToggleLabel() {
		return Component.translatable(
			"screen.kohs_inventory_tweaks.cursor_toggle",
			Component.translatable(this.isSelectedCursorEnabled()
				? "screen.kohs_inventory_tweaks.enabled"
				: "screen.kohs_inventory_tweaks.disabled")
		);
	}

	private Component centerFixLabel() {
		return Component.translatable(this.working.centerMouseFix
			? "screen.kohs_inventory_tweaks.enabled"
			: "screen.kohs_inventory_tweaks.disabled");
	}

	private Component superFastInventoryLabel() {
		return Component.translatable(this.working.superFastInventory
			? "screen.kohs_inventory_tweaks.enabled"
			: "screen.kohs_inventory_tweaks.disabled");
	}

	private Component removeAnimationsLabel() {
		return Component.translatable(this.working.removeAllInventoryAnimations
			? "screen.kohs_inventory_tweaks.enabled"
			: "screen.kohs_inventory_tweaks.disabled");
	}

	private Component guiScalerToggleLabel() {
		return Component.translatable(this.working.inventoryGuiScalerEnabled
			? "screen.kohs_inventory_tweaks.gui_scaler.switch.on"
			: "screen.kohs_inventory_tweaks.gui_scaler.switch.off");
	}

	private Component menuKeybindLabel() {
		if (this.awaitingMenuKey) {
			return Component.translatable("screen.kohs_inventory_tweaks.keybind.prompt");
		}
		return Component.translatable(
			"screen.kohs_inventory_tweaks.keybind",
			ConfigMenuKeyBinding.mapping().getTranslatedKeyMessage()
		);
	}

	private boolean isInsideCustomizationOptions(final double x, final double y) {
		return x >= this.customizationOptionsX
			&& x < this.customizationOptionsX + this.customizationOptionsWidth
			&& y >= this.customizationOptionsY + 21
			&& y < this.customizationOptionsY + this.customizationOptionsHeight;
	}

	private Component customizationPreviewLabel() {
		return Component.translatable(
			"screen.kohs_inventory_tweaks.customization.preview",
			Component.translatable(this.customizationPreviewTarget.translationKey())
		);
	}

	private void cycleCustomizationPreview(final int direction) {
		for (int i = 0; i < CUSTOMIZATION_PREVIEWS.length; i++) {
			if (CUSTOMIZATION_PREVIEWS[i] == this.customizationPreviewTarget) {
				this.customizationPreviewTarget = CUSTOMIZATION_PREVIEWS[
					Math.floorMod(i + direction, CUSTOMIZATION_PREVIEWS.length)
				];
				this.calculateCustomizationLayout();
				return;
			}
		}
		this.customizationPreviewTarget = CustomizationPreview.INVENTORY;
		this.calculateCustomizationLayout();
	}

	private void setTextureSource(final TextureSource source) {
		this.working.inventoryTextureSource = source;
		this.persistWorking();
		this.rebuildWidgets();
	}

	private void chooseBackgroundFile() {
		String selected;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(7);
			filters.put(stack.UTF8("*.png"));
			filters.put(stack.UTF8("*.jpg"));
			filters.put(stack.UTF8("*.jpeg"));
			filters.put(stack.UTF8("*.bmp"));
			filters.put(stack.UTF8("*.gif"));
			filters.put(stack.UTF8("*.mp4"));
			filters.put(stack.UTF8("*.mov"));
			filters.flip();
			selected = TinyFileDialogs.tinyfd_openFileDialog(
				Component.translatable("screen.kohs_inventory_tweaks.background.choose").getString(),
				"",
				filters,
				"Images, GIF, MP4 or MOV",
				false
			);
		}
		if (selected == null || selected.isBlank()) {
			return;
		}

		this.backgroundBusy = true;
		this.backgroundStatus = Component.translatable("screen.kohs_inventory_tweaks.crop.preparing");
		this.rebuildWidgets();
		BackgroundMediaManager.prepareAsync(Path.of(selected)).whenComplete((result, failure) -> this.minecraft.execute(() -> {
			this.backgroundBusy = false;
			if (failure != null) {
				this.backgroundStatus = Component.translatable("screen.kohs_inventory_tweaks.background.error.decode");
			} else if (result.success() && result.media() != null) {
				if (this.minecraft.screen != this || this.modal != Modal.CUSTOMIZATION) {
					return;
				}
				this.cropMedia = result.media();
				this.cropFocusX = 0.5;
				this.cropFocusY = 0.5;
				this.cropZoom = 1.0;
				this.cropStatus = Component.empty();
				this.uploadCropPreview(this.cropMedia);
				this.modal = Modal.CROP;
			} else {
				this.backgroundStatus = Component.translatable(result.messageKey());
			}
			if (this.minecraft.screen == this && (this.modal == Modal.CUSTOMIZATION || this.modal == Modal.CROP)) {
				this.rebuildWidgets();
			}
		}));
	}

	private void applyCrop() {
		if (this.cropMedia == null || this.backgroundBusy) {
			return;
		}
		PreparedMedia media = this.cropMedia;
		CropSettings crop = new CropSettings(this.cropFocusX, this.cropFocusY, this.cropZoom);
		this.backgroundBusy = true;
		this.cropStatus = Component.translatable("screen.kohs_inventory_tweaks.crop.processing");
		this.rebuildWidgets();
		BackgroundMediaManager.importAsync(media.source(), crop).whenComplete((result, failure) -> this.minecraft.execute(() -> {
			this.backgroundBusy = false;
			if (this.minecraft.screen != this) {
				return;
			}
			if (failure != null) {
				this.cropStatus = Component.translatable("screen.kohs_inventory_tweaks.background.error.decode");
			} else if (result.success()) {
				this.working.customBackgroundFile = result.fileName();
				this.backgroundStatus = Component.translatable(result.messageKey());
				this.releaseCropPreview();
				this.cropMedia = null;
				this.cropStatus = Component.empty();
				this.modal = Modal.CUSTOMIZATION;
				this.persistWorking();
			} else {
				this.cropStatus = Component.translatable(result.messageKey());
			}
			this.rebuildWidgets();
		}));
	}

	private void cancelCrop() {
		if (this.backgroundBusy) {
			return;
		}
		this.releaseCropPreview();
		this.cropMedia = null;
		this.cropStatus = Component.empty();
		this.cropDragging = false;
		this.modal = Modal.CUSTOMIZATION;
		this.rebuildWidgets();
	}

	private void uploadCropPreview(final PreparedMedia media) {
		this.releaseCropPreview();
		NativeImage image = new NativeImage(media.previewWidth(), media.previewHeight(), true);
		for (int y = 0; y < media.previewHeight(); y++) {
			for (int x = 0; x < media.previewWidth(); x++) {
				image.setPixel(x, y, media.previewPixels()[x + y * media.previewWidth()]);
			}
		}
		this.cropPreviewTexture = new DynamicTexture(() -> "KoHs inventory crop preview", image);
		this.minecraft.getTextureManager().register(CROP_PREVIEW_TEXTURE, this.cropPreviewTexture);
	}

	private void releaseCropPreview() {
		if (this.cropPreviewTexture != null) {
			this.minecraft.getTextureManager().release(CROP_PREVIEW_TEXTURE);
			this.cropPreviewTexture = null;
		}
	}

	private boolean isInsideCropFrame(final double x, final double y) {
		return x >= this.cropFrameX && x < this.cropFrameX + this.cropFrameWidth
			&& y >= this.cropFrameY && y < this.cropFrameY + this.cropFrameHeight;
	}

	private double cropDisplayScale() {
		if (this.cropMedia == null) {
			return 1.0;
		}
		return Math.max(
			this.cropFrameWidth / (double) this.cropMedia.previewWidth(),
			this.cropFrameHeight / (double) this.cropMedia.previewHeight()
		) * this.cropZoom;
	}

	private void moveCropBy(final double dx, final double dy) {
		if (this.cropMedia == null) {
			return;
		}
		double scale = this.cropDisplayScale();
		this.cropFocusX -= dx / Math.max(1.0, this.cropMedia.previewWidth() * scale);
		this.cropFocusY -= dy / Math.max(1.0, this.cropMedia.previewHeight() * scale);
		this.clampCropFocus();
	}

	private void clampCropFocus() {
		if (this.cropMedia == null) {
			this.cropFocusX = 0.5;
			this.cropFocusY = 0.5;
			return;
		}
		double scale = this.cropDisplayScale();
		double halfVisibleX = Math.min(0.5, this.cropFrameWidth / Math.max(2.0, 2.0 * this.cropMedia.previewWidth() * scale));
		double halfVisibleY = Math.min(0.5, this.cropFrameHeight / Math.max(2.0, 2.0 * this.cropMedia.previewHeight() * scale));
		this.cropFocusX = clamp(this.cropFocusX, halfVisibleX, 1.0 - halfVisibleX);
		this.cropFocusY = clamp(this.cropFocusY, halfVisibleY, 1.0 - halfVisibleY);
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private Component currentBackgroundStatus() {
		return this.working.customBackgroundFile == null
			? Component.translatable("screen.kohs_inventory_tweaks.background.none")
			: Component.translatable("screen.kohs_inventory_tweaks.background.selected", this.working.customBackgroundFile);
	}

	private static int red(final int color) {
		return color >> 16 & 0xFF;
	}

	private static int green(final int color) {
		return color >> 8 & 0xFF;
	}

	private static int blue(final int color) {
		return color & 0xFF;
	}

	private static int withChannel(final int color, final int shift, final int value) {
		int mask = 0xFF << shift;
		return color & ~mask | Mth.clamp(value, 0, 255) << shift;
	}
}
