package dev.zymekoh.kohsinventorydebug.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Local QA bridge into the same handler used by GLFW mouse-button callbacks. */
@Mixin(MouseHandler.class)
public interface MouseHandlerDebugInvoker {
	@Invoker("onMove")
	void kohsInventoryDebug$invokeMoveRaw(long windowHandle, double x, double y, double xrel, double yrel);

	@Invoker("onButton")
	void kohsInventoryDebug$invokeButton(long windowHandle, MouseButtonInfo info, int action);

	@Invoker("onScroll")
	void kohsInventoryDebug$invokeScroll(long windowHandle, double xOffset, double yOffset);
}
