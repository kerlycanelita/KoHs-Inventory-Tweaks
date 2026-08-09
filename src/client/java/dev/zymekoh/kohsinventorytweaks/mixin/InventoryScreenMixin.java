package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void kohsInventoryTweaks$beginInventoryScale(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		float scale = (float) InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		float centerX = screen.width * 0.5F;
		float centerY = screen.height * 0.5F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
	}

	@Inject(
		method = "extractBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V",
			shift = At.Shift.BEFORE
		)
	)
	private void kohsInventoryTweaks$beginScaledInventoryBackground(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		float scale = (float) InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		float centerX = screen.width * 0.5F;
		float centerY = screen.height * 0.5F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-centerX, -centerY);
	}

	@Inject(method = {"extractBackground", "extractRenderState"}, at = @At("RETURN"))
	private void kohsInventoryTweaks$endInventoryScale(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		graphics.pose().popMatrix();
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void kohsInventoryTweaks$finishFastOpening(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.onInventoryRendered(
			Minecraft.getInstance(),
			(InventoryScreen) (Object) this
		);
	}

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int kohsInventoryTweaks$transformRenderMouseX(final int mouseX) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale));
	}

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int kohsInventoryTweaks$transformRenderMouseY(final int mouseY) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale));
	}

	@ModifyVariable(method = "mouseReleased", at = @At("HEAD"), argsOnly = true)
	private MouseButtonEvent kohsInventoryTweaks$transformReleasedMouse(final MouseButtonEvent event) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryEvent(event, screen.width, screen.height, scale);
	}

	@ModifyArg(
		method = "extractBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
		),
		index = 1
	)
	private Identifier kohsInventoryTweaks$useCustomizedInventoryTexture(final Identifier original) {
		return InventoryTextureManager.textureFor(ConfigStore.get());
	}

	@Redirect(
		method = "extractBackground",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"
		)
	)
	private void kohsInventoryTweaks$scaleInventoryEntityAtRealPosition(
		final GuiGraphicsExtractor graphics,
		final int x0,
		final int y0,
		final int x1,
		final int y1,
		final int size,
		final float offsetY,
		final float mouseX,
		final float mouseY,
		final LivingEntity entity
	) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		float scale = (float) InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		float centerX = screen.width * 0.5F;
		float centerY = screen.height * 0.5F;
		InventoryScreen.extractEntityInInventoryFollowsMouse(
			graphics,
			Math.round(centerX + (x0 - centerX) * scale),
			Math.round(centerY + (y0 - centerY) * scale),
			Math.round(centerX + (x1 - centerX) * scale),
			Math.round(centerY + (y1 - centerY) * scale),
			Math.max(1, Math.round(size * scale)),
			offsetY,
			centerX + (mouseX - centerX) * scale,
			centerY + (mouseY - centerY) * scale,
			entity
		);
	}
}
