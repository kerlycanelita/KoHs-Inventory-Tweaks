package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The player in the inventory moves as smoothly as the player in the world (MC-265935).
 *
 * <p>Vanilla builds the inventory model with a partial tick of 1, the end of the current tick, so
 * its walk, swing, crouch and idle motion only change twenty times a second however fast the game
 * draws. The frame's real partial tick blends them as the world does. The rotations that follow
 * the pointer are written over the built state afterwards and stay exactly as they were.</p>
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryModelMixin {
	@ModifyArg(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"),
		index = 1
	)
	private static float kohsInventoryTweaks$framePartialTick(final float partialTick) {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}
}
