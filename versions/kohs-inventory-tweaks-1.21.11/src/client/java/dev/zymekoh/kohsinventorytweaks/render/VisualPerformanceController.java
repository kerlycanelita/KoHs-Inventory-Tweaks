package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import net.minecraft.client.Minecraft;

public final class VisualPerformanceController {
	private VisualPerformanceController() {
	}

	public static int particleCount(final int baseCount) {
		InventoryTweaksConfig config = ConfigStore.get();
		double density = config.menuParticleDensity / 100.0;
		Minecraft minecraft = Minecraft.getInstance();
		if (config.reduceParticlesWhenUnfocused && minecraft != null && !minecraft.isWindowActive()) {
			density *= 0.25;
		}
		return Math.max(0, (int) Math.round(Math.max(0, baseCount) * density));
	}

	public static long animatedBackgroundTime(final long elapsedMillis) {
		InventoryTweaksConfig config = ConfigStore.get();
		Minecraft minecraft = Minecraft.getInstance();
		if (config.pauseAnimatedBackgroundWhenUnfocused && minecraft != null && !minecraft.isWindowActive()) {
			return 0L;
		}
		int fps = Math.max(1, Math.min(60, config.animatedBackgroundFps));
		long frameMillis = Math.max(1L, 1_000L / fps);
		return Math.max(0L, elapsedMillis) / frameMillis * frameMillis;
	}
}
