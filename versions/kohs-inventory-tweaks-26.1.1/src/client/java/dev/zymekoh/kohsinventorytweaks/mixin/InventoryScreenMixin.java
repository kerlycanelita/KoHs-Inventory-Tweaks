package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// This priority wins the one confirmed, safely adaptable redirect collision.
// Unknown collisions remain blocked instead of being forced optimistically.
@Mixin(value = InventoryScreen.class, priority = 2000)
public abstract class InventoryScreenMixin {
	@ModifyArg(
		method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
		),
		index = 1
	)
	private Identifier kohsInventoryTweaks$useCustomizedInventoryTexture(final Identifier original) {
		return InventoryTextureManager.textureFor(ConfigStore.get());
	}

}
