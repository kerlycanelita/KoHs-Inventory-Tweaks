package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.SDLEvents;

/**
 * The labs' mouse bridge on 26.3, where MouseHandler#onMove also takes the relative motion.
 *
 * <p>Kept by the port script for the 26.3 companion only. A real SDL motion event reports how
 * far the pointer went, so the injected one does too: the distance from where Minecraft last
 * saw it. That is what turns the camera when the mouse is grabbed.</p>
 *
 * <p>SDL also turns every warp, such as the one that releases the mouse for a screen, into a
 * motion event of its own, read at the next poll. A physical hand moves after that event; the
 * lab's hand moves the handler directly, so a warp still queued would land on top of it and
 * put the pointer back. The lab's hand is the only mouse here, so those queued moves go.</p>
 */
public final class MouseMoves {
	private final MouseHandler handler;

	private MouseMoves(final MouseHandler handler) {
		this.handler = handler;
	}

	public static MouseMoves of(final MouseHandler handler) {
		return new MouseMoves(handler);
	}

	public void kohsInventoryDebug$invokeMove(final long windowHandle, final double x, final double y) {
		SDLEvents.SDL_FlushEvent(SDLEvents.SDL_EVENT_MOUSE_MOTION);
		((MouseHandlerDebugInvoker) this.handler).kohsInventoryDebug$invokeMoveRaw(
			windowHandle, x, y, x - this.handler.xpos(), y - this.handler.ypos());
	}

	public void kohsInventoryDebug$invokeButton(final long windowHandle, final MouseButtonInfo info, final int action) {
		((MouseHandlerDebugInvoker) this.handler).kohsInventoryDebug$invokeButton(windowHandle, info, action);
	}

	public void kohsInventoryDebug$invokeScroll(final long windowHandle, final double xOffset, final double yOffset) {
		((MouseHandlerDebugInvoker) this.handler).kohsInventoryDebug$invokeScroll(windowHandle, xOffset, yOffset);
	}
}
