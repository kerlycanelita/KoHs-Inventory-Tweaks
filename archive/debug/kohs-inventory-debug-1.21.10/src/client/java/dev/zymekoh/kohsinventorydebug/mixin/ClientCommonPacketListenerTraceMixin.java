package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes metadata from packets Minecraft has already chosen to send. */
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 3000)
abstract class ClientCommonPacketListenerTraceMixin {
	@Inject(method = "send", at = @At("HEAD"))
	private void kohsInventoryDebug$send(final Packet<?> packet, final CallbackInfo callbackInfo) {
		DebugCollector.onPacketSent(packet);
	}
}
