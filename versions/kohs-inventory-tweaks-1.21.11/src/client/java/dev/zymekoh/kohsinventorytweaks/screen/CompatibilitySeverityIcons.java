package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import dev.zymekoh.kohsinventorytweaks.ui.ZIcons;
import net.minecraft.client.gui.GuiGraphics;

final class CompatibilitySeverityIcons {
	private CompatibilitySeverityIcons() {
	}

	/** Shape and colour both carry the severity, so it never rests on colour alone. */
	static void draw(final GuiGraphics graphics, final CompatibilityIssue.Severity severity, final int x, final int y, final int size) {
		ZIcons icon = switch (severity) {
			case ADAPTABLE -> ZIcons.SEVERITY_ADAPTED;
			case DEGRADED -> ZIcons.SEVERITY_WARNING;
			case BLOCKING -> ZIcons.SEVERITY_CRITICAL;
		};
		icon.draw(graphics, x, y, size, colorFor(severity), 1.0F);
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
