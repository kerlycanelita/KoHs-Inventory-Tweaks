package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssue;
import net.minecraft.resources.Identifier;

final class CompatibilitySeverityIcons {
	private static final Identifier ADAPTED = texture("severity_adapted.png");
	private static final Identifier WARNING = texture("severity_warning.png");
	private static final Identifier CRITICAL = texture("severity_critical.png");

	private CompatibilitySeverityIcons() {
	}

	static Identifier textureFor(final CompatibilityIssue.Severity severity) {
		return switch (severity) {
			case ADAPTABLE -> ADAPTED;
			case DEGRADED -> WARNING;
			case BLOCKING -> CRITICAL;
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

	private static Identifier texture(final String fileName) {
		return Identifier.fromNamespaceAndPath(
			KoHsInventoryTweaksClient.MOD_ID,
			"textures/gui/compatibility/" + fileName
		);
	}
}
