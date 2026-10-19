package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.render.AnimatedItemPacer;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enchanted and animated GUI items are redrawn at a steady pace instead of on every frame. See {@link AnimatedItemPacer}. */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {
	@WrapOperation(method = "getOrUpdate", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/DynamicAtlasAllocator;getOrAllocate(Ljava/lang/Object;Z)Lnet/minecraft/client/gui/render/DynamicAtlasAllocator$Slot;"))
	private DynamicAtlasAllocator.Slot kohsInventoryTweaks$paceAnimatedItems(
		final DynamicAtlasAllocator<Object> allocator, final Object key, final boolean animated,
		final Operation<DynamicAtlasAllocator.Slot> original
	) {
		if (!animated || !AnimatedItemPacer.active()) {
			return original.call(allocator, key, animated);
		}
		// Kept across frames like any still item, and redrawn in place when its time comes.
		DynamicAtlasAllocator.Slot slot = original.call(allocator, key, false);
		if (slot != null && AnimatedItemPacer.redrawDue(key) && slot.state() == DynamicAtlasAllocator.SlotState.READY) {
			((AtlasSlotAccessor) slot).kohsInventoryTweaks$setExternalState(DynamicAtlasAllocator.SlotState.STALE);
		}
		return slot;
	}

	@Inject(method = "endFrame", at = @At("RETURN"))
	private void kohsInventoryTweaks$forgetUnseenItems(final CallbackInfo callbackInfo) {
		AnimatedItemPacer.endFrame();
	}
}
