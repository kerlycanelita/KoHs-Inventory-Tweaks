package dev.zymekoh.kohsinventorydebug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Creates and opens the disposable lab world the macros need, with commands on.
 *
 * <p>A world saved without commands refuses {@code /give} silently, so the offhand
 * macros could only report an empty hand and call it a failed swap. Rather than
 * change a world someone else made, this owns one of its own: it is created once,
 * always with commands enabled, and nothing outside this lab depends on it.</p>
 *
 * <p>Creation runs from the title screen, which is where the game is idle and the
 * flow that builds a level is safe to call.</p>
 */
public final class LabWorld {
	/** Set by the {@code -PdebugCreateWorld} switch on {@code runClient}. */
	private static final String CREATE_PROPERTY = "kohs.inventory.debug.createLabWorld";
	/** Directory under {@code saves/}, and the name {@code --quickPlaySingleplayer} takes. */
	public static final String LEVEL_ID = "KoHs Inventory Lab";

	private static boolean attempted;
	private static int idleTicks;

	private LabWorld() {
	}

	/** Runs on every client tick, including at the title screen. */
	public static void onClientTick(final Minecraft minecraft) {
		if (attempted || minecraft == null || !Boolean.getBoolean(CREATE_PROPERTY)) {
			return;
		}
		if (minecraft.level != null) {
			// Already in a world: whatever opened it wins, and the macro runs there.
			attempted = true;
			return;
		}
		if (!(minecraft.gui.screen() instanceof TitleScreen)) {
			return;
		}
		// The title screen appears before the resource reload has settled; opening a
		// level during that window drops the client into a half-loaded state.
		if (++idleTicks < 40) {
			return;
		}
		attempted = true;
		open(minecraft);
	}

	private static void open(final Minecraft minecraft) {
		try {
			if (minecraft.getLevelSource().levelExists(LEVEL_ID)) {
				DebugCollector.info("LAB_WORLD", "Opening the existing lab world: " + LEVEL_ID);
				minecraft.createWorldOpenFlows().openWorld(LEVEL_ID, () -> {
				});
				return;
			}
			DebugCollector.info("LAB_WORLD", "Creating the lab world with commands enabled: " + LEVEL_ID);
			LevelSettings settings = new LevelSettings(
				LEVEL_ID,
				GameType.CREATIVE,
				LevelSettings.DifficultySettings.DEFAULT,
				true,
				WorldDataConfiguration.DEFAULT
			);
			minecraft.createWorldOpenFlows().createFreshLevel(
				LEVEL_ID,
				settings,
				WorldOptions.defaultWithRandomSeed(),
				WorldPresets::createNormalWorldDimensions,
				minecraft.gui.screen()
			);
		} catch (RuntimeException | LinkageError failure) {
			// A lab that cannot build its own world is a reason to stop, not to run the
			// macros against whatever happens to be loaded and report those numbers.
			DebugCollector.issue("LAB_WORLD", "Could not open " + LEVEL_ID + "; "
				+ failure.getClass().getSimpleName() + ": " + failure.getMessage());
		}
	}
}
