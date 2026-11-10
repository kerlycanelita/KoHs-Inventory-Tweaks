package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.FrameRecorder;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a take records, every sound the game plays is marked with the file it chose, its volume
 * and its pitch, so the take's audio can be rebuilt in step with its frames.
 */
@Mixin(SoundManager.class)
abstract class SoundManagerTraceMixin {
	@Inject(method = "play", at = @At("RETURN"), require = 0)
	private void kohsInventoryDebug$markSound(final SoundInstance sound, final CallbackInfoReturnable<?> callback) {
		if (!FrameRecorder.recording()) {
			return;
		}
		try {
			FrameRecorder.mark("sound " + sound.getSound().getLocation() + " " + sound.getVolume() + " " + sound.getPitch());
		} catch (RuntimeException ignored) {
			// A sound that did not resolve plays nothing, so nothing is marked.
		}
	}
}
