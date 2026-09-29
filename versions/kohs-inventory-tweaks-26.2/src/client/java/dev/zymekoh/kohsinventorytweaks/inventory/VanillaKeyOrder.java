package dev.zymekoh.kohsinventorytweaks.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;

/**
 * Where Vanilla's {@code handleKeybinds} takes each mapping relative to the
 * inventory key, on this Minecraft version.
 *
 * <p>Read from the 26.2 client jar. 26.2 moved the HUD toggle, advancements,
 * social interactions, chat and commands into {@code Gui.handleKeybinds}, which
 * {@code Minecraft.handleKeybinds} runs before the inventory. Every maintained
 * version keeps its own copy of this class, checked against its own jar.</p>
 *
 * <p>An opening made at the next tick runs inside that pass, so it decides what
 * happens to every click queued in the same tick:</p>
 * <ul>
 *   <li>mappings drained <b>before</b> the inventory still act; an early opening
 *       would take their clicks away, so it waits;</li>
 *   <li>plain mappings drained <b>after</b> it are zeroed by the {@code releaseAll}
 *       of the opening, so Vanilla drops them too and they need no waiting;</li>
 *   <li>{@link ToggleKeyMapping}s after it (attack and use) keep their clicks
 *       through {@code releaseAll}, and Vanilla still runs them in that pass. An
 *       early opening would leave them queued until the screen closed, because
 *       {@code handleKeybinds} does not run under a screen, and they would fire
 *       then: a swing, a thrown pearl. So those wait as well.</li>
 * </ul>
 */
final class VanillaKeyOrder {
	private VanillaKeyOrder() {
	}

	/** Drained before {@code keyInventory}, in order. */
	static List<KeyMapping> beforeInventory(final Options options) {
		List<KeyMapping> mappings = new ArrayList<>(List.of(
			options.keyTogglePerspective,
			options.keySmoothCamera,
			options.keyToggleGui,
			options.keyAdvancements,
			options.keySocialInteractions,
			options.keyChat,
			options.keyCommand,
			options.keyToggleSpectatorShaderEffects,
			options.keySaveHotbarActivator,
			options.keyLoadHotbarActivator
		));
		mappings.addAll(List.of(options.keyHotbarSlots));
		return mappings;
	}

	/** Drained after {@code keyInventory}. */
	static boolean afterInventory(final Options options, final KeyMapping mapping) {
		return mapping == options.keyQuickActions || mapping == options.keySwapOffhand
			|| mapping == options.keyDrop || mapping == options.keyAttack
			|| mapping == options.keyUse || mapping == options.keyPickItem
			|| mapping == options.keySpectatorHotbar;
	}

	/** A click Vanilla discards itself when the inventory opens in the same tick. */
	static boolean droppedByOpening(final Options options, final KeyMapping mapping) {
		return !(mapping instanceof ToggleKeyMapping) && afterInventory(options, mapping);
	}

	/** Clicks Vanilla still runs in the tick of an opening, after it. */
	static List<KeyMapping> stillRunAfterOpening(final Options options) {
		List<KeyMapping> mappings = new ArrayList<>();
		for (KeyMapping mapping : List.of(options.keyAttack, options.keyUse)) {
			if (mapping instanceof ToggleKeyMapping && afterInventory(options, mapping)) {
				mappings.add(mapping);
			}
		}
		return mappings;
	}
}
