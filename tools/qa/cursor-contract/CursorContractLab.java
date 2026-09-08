package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.cursor.CursorLandingController;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.MouseHandlerAccessor;
import java.nio.DoubleBuffer;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.RecipeBookType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/** QA fixture compiled only into the disposable 26.1.2 debugger, never the mod. */
public final class CursorContractLab {
    private static boolean observing;
    private static int checks, failures, openings, warps, mode;
    private static String label;
    private CursorContractLab() {}
    public static void onWarp() { if (observing) warps++; }

    // Synchronous observer: runs after the production finalizer, before render.
    public static void onOpened(Minecraft mc, Screen requested) {
        if (!observing || !(mc.screen instanceof InventoryScreen screen) || mc.screen != requested) return;
        openings++;
        double x = mc.getWindow().getScreenWidth() / 2;
        double y = mc.getWindow().getScreenHeight() / 2;
        if (mode == 1 || mode == 2) {
            var bounds = (AbstractContainerScreenAccessor) screen;
            var point = ConfigStore.get().inventory;
            double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
            x = (screen.width * 0.5 + (bounds.kohsInventoryTweaks$getLeftPos()
                + point.x() * (bounds.kohsInventoryTweaks$getImageWidth() - 1) - screen.width * 0.5) * scale)
                * mc.getWindow().getScreenWidth() / screen.width;
            y = (screen.height * 0.5 + (bounds.kohsInventoryTweaks$getTopPos()
                + point.y() * (bounds.kohsInventoryTweaks$getImageHeight() - 1) - screen.height * 0.5) * scale)
                * mc.getWindow().getScreenHeight() / screen.height;
        }
        checkPosition(mc, x, y, "synchronous landing");
        check(warps == (mode == 3 ? 0 : 1), "one finalizer; warps=" + warps);
        if (mode != 3) check(delta(mc, "accumulatedDX") == 0 && delta(mc, "accumulatedDY") == 0, "old camera deltas cleared");
    }

    private static double delta(Minecraft mc, String name) {
        try {
            var field = mc.mouseHandler.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getDouble(mc.mouseHandler);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) {
            failures++;
            DebugCollector.issue("CURSOR_CONTRACT_FAIL", label + "; " + reason);
        }
    }

    private static void checkPosition(Minecraft mc, double x, double y, String stage) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer px = stack.mallocDouble(1), py = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(mc.getWindow().handle(), px, py);
            check(Math.abs(px.get(0) - x) <= 1 && Math.abs(py.get(0) - y) <= 1
                && Math.abs(mc.mouseHandler.xpos() - x) <= 1 && Math.abs(mc.mouseHandler.ypos() - y) <= 1,
                stage + "; expected=" + x + "," + y + "; native=" + px.get(0) + "," + py.get(0)
                    + "; internal=" + mc.mouseHandler.xpos() + "," + mc.mouseHandler.ypos());
        }
    }

    private static void tap(Minecraft mc) {
        var key = ((KeyMappingDebugAccessor) mc.options.keyInventory).kohsInventoryDebug$getKey();
        if (key.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) throw new IllegalStateException("Keyboard lab only");
        var event = new KeyEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
        var input = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
        input.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
        input.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
    }

    private static void move(Minecraft mc, double x, double y) {
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), x, y);
        ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), x, y);
    }

    public static void run(Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        var saved = ConfigStore.get().copy();
        int gui = mc.options.guiScale().get();
        boolean book = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
        checks = failures = openings = 0;
        try {
            for (int cycle = 0; cycle < 48; cycle++) {
                int index = cycle;
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    var config = ConfigStore.get();
                    mode = index % 4;
                    config.centerMouseFix = mode < 2;
                    config.inventoryLandingItem = null;
                    config.inventory = mode == 1 || mode == 2
                        ? new InventoryTweaksConfig.CursorPoint(index % 2 == 0 ? 1.0 : 0.23, 0.78) : null;
                    config.superFastInventory = (index / 4) % 2 == 0;
                    config.inventoryGuiScalerEnabled = true;
                    config.inventoryGuiScale = index % 3 == 0 ? 0.65 : index % 3 == 1 ? 2.0 : 3.15;
                    int guiScale = 2 + (index / 8) % 3;
                    mc.options.guiScale().set(guiScale);
                    mc.resizeGui();
                    mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, index >= 24);
                    label = "cycle=" + index + "; mode=" + mode + "; fast=" + config.superFastInventory
                        + "; gui=" + guiScale + "; actualGui=" + mc.getWindow().getGuiScale()
                        + "; book=" + (index >= 24) + "; scale=" + config.inventoryGuiScale;
                    warps = 0;
                    observing = true;
                    ((MouseHandlerAccessor) mc.mouseHandler).kohsInventoryTweaks$setAccumulatedDX(1200);
                    ((MouseHandlerAccessor) mc.mouseHandler).kohsInventoryTweaks$setAccumulatedDY(-800);
                    tap(mc);
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline) {
                    boolean[] open = {false};
                    mc.executeBlocking(() -> open[0] = mc.screen instanceof InventoryScreen);
                    if (open[0]) break;
                    Thread.sleep(2);
                }
                for (int sample = 0; sample < 5; sample++) {
                    int n = sample;
                    CloseHotbarRegressionLab.atPoll(mc, () -> {
                        if (!(mc.screen instanceof InventoryScreen)) throw new IllegalStateException("Opening timed out");
                        double x = mc.getWindow().getScreenWidth() * (n % 2 == 0 ? 0.12 : 0.85);
                        double y = mc.getWindow().getScreenHeight() * (n % 2 == 0 ? 0.84 : 0.17);
                        move(mc, x, y);
                        checkPosition(mc, x, y, "same-callback free motion " + n);
                        check(CursorLandingController.overrideReleasePosition(mc) == null, "late release must not request another custom landing");
                    });
                    Thread.sleep(20);
                    CloseHotbarRegressionLab.atPoll(mc, () -> {
                        checkPosition(mc, mc.getWindow().getScreenWidth() * (n % 2 == 0 ? 0.12 : 0.85),
                            mc.getWindow().getScreenHeight() * (n % 2 == 0 ? 0.84 : 0.17), "later-poll free motion " + n);
                        check(warps == (mode == 3 ? 0 : 1), "no extra finalization");
                    });
                }
                CloseHotbarRegressionLab.atPoll(mc, () -> {
                    observing = false;
                    mc.screen.onClose();
                    DebugCollector.info("CURSOR_CONTRACT_CASE", label + "; cumulativeFailures=" + failures);
                });
            }
            DebugCollector.info("CURSOR_CONTRACT_SUMMARY", "openings=" + openings + "/48; checks=" + checks + "; failures=" + failures);
            if (openings != 48 || failures != 0) throw new IllegalStateException("Cursor contract failed: " + failures);
        } catch (Exception error) { throw new IllegalStateException(error); }
        finally {
            mc.executeBlocking(() -> {
                observing = false;
                if (mc.screen instanceof InventoryScreen) mc.screen.onClose();
                var config = ConfigStore.get();
                config.centerMouseFix = saved.centerMouseFix;
                config.superFastInventory = saved.superFastInventory;
                config.inventory = saved.inventory;
                config.inventoryLandingItem = saved.inventoryLandingItem;
                config.inventoryGuiScalerEnabled = saved.inventoryGuiScalerEnabled;
                config.inventoryGuiScale = saved.inventoryGuiScale;
                mc.options.guiScale().set(gui);
                mc.resizeGui();
                if (mc.player != null) mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
            });
        }
    }
}
