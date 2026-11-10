package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player's inventory screen, laid out and drawn by {@link NextGenInventory} when one is chosen. */
@Mixin(InventoryScreen.class)
public abstract class NextGenInventoryMixin {
	private static final int VANILLA_MODEL_X = 26;
	private static final int VANILLA_MODEL_Y = 8;

	@Inject(method = "getRecipeBookButtonPosition", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$placeBookButton(final CallbackInfoReturnable<ScreenPosition> callbackInfo) {
		ScreenPosition position = NextGenInventory.recipeButtonPosition((InventoryScreen) (Object) this);
		if (position != null) {
			callbackInfo.setReturnValue(position);
		}
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void kohsInventoryTweaks$moveSlots(final CallbackInfo callbackInfo) {
		NextGenInventory.apply((InventoryScreen) (Object) this);
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$drawVisor(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		if (NextGenInventory.drawVisorBackground(graphics, (InventoryScreen) (Object) this, mouseX, mouseY)) {
			callbackInfo.cancel();
		}
	}

	@WrapOperation(
		method = "extractBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
		)
	)
	private void kohsInventoryTweaks$drawLayoutPanel(
		final GuiGraphicsExtractor graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int x,
		final int y,
		final float u,
		final float v,
		final int width,
		final int height,
		final int textureWidth,
		final int textureHeight,
		final Operation<Void> original
	) {
		if (!NextGenInventory.drawPanel(graphics, (InventoryScreen) (Object) this, pipeline, x, y)) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		}
	}

	@WrapOperation(
		method = "extractBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"
		)
	)
	private void kohsInventoryTweaks$moveModel(
		final GuiGraphicsExtractor graphics,
		final int x0,
		final int y0,
		final int x1,
		final int y1,
		final int size,
		final float offsetY,
		final float mouseX,
		final float mouseY,
		final LivingEntity entity,
		final Operation<Void> original
	) {
		int[] box = NextGenInventory.modelBox((InventoryScreen) (Object) this);
		if (box == null) {
			original.call(graphics, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity);
		} else if (box[4] > 0) {
			// Vanilla passes the panel's corner plus its own box, so the corner is recovered from that.
			int left = x0 - VANILLA_MODEL_X;
			int top = y0 - VANILLA_MODEL_Y;
			original.call(graphics, left + box[0], top + box[1], left + box[2], top + box[3], box[4], offsetY, mouseX, mouseY, entity);
		}
	}
}
