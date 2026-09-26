package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Convert only GUI dispatch arguments; native pointer and camera deltas stay physical. */
@Mixin(MouseHandler.class)
public abstract class ContainerMouseInputMixin {
	@WrapOperation(method = "onButton", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
	private boolean kohsInventoryTweaks$click(final Screen screen, final MouseButtonEvent event,
		final boolean doubleClick, final Operation<Boolean> original) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		return original.call(screen, InventoryGuiScaler.toInventoryEvent(event, screen.width, screen.height, scale), doubleClick);
	}

	@WrapOperation(method = "onButton", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
	private boolean kohsInventoryTweaks$release(final Screen screen, final MouseButtonEvent event,
		final Operation<Boolean> original) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		return original.call(screen, InventoryGuiScaler.toInventoryEvent(event, screen.width, screen.height, scale));
	}

	@WrapOperation(method = "handleAccumulatedMovement", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
	private boolean kohsInventoryTweaks$drag(final Screen screen, final MouseButtonEvent event,
		final double dx, final double dy, final Operation<Boolean> original) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		return original.call(screen, InventoryGuiScaler.toInventoryEvent(event, screen.width, screen.height, scale), dx / scale, dy / scale);
	}

	@WrapOperation(method = "handleAccumulatedMovement", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;mouseMoved(DD)V"))
	private void kohsInventoryTweaks$move(final Screen screen, final double x, final double y,
		final Operation<Void> original) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		original.call(screen, InventoryGuiScaler.toInventoryCoordinate(x, screen.width, scale),
			InventoryGuiScaler.toInventoryCoordinate(y, screen.height, scale));
	}

	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
	private boolean kohsInventoryTweaks$scroll(final Screen screen, final double x, final double y,
		final double horizontal, final double vertical, final Operation<Boolean> original) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		return original.call(screen, InventoryGuiScaler.toInventoryCoordinate(x, screen.width, scale),
			InventoryGuiScaler.toInventoryCoordinate(y, screen.height, scale), horizontal, vertical);
	}
}
