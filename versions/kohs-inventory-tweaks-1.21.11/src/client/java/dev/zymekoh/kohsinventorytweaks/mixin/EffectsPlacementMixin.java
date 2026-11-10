package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.zymekoh.kohsinventorytweaks.inventory.EffectsPlacement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Both of Vanilla's measurements must agree, or the HUD and the inventory would each leave the effects to the other. */
@Mixin(EffectsInInventory.class)
public abstract class EffectsPlacementMixin {
	@Shadow
	@Final
	private AbstractContainerScreen<?> screen;

	@WrapMethod(method = "canSeeEffects")
	private boolean kohsInventoryTweaks$measureWhereTheyAre(final Operation<Boolean> original) {
		return EffectsPlacement.whileMeasuring(this.screen, () -> original.call());
	}

	@WrapMethod(method = "render")
	private void kohsInventoryTweaks$drawWhereTheyFit(final GuiGraphics graphics, final int mouseX, final int mouseY, final Operation<Void> original) {
		EffectsPlacement.whileMeasuring(this.screen, () -> original.call(graphics, mouseX, mouseY));
	}
}
