package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.NextGenHotbar;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's own attack: only watched, for the hotbar's reaction, and never changed. */
@Mixin(MultiPlayerGameMode.class)
public abstract class HotbarAttackMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void kohsInventoryTweaks$noticeAttack(final Player player, final Entity entity, final CallbackInfo callbackInfo) {
		NextGenHotbar.onAttack();
	}
}
