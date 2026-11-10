package dev.zymekoh.kohsinventorytweaks.mixin;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Double clicks in containers as they were before 25w31a, plus the two cases they never got right.
 *
 * <p>25w31a moved double-click timing into {@code MouseHandler}, which remembers every click,
 * and dropped the two places where the container screen used to forget the last one: after a
 * double click had collected (MC-303393, MC-306262: a third quick click collects again instead
 * of placing) and after a release had put the cursor stack down (MC-301852: placing a stack and
 * shift-clicking it at once moves every stack of that item). Both resets are back. And a double
 * click only collects on the slot both clicks were on (MC-311852), never at the end of a drag
 * the second press started (MC-312333).</p>
 *
 * <p>All of it decides which click type Vanilla sends for the player's own clicks; nothing is
 * added, repeated or sent by itself.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerDoubleClickMixin {
	@Shadow private boolean doubleclick;
	@Shadow private @Nullable Slot lastClickSlot;
	@Shadow protected boolean isQuickCrafting;
	@Shadow @Final protected Set<Slot> quickCraftSlots;
	@Shadow @Final protected AbstractContainerMenu menu;

	@Shadow
	private @Nullable Slot getHoveredSlot(final double x, final double y) {
		throw new AssertionError();
	}

	/** Whether the cursor held a stack when this release began. */
	@Unique private boolean kohsInventoryTweaks$carriedAtRelease;

	@Inject(method = "mouseReleased", at = @At("HEAD"))
	private void kohsInventoryTweaks$keepDoubleClickOnItsSlot(
		final MouseButtonEvent event, final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		this.kohsInventoryTweaks$carriedAtRelease = !this.menu.getCarried().isEmpty();
		if (!this.doubleclick) {
			return;
		}
		Slot slot = this.getHoveredSlot(event.x(), event.y());
		if (slot != this.lastClickSlot || this.isQuickCrafting && this.quickCraftSlots.size() > 1) {
			this.doubleclick = false;
		}
	}

	/** The double click has done its work: the next click starts a new pair. */
	@Inject(
		method = "mouseReleased",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;doubleclick:Z",
			opcode = Opcodes.PUTFIELD)
	)
	private void kohsInventoryTweaks$endPairAfterCollect(
		final MouseButtonEvent event, final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		forgetLastClick();
	}

	/** A release that put the cursor stack down ends the pair too. */
	@Inject(method = "mouseReleased", at = @At("RETURN"))
	private void kohsInventoryTweaks$endPairAfterPlacing(
		final MouseButtonEvent event, final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if (this.kohsInventoryTweaks$carriedAtRelease && this.menu.getCarried().isEmpty()) {
			forgetLastClick();
		}
	}

	@Unique
	private static void forgetLastClick() {
		((MouseHandlerAccessor) Minecraft.getInstance().mouseHandler).kohsInventoryTweaks$setLastClickButton(-1);
	}
}
