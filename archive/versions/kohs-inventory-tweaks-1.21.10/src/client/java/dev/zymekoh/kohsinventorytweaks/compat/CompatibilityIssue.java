package dev.zymekoh.kohsinventorytweaks.compat;

import java.util.List;
import java.util.Set;

public record CompatibilityIssue(
	String modId,
	String modName,
	String version,
	String creators,
	Severity severity,
	Reason reason,
	List<String> conflictPoints,
	Set<CompatibilityFeature> affectedFeatures
) {
	public CompatibilityIssue {
		conflictPoints = List.copyOf(conflictPoints);
		affectedFeatures = Set.copyOf(affectedFeatures);
	}

	public CompatibilityIssue(
		final String modId,
		final String modName,
		final String version,
		final String creators,
		final Severity severity,
		final Reason reason,
		final List<String> conflictPoints
	) {
		this(modId, modName, version, creators, severity, reason, conflictPoints, Set.of());
	}

	public enum Reason {
		DIRECT_MUTATION("direct_mutation"),
		CRITICAL_OVERWRITE("critical_overwrite"),
		CONTAINER_SCALE_PIPELINE("container_scale_pipeline"),
		RAW_INPUT_PIPELINE_COLLISION("raw_input_pipeline_collision"),
		MOUSE_POSITION_OVERRIDE("mouse_position_override"),
		REDIRECT_COLLISION("redirect_collision"),
		SUPPRESSED_REDIRECT("suppressed_redirect");

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
		DEGRADED,
		BLOCKING
	}
}
