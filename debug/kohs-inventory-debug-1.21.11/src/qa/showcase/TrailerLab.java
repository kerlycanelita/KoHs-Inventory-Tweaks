package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.screen.GuiScalerScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.KohsScreen;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Records the 1.2.0 trailer: one full-size streamed take at 30 frames a second, in scenes the
 * editor cuts, captions and zooms. Every pointer move, the mascot's place and Zymekoh's face are
 * marked in the take so the editor can draw the pointer, which the frames do not hold, and
 * follow the mascot. Every sound the game plays is marked by the sound trace, so the edit gets
 * the take's own sounds in step. Input goes through the game's own handlers only; the player's
 * real pointer is never moved. Singleplayer lab world only; never the shipped mod.
 */
public final class TrailerLab {
    private static volatile boolean sampling;

    private TrailerLab() {}

    private static Field mascot(final String name) throws Exception {
        Field field = ZMascot.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(final String name) throws Exception {
        return mascot(name).get(null);
    }

    private static double[] head() throws Exception {
        int left = (int) get("left");
        int top = (int) get("top");
        int unit = (int) get("unit");
        return new double[] {left + 10.0 * unit, top + 8.0 * unit};
    }

    private static double[] at = {1, 1};

    /** Glides onto the mascot's head and keeps on it until it holds still there, so a press lands on it. */
    private static double[] toCat(final Minecraft mc, final long millis) throws Exception {
        double[] head = head();
        glide(mc, head[0], head[1], millis);
        for (int settle = 0; settle < 4; settle++) {
            Thread.sleep(70);
            head = head();
            move(mc, head[0], head[1]);
        }
        Thread.sleep(90);
        return head();
    }

    private static void move(final Minecraft mc, final double guiX, final double guiY) throws Exception {
        at = new double[] {guiX, guiY};
        FrameRecorder.mark("ptr " + guiX + " " + guiY);
        mc.executeBlocking(() -> {
            var window = mc.getWindow();
            ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(window.handle(),
                guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
                guiY * window.getScreenHeight() / window.getGuiScaledHeight());
        });
    }

    /** Glides the pointer to a point over {@code millis}, eased, a move per frame. */
    private static void glide(final Minecraft mc, final double toX, final double toY, final long millis) throws Exception {
        double[] from = at;
        int steps = Math.max(1, (int) (millis / 16));
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            double eased = t * t * (3.0 - 2.0 * t);
            move(mc, from[0] + (toX - from[0]) * eased, from[1] + (toY - from[1]) * eased);
            Thread.sleep(16);
        }
    }

