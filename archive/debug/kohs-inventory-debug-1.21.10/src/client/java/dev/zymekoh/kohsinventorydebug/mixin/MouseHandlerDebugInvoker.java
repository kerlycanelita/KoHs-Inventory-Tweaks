package dev.zymekoh.kohsinventorydebug.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Local QA bridge into the same handler used by GLFW mouse-button callbacks. */
@Mixin(MouseHandler.class)
public interface MouseHandlerDebugInvoker {
	@Invoker("onButton")
	void kohsInventoryDebug$invokeButton(long windowHandle, MouseButtonInfo info, int action);
}
