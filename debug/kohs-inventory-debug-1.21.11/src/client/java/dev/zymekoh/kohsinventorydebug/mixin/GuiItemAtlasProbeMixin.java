package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.InventoryFeelLab;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the items drawn into the GUI item atlas, for the inventory-feel lab. 1.21.11 draws them in GuiRenderer. Observes only. */
@Mixin(GuiRenderer.class)
public abstract class GuiItemAtlasProbeMixin {
	@Inject(method = "renderItemToAtlas", at = @At("HEAD"))
	private void kohsInventoryDebug$countDraw(final CallbackInfo callbackInfo) {
		InventoryFeelLab.onAtlasDraw();
	}
}
