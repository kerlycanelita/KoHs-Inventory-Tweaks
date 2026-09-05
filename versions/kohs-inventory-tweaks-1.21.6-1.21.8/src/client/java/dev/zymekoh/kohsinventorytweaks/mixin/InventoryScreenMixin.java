package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Wins the one confirmed redirect that KoHs can safely adapt.
@Mixin(value = InventoryScreen.class, priority = 2000)
public abstract class InventoryScreenMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void kohsInventoryTweaks$beginInventoryScale(
		final GuiGraphics graphics,
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

	@Inject(method = "render", at = @At("RETURN"))
	private void kohsInventoryTweaks$endInventoryScale(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		graphics.pose().popMatrix();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void kohsInventoryTweaks$finishFastOpening(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int kohsInventoryTweaks$transformRenderMouseX(final int mouseX) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale));
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int kohsInventoryTweaks$transformRenderMouseY(final int mouseY) {
		InventoryScreen screen = (InventoryScreen) (Object) this;
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return (int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale));
	}

	@ModifyArg(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"
		),
		index = 1
	)
	private ResourceLocation kohsInventoryTweaks$useCustomizedInventoryTexture(final ResourceLocation original) {
		return InventoryTextureManager.textureFor(ConfigStore.get());
	}

	@Redirect(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"
		)
	)
	private void kohsInventoryTweaks$scaleInventoryEntityAtRealPosition(
		final GuiGraphics graphics,
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
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(1.0F / scale, 1.0F / scale);
		graphics.pose().translate(-centerX, -centerY);
		InventoryScreen.renderEntityInInventoryFollowsMouse(
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
		graphics.pose().popMatrix();
	}

}
