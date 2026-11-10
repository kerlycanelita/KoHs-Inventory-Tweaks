package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.CursorWeightLab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the weight lab move the real pointer while the inventory initializes. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenInitProbeMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void kohsInventoryDebug$handMovesDuringInit(final CallbackInfo callbackInfo) {
		CursorWeightLab.onInventoryInit(Minecraft.getInstance());
	}
}
