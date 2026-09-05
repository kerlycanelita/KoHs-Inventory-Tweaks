package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.SuperFastInventoryController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decides the early inventory opening right after Minecraft drains the tasks the
 * last GLFW poll queued.
 *
 * <p>Keyboard, mouse-button and cursor-position callbacks are not handled where
 * GLFW delivers them: each one is posted through {@code Minecraft#execute} and
 * only runs when {@code runAllTasks} drains the queue, at the top of
 * {@code runTick}. Deciding at the poll itself therefore reads input that is one
 * frame old, and leaves cursor samples taken before the opening still queued, so
 * they overwrite the pointer this mod just placed. Deciding here reads the press
 * in the frame it arrived, after every sample of that frame has already been
 * applied, and after {@code processQueuedPackets}, so a container the server
 * opens in this same frame is visible to the availability check.</p>
 *
 * <p>This still runs before {@code Minecraft#tick}, which is the point of the
 * feature: the opening is answered on this frame instead of waiting for the next
 * 20 TPS tick.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftInputDrainMixin {
	@Inject(
		method = "runTick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Minecraft;runAllTasks()V",
			shift = At.Shift.AFTER
		)
	)
	private void kohsInventoryTweaks$afterQueuedInputDrain(
		final boolean renderLevel,
		final CallbackInfo callbackInfo
	) {
		SuperFastInventoryController.afterInputPoll((Minecraft) (Object) this);
	}
}
