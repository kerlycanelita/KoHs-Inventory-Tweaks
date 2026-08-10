package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
	@ModifyArg(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
		),
		index = 1
	)
	private Identifier kohsInventoryTweaks$useCustomizedContainerTexture(final Identifier original) {
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
		return InventoryTextureManager.containerTextureFor(
			ConfigStore.get(),
			original,
			screen.kohsInventoryTweaks$getImageHeight()
		);
	}
}
