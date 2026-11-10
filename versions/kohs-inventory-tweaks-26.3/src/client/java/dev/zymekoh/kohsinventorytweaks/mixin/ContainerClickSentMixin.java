package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.SteadyInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Shows Steady inventory each container click as it leaves. The packet goes out untouched. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ContainerClickSentMixin {
	@ModifyArg(method = "handleContainerInput", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private Packet<?> kohsInventoryTweaks$clickSent(final Packet<?> packet) {
		if (packet instanceof ServerboundContainerClickPacket click) {
			SteadyInventory.onClickSent(Minecraft.getInstance(), click);
		}
		return packet;
	}
}
