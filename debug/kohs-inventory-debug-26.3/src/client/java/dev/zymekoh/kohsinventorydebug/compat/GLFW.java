package dev.zymekoh.kohsinventorydebug.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * The few GLFW names the labs use, answered by 26.3's SDL input.
 *
 * <p>Kept by the port script for the 26.3 companion only, so the labs read the same on
 * every version. Every number is 26.3's own: SDL scancodes for keys, buttons from 1
 * (1 left, 3 right), REPEAT = -1, SDL modifier masks. The port script turns the labs'
 * literal GLFW button numbers into these names.</p>
 */
public final class GLFW {
	public static final int GLFW_PRESS = InputConstants.PRESS;
	public static final int GLFW_RELEASE = InputConstants.RELEASE;
	public static final int GLFW_REPEAT = InputConstants.REPEAT;
	public static final int GLFW_MOD_SHIFT = InputConstants.MOD_SHIFT;
	public static final int GLFW_MOD_CONTROL = InputConstants.MOD_CONTROL;
	public static final int GLFW_KEY_ESCAPE = InputConstants.KEY_ESCAPE;
	public static final int GLFW_KEY_J = InputConstants.KEY_J;
	public static final int GLFW_KEY_F24 = InputConstants.KEY_F24;
	public static final int GLFW_MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	public static final int GLFW_MOUSE_BUTTON_4 = InputConstants.MOUSE_BUTTON_4;
	public static final int GLFW_MOUSE_BUTTON_5 = InputConstants.MOUSE_BUTTON_5;

	private GLFW() {
	}

	/** The second number of a KeyEvent: on 26.3 that is the SDL keycode of the key. */
	public static int glfwGetKeyScancode(final int key) {
		return SDLKeyboard.SDL_GetKeyFromScancode(key, (short) 0, true);
	}

	public static void glfwGetCursorPos(final long window, final DoubleBuffer x, final DoubleBuffer y) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer pointerX = stack.mallocFloat(1);
			FloatBuffer pointerY = stack.mallocFloat(1);
			SDLMouse.SDL_GetMouseState(pointerX, pointerY);
			x.put(x.position(), pointerX.get(0));
			y.put(y.position(), pointerY.get(0));
		}
	}

	public static void glfwSetCursorPos(final long window, final double x, final double y) {
		SDLMouse.SDL_WarpMouseInWindow(window, (float) x, (float) y);
	}

	public static void glfwSetWindowSize(final long window, final int width, final int height) {
		SDLVideo.SDL_SetWindowSize(window, width, height);
	}
}
