package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server confirmations relevant to inventory and offhand ghost reports. */
@Mixin(value = ClientPacketListener.class, priority = 3000)
abstract class ClientPacketListenerTraceMixin {
	@Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
	private void kohsInventoryDebug$slot(final ClientboundContainerSetSlotPacket packet, final CallbackInfo callbackInfo) {
		DebugCollector.onContainerSlot(packet);
	}

	@Inject(method = "handleContainerContent", at = @At("HEAD"))
	private void kohsInventoryDebug$content(final ClientboundContainerSetContentPacket packet, final CallbackInfo callbackInfo) {
		DebugCollector.onContainerContent(packet);
	}
}
