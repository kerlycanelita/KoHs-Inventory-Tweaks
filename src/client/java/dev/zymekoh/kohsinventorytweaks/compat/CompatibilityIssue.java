package dev.zymekoh.kohsinventorytweaks.compat;

import java.util.List;

public record CompatibilityIssue(
	String modId,
	String modName,
	String version,
	String creators,
	Severity severity,
	Reason reason,
	List<String> conflictPoints
) {
	public CompatibilityIssue {
		conflictPoints = List.copyOf(conflictPoints);
	}

	public enum Reason {
		DIRECT_MUTATION("direct_mutation"),
		CRITICAL_OVERWRITE("critical_overwrite"),
		REDIRECT_COLLISION("redirect_collision");

		private final String translationSuffix;

		Reason(final String translationSuffix) {
			this.translationSuffix = translationSuffix;
		}

		public String translationKey() {
			return "screen.kohs_inventory_tweaks.issues_tracker.reason." + this.translationSuffix;
		}
	}

	public enum Severity {
		ADAPTABLE,
		BLOCKING
	}
}
