package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.KeyMappingDebugAccessor;
import dev.zymekoh.kohsinventorydebug.mixin.KeyboardHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.nio.DoubleBuffer;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * Scripted A/B takes for the mod page and the in-game previews: each feature is
 * recorded off and on with the same input, in the same scene, so the only change
 * between the two is the feature. Singleplayer lab world only.
 */
public final class RecordingLab {
    private static final int PURPLE = 0xFFB86BFF;
    private static final int GREY = 0xFF8C8398;
    private static volatile CountDownLatch pressed;
    /** 0 idle, 1 waiting for a tick to end, 2 press on the next poll. Render thread only past 1. */
    private static volatile int pressState;

    private RecordingLab() {}

    /** From the tick-end hook, on the render thread. */
    public static void onTickEnd() {
        if (pressState == 1) pressState = 2;
    }

    /** From the input-poll hook, on the render thread, before the fast-open decision. */
    public static void onPoll(final Minecraft mc) {
        if (pressState != 2) return;
        pressState = 0;
        RecordingOverlay.pressed();
        tapInventory(mc);
        CountDownLatch latch = pressed;
        if (latch != null) latch.countDown();
    }

    private static void command(final Minecraft mc, final String command) throws Exception {
        mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
        Thread.sleep(100);
    }

    private static void tapInventory(final Minecraft mc) {
        var key = ((KeyMappingDebugAccessor) mc.options.keyInventory).kohsInventoryDebug$getKey();
        var event = new KeyEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
        var input = (KeyboardHandlerDebugInvoker) mc.keyboardHandler;
        input.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
        input.kohsInventoryDebug$invokeKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
    }

