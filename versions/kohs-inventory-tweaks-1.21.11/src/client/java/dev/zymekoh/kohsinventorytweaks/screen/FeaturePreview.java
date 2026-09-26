package dev.zymekoh.kohsinventorytweaks.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Recorded before/after strips shown in an option's hover.
 *
 * <p>Each strip is a vertical sheet of small side-by-side frames, Vanilla on the
 * left and the option on the right, recorded by the debug companion with the same
 * input for both. Its timing and the measured latencies travel in a JSON file
 * next to it. Loaded lazily, dropped on resource reload.</p>
 */
final class FeaturePreview {
	record Strip(
		Identifier texture,
		int frames,
		int frameWidth,
		int frameHeight,
		int frameMillis,
		int offMillis,
		int onMillis
	) {
		boolean hasLatency() {
			return this.offMillis > 0 && this.onMillis > 0;
		}
	}

	private static final Map<String, Optional<Strip>> CACHE = new HashMap<>();

	private FeaturePreview() {
	}

	static Optional<Strip> of(final String clip) {
		return CACHE.computeIfAbsent(clip, FeaturePreview::load);
	}

	static void invalidate() {
		CACHE.clear();
	}

	private static Optional<Strip> load(final String clip) {
		Identifier meta = Identifier.fromNamespaceAndPath("kohs_inventory_tweaks", "textures/gui/preview/" + clip + ".json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(meta)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			return Optional.of(new Strip(
				Identifier.fromNamespaceAndPath("kohs_inventory_tweaks", "textures/gui/preview/" + clip + ".png"),
				json.get("frames").getAsInt(),
				json.get("frameWidth").getAsInt(),
				json.get("frameHeight").getAsInt(),
				Math.max(20, json.get("frameMillis").getAsInt()),
				json.has("latencyOffMillis") && !json.get("latencyOffMillis").isJsonNull() ? json.get("latencyOffMillis").getAsInt() : 0,
				json.has("latencyOnMillis") && !json.get("latencyOnMillis").isJsonNull() ? json.get("latencyOnMillis").getAsInt() : 0
			));
		} catch (Exception missing) {
			return Optional.empty();
		}
	}

	/** Draws the frame due now, scaled to {@code width}; returns the drawn height. */
	static int draw(final GuiGraphics graphics, final Strip strip, final int x, final int y, final int width) {
		int frame = (int) (System.nanoTime() / 1_000_000L / strip.frameMillis() % strip.frames());
		float scale = width / (float) strip.frameWidth();
		int height = Math.round(strip.frameHeight() * scale);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			strip.texture(),
			0,
			0,
			0.0F,
			(float) (frame * strip.frameHeight()),
			strip.frameWidth(),
			strip.frameHeight(),
			strip.frameWidth(),
			strip.frameHeight() * strip.frames()
		);
		graphics.pose().popMatrix();
		return height;
	}
}
