package dev.zymekoh.kohsinventorydebug;

import java.nio.DoubleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * What a viewer needs to read a take: which side it is, when the key went down,
 * how long the inventory took, and where the real pointer is. The desktop cursor
 * is not part of the frame buffer, so it is drawn here from GLFW's position.
 */
public final class RecordingOverlay {
    private static volatile String label;
    private static volatile int accent = 0xFFB86BFF;
    private static volatile boolean showCursor;
    private static volatile long pressNanos;
    private static volatile long openNanos;
    private static volatile String keyName = "E";

    /** 1-bit arrow, 12 wide: '#' white, 'o' black outline. */
    private static final String[] CURSOR = {
        "o",
        "oo",
        "o#o",
        "o##o",
        "o###o",
        "o####o",
        "o#####o",
        "o######o",
        "o#######o",
        "o########o",
        "o#####ooooo",
        "o##o##o",
        "o#o o##o",
        "oo  o##o",
        "o    o##o",
        "     o##o",
        "      oo",
    };

    private RecordingOverlay() {}

    public static void begin(final String takeLabel, final int takeAccent, final boolean cursor, final String key) {
        label = takeLabel;
        accent = takeAccent;
        showCursor = cursor;
        keyName = key;
        pressNanos = 0L;
        openNanos = 0L;
    }

    public static void end() {
        label = null;
    }

    public static void pressed() {
        pressNanos = System.nanoTime();
        openNanos = 0L;
        FrameRecorder.mark("press");
    }

    public static void opened() {
        if (pressNanos != 0L && openNanos == 0L) {
            openNanos = System.nanoTime();
            FrameRecorder.mark("open");
        }
    }

    public static void draw(final GuiGraphics graphics, final boolean overScreen) {
        String text = label;
        if (text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!overScreen && mc.screen != null) return;
        var font = mc.font;
        int x = 8;
        int y = 8;
        int width = Math.max(150, font.width(text) + 20);
        graphics.fill(x, y, x + width, y + 20, 0xC0100818);
        graphics.fill(x, y, x + 3, y + 20, accent);
        graphics.drawString(font, Component.literal(text), x + 9, y + 6, 0xFFFFFFFF, false);

        long now = System.nanoTime();
        boolean lit = pressNanos != 0L && now - pressNanos < 180_000_000L;
        int keyY = y + 26;
        graphics.fill(x, keyY, x + 22, keyY + 22, lit ? accent : 0xC0231634);
        graphics.fill(x + 1, keyY + 1, x + 21, keyY + 21, lit ? 0xFFEBD8FF : 0xE0140B22);
        graphics.drawCenteredString(font, Component.literal(keyName), x + 11, keyY + 7, lit ? 0xFF1A0B2A : 0xFFD7C4F2);
        if (pressNanos != 0L) {
            String timing = openNanos != 0L
                ? "opened after " + (openNanos - pressNanos) / 1_000_000 + " ms"
                : "waiting " + (now - pressNanos) / 1_000_000 + " ms";
            graphics.drawString(font, Component.literal(timing), x + 28, keyY + 7, openNanos != 0L ? 0xFF9CF7B4 : 0xFFFFD27A, false);
        }
        if (showCursor && mc.screen != null) {
            drawCursor(graphics, mc);
        }
    }

    private static void drawCursor(final GuiGraphics graphics, final Minecraft mc) {
        double px;
        double py;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer bx = stack.mallocDouble(1), by = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(mc.getWindow().handle(), bx, by);
            px = bx.get(0);
            py = by.get(0);
        }
        int gx = (int) Math.round(px * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth()));
        int gy = (int) Math.round(py * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight()));
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            for (int column = 0; column < line.length(); column++) {
                char c = line.charAt(column);
                if (c == ' ') continue;
                int color = c == '#' ? 0xFFFFFFFF : 0xFF000000;
                graphics.fill(gx + column, gy + row, gx + column + 1, gy + row + 1, color);
            }
        }
    }
}
