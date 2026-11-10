package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.InterfaceTheme;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The colour of Minecraft's own buttons, when the player chose to theme them. */
@Mixin(AbstractButton.class)
public abstract class ButtonThemeMixin {
	@ModifyArg(
		method = "renderDefaultSprite",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"
		),
		index = 6
	)
	private int kohsInventoryTweaks$themeButton(final int color) {
		return InterfaceTheme.buttonColor(color);
	}
}
