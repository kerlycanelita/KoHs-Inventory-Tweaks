package dev.zymekoh.kohsinventorydebug.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Local QA bridge into the same handler used by GLFW keyboard callbacks. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerDebugInvoker {
	@Invoker("keyPress")
	void kohsInventoryDebug$invokeKeyPress(long windowHandle, int action, KeyEvent event);
}
