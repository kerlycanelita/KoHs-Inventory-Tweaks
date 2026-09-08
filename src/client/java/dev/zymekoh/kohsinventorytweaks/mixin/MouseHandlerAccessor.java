package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("xpos")
	void kohsInventoryTweaks$setXpos(double value);

	@Accessor("ypos")
	void kohsInventoryTweaks$setYpos(double value);

	@Accessor("accumulatedDX")
	void kohsInventoryTweaks$setAccumulatedDX(double value);

	@Accessor("accumulatedDY")
	void kohsInventoryTweaks$setAccumulatedDY(double value);

	/**
	 * The mouse button currently held down, or null when none is.
	 *
	 * <p>The public {@code isLeftPressed}/{@code isRightPressed} pair cannot answer this:
	 * Vanilla only assigns them inside the {@code screen == null} branch of
	 * {@code onButton}, so a button released while any screen is open stays reported as
	 * pressed until the next press-and-release in the world. {@code activeButton} is
	 * assigned before that branch, on both edges, and is therefore the one field that is
	 * always symmetric.</p>
	 */
	@Accessor("activeButton")
	@Nullable MouseButtonInfo kohsInventoryTweaks$getActiveButton();
}
