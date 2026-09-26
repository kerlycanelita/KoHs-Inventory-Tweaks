package dev.zymekoh.kohsinventorydebug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Captures every rendered frame of a take to numbered PNG files, off the render thread.
 *
 * <p>Each frame is requested at the end of {@code runTick}, after the frame was drawn
 * and before the next one clears the target. The readback completes a few frames
 * later on the GPU fence; the index is fixed at request time so ordering holds. The
 * images stay in memory until the take ends and are only encoded then: encoding
 * during the take competes with the game for CPU and stretches the very frames the
 * recording exists to show. A
 * {@code take.txt} next to the frames records each frame's timestamp and the named
 * events of the take, so the composer can align two takes on the key press.</p>
 */
public final class FrameRecorder {
    private static volatile Path directory;
    private static final AtomicInteger FRAMES = new AtomicInteger();
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final List<String> LOG = new ArrayList<>();
    private static final Map<Integer, NativeImage> HELD = new ConcurrentHashMap<>();
    private static ExecutorService writers;
    private static long startNanos;

    private FrameRecorder() {}

    public static synchronized void start(final Path target) throws IOException {
        Files.createDirectories(target);
        try (var old = Files.list(target)) {
            for (Path file : old.toList()) Files.deleteIfExists(file);
        }
        writers = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "KoHs frame writer");
            thread.setDaemon(true);
            return thread;
        });
        FRAMES.set(0);
        PENDING.set(0);
        synchronized (LOG) {
            LOG.clear();
        }
        startNanos = System.nanoTime();
        directory = target;
    }

    public static boolean recording() {
        return directory != null;
    }

    public static void mark(final String event) {
        if (directory == null) return;
        synchronized (LOG) {
            LOG.add("event " + event + " " + (System.nanoTime() - startNanos) / 1_000);
        }
    }

    /** From the frame-end hook, on the render thread. */
    public static void onFrameEnd(final Minecraft minecraft) {
        Path target = directory;
        if (target == null) return;
        int index = FRAMES.getAndIncrement();
        synchronized (LOG) {
            LOG.add("frame " + index + " " + (System.nanoTime() - startNanos) / 1_000);
        }
        PENDING.incrementAndGet();
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), 2, image -> {
            HELD.put(index, image);
            PENDING.decrementAndGet();
        });
    }

    public static synchronized int stop() throws InterruptedException, IOException {
        Path target = directory;
        directory = null;
        if (target == null) return 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (PENDING.get() > 0 && System.nanoTime() < deadline) Thread.sleep(10);
        for (var entry : HELD.entrySet()) {
            int index = entry.getKey();
            NativeImage image = entry.getValue();
            writers.execute(() -> {
                try (image) {
                    image.writeToFile(target.resolve(String.format("frame_%05d.png", index)));
                } catch (IOException error) {
                    DebugCollector.issue("RECORDING", "frame " + index + " not written: " + error.getMessage());
                }
            });
        }
        HELD.clear();
        writers.shutdown();
        writers.awaitTermination(60, TimeUnit.SECONDS);
        synchronized (LOG) {
            Files.write(target.resolve("take.txt"), LOG, StandardCharsets.UTF_8);
        }
        return FRAMES.get();
    }
}
