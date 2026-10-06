package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.screen.AdvancedSettingsScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.IssuesTrackerScreen;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * Records the mascot through the game's own input handlers, in two takes. The meal:
 * petting, its menu, five totems pulled from the inventory preview and eaten, fat
 * and heavy on the pointer, digested, and the disgust when no totem is left. The
 * body: a drop, a hard throw that splats against the edge of the window, a soft one
 * that bounces, and sleep. Frames go to {@code mascot-take} and {@code mascot-take-body};
 * stills of each moment and of other windows go next to the other captures.
 * Singleplayer lab world only; never the shipped mod.
 */
public final class MascotLab {
    private MascotLab() {}

    private static Field field(final String name) throws Exception {
        Field field = ZMascot.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(final String name) throws Exception {
        return field(name).get(null);
    }

    /** The middle of the mascot's head, in GUI coordinates. */
    private static double[] head() throws Exception {
        int left = (int) get("left");
        int top = (int) get("top");
        int unit = (int) get("unit");
        return new double[] {left + 10.0 * unit, top + 8.0 * unit};
    }

    private static void move(final Minecraft mc, final double guiX, final double guiY) throws Exception {
        mc.executeBlocking(() -> {
            var window = mc.getWindow();
            ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(window.handle(),
                guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
                guiY * window.getScreenHeight() / window.getGuiScaledHeight());
        });
    }

    /** Glides the pointer to a point over {@code millis}, one move per frame-ish step. */
    private static void glide(final Minecraft mc, final double[] from, final double[] to, final long millis) throws Exception {
        int steps = Math.max(1, (int) (millis / 16));
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            move(mc, from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t);
            Thread.sleep(16);
        }
    }

