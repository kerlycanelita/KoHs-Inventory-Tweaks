package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.InterfaceTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The box behind a tooltip, when the player chose to theme it. */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipThemeMixin {
	@Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
	private static void kohsInventoryTweaks$themeTooltip(
		final GuiGraphicsExtractor graphics,
		final int x,
		final int y,
		final int width,
		final int height,
		final @Nullable Identifier style,
		final CallbackInfo callbackInfo
	) {
		if (InterfaceTheme.drawTooltip(graphics, x, y, width, height, style)) {
			callbackInfo.cancel();
		}
	}
}
