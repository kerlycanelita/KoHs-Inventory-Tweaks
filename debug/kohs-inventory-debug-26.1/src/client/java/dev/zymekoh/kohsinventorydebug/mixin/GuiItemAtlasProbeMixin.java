package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.InventoryFeelLab;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the items drawn into the GUI item atlas, for the inventory-feel lab. Observes only. */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasProbeMixin {
	@Inject(method = "drawToSlot", at = @At("HEAD"))
	private void kohsInventoryDebug$countDraw(final CallbackInfo callbackInfo) {
		InventoryFeelLab.onAtlasDraw();
	}
}
