package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.ui.ZScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class CompatibilitySeverityIcons {
	private CompatibilitySeverityIcons() {
	}

	/** An emerald, a redstone torch or a barrier: the item and its light both carry the severity, never colour alone. */
	static void draw(final GuiGraphicsExtractor graphics, final CompatibilityIssue.Severity severity, final int x, final int y, final int size) {
		scene(severity).draw(graphics, x, y, size, 0.4F);
	}

	static ZScene scene(final CompatibilityIssue.Severity severity) {
		return switch (severity) {
			case ADAPTABLE -> ZScene.ADAPTED;
			case DEGRADED -> ZScene.WARNING;
			case BLOCKING -> ZScene.CRITICAL;
		};
	}

	static int colorFor(final CompatibilityIssue.Severity severity) {
		return switch (severity) {
			case ADAPTABLE -> UiTheme.ACCENT_BRIGHT;
			case DEGRADED -> UiTheme.WARNING;
			case BLOCKING -> UiTheme.DANGER;
		};
	}

	static String statusKey(final CompatibilityIssue.Severity severity) {
		return switch (severity) {
			case ADAPTABLE -> "screen.kohs_inventory_tweaks.issues_tracker.status.adaptable";
			case DEGRADED -> "screen.kohs_inventory_tweaks.issues_tracker.status.degraded";
			case BLOCKING -> "screen.kohs_inventory_tweaks.issues_tracker.status.blocking";
		};
	}
}
