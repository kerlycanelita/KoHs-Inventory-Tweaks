package dev.zymekoh.kohsinventorytweaks.compat;

import dev.zymekoh.kohsinventorytweaks.screen.CompatibilityNoticeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

public final class CompatibilityNoticeController {
	private static boolean shownThisSession;

	private CompatibilityNoticeController() {
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (shownThisSession
			|| minecraft.level != null
			|| !(minecraft.screen instanceof TitleScreen)
			|| !CompatibilityIssueManager.hasModalNoticeIssues()) {
			return;
		}
		shownThisSession = true;
		minecraft.setScreen(new CompatibilityNoticeScreen(minecraft.screen));
	}

	public static void acknowledge() {
		shownThisSession = true;
	}
}

