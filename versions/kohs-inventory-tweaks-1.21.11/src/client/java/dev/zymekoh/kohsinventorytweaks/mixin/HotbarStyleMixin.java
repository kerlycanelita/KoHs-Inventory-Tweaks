package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.zymekoh.kohsinventorytweaks.render.NextGenHotbar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The hotbar on the HUD, handed to {@link NextGenHotbar} to shape and bring to life. */
@Mixin(Gui.class)
public abstract class HotbarStyleMixin {
	@Shadow
	protected abstract void renderSlot(GuiGraphics graphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed);

	@WrapMethod(method = "renderHotbarAndDecorations")
	private void kohsInventoryTweaks$moveStatusBars(
		final GuiGraphics graphics,
		final DeltaTracker deltaTracker,
		final Operation<Void> original
	) {
		NextGenHotbar.extractHud(graphics, () -> original.call(graphics, deltaTracker));
	}

	@WrapMethod(method = "renderItemHotbar")
	private void kohsInventoryTweaks$styleHotbar(
		final GuiGraphics graphics,
		final DeltaTracker deltaTracker,
		final Operation<Void> original
	) {
		NextGenHotbar.extract(
			graphics,
			(x, y, player, stack, seed) -> this.renderSlot(graphics, x, y, deltaTracker, player, stack, seed),
			() -> original.call(graphics, deltaTracker)
		);
	}
}
