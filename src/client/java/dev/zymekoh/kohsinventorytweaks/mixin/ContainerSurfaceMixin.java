package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scale once before virtual dispatch, including workstation widgets and recipe books. */
@Mixin(Screen.class)
public abstract class ContainerSurfaceMixin {

	@WrapOperation(method = "extractRenderStateWithTooltipAndSubtitles", at = {
		@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
		@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V")
	})
	private void kohsInventoryTweaks$extractSurface(final Screen screen, final GuiGraphicsExtractor graphics,
		final int mouseX, final int mouseY, final float delta, final Operation<Void> original) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		if (Math.abs(scale - 1.0) < 0.0001) {
			original.call(screen, graphics, mouseX, mouseY, delta);
			return;
		}
		InventoryGuiScaler.beginScaledSurface(graphics, screen.width * 0.5F, screen.height * 0.5F, (float) scale);
		try {
			original.call(screen, graphics,
				(int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale)),
				(int) Math.round(InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale)), delta);
		} finally {
			InventoryGuiScaler.endScaledSurface(graphics);
		}
	}

	// The superclass draws the whole-window backdrop, not the container's texture.
	@WrapMethod(method = "extractBackground")
	private void kohsInventoryTweaks$fullWindowBackdrop(final GuiGraphicsExtractor graphics,
		final int mouseX, final int mouseY, final float delta, final Operation<Void> original) {
		if (!InventoryGuiScaler.isScaledSurfaceActive()) {
			original.call(graphics, mouseX, mouseY, delta);
			return;
		}
		Screen screen = (Screen) (Object) this;
		InventoryGuiScaler.beginScaledSurface(graphics, screen.width * 0.5F, screen.height * 0.5F,
			(float) (1.0 / InventoryGuiScaler.activeSurfaceScale()));
		try {
			original.call(graphics, mouseX, mouseY, delta);
		} finally {
			InventoryGuiScaler.endScaledSurface(graphics);
		}
	}
}
