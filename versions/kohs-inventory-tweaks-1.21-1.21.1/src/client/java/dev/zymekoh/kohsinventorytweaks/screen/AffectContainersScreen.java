package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.ContainerScaleTarget;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen container scale calibration. The preview stays centered at the
 * exact configured scale while navigation and state controls live in side
 * rails, so enabling container scaling never shifts the reference surface.
 */
public final class AffectContainersScreen extends Screen {
	private static final ResourceLocation CONTAINER_TEXTURE = ResourceLocation.withDefaultNamespace(
		"textures/gui/container/generic_54.png"
	);
	private static final ContainerScaleTarget[] TARGETS = ContainerScaleTarget.values();

	private final GuiScalerScreen parent;
	private InventoryTweaksConfig working;
	private int targetIndex;
	private boolean warningVisible;

	public AffectContainersScreen(final GuiScalerScreen parent) {
		super(Component.translatable("screen.kohs_inventory_tweaks.affect_containers.title"));
		this.parent = parent;
		this.working = ConfigStore.get().copy();
	}

	@Override
	protected void init() {
		if (this.warningVisible) {
			this.addWarningButtons();
			return;
		}

		int margin = this.width < 360 ? 5 : 9;
		int top = margin + 30;
		int arrowWidth = Math.min(42, Math.max(28, this.width / 16));
		int arrowGap = 4;
		this.addRenderableWidget(new GlassButton(
			margin,
			top,
			arrowWidth,
			22,
			Component.literal("<"),
			button -> this.cycleTarget(-1),
			GlassButton.Variant.NORMAL
		));
		this.addRenderableWidget(new GlassButton(
			margin + arrowWidth + arrowGap,
			top,
			arrowWidth,
			22,
			Component.literal(">"),
			button -> this.cycleTarget(1),
			GlassButton.Variant.NORMAL
		));

		int switchWidth = Math.min(224, Math.max(122, this.width / 3));
		this.addRenderableWidget(new GlassButton(
			this.width - margin - switchWidth,
			top,
			switchWidth,
			22,
			this.switchLabel(),
			button -> {
				if (this.working.affectAllContainers) {
					this.working.affectAllContainers = false;
					this.persistWorking();
					this.rebuildWidgets();
				} else {
					this.warningVisible = true;
					this.rebuildWidgets();
				}
			},
			GlassButton.Variant.SWITCH,
			() -> this.working.affectAllContainers
		));

		int backWidth = Math.min(150, Math.max(82, this.width / 5));
		this.addRenderableWidget(new GlassButton(
			this.width - margin - backWidth,
			Math.max(0, this.height - 27),
			backWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.go_back"),
			button -> this.closeToParent(),
			GlassButton.Variant.PRIMARY
		));
	}

	private void addWarningButtons() {
		int panelWidth = Math.max(1, Math.min(430, this.width - 12));
		int panelHeight = Math.max(1, Math.min(176, this.height - 12));
		int panelX = (this.width - panelWidth) / 2;
		int panelY = (this.height - panelHeight) / 2;
		int gap = 6;
		int buttonWidth = Math.max(1, (panelWidth - 24 - gap) / 2);
		int buttonY = panelY + panelHeight - 31;
		this.addRenderableWidget(new GlassButton(
			panelX + 9,
			buttonY,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.affect_containers.warning.cancel"),
			button -> {
				this.warningVisible = false;
				this.rebuildWidgets();
			},
			GlassButton.Variant.DANGER
		));
		this.addRenderableWidget(new GlassButton(
			panelX + panelWidth - 9 - buttonWidth,
			buttonY,
			buttonWidth,
			22,
			Component.translatable("screen.kohs_inventory_tweaks.affect_containers.warning.enable"),
			button -> {
				this.working.affectAllContainers = true;
				this.warningVisible = false;
				this.persistWorking();
				this.rebuildWidgets();
			},
			GlassButton.Variant.PRIMARY
		));
	}

	/** The visible world remains the reference background for real-size calibration. */
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
		ContainerScaleTarget target = this.selectedTarget();
		double scale = this.working.affectAllContainers
			? InventoryGuiScaler.configuredContainerScale(this.width, this.height, this.working, target)
			: 1.0;
		int previewWidth = Math.max(1, (int) Math.round(target.previewWidth() * scale));
		int previewHeight = Math.max(1, (int) Math.round(target.previewHeight() * scale));
		int previewX = (this.width - previewWidth) / 2;
		int previewY = (this.height - previewHeight) / 2;

		UiRender.panel(
			graphics,
			previewX - 6,
			previewY - 6,
			previewWidth + 12,
			previewHeight + 12,
			7,
			UiTheme.PREVIEW_GLASS,
			UiTheme.BORDER_SOFT
		);
		this.drawContainerPreview(graphics, previewX, previewY, (float) scale, target);

