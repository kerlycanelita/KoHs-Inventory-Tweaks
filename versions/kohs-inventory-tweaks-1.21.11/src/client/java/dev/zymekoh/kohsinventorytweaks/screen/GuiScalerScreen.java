package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/**
 * Full-screen calibration view. It deliberately has no blur, dim layer, panel or
 * particles so the inventory can be judged against the real world background.
 */
public final class GuiScalerScreen extends Screen {
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;

	private final Screen parent;
	private InventoryTweaksConfig working;
	private VerticalInventoryScaleSlider scaleSlider;
	private GlassButton enabledButton;

	public GuiScalerScreen(final Screen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.gui_scaler"));
		this.parent = parent;
		this.working = ConfigStore.get().copy();
	}

	@Override
	protected void init() {
		int outerMargin = this.width < 360 ? 5 : 9;
		int toggleWidth = Math.min(320, Math.max(132, (int) (this.width * 0.56F)));
		toggleWidth = Math.min(toggleWidth, Math.max(1, this.width - outerMargin * 2));
		int toggleX = (this.width - toggleWidth) / 2;
		this.enabledButton = this.addRenderableWidget(new GlassButton(
			toggleX,
			outerMargin,
			toggleWidth,
			22,
			this.enabledLabel(),
			button -> {
				this.working.inventoryGuiScalerEnabled = !this.working.inventoryGuiScalerEnabled;
				button.setMessage(this.enabledLabel());
				this.persistWorking();
			},
			GlassButton.Variant.SWITCH,
			() -> this.working.inventoryGuiScalerEnabled
		));

		int controlsTop = outerMargin + 30;
		int controlsBottom = Math.max(controlsTop + 24, this.height - 46);
		int availableSliderHeight = Math.max(24, controlsBottom - controlsTop);
		int sliderHeight = Math.min(280, availableSliderHeight);
		int sliderY = controlsTop + Math.max(0, (availableSliderHeight - sliderHeight) / 2);
		int sliderWidth = Math.min(28, Math.max(18, this.width / 16));
		int sliderX = Math.max(outerMargin, Math.min(this.width - sliderWidth - outerMargin, this.width < 500 ? 12 : 24));
		double maximumScale = InventoryGuiScaler.maximumScaleFor(this.width, this.height);
		this.scaleSlider = this.addRenderableWidget(new VerticalInventoryScaleSlider(
			sliderX,
			sliderY,
			sliderWidth,
			sliderHeight,
			maximumScale,
			this.working.inventoryGuiScale,
			value -> {
				this.working.inventoryGuiScale = value;
				if (!this.working.inventoryGuiScalerEnabled) {
					this.working.inventoryGuiScalerEnabled = true;
					this.enabledButton.setMessage(this.enabledLabel());
				}
			}
		));

		int containersWidth = Math.min(176, Math.max(112, this.width / 4));
		this.addRenderableWidget(new GlassButton(
			this.width - outerMargin - containersWidth,
			controlsTop,
			containersWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.gui_scaler.affect_containers"),
			button -> {
				this.persistWorking();
				this.minecraft.setScreen(new AffectContainersScreen(this));
			},
			GlassButton.Variant.NORMAL
		));

		int gap = Math.max(5, Math.min(10, this.width / 40));
		int buttonWidth = Math.min(146, Math.max(68, (this.width - outerMargin * 2 - gap) / 2));
		int actionY = Math.max(0, this.height - 27);
		this.addRenderableWidget(new GlassButton(
			outerMargin,
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
		this.addRenderableWidget(new GlassButton(
			this.width - outerMargin - buttonWidth,
			actionY,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.save_exit"),
			button -> this.saveAndExit(),
			GlassButton.Variant.PRIMARY
		));
	}

	/** Keep the rendered world completely visible behind the calibration view. */
	@Override
	public void renderBackground(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float a
	) {
	}

	@Override
	public void render(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float a
	) {
		this.drawInventoryAtRealPosition(graphics, mouseX, mouseY);

		Component scale = Component.translatable(
			"screen.kohs_inventory_tweaks.gui_scaler.slider",
			(int) Math.round(this.configuredScale() * 100.0)
		);
		graphics.drawString(
			this.font,
			scale,
			this.scaleSlider.getX() + this.scaleSlider.getWidth() + 5,
			this.scaleSlider.getY(),
			UiTheme.TEXT,
			true
		);
		graphics.drawString(this.font, "+", this.scaleSlider.getX() + 9, this.scaleSlider.getY() - 9, UiTheme.ACCENT, true);
		graphics.drawString(
			this.font,
			"−",
			this.scaleSlider.getX() + 8,
			this.scaleSlider.getY() + this.scaleSlider.getHeight() + 1,
			UiTheme.ACCENT,
			true
		);
		super.render(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		boolean handled = super.mouseReleased(event);
		if (!this.working.sameValues(ConfigStore.get())) {
			this.persistWorking();
		}
		return handled;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.isEscape()) {
			this.saveAndExit();
			return true;
		}
		boolean handled = super.keyPressed(event);
		if (handled && !this.working.sameValues(ConfigStore.get())) {
			this.persistWorking();
		}
		return handled;
	}

	private void drawInventoryAtRealPosition(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY
	) {
		float scale = (float) this.actualScale();
		float localMouseX = (mouseX - this.width * 0.5F) / scale + INVENTORY_WIDTH * 0.5F;
		float localMouseY = (mouseY - this.height * 0.5F) / scale + INVENTORY_HEIGHT * 0.5F;

		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width * 0.5F, this.height * 0.5F);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-INVENTORY_WIDTH * 0.5F, -INVENTORY_HEIGHT * 0.5F);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			InventoryTextureManager.textureFor(this.working),
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
			float inventoryLeft = this.width * 0.5F - INVENTORY_WIDTH * 0.5F * scale;
			float inventoryTop = this.height * 0.5F - INVENTORY_HEIGHT * 0.5F * scale;
			InventoryScreen.renderEntityInInventoryFollowsMouse(
				graphics,
				Math.round(inventoryLeft + 26.0F * scale),
				Math.round(inventoryTop + 8.0F * scale),
				Math.round(inventoryLeft + 75.0F * scale),
				Math.round(inventoryTop + 78.0F * scale),
				Math.max(1, Math.round(30.0F * scale)),
				0.0625F,
				mouseX,
				mouseY,
				this.minecraft.player
			);
			graphics.drawString(this.font, Component.translatable("container.crafting"), 97, 6, -12566464, false);
			this.drawInventoryItems(graphics, localMouseX, localMouseY);
		}
		graphics.pose().popMatrix();
	}

	private void drawInventoryItems(
		final GuiGraphics graphics,
		final float localMouseX,
		final float localMouseY
	) {
		String hoveredDynamicItem = null;
		for (Slot slot : this.minecraft.player.inventoryMenu.slots) {
			if (!slot.isActive()
				|| localMouseX < slot.x
				|| localMouseX >= slot.x + 16
				|| localMouseY < slot.y
				|| localMouseY >= slot.y + 16) {
				continue;
			}
			InventoryTweaksConfig.ItemHighlight highlight = this.highlightFor(slot);
			if (highlight != null && highlight.dynamicHighlight) {
				hoveredDynamicItem = highlight.itemId;
			}
			break;
		}

		for (Slot slot : this.minecraft.player.inventoryMenu.slots) {
			if (!slot.isActive() || slot.getItem().isEmpty()) {
				continue;
			}
			InventoryTweaksConfig.ItemHighlight highlight = this.highlightFor(slot);
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

	private InventoryTweaksConfig.ItemHighlight highlightFor(final Slot slot) {
		if (slot.getItem().isEmpty()) {
			return null;
		}
		return this.working.findItemHighlight(
			BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()
		);
	}

	private double actualScale() {
		return InventoryGuiScaler.appliedScale(this.width, this.height, this.working);
	}

	private double configuredScale() {
		return InventoryGuiScaler.configuredPhysicalScale(this.width, this.height, this.working);
	}

	private Component enabledLabel() {
		return Component.translatable(this.working.inventoryGuiScalerEnabled
			? "screen.kohs_inventory_tweaks.gui_scaler.switch.on"
			: "screen.kohs_inventory_tweaks.gui_scaler.switch.off");
	}

	private void persistWorking() {
		ConfigStore.replaceAndSave(this.working);
		this.working = ConfigStore.get().copy();
		InventoryTextureManager.invalidateConfiguration();
	}

	void reloadConfigurationFromStore() {
		this.working = ConfigStore.get().copy();
	}

	private void saveAndExit() {
		this.persistWorking();
		if (this.parent instanceof InventoryTweaksScreen screen) {
			screen.reloadConfigurationFromStore();
		}
		this.minecraft.setScreen(this.parent);
	}
}
