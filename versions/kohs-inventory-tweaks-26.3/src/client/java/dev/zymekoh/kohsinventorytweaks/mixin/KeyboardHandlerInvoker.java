package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Minecraft's own keyboard entry points, for handing over input the fence kept. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
	@Invoker("keyPress")
	void kohsInventoryTweaks$keyPress(long window, int action, KeyEvent event);

	@Invoker("charTyped")
	void kohsInventoryTweaks$charTyped(long window, CharacterEvent event);
}
