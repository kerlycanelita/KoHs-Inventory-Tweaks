package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * How often an animated item in a menu or the hotbar is drawn again.
 *
 * <p>Minecraft draws every GUI item once into an atlas and copies it from there on later frames,
 * except items that move: anything enchanted, for the shine, and items with animated textures.
 * Those are drawn again from scratch on every single frame, which at a few hundred frames a
 * second with a set of enchanted gear costs a third of the frame rate (MC-310555, closed as Won't
 * Fix). The shine scrolls slowly and animated textures change twenty times a second, so drawing
 * them sixty times a second looks the same: in between, the atlas keeps the last drawing. With the
 * shine's speed at zero nothing moves but textures, and twenty times a second is enough.</p>
 */
public final class AnimatedItemPacer {
	private static final long STEADY_NANOS = 1_000_000_000L / 60L;
	private static final long TEXTURE_NANOS = 1_000_000_000L / 20L;
	/** Items not seen for this long are forgotten. */
	private static final long FORGET_NANOS = 2_000_000_000L;
	private static final Map<Object, Long> LAST_DRAWN = new HashMap<>();
	private static long lastSweep;

	private AnimatedItemPacer() {
	}

	public static boolean active() {
		return ConfigStore.get().fastEnchantedItems
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	/** Whether the item with this model identity is due to be drawn again now; records it if so. */
	public static boolean redrawDue(final Object identity) {
		long now = System.nanoTime();
		Long last = LAST_DRAWN.get(identity);
		if (last != null && now - last < interval()) {
			return false;
		}
		LAST_DRAWN.put(identity, now);
		// The first sighting was just drawn by the allocation itself.
		return last != null;
	}

	public static void endFrame() {
		long now = System.nanoTime();
		if (now - lastSweep < FORGET_NANOS) {
			return;
		}
		lastSweep = now;
		LAST_DRAWN.values().removeIf(last -> now - last > FORGET_NANOS);
	}

	private static long interval() {
		return Minecraft.getInstance().options.glintSpeed().get() <= 0.0 ? TEXTURE_NANOS : STEADY_NANOS;
	}
}
