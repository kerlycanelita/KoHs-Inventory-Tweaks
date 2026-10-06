package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.screen.AdvancedSettingsScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import dev.zymekoh.kohsinventorytweaks.screen.KohsScreen;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Builds a small scene and captures the screens and effects that need eyes on them:
 * the Player Visibility editor and main menu at GUI scales 2, 3 and 4, and the
 * silhouette glow on a visible mannequin next to one hidden behind a wall. {@link #media}
 * captures the page media used by the README and Modrinth.
 * Singleplayer lab world only; never the shipped mod.
 */
public final class UiShowcaseLab {
    private UiShowcaseLab() {}

    private static void command(final Minecraft mc, final String command) throws Exception {
        mc.executeBlocking(() -> mc.player.connection.sendCommand(command));
        Thread.sleep(120);
    }

    static void screenshot(final Minecraft mc, final String name) throws Exception {
        Thread.sleep(450);
        CompletableFuture<Void> saved = new CompletableFuture<>();
        mc.executeBlocking(() -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
            message -> saved.complete(null)));
        saved.get(10, TimeUnit.SECONDS);
        DebugCollector.info("UI_SHOWCASE_CAPTURE", name + ".png");
    }

    private static void guiScale(final Minecraft mc, final int scale) {
        mc.executeBlocking(() -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
    }

    private static void open(final Minecraft mc, final Screen screen) throws Exception {
        mc.executeBlocking(() -> mc.gui.setScreen(screen));
        Thread.sleep(300);
    }

    private static void openTweaksPage(final Minecraft mc) throws Exception {
        press(mc, "screen.kohs_inventory_tweaks.inventory_tweaks", true);
    }

    /** Presses the widget of the open screen whose label is this translation, if there is one. */
    private static boolean press(final Minecraft mc, final String key, final boolean required) throws Exception {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        boolean[] found = new boolean[1];
        mc.executeBlocking(() -> {
            for (var child : mc.gui.screen().children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getMessage().getString().equals(label)) {
                    widget.onClick(new net.minecraft.client.input.MouseButtonEvent(widget.getX() + 2, widget.getY() + 2,
                        new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    found[0] = true;
                    return;
                }
            }
        });
        if (!found[0] && required) throw new IllegalStateException(label + " button not found");
        Thread.sleep(500);
        return found[0];
    }

    /**
     * Parks the GUI pointer in the window's corner so no capture shows a hover. It goes
     * through the ordinary move callback: the player's real pointer is not moved.
     */
    private static void parkPointer(final Minecraft mc) throws Exception {
        mc.executeBlocking(() -> ((dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker) mc.mouseHandler)
            .kohsInventoryDebug$invokeMove(mc.getWindow().handle(), 1.0, 1.0));
        Thread.sleep(250);
    }

    /**
     * Page media for the README and Modrinth: the main menu and each page it opens,
     * at GUI scale 3, with the chat cleared and nothing hovered.
     */
    public static void media(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        InventoryTweaksConfig saved = ConfigStore.get().copy();
        int gui = mc.options.guiScale().get();
        boolean hudHidden = hudHidden(mc);
        try {
            stage(mc);
            glow(mc);
            // Golden hour and no HUD: the world behind the glass reads as a scene.
            command(mc, "time set 12600");
            mc.executeBlocking(() -> {
                mc.gui.hud.getChat().clearMessages(false);
                setHudHidden(mc, true);
            });
            guiScale(mc, 3);
            open(mc, new InventoryScreen(mc.player));
            parkPointer(mc);
            screenshot(mc, "media-glow");
            String[][] pages = {
                {"main-menu", null},
                {"cursor-landing", "screen.kohs_inventory_tweaks.cursor_landing"},
                {"inventory-tweaks", "screen.kohs_inventory_tweaks.inventory_tweaks"},
                {"customization", "screen.kohs_inventory_tweaks.customization"},
                {"item-highlighter", "screen.kohs_inventory_tweaks.item_highlighter"},
                {"gui-scaler", "screen.kohs_inventory_tweaks.gui_scaler"},
                {"kohs-tab", "screen.kohs_inventory_tweaks.kohs"},
            };
            int captures = 1;
            for (String[] page : pages) {
                open(mc, new InventoryTweaksScreen(null));
                if (page[1] != null) {
                    press(mc, page[1], true);
                    // First visits open with a warning; accept it to reach the page.
                    if (page[0].equals("customization")) {
                        press(mc, "screen.kohs_inventory_tweaks.customization.warning.continue", false);
                    } else if (page[0].equals("gui-scaler")) {
                        press(mc, "screen.kohs_inventory_tweaks.gui_scaler.warning.accept", false);
                    } else if (page[0].equals("kohs-tab")) {
                        // Past its entrance: the art, the chips and the finale have all settled.
                        Thread.sleep(2600);
                    }
                }
                parkPointer(mc);
                screenshot(mc, "media-" + page[0]);
                captures++;
            }
            // The KoHs tab at the other GUI scales: its four links in one row, or in two rows of two.
            for (int scale : new int[] {2, 4}) {
                guiScale(mc, scale);
                open(mc, new KohsScreen(null));
                Thread.sleep(2600);
                parkPointer(mc);
                screenshot(mc, "media-kohs-tab-gui" + scale);
                captures++;
            }
            // Its live preview needs the room GUI scale 2 leaves on this window.
            guiScale(mc, 2);
            open(mc, AdvancedSettingsScreen.playerGlow(null));
            parkPointer(mc);
            screenshot(mc, "media-player-visibility");
            DebugCollector.info("UI_MEDIA_SUMMARY", "captures=" + (captures + 1));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            mc.executeBlocking(() -> setHudHidden(mc, hudHidden));
            restore(mc, saved, gui);
        }
    }

    /**
     * Design review: the main menu at GUI scales 2, 3 and 4, a hovered card, the
     * opening frame, every page, a warning, the Issues tracker and reduced motion.
     * The pointer only moves through the move callback; the player's is untouched.
     */
    public static void review(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        InventoryTweaksConfig saved = ConfigStore.get().copy();
        int gui = mc.options.guiScale().get();
        try {
            stage(mc);
            mc.executeBlocking(() -> mc.gui.hud.getChat().clearMessages(false));
            for (int scale = 2; scale <= 4; scale++) {
                guiScale(mc, scale);
                open(mc, new InventoryTweaksScreen(null));
                parkPointer(mc);
                screenshot(mc, "review-main-gui" + scale);
                press(mc, "screen.kohs_inventory_tweaks.inventory_tweaks", true);
                parkPointer(mc);
                screenshot(mc, "review-tweaks-gui" + scale);
                open(mc, null);
            }
            guiScale(mc, 3);
            mc.executeBlocking(() -> mc.gui.setScreen(new InventoryTweaksScreen(null)));
            screenshotAfter(mc, "review-main-opening", 110);
            Thread.sleep(400);
            hover(mc, "screen.kohs_inventory_tweaks.inventory_tweaks");
            screenshot(mc, "review-main-hover");
            String[][] pages = {
                {"cursor", "screen.kohs_inventory_tweaks.cursor_landing"},
                {"customization", "screen.kohs_inventory_tweaks.customization"},
                {"highlighter", "screen.kohs_inventory_tweaks.item_highlighter"},
                {"scaler", "screen.kohs_inventory_tweaks.gui_scaler"},
            };
            for (String[] page : pages) {
                open(mc, new InventoryTweaksScreen(null));
                press(mc, page[1], true);
                parkPointer(mc);
                screenshot(mc, "review-" + page[0]);
                if (page[0].equals("cursor")) {
                    press(mc, "screen.kohs_inventory_tweaks.target.chest", true);
                    parkPointer(mc);
                    screenshot(mc, "review-cursor-chest");
                } else if (page[0].equals("customization") || page[0].equals("scaler")) {
                    boolean accepted = press(mc, page[0].equals("scaler")
                        ? "screen.kohs_inventory_tweaks.gui_scaler.warning.accept"
                        : "screen.kohs_inventory_tweaks.customization.warning.continue", false);
                    if (accepted) {
                        parkPointer(mc);
                        screenshot(mc, "review-" + page[0] + "-page");
                    }
                }
            }
            open(mc, new InventoryTweaksScreen(null));
            press(mc, "screen.kohs_inventory_tweaks.inventory_tweaks", true);
            if (pressLast(mc, "screen.kohs_inventory_tweaks.disabled")) {
                parkPointer(mc);
                screenshot(mc, "review-warning");
            }
            open(mc, new dev.zymekoh.kohsinventorytweaks.screen.IssuesTrackerScreen(null));
            parkPointer(mc);
            screenshot(mc, "review-issues");
            mc.executeBlocking(() -> ConfigStore.get().reduceInventoryMotion = true);
            open(mc, new InventoryTweaksScreen(null));
            parkPointer(mc);
            screenshot(mc, "review-main-reduced-motion");
            mc.executeBlocking(() -> ConfigStore.get().reduceInventoryMotion = saved.reduceInventoryMotion);
            guiScale(mc, 2);
            open(mc, AdvancedSettingsScreen.playerGlow(null));
            parkPointer(mc);
            screenshot(mc, "review-player-visibility");
            DebugCollector.info("UI_REVIEW_SUMMARY", "done");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            restore(mc, saved, gui);
        }
    }

    static void screenshotAfter(final Minecraft mc, final String name, final long delayMillis) throws Exception {
        Thread.sleep(delayMillis);
        CompletableFuture<Void> saved = new CompletableFuture<>();
        mc.executeBlocking(() -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
            message -> saved.complete(null)));
        saved.get(10, TimeUnit.SECONDS);
        DebugCollector.info("UI_SHOWCASE_CAPTURE", name + ".png");
    }

    /** Moves the GUI pointer over the widget with this label through the move callback. */
    private static void hover(final Minecraft mc, final String key) throws Exception {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        mc.executeBlocking(() -> {
            for (var child : mc.gui.screen().children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getMessage().getString().equals(label)) {
                    var window = mc.getWindow();
                    double x = (widget.getX() + widget.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth();
                    double y = (widget.getY() + widget.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight();
                    ((dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker) mc.mouseHandler)
                        .kohsInventoryDebug$invokeMove(window.handle(), x, y);
                    return;
                }
            }
        });
        Thread.sleep(500);
    }

    /** Presses the last widget with this label; the Tweaks switches share theirs. */
    private static boolean pressLast(final Minecraft mc, final String key) throws Exception {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        boolean[] found = new boolean[1];
        mc.executeBlocking(() -> {
            net.minecraft.client.gui.components.AbstractWidget last = null;
            for (var child : mc.gui.screen().children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getMessage().getString().equals(label)) {
                    last = widget;
                }
            }
            if (last != null) {
                last.onClick(new net.minecraft.client.input.MouseButtonEvent(last.getX() + 2, last.getY() + 2,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                found[0] = true;
            }
        });
        Thread.sleep(500);
        return found[0];
    }

    private static boolean hudHidden(final Minecraft mc) {
        return mc.gui.hud.isHidden();
    }

    /** Hides or shows the HUD, so a capture shows the screen over the world alone. */
    private static void setHudHidden(final Minecraft mc, final boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle();
    }

    /** A flat stage, a wall, one mannequin in the open and one behind the wall. */
    private static void stage(final Minecraft mc) throws Exception {
        int[] origin = new int[3];
        mc.executeBlocking(() -> {
            origin[0] = mc.player.blockPosition().getX();
            origin[1] = mc.player.blockPosition().getY() + 20;
            origin[2] = mc.player.blockPosition().getZ();
        });
        int x = origin[0], y = origin[1], z = origin[2];
        command(mc, "gamemode survival");
        command(mc, "difficulty peaceful");
        command(mc, "time set 6000");
        command(mc, "weather clear");
        command(mc, "gamerule doDaylightCycle false");
        command(mc, "fill " + (x - 9) + " " + (y - 1) + " " + (z - 12) + " " + (x + 9) + " " + (y - 1) + " " + (z + 3) + " minecraft:polished_blackstone");
        command(mc, "fill " + (x - 9) + " " + y + " " + (z - 12) + " " + (x + 9) + " " + (y + 5) + " " + (z + 3) + " minecraft:air");
        command(mc, "fill " + (x - 7) + " " + y + " " + (z - 6) + " " + (x - 2) + " " + (y + 3) + " " + (z - 6) + " minecraft:purple_concrete");
        command(mc, "kill @e[type=minecraft:mannequin]");
        command(mc, "summon minecraft:mannequin " + (x + 4.5) + " " + y + " " + (z - 6.5) + " {Rotation:[0f,0f]}");
        command(mc, "summon minecraft:mannequin " + (x - 4.5) + " " + y + " " + (z - 8.5) + " {Rotation:[0f,0f]}");
        command(mc, "tp @s " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 180 0");
        Thread.sleep(1500);
    }

    private static void glow(final Minecraft mc) {
        mc.executeBlocking(() -> {
            var config = ConfigStore.get();
            config.visiblePlayerGlowEnabled = true;
            config.visiblePlayerHighlightEnabled = true;
            config.visiblePlayerGlowIntensity = 90;
            config.visiblePlayerLightGlowEnabled = true;
            config.visiblePlayerGlowBrightness = 220;
            config.visiblePlayerGlowColor = 0xB86BFF;
            config.visiblePlayerGlowDistance = 32;
            config.visiblePlayerGlowPulse = false;
        });
    }

    private static void restore(final Minecraft mc, final InventoryTweaksConfig saved, final int gui) {
        mc.executeBlocking(() -> {
            var config = ConfigStore.get();
            config.visiblePlayerGlowEnabled = saved.visiblePlayerGlowEnabled;
            config.visiblePlayerHighlightEnabled = saved.visiblePlayerHighlightEnabled;
            config.visiblePlayerGlowIntensity = saved.visiblePlayerGlowIntensity;
            config.visiblePlayerLightGlowEnabled = saved.visiblePlayerLightGlowEnabled;
            config.visiblePlayerGlowBrightness = saved.visiblePlayerGlowBrightness;
            config.visiblePlayerGlowColor = saved.visiblePlayerGlowColor;
            config.visiblePlayerGlowDistance = saved.visiblePlayerGlowDistance;
            config.visiblePlayerGlowPulse = saved.visiblePlayerGlowPulse;
            if (mc.gui.screen() != null) mc.gui.setScreen(null);
            mc.options.guiScale().set(gui);
            mc.resizeGui();
        });
    }

    private static void hoverFirstTweak(final Minecraft mc) throws Exception {
        mc.executeBlocking(() -> {
            double x = mc.getWindow().getScreenWidth() * 0.5;
            double y = mc.getWindow().getScreenHeight() * 0.235;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(), x, y);
        });
        Thread.sleep(700);
    }

    public static void run(final Minecraft mc) {
        if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
        InventoryTweaksConfig saved = ConfigStore.get().copy();
        int gui = mc.options.guiScale().get();
        try {
            stage(mc);
            glow(mc);
            guiScale(mc, 2);
            open(mc, new InventoryScreen(mc.player));
            screenshot(mc, "showcase-glow-on");
            mc.executeBlocking(() -> ConfigStore.get().visiblePlayerGlowEnabled = false);
            screenshot(mc, "showcase-glow-off");
            mc.executeBlocking(() -> ConfigStore.get().visiblePlayerGlowEnabled = true);
            open(mc, null);

            for (int scale = 2; scale <= 4; scale++) {
                guiScale(mc, scale);
                open(mc, AdvancedSettingsScreen.playerGlow(null));
                screenshot(mc, "showcase-player-visibility-gui" + scale);
                open(mc, new InventoryTweaksScreen(null));
                screenshot(mc, "showcase-main-menu-gui" + scale);
                openTweaksPage(mc);
                screenshot(mc, "showcase-tweaks-gui" + scale);
                if (scale == 2) {
                    hoverFirstTweak(mc);
                    screenshot(mc, "showcase-tweaks-hover-gui2");
                }
                open(mc, null);
            }
            DebugCollector.info("UI_SHOWCASE_SUMMARY", "captures=13");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            restore(mc, saved, gui);
        }
    }
}
