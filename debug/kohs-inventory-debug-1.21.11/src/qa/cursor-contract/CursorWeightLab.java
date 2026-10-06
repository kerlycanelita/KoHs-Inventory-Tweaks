package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import java.nio.DoubleBuffer;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.RecipeBookType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * QA fixture for the cursor "weight" players report with Center Mouse Fix on.
 *
 * <p>The hand does not wait for the inventory to finish initializing. This lab
 * moves the real Windows pointer from inside {@code InventoryScreen#init}, after
 * Vanilla has released the mouse and before the opening returns, exactly where a
 * fast flick toward a totem lands. The movement must survive the opening: a
 * pointer pulled back to the landing target is the weight. A second battery
 * resizes the window while the mouse is captured, which leaves GLFW's saved
 * restore position stale; that is the case Center Mouse Fix exists to correct.
 * Compiled only into the disposable debugger, never the mod.</p>
 */
public final class CursorWeightLab {
    /** A hand that does not move: it writes nothing, and the pointer stays where the opening put it. */
    private static final double[] STILL = new double[0];
    private static volatile double[] handOffset;
    private static double[] handTarget;
    private static boolean observing;
    private static int checks, failures, openings, moved, writesAfterHand;
    private static String label = "";

    private CursorWeightLab() {}

    /** From the probe mixin at the tail of {@code InventoryScreen#init}. */
    public static void onInventoryInit(final Minecraft mc) {
        double[] offset = handOffset;
        if (!observing || offset == null) return;
        handOffset = null;
        writesAfterHand = 0;
        moved++;
        if (offset[0] == 0 && offset[1] == 0) {
            // Writing back a position read right after a landing can restore the pre-move
            // position Windows still reports for a moment: that is the lab moving the
            // pointer, not a hand. A hand at rest writes nothing.
            handTarget = STILL;
            return;
        }
        double[] now = nativePointer(mc);
        handTarget = new double[] {now[0] + offset[0], now[1] + offset[1]};
        // In NORMAL cursor mode this moves the desktop pointer, the way a hand does.
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), handTarget[0], handTarget[1]);
    }

    /** From the cursor trace at every KoHs pointer write. */
    public static void onWarp() {
        if (observing && handTarget != null) writesAfterHand++;
    }

    /** From the cursor trace at the return of the opening finalizer. */
    public static void onOpened(final Minecraft mc, final Screen requested) {
        if (!observing || !(mc.screen instanceof InventoryScreen) || mc.screen != requested || handTarget == null) return;
        openings++;
        // Windows can report the pre-move position for a moment after SetCursorPos, so
        // the synchronous check is on what the opening did: it must not write the
        // pointer after the hand moved it. Where the pointer ends is checked a poll later.
        check(writesAfterHand == 0, "the opening does not write the pointer after the hand moved it; writes="
            + writesAfterHand + "; hand=" + fmt(handTarget) + "; native=" + fmt(nativePointer(mc)));
    }

    private static double[] nativePointer(final Minecraft mc) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1), y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(mc.getWindow().handle(), x, y);
            return new double[] {x.get(0), y.get(0)};
        }
    }

    private static boolean close(final double[] a, final double[] b) {
        return Math.abs(a[0] - b[0]) <= 1.0 && Math.abs(a[1] - b[1]) <= 1.0;
    }

    private static String fmt(final double[] p) {
        return p == null ? "none" : p.length < 2 ? "still" : Math.round(p[0]) + "," + Math.round(p[1]);
    }

    private static void check(final boolean passed, final String reason) {
        checks++;
        if (!passed) {
            failures++;
            DebugCollector.issue("CURSOR_WEIGHT_FAIL", label + "; " + reason);
        }
    }

    private static void tap(final Minecraft mc) {
        var key = ((KeyMappingDebugAccessor) mc.options.keyInventory).kohsInventoryDebug$getKey();
        if (key.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) throw new IllegalStateException("Keyboard lab only");
        var event = new KeyEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
        var input = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
        input.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
        input.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
    }

    private static void awaitInventory(final Minecraft mc, final boolean open) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean[] state = {false};
            mc.executeBlocking(() -> state[0] = mc.screen instanceof InventoryScreen);
            if (state[0] == open) return;
            Thread.sleep(2);
        }
        throw new IllegalStateException("Inventory " + (open ? "opening" : "closing") + " timed out: " + label);
    }

    private static void awaitGrabbed(final Minecraft mc) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean[] grabbed = {false};
            mc.executeBlocking(() -> grabbed[0] = mc.screen == null && mc.mouseHandler.isMouseGrabbed());
            if (grabbed[0]) return;
            Thread.sleep(2);
        }
        throw new IllegalStateException("Mouse was not captured again: " + label);
    }

    public static void run(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        InventoryTweaksConfig saved = ConfigStore.get().copy();
        int gui = mc.options.guiScale().get();
        boolean book = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
        int[] windowSize = {mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()};
        checks = failures = openings = moved = 0;
        double[][] offsets = {{3, 2}, {37, -23}, {-58, 41}, {211, 97}, {-160, -120}, {0, 0}};
        try {
            // Battery 1: a hand already moving while the inventory initializes.
            for (int cycle = 0; cycle < 48; cycle++) {
                int index = cycle;
                awaitGrabbed(mc);
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    var config = ConfigStore.get();
                    boolean center = index % 2 == 0;
                    boolean custom = (index / 2) % 3 == 2;
                    config.centerMouseFix = center;
                    config.inventory = custom ? new InventoryTweaksConfig.CursorPoint(0.23, 0.78) : null;
                    config.superFastInventory = (index / 6) % 2 == 0;
                    config.inventoryGuiScalerEnabled = true;
                    config.inventoryGuiScale = index % 3 == 0 ? 0.65 : index % 3 == 1 ? 2.0 : 3.15;
                    int guiScale = 2 + (index / 12) % 3;
                    mc.options.guiScale().set(guiScale);
                    mc.resizeDisplay();
                    mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, index >= 36);
                    double[] offset = offsets[index % offsets.length];
                    label = "cycle=" + index + "; centerMouseFix=" + center + "; customPoint=" + custom
                        + "; fast=" + config.superFastInventory + "; gui=" + guiScale + "; book=" + (index >= 36)
                        + "; hand=" + Math.round(offset[0]) + "," + Math.round(offset[1]);
                    handTarget = null;
                    handOffset = offset;
                    observing = true;
                    tap(mc);
                });
                awaitInventory(mc, true);
                Thread.sleep(25);
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    if (handTarget != null) {
                        double[] now = nativePointer(mc);
                        double[] expected = handTarget == STILL ? now : handTarget;
                        if (handTarget != STILL) {
                            check(close(now, handTarget), "later poll keeps the hand's position; native=" + fmt(now));
                        }
                        check(Math.abs(mc.mouseHandler.xpos() - expected[0]) <= 1.0
                                && Math.abs(mc.mouseHandler.ypos() - expected[1]) <= 1.0,
                            "Minecraft follows the hand; internal=" + Math.round(mc.mouseHandler.xpos())
                                + "," + Math.round(mc.mouseHandler.ypos()) + "; native=" + fmt(now));
                    } else {
                        check(false, "the probe never ran inside InventoryScreen#init");
                    }
                    observing = false;
                    DebugCollector.info("CURSOR_WEIGHT_CASE", label + "; cumulativeFailures=" + failures);
                    mc.screen.onClose();
                });
                awaitInventory(mc, false);
            }

            // Battery 2: a window resized while the mouse is captured leaves GLFW's
            // saved restore position at the old centre. Center Mouse Fix corrects it;
            // without it the pointer appears where Vanilla never meant it to be.
            for (int cycle = 0; cycle < 4; cycle++) {
                int index = cycle;
                awaitGrabbed(mc);
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    int width = windowSize[0] + (index % 2 == 0 ? 180 : -140);
                    int height = windowSize[1] + (index % 2 == 0 ? 100 : -80);
                    GLFW.glfwSetWindowSize(mc.getWindow().handle(), width, height);
                });
                Thread.sleep(400);
                awaitGrabbed(mc);
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    var config = ConfigStore.get();
                    config.centerMouseFix = index < 2;
                    config.inventory = null;
                    config.superFastInventory = true;
                    label = "stale-restore cycle=" + index + "; centerMouseFix=" + config.centerMouseFix
                        + "; window=" + mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight();
                    handTarget = null;
                    handOffset = null;
                    observing = false;
                    tap(mc);
                });
                awaitInventory(mc, true);
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    double[] centre = {mc.getWindow().getScreenWidth() / 2, mc.getWindow().getScreenHeight() / 2};
                    double[] now = nativePointer(mc);
                    boolean centred = close(now, centre);
                    if (ConfigStore.get().centerMouseFix) {
                        check(centred, "Center Mouse Fix lands on the current centre; centre=" + fmt(centre) + "; native=" + fmt(now));
                    }
                    DebugCollector.info("CURSOR_WEIGHT_STALE", label + "; centre=" + fmt(centre) + "; native=" + fmt(now)
                        + "; centred=" + centred);
                    mc.screen.onClose();
                });
                awaitInventory(mc, false);
                CloseHotbarRegressionLab.atPoll(mc, () -> GLFW.glfwSetWindowSize(mc.getWindow().handle(), windowSize[0], windowSize[1]));
                Thread.sleep(400);
            }
            DebugCollector.info("CURSOR_WEIGHT_SUMMARY", "openings=" + openings + "/48; handMoves=" + moved
                + "; checks=" + checks + "; failures=" + failures);
            if (openings != 48 || failures != 0) throw new IllegalStateException("Cursor weight failed: " + failures);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            mc.executeBlocking(() -> {
                observing = false;
                handOffset = null;
                if (mc.screen instanceof InventoryScreen) mc.screen.onClose();
                var config = ConfigStore.get();
                config.centerMouseFix = saved.centerMouseFix;
                config.superFastInventory = saved.superFastInventory;
                config.inventory = saved.inventory;
                config.inventoryGuiScalerEnabled = saved.inventoryGuiScalerEnabled;
                config.inventoryGuiScale = saved.inventoryGuiScale;
                mc.options.guiScale().set(gui);
                mc.resizeDisplay();
                if (mc.player != null) mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
            });
        }
    }
}