    private static void button(final Minecraft mc, final int button, final boolean down) {
        mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler)
            .kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(button, 0), down ? 1 : 0));
    }

    private static void click(final Minecraft mc, final int button) throws Exception {
        button(mc, button, true);
        Thread.sleep(40);
        button(mc, button, false);
    }

    /** Opens the mascot's menu and clicks one of its rows. */
    private static void menu(final Minecraft mc, final int row) throws Exception {
        double[] head = head();
        move(mc, head[0], head[1]);
        Thread.sleep(120);
        click(mc, 1);
        Thread.sleep(250);
        move(mc, (int) get("menuX") + 24, (int) get("menuY") + 3 + 14 * row + 7);
        Thread.sleep(200);
    }

    private static void still(final Minecraft mc, final String name) throws Exception {
        UiShowcaseLab.screenshotAfter(mc, "mascot-" + name, 30);
        FrameRecorder.mark(name);
    }

    private static void command(final Minecraft mc, final String command) throws Exception {
        mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
        Thread.sleep(120);
    }

    public static void run(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        int gui = mc.options.guiScale().get();
        ZMascot.Prefs prefs = ZMascot.prefs();
        try {
            prefs.enabled = true;
            prefs.size = 2;
            prefs.playful = true;
            prefs.sounds = true;
            prefs.place = 0.86;
            field("eatenTotems").setInt(null, 0);
            command(mc, "gamemode survival");
            command(mc, "clear @s");
            command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
            command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
            command(mc, "item replace entity @s hotbar.1 with minecraft:totem_of_undying");
            for (int slot = 0; slot < 5; slot++) {
                command(mc, "item replace entity @s inventory." + (slot * 2) + " with minecraft:totem_of_undying");
            }
            mc.executeBlocking(() -> {
                mc.gui.getChat().clearMessages(false);
                mc.getToastManager().clear();
                mc.options.guiScale().set(3);
                mc.resizeGui();
            });
            move(mc, 1, 1);
            mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
            Thread.sleep(1200);
            FrameRecorder.start(mc.gameDirectory.toPath().resolve("mascot-take"));
            Thread.sleep(500);
            still(mc, "wave");

            // Pointer to its head: a slow blink, then the hint.
            double[] head = head();
            glide(mc, new double[] {1, 1}, new double[] {head[0] - 120, head[1] - 40}, 500);
            glide(mc, new double[] {head[0] - 120, head[1] - 40}, head, 350);
            Thread.sleep(180);
            still(mc, "slow-blink");
            Thread.sleep(700);
            still(mc, "hover-hint");
            click(mc, 0);
            Thread.sleep(300);
            still(mc, "pet");
            Thread.sleep(800);
            for (int pet = 0; pet < 4; pet++) {
                click(mc, 0);
                Thread.sleep(140);
            }
            Thread.sleep(250);
            still(mc, "love");
            Thread.sleep(1700);

            // The menu, then five meals pulled from the preview.
            menu(mc, 0);
            still(mc, "menu");
            for (int meal = 1; meal <= 5; meal++) {
                click(mc, 0);
                if (meal == 1) {
                    Thread.sleep(220);
                    still(mc, "feed-cast");
                    Thread.sleep(560);
                    still(mc, "feed-pull");
                    Thread.sleep(750);
                    still(mc, "feed-eat");
                    Thread.sleep(1700);
                    still(mc, "feed-done");
                    Thread.sleep(1400);
                } else {
                    Thread.sleep(4600);
                }
                if (meal < 5) {
                    menu(mc, 0);
                }
            }
            Thread.sleep(500);
            still(mc, "fat");
            Thread.sleep(1500);

            // Heavy on the pointer.
            head = head();
            move(mc, head[0], head[1]);
            Thread.sleep(150);
            button(mc, 0, true);
            glide(mc, head, new double[] {head[0] - 200, head[1] - 120}, 700);
            Thread.sleep(80);
            still(mc, "fat-carried");
            Thread.sleep(400);
            button(mc, 0, false);
            Thread.sleep(700);
            still(mc, "fat-landed");
            Thread.sleep(1200);

            // Ten minutes later: digested.
            field("lastMealAt").setLong(null, System.nanoTime() - 11L * 60L * 1_000_000_000L);
            Thread.sleep(500);
            still(mc, "slim");
            Thread.sleep(1200);

            // No totem left: disgust, and the tip.
            command(mc, "clear @s minecraft:totem_of_undying");
            Thread.sleep(300);
            menu(mc, 0);
            click(mc, 0);
            Thread.sleep(450);
            still(mc, "disgust");
            Thread.sleep(2600);
            DebugCollector.info("MASCOT_TAKE", "frames=" + FrameRecorder.stop());

            body(mc);


            mc.executeBlocking(() -> mc.setScreen(new IssuesTrackerScreen(null)));
            Thread.sleep(900);
            UiShowcaseLab.screenshot(mc, "mascot-issues");
            mc.executeBlocking(() -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
                mc.setScreen(AdvancedSettingsScreen.playerGlow(null));
            });
            Thread.sleep(900);
            UiShowcaseLab.screenshot(mc, "mascot-player-visibility");
            DebugCollector.info("MASCOT_SUMMARY", "done");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            try {
                if (FrameRecorder.recording()) FrameRecorder.stop();
            } catch (Exception ignored) {
            }
            mc.executeBlocking(() -> {
                if (mc.screen != null) mc.setScreen(null);
                mc.options.guiScale().set(gui);
                mc.resizeGui();
            });
        }
    }

    /** The body take alone, with the mascot's own state logged at every step. */
    public static void runBody(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        int gui = mc.options.guiScale().get();
        try {
            ZMascot.prefs().enabled = true;
            ZMascot.prefs().place = 0.86;
            mc.executeBlocking(() -> {
                mc.options.guiScale().set(3);
                mc.resizeGui();
            });
            move(mc, 1, 1);
            mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
            Thread.sleep(1500);
            body(mc);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            try {
                if (FrameRecorder.recording()) FrameRecorder.stop();
            } catch (Exception ignored) {
            }
            mc.executeBlocking(() -> {
                if (mc.screen != null) mc.setScreen(null);
                mc.options.guiScale().set(gui);
                mc.resizeGui();
            });
        }
    }

    private static void state(final String label) throws Exception {
        DebugCollector.info("MASCOT_STATE", label + "; body=" + get("body") + "; pressed=" + get("pressed")
            + "; carrying=" + get("carrying") + "; mood=" + get("mood") + "; x=" + get("bodyX") + "; y=" + get("bodyY")
            + "; vx=" + get("velX") + "; vy=" + get("velY"));
    }

    /** A drop, a splat against the left edge, a soft throw, and sleep. */
    private static void body(final Minecraft mc) throws Exception {
            // The body: a drop, a splat, a bounce.
            FrameRecorder.start(mc.gameDirectory.toPath().resolve("mascot-take-body"));
            double[] head = head();
            move(mc, head[0], head[1]);
            Thread.sleep(150);
            state("before press");
            button(mc, 0, true);
            Thread.sleep(40);
            state("pressed");
            glide(mc, head, new double[] {head[0] - 60, head[1] - 170}, 600);
            Thread.sleep(400);
            state("dragged");
            still(mc, "carried");
            button(mc, 0, false);
            Thread.sleep(160);
            still(mc, "falling");
            Thread.sleep(1500);
            still(mc, "dropped");
            Thread.sleep(800);

            head = head();
            move(mc, head[0], head[1]);
            Thread.sleep(150);
            button(mc, 0, true);
            glide(mc, head, new double[] {head[0] - 40, head[1] - 140}, 400);
            Thread.sleep(250);
            // A hard flick to the left edge.
            double[] from = {head[0] - 40, head[1] - 140};
            glide(mc, from, new double[] {from[0] - 260, from[1] - 30}, 64);
            button(mc, 0, false);
            state("thrown");
            Thread.sleep(260);
            still(mc, "splat");
            Thread.sleep(1600);
            still(mc, "after-splat");
            Thread.sleep(1500);

            head = head();
            move(mc, head[0], head[1]);
            Thread.sleep(150);
            button(mc, 0, true);
            glide(mc, head, new double[] {head[0] + 30, head[1] - 120}, 350);
            Thread.sleep(250);
            from = new double[] {head[0] + 30, head[1] - 120};
            glide(mc, from, new double[] {from[0] + 110, from[1] - 40}, 160);
            button(mc, 0, false);
            Thread.sleep(500);
            still(mc, "throw-soft");
            Thread.sleep(2200);

            mc.executeBlocking(() -> ZMascot.react(ZMascot.Mood.SLEEP));
            Thread.sleep(3000);
            still(mc, "sleep");
            DebugCollector.info("MASCOT_TAKE_BODY", "frames=" + FrameRecorder.stop());
    }
}