    /** A straight, unhurried glide: for drags and flicks, where easing would bend the speed. */
    private static void sweep(final Minecraft mc, final double toX, final double toY, final long millis) throws Exception {
        double[] from = at;
        int steps = Math.max(1, (int) (millis / 16));
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            move(mc, from[0] + (toX - from[0]) * t, from[1] + (toY - from[1]) * t);
            Thread.sleep(16);
        }
    }

    private static void button(final Minecraft mc, final int button, final boolean down) {
        FrameRecorder.mark("btn " + button + " " + (down ? 1 : 0));
        mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler)
            .kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(button, 0), down ? 1 : 0));
    }

    private static void click(final Minecraft mc, final int button) throws Exception {
        button(mc, button, true);
        Thread.sleep(60);
        button(mc, button, false);
    }

    private static AbstractWidget widget(final Minecraft mc, final String label) throws Exception {
        AbstractWidget[] found = new AbstractWidget[1];
        mc.executeBlocking(() -> {
            for (var child : mc.screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(label)) {
                    found[0] = widget;
                    return;
                }
            }
        });
        return found[0];
    }

    /** Glides to the widget with this text and clicks it, as a player would. */
    private static boolean press(final Minecraft mc, final String label, final long millis) throws Exception {
        AbstractWidget target = widget(mc, label);
        if (target == null) {
            DebugCollector.info("TRAILER", "no widget " + label);
            return false;
        }
        glide(mc, target.getX() + target.getWidth() * 0.5, target.getY() + target.getHeight() * 0.5, millis);
        Thread.sleep(160);
        click(mc, 0);
        return true;
    }

    private static boolean pressKey(final Minecraft mc, final String key, final long millis) throws Exception {
        return press(mc, Component.translatable(key).getString(), millis);
    }

    private static void scene(final String name) {
        FrameRecorder.mark("scene " + name);
        DebugCollector.info("TRAILER_SCENE", name);
    }

    private static void command(final Minecraft mc, final String command) throws Exception {
        mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
        Thread.sleep(110);
    }

    private static void menu(final Minecraft mc, final int row) throws Exception {
        double[] head = toCat(mc, 380);
        click(mc, 1);
        Thread.sleep(380);
        glide(mc, (int) get("menuX") + 26, (int) get("menuY") + 3 + 14 * row + 7, 300);
        Thread.sleep(260);
    }

    /** Marks where the mascot and Zymekoh's face are, about thirty times a second, while the take runs. */
    private static void startSampler(final Minecraft mc) {
        sampling = true;
        Thread.ofPlatform().daemon(true).name("KoHs trailer sampler").start(() -> {
            while (sampling) {
                try {
                    FrameRecorder.mark("cat " + get("left") + " " + get("top") + " " + get("unit") + " " + get("body"));
                    if (mc.screen instanceof KohsScreen kohs) {
                        Field x = KohsScreen.class.getDeclaredField("faceShownX");
                        Field y = KohsScreen.class.getDeclaredField("faceShownY");
                        Field anger = KohsScreen.class.getDeclaredField("anger");
                        x.setAccessible(true);
                        y.setAccessible(true);
                        anger.setAccessible(true);
                        FrameRecorder.mark("face " + x.getFloat(kohs) + " " + y.getFloat(kohs) + " " + anger.getFloat(kohs));
                    }
                    Thread.sleep(33);
                } catch (Exception ignored) {
                    return;
                }
            }
        });
    }

    public static void run(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        int gui = mc.options.guiScale().get();
        int fps = mc.options.framerateLimit().get();
        InactivityFpsLimit inactivity = mc.options.inactivityFpsLimit().get();
        boolean hud = mc.options.hideGui;
        InventoryTweaksConfig saved = ConfigStore.get().copy();
        ZMascot.Prefs prefs = ZMascot.prefs();
        double place = prefs.place;
        try {
            prefs.enabled = true;
            prefs.size = 2;
            prefs.playful = true;
            prefs.sounds = true;
            prefs.place = 0.84;
            mascot("eatenTotems").setInt(null, 0);
            command(mc, "gamemode survival");
            command(mc, "time set 6000");
            command(mc, "weather clear");
            command(mc, "clear @s");
            String[] kit = {
                "weapon.offhand totem_of_undying", "hotbar.0 netherite_sword", "hotbar.1 end_crystal 64",
                "hotbar.2 obsidian 64", "hotbar.3 respawn_anchor 16", "hotbar.4 glowstone 64", "hotbar.5 golden_apple 32",
                "hotbar.6 ender_pearl 16", "hotbar.7 totem_of_undying", "hotbar.8 totem_of_undying",
                "inventory.0 totem_of_undying", "inventory.2 totem_of_undying", "inventory.4 totem_of_undying",
                "inventory.6 totem_of_undying", "inventory.9 netherite_pickaxe", "inventory.13 experience_bottle 64",
                "armor.head netherite_helmet", "armor.chest netherite_chestplate", "armor.legs netherite_leggings",
                "armor.feet netherite_boots"};
            for (String item : kit) {
                String[] parts = item.split(" ", 2);
                command(mc, "item replace entity @s " + parts[0] + " with minecraft:" + parts[1]);
            }
            mc.executeBlocking(() -> {
                ConfigStore.get().inventoryGuiScalerEnabled = true;
                ConfigStore.get().inventoryGuiScale = 2.0;
                ConfigStore.get().pixelPerfectScale = true;
                ConfigStore.get().superFastInventory = true;
                ConfigStore.get().shortcutsFollowPointer = true;
                ConfigStore.get().reduceInventoryMotion = false;
                ConfigStore.get().guiScalerWarningDismissed = true;
                mc.gui.getChat().clearMessages(false);
                mc.getToastManager().clear();
                mc.options.hideGui = true;
                mc.options.guiScale().set(3);
                mc.options.framerateLimit().set(30);
                mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
                mc.resizeDisplay();
            });
            move(mc, 1, 1);
            Thread.sleep(1500);
            int width = mc.getMainRenderTarget().width;
            int height = mc.getMainRenderTarget().height;
            FrameRecorder.startVideo(mc.gameDirectory.toPath().resolve("trailer-take"), System.getProperty("kohs.ffmpeg", "ffmpeg"), width, height);
            FrameRecorder.mark("meta " + mc.getWindow().getWidth() + " " + mc.getWindow().getHeight() + " " + mc.getWindow().getGuiScale());
            startSampler(mc);
            Thread.sleep(300);

            // 1. The main menu opens; the mascot waves.
            scene("intro");
            mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
            Thread.sleep(400);
            move(mc, 470, 300);
            Thread.sleep(800);
            glide(mc, 330, 190, 1600);
            Thread.sleep(1800);

            // 2. Every tab's icon wakes under the pointer.
            scene("icons");
            String[] tabs = {"screen.kohs_inventory_tweaks.cursor_landing", "screen.kohs_inventory_tweaks.inventory_tweaks",
                "screen.kohs_inventory_tweaks.issues_tracker", "screen.kohs_inventory_tweaks.kohs",
                "screen.kohs_inventory_tweaks.customization", "screen.kohs_inventory_tweaks.item_highlighter",
                "screen.kohs_inventory_tweaks.gui_scaler"};
            for (String key : tabs) {
                AbstractWidget tab = widget(mc, Component.translatable(key).getString());
                if (tab != null) {
                    glide(mc, tab.getX() + 18, tab.getY() + tab.getHeight() * 0.5, 380);
                    Thread.sleep(620);
                }
            }

            // 3. The Inventory Tweaks page, a tree: a sub-option sleeps with its parent.
            scene("tree");
            pressKey(mc, "screen.kohs_inventory_tweaks.inventory_tweaks", 450);
            Thread.sleep(900);
            List<?> switches = (List<?>) field(mc.screen, "tweakScrollingWidgets");
            AbstractWidget fastSwitch = (AbstractWidget) switches.get(0);
            AbstractWidget pointerSwitch = (AbstractWidget) switches.get(1);
            glide(mc, fastSwitch.getX() - 180, fastSwitch.getY() + 12, 500);
            Thread.sleep(1300);
            glide(mc, pointerSwitch.getX() - 180, pointerSwitch.getY() + 12, 400);
            Thread.sleep(1200);
            glide(mc, fastSwitch.getX() + fastSwitch.getWidth() * 0.5, fastSwitch.getY() + 12, 400);
            Thread.sleep(200);
            click(mc, 0);
            Thread.sleep(1400);
            click(mc, 0);
            Thread.sleep(1300);
            mc.executeBlocking(() -> mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
            Thread.sleep(700);

            // 4. The GUI Scaler page: pixel-perfect scale.
            scene("scaler");
            pressKey(mc, "screen.kohs_inventory_tweaks.gui_scaler", 450);
            Thread.sleep(900);
            AbstractWidget pixel = widget(mc, Component.translatable("screen.kohs_inventory_tweaks.pixel_perfect_scale.short").getString());
            if (pixel != null) {
                glide(mc, pixel.getX() + pixel.getWidth() * 0.5, pixel.getY() + pixel.getHeight() * 0.5, 500);
                Thread.sleep(1100);
                click(mc, 0);
                Thread.sleep(1100);
                click(mc, 0);
                Thread.sleep(900);
            }
            mc.executeBlocking(() -> mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
            Thread.sleep(500);

            // 5. In the inventory, the slot that lights up is the slot a click takes.
            scene("agreement");
            mc.executeBlocking(() -> mc.setScreen(new InventoryScreen(mc.player)));
            Thread.sleep(600);
            double[] slotA = slotCenter(mc, 9);
            double[] slotB = slotCenter(mc, 17);
            double[] slotC = slotCenter(mc, 35);
            move(mc, slotA[0] - 30, slotA[1] - 30);
            Thread.sleep(300);
            sweep(mc, slotA[0], slotA[1], 300);
            sweep(mc, slotB[0], slotB[1], 2200);
            sweep(mc, slotC[0], slotC[1], 1200);
            Thread.sleep(500);
            mc.executeBlocking(() -> mc.setScreen(null));
            Thread.sleep(300);

            // 6. The mascot: pets, love, its menu, two meals, fat and heavy, digested, and no totems left.
            scene("mascot");
            mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
            Thread.sleep(900);
            double[] head = head();
            glide(mc, head[0] - 90, head[1] - 50, 500);
            head = toCat(mc, 420);
            Thread.sleep(800);
            toCat(mc, 60);
            click(mc, 0);
            Thread.sleep(900);
            for (int pet = 0; pet < 4; pet++) {
                toCat(mc, 40);
                click(mc, 0);
            }
            Thread.sleep(1500);
            scene("feed");
            menu(mc, 0);
            click(mc, 0);
            Thread.sleep(4700);
            mascot("eatenTotems").setInt(null, 4);
            scene("fat");
            menu(mc, 0);
            click(mc, 0);
            Thread.sleep(5200);
            head = toCat(mc, 300);
            button(mc, 0, true);
            Thread.sleep(60);
            sweep(mc, head[0] - 120, head[1] - 90, 700);
            sweep(mc, head[0] - 40, head[1] - 140, 600);
            Thread.sleep(500);
            button(mc, 0, false);
            Thread.sleep(1500);
            mascot("lastMealAt").setLong(null, System.nanoTime() - 11L * 60L * 1_000_000_000L);
            Thread.sleep(1500);
            scene("disgust");
            command(mc, "clear @s minecraft:totem_of_undying");
            Thread.sleep(200);
            menu(mc, 0);
            click(mc, 0);
            Thread.sleep(3000);

            // 7. Dropped, thrown hard against the edge, thrown softly.
            scene("throw");
            head = toCat(mc, 350);
            button(mc, 0, true);
            Thread.sleep(60);
            sweep(mc, head[0] - 30, head[1] - 150, 650);
            Thread.sleep(450);
            button(mc, 0, false);
            Thread.sleep(1500);
            head = toCat(mc, 350);
            button(mc, 0, true);
            Thread.sleep(60);
            sweep(mc, head[0] - 20, head[1] - 120, 420);
            Thread.sleep(250);
            sweep(mc, at[0] - 260, at[1] - 30, 64);
            button(mc, 0, false);
            scene("splat");
            Thread.sleep(2600);
            head = toCat(mc, 350);
            button(mc, 0, true);
            Thread.sleep(60);
            sweep(mc, head[0] + 30, head[1] - 110, 380);
            Thread.sleep(250);
            sweep(mc, at[0] + 110, at[1] - 40, 160);
            button(mc, 0, false);
            Thread.sleep(2300);

            // 8. The KoHs tab: Zymekoh, cross when the mascot is brought to her face.
            scene("kohs");
            mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
            Thread.sleep(500);
            pressKey(mc, "screen.kohs_inventory_tweaks.kohs", 500);
            Thread.sleep(2400);
            Field faceX = KohsScreen.class.getDeclaredField("faceShownX");
            Field faceY = KohsScreen.class.getDeclaredField("faceShownY");
            faceX.setAccessible(true);
            faceY.setAccessible(true);
            float fx = faceX.getFloat(mc.screen);
            float fy = faceY.getFloat(mc.screen);
            glide(mc, fx, fy, 500);
            Thread.sleep(300);
            click(mc, 0);
            Thread.sleep(1300);
            scene("cross");
            head = toCat(mc, 600);
            button(mc, 0, true);
            Thread.sleep(60);
            sweep(mc, fx + 30, fy + 26, 1300);
            Thread.sleep(700);
            sweep(mc, fx - 24, fy + 30, 500);
            Thread.sleep(900);
            sweep(mc, fx + 18, fy + 22, 400);
            Thread.sleep(800);
            sweep(mc, fx + 160, fy + 20, 700);
            Thread.sleep(1200);
            button(mc, 0, false);
            Thread.sleep(1800);

            // 9. Back to the menu: it falls asleep.
            scene("outro");
            mc.executeBlocking(() -> mc.setScreen(new InventoryTweaksScreen(null)));
            Thread.sleep(700);
            glide(mc, 330, 120, 800);
            mc.executeBlocking(() -> ZMascot.react(ZMascot.Mood.SLEEP));
            Thread.sleep(3800);
            scene("end");
            sampling = false;
            DebugCollector.info("TRAILER_TAKE", "frames=" + FrameRecorder.stop());
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            sampling = false;
            try {
                if (FrameRecorder.recording()) FrameRecorder.stop();
            } catch (Exception ignored) {
            }
            prefs.place = place;
            mc.executeBlocking(() -> {
                if (mc.screen != null) mc.setScreen(null);
                ConfigStore.replaceAndSave(saved);
                mc.options.hideGui = hud;
                mc.options.guiScale().set(gui);
                mc.options.framerateLimit().set(fps);
                mc.options.inactivityFpsLimit().set(inactivity);
                mc.resizeDisplay();
            });
        }
    }

    private static Object field(final Object owner, final String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    /** The GUI point at the centre of a menu slot of the open inventory, through its surface scale. */
    private static double[] slotCenter(final Minecraft mc, final int index) throws Exception {
        double[] result = new double[2];
        mc.executeBlocking(() -> {
            var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
            var access = (dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor) screen;
            var slot = screen.getMenu().getSlot(index);
            double scale = dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
            double x = access.kohsInventoryTweaks$getLeftPos() + slot.x + 8;
            double y = access.kohsInventoryTweaks$getTopPos() + slot.y + 8;
            result[0] = screen.width * 0.5 + (x - screen.width * 0.5) * scale;
            result[1] = screen.height * 0.5 + (y - screen.height * 0.5) * scale;
        });
        return result;
    }
}
