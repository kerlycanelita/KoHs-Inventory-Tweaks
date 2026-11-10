package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.NextGenHotbar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The moment a totem goes off on the local player: only watched, for the hotbar's reaction. */
@Mixin(LocalPlayer.class)
public abstract class HotbarEventsMixin {
	@Inject(method = "displayItemActivation", at = @At("HEAD"))
	private void kohsInventoryTweaks$noticeItemActivation(final ItemStack stack, final CallbackInfo callbackInfo) {
		NextGenHotbar.onItemActivation(stack);
	}
}
