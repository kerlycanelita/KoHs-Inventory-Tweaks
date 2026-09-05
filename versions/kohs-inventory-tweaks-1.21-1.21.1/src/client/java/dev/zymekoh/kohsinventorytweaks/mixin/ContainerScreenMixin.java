package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
	@ModifyArg(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
		),
		index = 0
	)
	private ResourceLocation kohsInventoryTweaks$useCustomizedContainerTexture(final ResourceLocation original) {
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
		return InventoryTextureManager.containerTextureFor(
			ConfigStore.get(),
			original,
			screen.kohsInventoryTweaks$getImageHeight()
		);
	}
}
