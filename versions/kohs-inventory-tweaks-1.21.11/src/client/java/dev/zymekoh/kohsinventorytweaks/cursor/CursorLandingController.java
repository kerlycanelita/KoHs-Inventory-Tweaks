package dev.zymekoh.kohsinventorytweaks.cursor;

import dev.zymekoh.kohsinventorytweaks.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

/**
 * The opening lifecycle of Center Mouse Fix and Cursor Landing: which screen is
 * opening, where the release put the pointer, and the one correction each opening
 * may make. Positions come from {@link CursorPlacement}, targets from
 * {@link CursorScreens}; this class is the only one that moves the pointer.
 */
public final class CursorLandingController {
	private static final double CURSOR_POSITION_EPSILON = 0.5;
	private static @Nullable Screen openingScreen;
	private static @Nullable CursorTarget openingTarget;
	/** Where this opening's release left the pointer once GLFW showed it again. */
	private static double @Nullable [] releasedPosition;

	private CursorLandingController() {
	}

	public static void onScreenRequested(final @Nullable Screen screen) {
		if (!isScreenHandlingAvailable(screen)) {
			clearAllState();
			return;
		}
		openingScreen = screen;
		openingTarget = CursorScreens.classify(screen);
		releasedPosition = null;
	}

	public static @Nullable double[] overrideReleasePosition(final Minecraft minecraft) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		// A later release/refocus is not another inventory opening. Never re-arm
		// custom landing merely because the same inventory is still on screen.
		if (screen == null || screen != openingScreen || !canPositionCursor(minecraft)) {
			return null;
		}
		CursorTarget target = openingTarget;
		if (target == null || !CursorPlacement.shouldPlace(target)) {
			return null;
		}

		// Let Vanilla attempt its normal center. The opening finalizer also covers
		// an already released mouse (releaseMouse returns without centering then).
		return CursorPlacement.customPoint(target) == null
			? null
			: CursorPlacement.resolve(minecraft, screen, target, false);
	}

	/**
	 * Runs inside {@code releaseMouse}, right after GLFW leaves disabled-cursor mode.
	 *
	 * <p>Leaving that mode puts the pointer back where GLFW saved it when the mouse
	 * was grabbed, which is not always where Minecraft just asked for it: a window
	 * resized while grabbed has a new centre. Checked here, microseconds after the
	 * switch, that difference is found before the player's hand has had time to move
	 * anything. The same check used to run after {@code Screen#init}, where movement
	 * made during initialization looked exactly like that difference and was pulled
	 * back to the target -- the weight players felt at the start of every move
	 * toward an item with Center Mouse Fix on.</p>
	 */
	public static void afterMouseRelease(final Minecraft minecraft, final double x, final double y) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		if (screen == null || screen != openingScreen || !canPositionCursor(minecraft)) {
			return;
		}
		CursorTarget target = openingTarget;
		if (target == null || !CursorPlacement.shouldPlace(target)) {
			return;
		}
		double[] requested = {x, y};
		warp(minecraft, requested);
		releasedPosition = requested;
	}

	/**
	 * Finishes the opening after {@link Minecraft#setScreen(Screen)} has completed
	 * the whole synchronous transaction.
	 *
	 * <p>This is still the same input event and frame: no tick, render or scheduled
	 * task is crossed. When the release already placed the pointer, the only thing
	 * left to do is follow a target the initialized layout moved, and only while the
	 * pointer is still exactly where the release left it: once it has moved, it
	 * belongs to the player. When Vanilla skipped the release because the mouse was
	 * already free, the pointer is placed here once.</p>
	 */
	public static void onScreenOpened(final Minecraft minecraft, final @Nullable Screen requestedScreen) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		if (screen == null || screen != requestedScreen || screen != openingScreen) {
			clearAllState();
			return;
		}
		if (!isScreenHandlingAvailable(screen) || !canPositionCursor(minecraft)) {
			clearAllState();
			return;
		}

		CursorTarget target = openingTarget != null ? openingTarget : CursorScreens.classify(screen);
		if (target == null || !CursorPlacement.shouldPlace(target)) {
			clearOpeningState();
			return;
		}

		double[] placement = CursorPlacement.resolve(minecraft, screen, target, true);
		double[] released = releasedPosition;
		if (released == null) {
			// One screen replacing another: nothing has placed the pointer for this opening.
			warp(minecraft, placement);
		} else if (!matches(placement, released)) {
			// The initialized layout moved the target, as an open recipe book can.
			double[] current = pointerPosition(minecraft);
			if (current != null && matches(current, released)) {
				warp(minecraft, placement);
			}
		}
		// A Vanilla fallback can also open before handleAccumulatedMovement. Deltas
		// sampled before this synchronous landing belong to the old screen/camera,
		// not to a drag in the new inventory. Future callbacks remain untouched.
		MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
		mouse.kohsInventoryTweaks$setAccumulatedDX(0.0);
		mouse.kohsInventoryTweaks$setAccumulatedDY(0.0);
		clearOpeningState();
	}

	private static boolean matches(final double[] current, final double[] expected) {
		return Math.abs(current[0] - expected[0]) <= CURSOR_POSITION_EPSILON
			&& Math.abs(current[1] - expected[1]) <= CURSOR_POSITION_EPSILON;
	}

	private static boolean canPositionCursor(final Minecraft minecraft) {
		return minecraft != null && minecraft.isWindowActive()
			&& !minecraft.getWindow().isMinimized();
	}

	private static double @Nullable [] pointerPosition(final Minecraft minecraft) {
		if (minecraft == null) {
			return null;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			DoubleBuffer x = stack.mallocDouble(1);
			DoubleBuffer y = stack.mallocDouble(1);
			GLFW.glfwGetCursorPos(minecraft.getWindow().handle(), x, y);
			return new double[] {x.get(0), y.get(0)};
		}
	}

	private static void clearOpeningState() {
		openingScreen = null;
		openingTarget = null;
		releasedPosition = null;
	}

	private static void clearAllState() {
		clearOpeningState();
	}

	private static void warp(final Minecraft minecraft, final double[] position) {
		if (minecraft == null || position == null) {
			return;
		}
		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setXpos(position[0]);
		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setYpos(position[1]);

		double[] current = pointerPosition(minecraft);
		if (current != null && matches(current, position)) {
			return;
		}
		GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), position[0], position[1]);
	}

	private static boolean isScreenHandlingAvailable(final @Nullable Screen screen) {
		CursorTarget target = CursorScreens.classify(screen);
		if (target == null) {
			return false;
		}
		return CursorPlacement.shouldPlace(target);
	}
}
