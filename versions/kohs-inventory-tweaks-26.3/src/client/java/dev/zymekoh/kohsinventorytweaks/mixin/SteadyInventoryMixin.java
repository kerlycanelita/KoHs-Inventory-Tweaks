package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.inventory.SteadyInventory;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The inventory answers of the server, for Steady inventory. A whole-inventory answer is still put
 * on its menu by Vanilla's own call; only the stacks handed to that call can differ.
 */
@Mixin(ClientPacketListener.class)
public abstract class SteadyInventoryMixin {
	@WrapOperation(method = "handleContainerContent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/inventory/InventoryMenu;initializeContents(ILjava/util/List;Lnet/minecraft/world/item/ItemStack;)V"))
	private void kohsInventoryTweaks$steadyInventory(final InventoryMenu menu, final int stateId, final List<ItemStack> items,
		final ItemStack carried, final Operation<Void> original) {
		SteadyInventory.Answer answer = SteadyInventory.onAnswer(Minecraft.getInstance(), menu, items, carried);
		original.call(menu, stateId, answer.items(), answer.carried());
	}

	@WrapOperation(method = "handleContainerContent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;initializeContents(ILjava/util/List;Lnet/minecraft/world/item/ItemStack;)V"))
	private void kohsInventoryTweaks$steadyContainer(final AbstractContainerMenu menu, final int stateId, final List<ItemStack> items,
		final ItemStack carried, final Operation<Void> original) {
		SteadyInventory.Answer answer = SteadyInventory.onAnswer(Minecraft.getInstance(), menu, items, carried);
		original.call(menu, stateId, answer.items(), answer.carried());
	}

	@Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
	private void kohsInventoryTweaks$slotChanged(final ClientboundContainerSetSlotPacket packet, final CallbackInfo callbackInfo) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.isSameThread() || minecraft.player == null) {
			return;
		}
		AbstractContainerMenu menu = packet.getContainerId() == 0 ? minecraft.player.inventoryMenu
			: packet.getContainerId() == minecraft.player.containerMenu.containerId ? minecraft.player.containerMenu : null;
		if (menu != null && packet.getSlot() >= 0 && packet.getSlot() < menu.slots.size()) {
			Slot slot = menu.getSlot(packet.getSlot());
			SteadyInventory.onServerSlot(slot.container, slot.getContainerSlot());
		}
	}

	@Inject(method = "handleSetPlayerInventory", at = @At("RETURN"))
	private void kohsInventoryTweaks$playerSlotChanged(final ClientboundSetPlayerInventoryPacket packet, final CallbackInfo callbackInfo) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isSameThread() && minecraft.player != null) {
			SteadyInventory.onServerSlot(minecraft.player.getInventory(), packet.slot());
		}
	}

	@Inject(method = "handleSetCursorItem", at = @At("RETURN"))
	private void kohsInventoryTweaks$carriedChanged(final ClientboundSetCursorItemPacket packet, final CallbackInfo callbackInfo) {
		if (Minecraft.getInstance().isSameThread()) {
			SteadyInventory.onServerCarried();
		}
	}
}