		Component previewLabel = Component.translatable(
			"screen.kohs_inventory_tweaks.affect_containers.preview",
			Component.translatable(target.translationKey()),
			(int) Math.round(scale * 100.0)
		);
		graphics.drawCenteredString(
			this.font,
			previewLabel,
			this.width / 2,
			Math.max(3, previewY - 18),
			UiTheme.TEXT
		);
		graphics.drawCenteredString(
			this.font,
			this.title,
			this.width / 2,
			9,
			UiTheme.TEXT
		);

		if (this.warningVisible) {
			this.drawWarning(graphics);
		}
		super.render(graphics, mouseX, mouseY, a);
	}

	private void drawContainerPreview(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final float scale,
		final ContainerScaleTarget target
	) {
		if (target == ContainerScaleTarget.SHULKER) {
			CustomizationPreview surface = CustomizationPreview.SHULKER_BOX;
			ResourceLocation texture = InventoryTextureManager.previewTextureFor(
				this.working,
				surface.texture(),
				surface.previewWidth(),
				surface.previewHeight(),
				surface.textureWidth(),
				surface.textureHeight(),
				surface.slots()
			);
			graphics.pose().pushPose();
			graphics.pose().translate(x, y, 0.0F);
			graphics.pose().scale(scale, scale, 1.0F);
			graphics.blit(
				texture,
				0,
				0,
				0.0F,
				0.0F,
				surface.previewWidth(),
				surface.previewHeight(),
				surface.textureWidth(),
				surface.textureHeight()
			);
			graphics.pose().popPose();
			return;
		}

		int rows = target.rows();
		int topHeight = rows * 18 + 17;
		int imageHeight = 114 + rows * 18;
		ResourceLocation texture = InventoryTextureManager.containerTextureFor(
			this.working,
			CONTAINER_TEXTURE,
			imageHeight
		);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0.0F);
		graphics.pose().scale(scale, scale, 1.0F);
		graphics.blit(texture, 0, 0, 0.0F, 0.0F, 176, topHeight, 256, 256);
		graphics.blit(texture, 0, topHeight, 0.0F, 126.0F, 176, 96, 256, 256);
		graphics.drawString(this.font, Component.translatable(target.translationKey()), 8, 6, 0xFF404040, false);
		graphics.drawString(this.font, Component.translatable("container.inventory"), 8, imageHeight - 94, 0xFF404040, false);
		graphics.pose().popPose();
	}

	private void drawWarning(final GuiGraphics graphics) {
		graphics.fill(0, 0, this.width, this.height, 0x990A0612);
		int panelWidth = Math.max(1, Math.min(430, this.width - 12));
		int panelHeight = Math.max(1, Math.min(176, this.height - 12));
		int panelX = (this.width - panelWidth) / 2;
		int panelY = (this.height - panelHeight) / 2;
		UiRender.panel(graphics, panelX, panelY, panelWidth, panelHeight, 8, UiTheme.GLASS, UiTheme.ACCENT_SOFT);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("screen.kohs_inventory_tweaks.affect_containers.warning.title"),
			this.width / 2,
			panelY + 12,
			UiTheme.TEXT
		);
		int textY = panelY + 31;
		for (FormattedCharSequence line : this.font.split(
			Component.translatable("screen.kohs_inventory_tweaks.affect_containers.warning.description"),
			panelWidth - 28
		)) {
			if (textY > panelY + panelHeight - 44) {
				break;
			}
			graphics.drawString(this.font, line, panelX + 14, textY, UiTheme.TEXT_MUTED, false);
			textY += 10;
		}
	}

	@Override
	public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (this.warningVisible) {
				this.warningVisible = false;
				this.rebuildWidgets();
			} else {
				this.closeToParent();
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private ContainerScaleTarget selectedTarget() {
		return TARGETS[Math.floorMod(this.targetIndex, TARGETS.length)];
	}

	private void cycleTarget(final int direction) {
		this.targetIndex = Math.floorMod(this.targetIndex + direction, TARGETS.length);
	}

	private Component switchLabel() {
		return Component.translatable(
			"screen.kohs_inventory_tweaks.affect_containers.switch",
			Component.translatable(this.working.affectAllContainers
				? "screen.kohs_inventory_tweaks.enabled"
				: "screen.kohs_inventory_tweaks.disabled")
		);
	}

	private void persistWorking() {
		ConfigStore.replaceAndSave(this.working);
		this.working = ConfigStore.get().copy();
		InventoryTextureManager.invalidateConfiguration();
	}

	private void closeToParent() {
		this.persistWorking();
		this.parent.reloadConfigurationFromStore();
		this.minecraft.setScreen(this.parent);
	}
}
