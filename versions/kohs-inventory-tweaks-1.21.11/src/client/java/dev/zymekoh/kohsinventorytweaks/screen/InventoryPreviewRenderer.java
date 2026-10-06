package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.cursor.CursorTarget;
import dev.zymekoh.kohsinventorytweaks.render.AccessibilityRenderController;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import dev.zymekoh.kohsinventorytweaks.render.ItemHighlighterController;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/**
 * Draws the inventories the configuration screens preview: the player's own, with the
 * player model and the highlighted items, and every container and surface the
 * customization reaches. Each preview is drawn at native size under a scale, so the
 * callers only decide where it goes and how large.
 */
final class InventoryPreviewRenderer {
	static final int INVENTORY_WIDTH = 176;
	static final int INVENTORY_HEIGHT = 166;
	private static final Identifier CONTAINER_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	private InventoryPreviewRenderer() {
	}

	static void drawPlayerInventory(
		final GuiGraphics graphics,
		final Font font,
		final int x,
		final int y,
		final float scale,
		final int mouseX,
		final int mouseY,
		final boolean followMouse,
		final InventoryTweaksConfig visualConfig
	) {
		Minecraft minecraft = Minecraft.getInstance();
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
		if (minecraft.player != null) {
			int entityX0 = x + Math.round(26 * scale);
			int entityY0 = y + Math.round(8 * scale);
			int entityX1 = x + Math.round(75 * scale);
			int entityY1 = y + Math.round(78 * scale);
			float entityMouseX = followMouse ? mouseX : (entityX0 + entityX1) * 0.5F;
			float entityMouseY = followMouse ? mouseY : (entityY0 + entityY1) * 0.5F;
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
				minecraft.player
			);
			String hoveredDynamicItem = null;
			Slot hoveredPreviewSlot = null;
			if (followMouse && scale > 0.0F) {
				double localMouseX = (mouseX - x) / scale;
				double localMouseY = (mouseY - y) / scale;
				for (Slot slot : minecraft.player.inventoryMenu.slots) {
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
						hoveredPreviewSlot = slot;
						break;
					}
				}
			}
			int totems = 0;
			for (Slot slot : minecraft.player.inventoryMenu.slots) {
				if (slot.isActive() && !slot.getItem().isEmpty()) {
					InventoryTweaksConfig.ItemHighlight highlight = ItemHighlighterController.highlightFor(visualConfig, slot.getItem());
					boolean renderHighlight = highlight != null
						&& (!highlight.dynamicHighlight || highlight.itemId.equals(hoveredDynamicItem));
					if (renderHighlight) {
						ItemHighlighterController.drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, false);
					}
					graphics.renderItem(slot.getItem(), slot.x, slot.y, slot.x + slot.y * INVENTORY_WIDTH);
					graphics.renderItemDecorations(font, slot.getItem(), slot.x, slot.y);
					if (slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
						// The mascot pulls its meals from here; an eaten totem fades in the preview only.
						int ordinal = totems++;
						ZMascot.previewTotem(ordinal, x + slot.x * scale, y + slot.y * scale, 16.0F * scale);
						if (ZMascot.totemEaten(ordinal)) {
							graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xB08B8B8B);
						}
					}
					if (renderHighlight) {
						ItemHighlighterController.drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, true);
					}
					if (slot == hoveredPreviewSlot) {
						AccessibilityRenderController.drawPreviewFocus(graphics, slot.x, slot.y, visualConfig);
					}
				}
			}
		}
		graphics.pose().popMatrix();
	}

	static void drawContainerPreview(
		final GuiGraphics graphics,
		final Font font,
		final int x,
		final int y,
		final float scale,
		final CursorTarget target,
		final InventoryTweaksConfig visualConfig
	) {
		if (target == CursorTarget.SHULKER) {
			drawSurfacePreview(
				graphics,
				font,
				x,
				y,
				scale,
				CustomizationPreview.SHULKER_BOX,
				visualConfig
			);
			return;
		}
		drawGenericContainerPreview(
			graphics,
			font,
			x,
			y,
			scale,
			target.containerRows(),
			target.translationKey(),
			visualConfig
		);
	}

	static void drawContainerPreview(
		final GuiGraphics graphics,
		final Font font,
		final int x,
		final int y,
		final float scale,
		final CustomizationPreview target,
		final InventoryTweaksConfig visualConfig
	) {
		drawGenericContainerPreview(
			graphics,
			font,
			x,
			y,
			scale,
			target.containerRows(),
			target.translationKey(),
			visualConfig
		);
	}

	static void drawGenericContainerPreview(
		final GuiGraphics graphics,
		final Font font,
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
		Identifier texture = InventoryTextureManager.containerTextureFor(visualConfig, CONTAINER_TEXTURE, imageHeight);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 176, topHeight, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, topHeight, 0.0F, 126.0F, 176, 96, 256, 256);
		graphics.drawString(font, Component.translatable(translationKey), 8, 6, 0xFF404040, false);
		graphics.drawString(font, Component.translatable("container.inventory"), 8, imageHeight - 94, 0xFF404040, false);
		graphics.pose().popMatrix();
	}

	static void drawSurfacePreview(
		final GuiGraphics graphics,
		final Font font,
		final int x,
		final int y,
		final float scale,
		final CustomizationPreview target,
		final InventoryTweaksConfig visualConfig
	) {
		Identifier texture = InventoryTextureManager.previewTextureFor(
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

	static void drawBundlePreview(
		final GuiGraphics graphics,
		final Font font,
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
}
