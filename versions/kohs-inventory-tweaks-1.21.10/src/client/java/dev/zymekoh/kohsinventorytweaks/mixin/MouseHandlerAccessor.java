package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("xpos")
	void kohsInventoryTweaks$setXpos(double value);

	@Accessor("ypos")
	void kohsInventoryTweaks$setYpos(double value);
}