    private static void await(final Minecraft mc, final boolean open) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean[] state = {false};
            mc.executeBlocking(() -> state[0] = open
                ? mc.screen instanceof InventoryScreen
                : mc.screen == null && mc.mouseHandler.isMouseGrabbed());
            if (state[0]) return;
            Thread.sleep(2);
        }
        throw new IllegalStateException("Inventory " + (open ? "did not open" : "did not close"));
    }

    private static void closeInventory(final Minecraft mc) throws Exception {
        CloseHotbarRegressionLab.atPoll(mc, () -> {
            if (mc.screen != null) mc.screen.onClose();
        });
        await(mc, false);
    }

    /**
     * Presses the inventory key on the first poll after a client tick ends, armed and
     * fired on the render thread so no thread wake-up can shift it into another frame.
     * Vanilla then waits for the next tick; the press lands one frame after the last.
     */
    private static void pressAfterTick(final Minecraft mc) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        pressed = latch;
        pressState = 1;
        if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Press never fired");
        pressed = null;
    }

    private static void prepareScene(final Minecraft mc) throws Exception {
        command(mc, "gamemode survival");
        command(mc, "difficulty peaceful");
        command(mc, "time set 6000");
        command(mc, "weather clear");
        command(mc, "clear @s");
        command(mc, "item replace entity @s hotbar.0 with minecraft:diamond_sword");
        command(mc, "item replace entity @s hotbar.1 with minecraft:end_crystal 64");
        command(mc, "item replace entity @s hotbar.2 with minecraft:obsidian 64");
        command(mc, "item replace entity @s hotbar.3 with minecraft:respawn_anchor 16");
        command(mc, "item replace entity @s hotbar.4 with minecraft:glowstone 64");
        command(mc, "item replace entity @s hotbar.5 with minecraft:enchanted_golden_apple 8");
        command(mc, "item replace entity @s hotbar.6 with minecraft:experience_bottle 64");
        command(mc, "item replace entity @s hotbar.8 with minecraft:totem_of_undying");
        command(mc, "item replace entity @s inventory.4 with minecraft:totem_of_undying");
        command(mc, "item replace entity @s inventory.13 with minecraft:enchanted_book");
        command(mc, "item replace entity @s inventory.14 with minecraft:nether_star");
        command(mc, "item replace entity @s inventory.22 with minecraft:experience_bottle 32");
        command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
        command(mc, "tp @s ~ ~ ~ 180 4");
        Thread.sleep(600);
    }

    /** Nothing on screen but the feature: no chat backlog, no advancement or recipe toasts. */
    private static void quietHud(final Minecraft mc) throws Exception {
        mc.executeBlocking(() -> {
            mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
        });
        Thread.sleep(120);
    }

    private static Path takeDirectory(final Minecraft mc, final String name) {
        return mc.gameDirectory.toPath().resolve("recordings").resolve(name);
    }

    private static double[] nativePointer(final Minecraft mc) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1), y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(mc.getWindow().handle(), x, y);
            return new double[] {x.get(0), y.get(0)};
        }
    }

    /** A hand moving toward the inventory totem: smooth steps, as a real mouse sends them. */
    private static void moveToTotem(final Minecraft mc) throws Exception {
        double[] target = new double[2];
        mc.executeBlocking(() -> {
            if (!(mc.screen instanceof InventoryScreen screen)) return;
            var access = (AbstractContainerScreenAccessor) screen;
            for (Slot slot : screen.getMenu().slots) {
                if (slot.getItem().is(Items.TOTEM_OF_UNDYING) && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36) {
                    double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
                    double gx = screen.width * 0.5 + (access.kohsInventoryTweaks$getLeftPos() + slot.x + 8 - screen.width * 0.5) * scale;
                    double gy = screen.height * 0.5 + (access.kohsInventoryTweaks$getTopPos() + slot.y + 8 - screen.height * 0.5) * scale;
                    target[0] = gx * mc.getWindow().getScreenWidth() / screen.width;
                    target[1] = gy * mc.getWindow().getScreenHeight() / screen.height;
                    return;
                }
            }
        });
        if (target[0] == 0 && target[1] == 0) return;
        double[] from = new double[2];
        mc.executeBlocking(() -> {
            double[] now = nativePointer(mc);
            from[0] = now[0];
            from[1] = now[1];
        });
        int steps = 24;
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            double eased = 1 - Math.pow(1 - t, 3);
            double x = from[0] + (target[0] - from[0]) * eased;
            double y = from[1] + (target[1] - from[1]) * eased;
            mc.executeBlocking(() -> GLFW.glfwSetCursorPos(mc.getWindow().handle(), x, y));
            Thread.sleep(14);
        }
    }

    private static void fastInventory(final Minecraft mc) throws Exception {
        for (boolean on : new boolean[] {false, true}) {
            mc.executeBlocking(() -> {
                var config = ConfigStore.get();
                config.superFastInventory = on;
                config.reduceInventoryMotion = false;
                config.centerMouseFix = true;
            });
            await(mc, false);
            Thread.sleep(300);
            quietHud(mc);
            RecordingOverlay.begin(on ? "Super Fast Inventory: ON" : "Vanilla (Super Fast Inventory off)", on ? PURPLE : GREY, false, "E");
            FrameRecorder.start(takeDirectory(mc, "fast-" + (on ? "on" : "off")));
            Thread.sleep(450);
            pressAfterTick(mc);
            await(mc, true);
            RecordingOverlay.opened();
            Thread.sleep(900);
            closeInventory(mc);
            Thread.sleep(350);
            DebugCollector.info("RECORDING_TAKE", "fast-" + (on ? "on" : "off") + "; frames=" + FrameRecorder.stop());
            RecordingOverlay.end();
        }
    }

    private static void centerMouse(final Minecraft mc) throws Exception {
        int[] size = new int[2];
        mc.executeBlocking(() -> {
            size[0] = mc.getWindow().getScreenWidth();
            size[1] = mc.getWindow().getScreenHeight();
        });
        for (boolean on : new boolean[] {false, true}) {
            mc.executeBlocking(() -> {
                var config = ConfigStore.get();
                config.centerMouseFix = on;
                config.superFastInventory = true;
                config.inventory = null;
                config.inventoryLandingItem = null;
            });
            await(mc, false);
            // A window resized while playing leaves GLFW restoring the pointer to the old centre.
            CloseHotbarRegressionLab.atPoll(mc, () -> GLFW.glfwSetWindowSize(mc.getWindow().handle(), size[0] + 200, size[1] + 120));
            Thread.sleep(900);
            await(mc, false);
            quietHud(mc);
            RecordingOverlay.begin(on ? "Center Mouse Fix: ON" : "Vanilla (Center Mouse Fix off)", on ? PURPLE : GREY, true, "E");
            FrameRecorder.start(takeDirectory(mc, "center-" + (on ? "on" : "off")));
            Thread.sleep(450);
            pressAfterTick(mc);
            await(mc, true);
            RecordingOverlay.opened();
            Thread.sleep(900);
            moveToTotem(mc);
            Thread.sleep(700);
            closeInventory(mc);
            Thread.sleep(300);
            DebugCollector.info("RECORDING_TAKE", "center-" + (on ? "on" : "off") + "; frames=" + FrameRecorder.stop());
            RecordingOverlay.end();
            CloseHotbarRegressionLab.atPoll(mc, () -> GLFW.glfwSetWindowSize(mc.getWindow().handle(), size[0], size[1]));
            Thread.sleep(900);
        }
    }

    private static void animations(final Minecraft mc) throws Exception {
        mc.executeBlocking(() -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true));
        for (boolean on : new boolean[] {false, true}) {
            mc.executeBlocking(() -> {
                var config = ConfigStore.get();
                config.reduceInventoryMotion = on;
                config.superFastInventory = true;
            });
            await(mc, false);
            CloseHotbarRegressionLab.atPoll(mc, () -> tapInventory(mc));
            await(mc, true);
            Thread.sleep(400);
            quietHud(mc);
            RecordingOverlay.begin(on ? "Remove inventory animations: ON" : "Vanilla animations", on ? PURPLE : GREY, false, "E");
            FrameRecorder.start(takeDirectory(mc, "animations-" + (on ? "on" : "off")));
            Thread.sleep(2600);
            DebugCollector.info("RECORDING_TAKE", "animations-" + (on ? "on" : "off") + "; frames=" + FrameRecorder.stop());
            RecordingOverlay.end();
            closeInventory(mc);
            Thread.sleep(300);
        }
    }

    /** The first opening of a session loads classes; keep that out of every take. */
    private static void warmInventory(final Minecraft mc) throws Exception {
        await(mc, false);
        CloseHotbarRegressionLab.atPoll(mc, () -> tapInventory(mc));
        await(mc, true);
        Thread.sleep(300);
        closeInventory(mc);
        Thread.sleep(300);
    }

    public static void run(final Minecraft mc, final String clip) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        InventoryTweaksConfig saved = ConfigStore.get().copy();
        int gui = mc.options.guiScale().get();
        int fps = mc.options.framerateLimit().get();
        boolean book = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
        try {
            mc.executeBlocking(() -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
                mc.options.framerateLimit().set(60);
                var config = ConfigStore.get();
                config.inventoryGuiScalerEnabled = false;
                config.visiblePlayerGlowEnabled = false;
            });
            prepareScene(mc);
            warmInventory(mc);
            if (clip.equals("all") || clip.equals("fast")) fastInventory(mc);
            if (clip.equals("all") || clip.equals("center")) centerMouse(mc);
            if (clip.equals("all") || clip.equals("animations")) animations(mc);
            DebugCollector.info("RECORDING_SUMMARY", "clip=" + clip + "; done");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            RecordingOverlay.end();
            try {
                FrameRecorder.stop();
            } catch (Exception ignored) {
            }
            mc.executeBlocking(() -> {
                if (mc.screen != null) mc.setScreen(null);
                var config = ConfigStore.get();
                config.superFastInventory = saved.superFastInventory;
                config.centerMouseFix = saved.centerMouseFix;
                config.reduceInventoryMotion = saved.reduceInventoryMotion;
                config.inventory = saved.inventory;
                config.inventoryLandingItem = saved.inventoryLandingItem;
                config.inventoryGuiScalerEnabled = saved.inventoryGuiScalerEnabled;
                config.visiblePlayerGlowEnabled = saved.visiblePlayerGlowEnabled;
                mc.options.guiScale().set(gui);
                mc.options.framerateLimit().set(fps);
                mc.resizeGui();
                if (mc.player != null) mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, book);
            });
        }
    }
}
