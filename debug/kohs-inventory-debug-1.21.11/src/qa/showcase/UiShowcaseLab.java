package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.screen.AdvancedSettingsScreen;
import dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Builds a small scene and captures the screens and effects that need eyes on them:
 * the Player Visibility editor and main menu at GUI scales 2, 3 and 4, and the
 * silhouette glow on a visible mannequin next to one hidden behind a wall.
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
        mc.executeBlocking(() -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
            message -> saved.complete(null)));
        saved.get(10, TimeUnit.SECONDS);
        DebugCollector.info("UI_SHOWCASE_CAPTURE", name + ".png");
    }

    private static void guiScale(final Minecraft mc, final int scale) {
        mc.executeBlocking(() -> {
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        });
    }

    private static void open(final Minecraft mc, final Screen screen) throws Exception {
        mc.executeBlocking(() -> mc.setScreen(screen));
        Thread.sleep(300);
    }

    private static void openTweaksPage(final Minecraft mc) throws Exception {
        String label = net.minecraft.network.chat.Component.translatable("screen.kohs_inventory_tweaks.inventory_tweaks").getString();
        mc.executeBlocking(() -> {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getMessage().getString().equals(label)) {
                    widget.onClick(new net.minecraft.client.input.MouseButtonEvent(widget.getX() + 2, widget.getY() + 2,
                        new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    return;
                }
            }
            throw new IllegalStateException("Inventory Tweaks button not found");
        });
        Thread.sleep(500);
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
            // Scene: a flat stage, a wall, one mannequin in the open and one behind the wall.
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
                if (mc.screen != null) mc.setScreen(null);
                mc.options.guiScale().set(gui);
                mc.resizeDisplay();
            });
        }
    }
}
