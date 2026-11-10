package dev.zymekoh.kohsinventorytweaks.compat;

import dev.zymekoh.kohsinventorytweaks.screen.BlockingCompatibilityScreen;
import net.minecraft.client.Minecraft;

public final class BlockingCompatibilityController {
	private BlockingCompatibilityController() {
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (!CompatibilityIssueManager.isSafelyBlocked()
			|| minecraft.gui.screen() instanceof BlockingCompatibilityScreen
			|| minecraft.gui.screen() == null) {
			return;
		}
		minecraft.gui.setScreen(new BlockingCompatibilityScreen());
	}
}
