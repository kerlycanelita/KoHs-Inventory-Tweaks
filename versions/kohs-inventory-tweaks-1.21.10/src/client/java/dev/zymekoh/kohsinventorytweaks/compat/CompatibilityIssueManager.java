package dev.zymekoh.kohsinventorytweaks.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompatibilityIssueManager {
	private static final String MOD_ID = "kohs_inventory_tweaks";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "/compatibility");
	private static final String INTERNAL_PACKAGE = "dev.zymekoh.kohsinventorytweaks.";
	private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String OVERWRITE_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String REDIRECT_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String MIXINEXTRAS_INJECTION_PREFIX = "Lcom/llamalad7/mixinextras/injector/";
	private static final String WRAP_METHOD_DESCRIPTOR = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
	private static final String INVENTORY_SCALE_FIX_ID = "inventoryscalefix";
	private static final String BETTER_SCREENS_ID = "betterscreens";
	private static final String RAW_INPUT_BUFFER_ID = "rawinputbuffer";
	private static final String IXERIS_ID = "ixeris";
	private static final String KOHS_SYNAPSE_ID = "kohs_synapse";
	private static final String MOUSE_HANDLER_CLASS = "net.minecraft.client.MouseHandler";
	private static final String MOUSE_HANDLER_INTERMEDIARY_CLASS = "net.minecraft.class_312";
	private static final String INPUT_CONSTANTS_CLASS = "com.mojang.blaze3d.platform.InputConstants";
	private static final String INPUT_CONSTANTS_INTERMEDIARY_CLASS = "net.minecraft.class_3675";
	private static final Set<String> INTERMEDIARY_CURSOR_CRITICAL_METHODS = Set.of(
		"method_1607", "method_1612", "method_1610", "method_1600"
	);
	private static final Set<String> INTERMEDIARY_MOUSE_PIPELINE_METHODS = Set.of("method_1607", "method_1600");
	private static final Set<String> INTERMEDIARY_MOUSE_COORDINATE_FIELDS = Set.of("field_1795", "field_1794");
	private static final String INVENTORY_ENTITY_INVOCATION = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;"
		+ "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V";
	private static volatile boolean initialized;
	private static volatile boolean earlyMixinGateActive;
	private static volatile boolean adaptationActive;
	private static volatile List<CompatibilityIssue> issues = List.of();

	private CompatibilityIssueManager() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		List<CompatibilityIssue> explicitIssues = detectExplicitIssues();
		try {
			ModContainer ownContainer = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.orElse(null);
			if (ownContainer == null) {
				issues = explicitIssues;
				return;
			}
			Set<TargetMethod> ownHooks = new HashSet<>();
			Set<TargetMethod> criticalOwnHooks = new HashSet<>();
			Set<RedirectPoint> ownRedirects = new HashSet<>();
			for (MixinInspection inspection : inspectMixins(ownContainer)) {
				for (String target : inspection.targets()) {
					for (String method : inspection.injectedMethods()) {
						ownHooks.add(new TargetMethod(target, method));
					}
					for (String method : inspection.criticalInjectedMethods()) {
						criticalOwnHooks.add(new TargetMethod(target, method));
					}
					for (RedirectHook redirect : inspection.redirectHooks()) {
						ownRedirects.add(new RedirectPoint(target, redirect.method(), redirect.invocationTarget()));
					}
				}
			}

			List<CompatibilityIssue> detected = new ArrayList<>(explicitIssues);
			Set<String> explicitlyHandledIds = explicitIssues.stream()
				.map(CompatibilityIssue::modId)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
			boolean rawInputPipelineCollisionHandled = FabricLoader.getInstance().isModLoaded(IXERIS_ID)
				&& FabricLoader.getInstance().isModLoaded(RAW_INPUT_BUFFER_ID);
			for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
				String modId = container.getMetadata().getId();
				if (modId.equals(MOD_ID)
					|| modId.startsWith("fabric-") || modId.equals("minecraft")
					|| explicitlyHandledIds.contains(modId)
					|| (rawInputPipelineCollisionHandled
						&& (IXERIS_ID.equals(modId) || RAW_INPUT_BUFFER_ID.equals(modId)))) {
					continue;
				}
				CompatibilityIssue issue = inspectForeignMod(container, ownHooks, criticalOwnHooks, ownRedirects);
				if (issue != null) {
					detected.add(issue);
				}
			}
			detected.sort(Comparator.comparing(CompatibilityIssue::modName, String.CASE_INSENSITIVE_ORDER));
			issues = List.copyOf(detected);
			StartupFailureRegistry.refreshFromCrashReports();
			StartupFailureRegistry.recordBlockingIssues(blockingIssues());
			if (!issues.isEmpty()) {
				LOGGER.warn("Detected {} confirmed KoHs Inventory Tweaks compatibility issue(s)", issues.size());
				for (CompatibilityIssue issue : issues) {
					LOGGER.warn(
						"Compatibility issue: {} ({}) {} severity={} at {}",
						issue.modName(),
						issue.modId(),
						issue.version(),
						issue.severity(),
						issue.conflictPoints()
					);
				}
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Heuristic compatibility scan failed; explicit compatibility rules remain active", exception);
			issues = explicitIssues;
		}
	}

	private static List<CompatibilityIssue> detectExplicitIssues() {
		List<CompatibilityIssue> detected = new ArrayList<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			try {
				CompatibilityIssue issue = explicitIssueFor(container);
				if (issue != null) {
					detected.add(issue);
				}
			} catch (RuntimeException exception) {
				LOGGER.debug("Could not evaluate explicit compatibility rule for a loaded mod", exception);
			}
		}
		detected.sort(Comparator.comparing(CompatibilityIssue::modName, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(detected);
	}

	public static List<CompatibilityIssue> issues() {
		initialize();
		return issues;
	}

	public static boolean hasIssues() {
		return !issues().isEmpty();
	}

	public static List<CompatibilityIssue> blockingIssues() {
		return issues().stream()
			.filter(issue -> issue.severity() == CompatibilityIssue.Severity.BLOCKING)
			.toList();
	}

	public static List<CompatibilityIssue> degradedIssues() {
		return issues().stream()
			.filter(issue -> issue.severity() == CompatibilityIssue.Severity.DEGRADED)
			.toList();
	}

	/**
	 * Total yield: any confirmed overlap disables the affected feature outright,
	 * whatever its severity.
	 *
	 * <p>When two mods write the same thing the result stops being predictable from
	 * either one's code, and an optimisation that only usually wins is worse than not
	 * competing at all. The issue is still published, so the Issues Tracker names the
	 * mod that took over and why: stepping aside quietly and stepping aside invisibly
	 * are not the same thing.</p>
	 */
	public static boolean isFeatureAvailable(final CompatibilityFeature feature) {
		return issues().stream().noneMatch(issue -> issue.affectedFeatures().contains(feature));
	}

	public static List<CompatibilityIssue> issuesAffecting(final CompatibilityFeature feature) {
		return issues().stream()
			.filter(issue -> issue.affectedFeatures().contains(feature))
			.toList();
	}

	public static boolean hasBlockingIssues() {
		return !blockingIssues().isEmpty();
	}

	public static List<CompatibilityIssue> mousePositionIssues() {
		return issues().stream()
			.filter(issue -> issue.reason() == CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE)
			.toList();
	}

	public static boolean hasModalNoticeIssues() {
		return issues().stream()
			.anyMatch(issue -> issue.severity() != CompatibilityIssue.Severity.BLOCKING
				&& issue.reason() != CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE);
	}

	public static void activateEarlyMixinGate() {
		earlyMixinGateActive = true;
	}

	public static boolean isSafelyBlocked() {
		return earlyMixinGateActive && hasBlockingIssues();
	}

	public static void enableAdaptation() {
		adaptationActive = true;
	}

	public static boolean isAdaptationActive() {
		return adaptationActive;
	}

	private static CompatibilityIssue inspectForeignMod(
		final ModContainer container,
		final Set<TargetMethod> ownHooks,
		final Set<TargetMethod> criticalOwnHooks,
		final Set<RedirectPoint> ownRedirects
	) {
		Set<String> directPoints = new LinkedHashSet<>();
		Set<String> overwritePoints = new LinkedHashSet<>();
		Set<String> blockingOverwritePoints = new LinkedHashSet<>();
		Set<String> redirectCollisionPoints = new LinkedHashSet<>();
		Set<String> mousePositionPoints = new LinkedHashSet<>();
		Set<String> blockingMousePositionPoints = new LinkedHashSet<>();
		String runtimeMouseHandlerClass = runtimeMouseHandlerClass();
		Set<String> runtimeCursorCriticalMethods = runtimeCursorCriticalMethods();
		Set<String> runtimeMousePipelineMethods = runtimeMousePipelineMethods();
		for (MixinInspection inspection : inspectMixins(container)) {
			for (String target : inspection.targets()) {
				if (target.startsWith(INTERNAL_PACKAGE)) {
					if (inspection.injectedMethods().isEmpty() && inspection.overwrittenMethods().isEmpty()) {
						directPoints.add(target);
					}
					for (String method : inspection.injectedMethods()) {
						directPoints.add(target + "#" + method);
					}
					for (String method : inspection.overwrittenMethods()) {
						directPoints.add(target + "#" + method);
					}
				}
				for (String method : inspection.overwrittenMethods()) {
					TargetMethod point = new TargetMethod(target, method);
					if (ownHooks.contains(point)) {
						overwritePoints.add(target + "#" + method);
					}
					if (criticalOwnHooks.contains(point)) {
						blockingOverwritePoints.add(target + "#" + method);
					}
				}
				for (RedirectHook redirect : inspection.redirectHooks()) {
					RedirectPoint point = new RedirectPoint(target, redirect.method(), redirect.invocationTarget());
					if (ownRedirects.contains(point)) {
						redirectCollisionPoints.add(target + "#" + redirect.method() + " -> " + redirect.invocationTarget());
					}
				}
				if (runtimeMouseHandlerClass.equals(target)) {
					Set<String> relevantMethods = new LinkedHashSet<>();
					for (String method : inspection.injectedMethods()) {
						if (runtimeCursorCriticalMethods.contains(method)) {
							relevantMethods.add(method);
						}
					}
					for (String method : inspection.overwrittenMethods()) {
						if (runtimeCursorCriticalMethods.contains(method)) {
							relevantMethods.add(method);
						}
					}
					boolean criticalCoordinateHook = inspection.criticalInjectedMethods().stream()
						.anyMatch(relevantMethods::contains);
					if (!relevantMethods.isEmpty() && (inspection.cursorPositionMutation() || criticalCoordinateHook)) {
						for (String method : relevantMethods) {
							String point = target + "#" + method;
							if (runtimeMousePipelineMethods.contains(method)
								&& inspection.criticalInjectedMethods().contains(method)) {
								blockingMousePositionPoints.add(point);
							} else {
								mousePositionPoints.add(point);
							}
						}
					}
				}
			}
		}
		CompatibilityIssue.Reason reason;
		CompatibilityIssue.Severity severity;
		List<String> points;
		if (!redirectCollisionPoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.REDIRECT_COLLISION;
			severity = CompatibilityIssue.Severity.BLOCKING;
			points = redirectCollisionPoints.stream().limit(8).toList();
		} else if (!blockingOverwritePoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.CRITICAL_OVERWRITE;
			severity = CompatibilityIssue.Severity.BLOCKING;
			points = blockingOverwritePoints.stream().limit(8).toList();
		} else if (!blockingMousePositionPoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE;
			severity = CompatibilityIssue.Severity.BLOCKING;
			points = blockingMousePositionPoints.stream().limit(8).toList();
		} else if (!directPoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.DIRECT_MUTATION;
			severity = CompatibilityIssue.Severity.DEGRADED;
			points = directPoints.stream().limit(8).toList();
		} else if (!mousePositionPoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE;
			severity = CompatibilityIssue.Severity.DEGRADED;
			points = mousePositionPoints.stream().limit(8).toList();
		} else if (!overwritePoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.CRITICAL_OVERWRITE;
			severity = CompatibilityIssue.Severity.DEGRADED;
			points = overwritePoints.stream().limit(8).toList();
		} else {
			return null;
		}
		String creators = container.getMetadata().getAuthors().stream()
			.map(Person::getName)
			.filter(name -> !name.isBlank())
			.reduce((left, right) -> left + ", " + right)
			.orElse("Unknown");
		return new CompatibilityIssue(
			container.getMetadata().getId(),
			container.getMetadata().getName(),
			container.getMetadata().getVersion().getFriendlyString(),
			creators,
			severity,
			reason,
			points,
			affectedFeaturesFor(reason, points)
		);
	}

	private static CompatibilityIssue explicitIssueFor(final ModContainer container) {
		String modId = container.getMetadata().getId();
		if (KOHS_SYNAPSE_ID.equals(modId)) {
			return new CompatibilityIssue(
				container.getMetadata().getId(),
				container.getMetadata().getName(),
				container.getMetadata().getVersion().getFriendlyString(),
				creatorsOf(container),
				CompatibilityIssue.Severity.DEGRADED,
				CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE,
				List.of(
					"net.minecraft.client.Minecraft#setScreen",
					"net.minecraft.client.MouseHandler#onMove"
				),
				Set.of(CompatibilityFeature.CURSOR_LANDING)
			);
		}
		if (IXERIS_ID.equals(modId)) {
			ModContainer rawInputBuffer = FabricLoader.getInstance().getModContainer(RAW_INPUT_BUFFER_ID).orElse(null);
			if (rawInputBuffer != null) {
				return rawInputPipelineCollision(container, rawInputBuffer);
			}
		}
		if (RAW_INPUT_BUFFER_ID.equals(modId) && FabricLoader.getInstance().isModLoaded(IXERIS_ID)) {
			return null;
		}
		if (RAW_INPUT_BUFFER_ID.equals(modId)) {
			return new CompatibilityIssue(
				container.getMetadata().getId(),
				container.getMetadata().getName(),
				container.getMetadata().getVersion().getFriendlyString(),
				creatorsOf(container),
				CompatibilityIssue.Severity.DEGRADED,
				CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE,
				List.of(
					"walksy.rawinput.RawInputHandler#handleRawInput -> User32.SetCursorPos",
					"net.minecraft.client.MouseHandler#releaseMouse"
				),
				Set.of(CompatibilityFeature.CURSOR_LANDING)
			);
		}
		if (BETTER_SCREENS_ID.equals(container.getMetadata().getId())) {
			return new CompatibilityIssue(
				container.getMetadata().getId(),
				container.getMetadata().getName(),
				container.getMetadata().getVersion().getFriendlyString(),
				creatorsOf(container),
				CompatibilityIssue.Severity.BLOCKING,
				CompatibilityIssue.Reason.CONTAINER_SCALE_PIPELINE,
				List.of(
					"net.minecraft.client.gui.Gui#extractRenderState",
					"net.minecraft.client.MouseHandler#getScaledXPos",
					"net.minecraft.client.MouseHandler#getScaledYPos",
					"net.minecraft.client.renderer.GameRenderer#render"
				),
				Set.of(
					CompatibilityFeature.CURSOR_LANDING,
					CompatibilityFeature.INVENTORY_TWEAKS,
					CompatibilityFeature.CUSTOMIZATION,
					CompatibilityFeature.ITEM_HIGHLIGHTER,
					CompatibilityFeature.GUI_SCALER
				)
			);
		}
		if (!INVENTORY_SCALE_FIX_ID.equals(container.getMetadata().getId())) {
			return null;
		}
		// Confirmed redirect overlap. KoHs owns this invocation at a higher
		// priority, so only the foreign redirect is suppressed.
		return new CompatibilityIssue(
			container.getMetadata().getId(),
			container.getMetadata().getName(),
			container.getMetadata().getVersion().getFriendlyString(),
			creatorsOf(container),
			CompatibilityIssue.Severity.ADAPTABLE,
			CompatibilityIssue.Reason.SUPPRESSED_REDIRECT,
			List.of("net.minecraft.client.gui.screens.inventory.InventoryScreen#renderBackground -> " + INVENTORY_ENTITY_INVOCATION),
			Set.of(CompatibilityFeature.GUI_SCALER)
		);
	}

	private static CompatibilityIssue rawInputPipelineCollision(
		final ModContainer ixeris,
		final ModContainer rawInputBuffer
	) {
		return new CompatibilityIssue(
			IXERIS_ID,
			"Ixeris + Raw Input Buffer",
			ixeris.getMetadata().getVersion().getFriendlyString() + " + "
				+ rawInputBuffer.getMetadata().getVersion().getFriendlyString(),
			creatorsOf(ixeris) + " / " + creatorsOf(rawInputBuffer),
			CompatibilityIssue.Severity.BLOCKING,
			CompatibilityIssue.Reason.RAW_INPUT_PIPELINE_COLLISION,
			List.of(
				"Ixeris: Windows Raw Input -> RegisterRawInputDevices(NOLEGACY)",
				"Raw Input Buffer: Windows Raw Input -> RegisterRawInputDevices(RIDEV_NOLEGACY)",
				"net.minecraft.client.MouseHandler#setup/grabMouse/releaseMouse"
			),
			Set.of(CompatibilityFeature.CURSOR_LANDING, CompatibilityFeature.INVENTORY_TWEAKS)
		);
	}

	private static Set<CompatibilityFeature> affectedFeaturesFor(
		final CompatibilityIssue.Reason reason,
		final List<String> points
	) {
		if (reason == CompatibilityIssue.Reason.RAW_INPUT_PIPELINE_COLLISION) {
			return Set.of(CompatibilityFeature.CURSOR_LANDING, CompatibilityFeature.INVENTORY_TWEAKS);
		}
		if (reason == CompatibilityIssue.Reason.MOUSE_POSITION_OVERRIDE) {
			return Set.of(CompatibilityFeature.CURSOR_LANDING);
		}
		if (reason == CompatibilityIssue.Reason.CONTAINER_SCALE_PIPELINE) {
			return Set.of(CompatibilityFeature.GUI_SCALER, CompatibilityFeature.CURSOR_LANDING);
		}
		if (reason == CompatibilityIssue.Reason.REDIRECT_COLLISION
			|| reason == CompatibilityIssue.Reason.SUPPRESSED_REDIRECT) {
			return Set.of(CompatibilityFeature.GUI_SCALER);
		}

		Set<CompatibilityFeature> affected = new LinkedHashSet<>();
		for (String point : points) {
			String normalized = point.toLowerCase(java.util.Locale.ROOT);
			if (normalized.contains("cursor") || normalized.contains("mousehandler")) {
				affected.add(CompatibilityFeature.CURSOR_LANDING);
			}
			if (normalized.contains("superfast") || normalized.contains("keyboardhandler")
				|| normalized.contains("animation")) {
				affected.add(CompatibilityFeature.INVENTORY_TWEAKS);
			}
			if (normalized.contains("texture") || normalized.contains("customization")
				|| normalized.contains("guigraphics")) {
				affected.add(CompatibilityFeature.CUSTOMIZATION);
			}
			if (normalized.contains("itemhighlighter") || normalized.contains("hotbar")) {
				affected.add(CompatibilityFeature.ITEM_HIGHLIGHTER);
			}
			if (normalized.contains("scale") || normalized.contains("inventoryscreen")
				|| normalized.contains("abstractcontainerscreen")) {
				affected.add(CompatibilityFeature.GUI_SCALER);
			}
		}
		if (affected.isEmpty()) {
			return Set.of(
				CompatibilityFeature.CURSOR_LANDING,
				CompatibilityFeature.INVENTORY_TWEAKS,
				CompatibilityFeature.CUSTOMIZATION,
				CompatibilityFeature.ITEM_HIGHLIGHTER,
				CompatibilityFeature.GUI_SCALER
			);
		}
		return Set.copyOf(affected);
	}

	private static String creatorsOf(final ModContainer container) {
		return container.getMetadata().getAuthors().stream()
			.map(Person::getName)
			.filter(name -> !name.isBlank())
			.reduce((left, right) -> left + ", " + right)
			.orElse("Unknown");
	}

	private static String runtimeMouseHandlerClass() {
		var resolver = FabricLoader.getInstance().getMappingResolver();
		if (resolver.getNamespaces().contains("named")) {
			return resolver.mapClassName("named", MOUSE_HANDLER_CLASS);
		}
		return "named".equals(resolver.getCurrentRuntimeNamespace())
			? MOUSE_HANDLER_CLASS
			: MOUSE_HANDLER_INTERMEDIARY_CLASS;
	}

	private static String runtimeInputConstantsOwner() {
		var resolver = FabricLoader.getInstance().getMappingResolver();
		String className;
		if (resolver.getNamespaces().contains("named")) {
			className = resolver.mapClassName("named", INPUT_CONSTANTS_CLASS);
		} else {
			className = "named".equals(resolver.getCurrentRuntimeNamespace())
				? INPUT_CONSTANTS_CLASS
				: INPUT_CONSTANTS_INTERMEDIARY_CLASS;
		}
		return className.replace('.', '/');
	}

	private static String runtimeInputGrabMethod() {
		var resolver = FabricLoader.getInstance().getMappingResolver();
		if (resolver.getNamespaces().contains("named")) {
			return resolver.mapMethodName(
				"named",
				INPUT_CONSTANTS_CLASS,
				"grabOrReleaseMouse",
				"(Lcom/mojang/blaze3d/platform/Window;IDD)V"
			);
		}
		return "named".equals(resolver.getCurrentRuntimeNamespace()) ? "grabOrReleaseMouse" : "method_15984";
	}

	private static String runtimeMouseHandlerOwner() {
		return runtimeMouseHandlerClass().replace('.', '/');
	}

	private static Set<String> runtimeMouseCoordinateFields() {
		var resolver = FabricLoader.getInstance().getMappingResolver();
		if (!resolver.getNamespaces().contains("named")) {
			return "named".equals(resolver.getCurrentRuntimeNamespace())
				? Set.of("xpos", "ypos")
				: INTERMEDIARY_MOUSE_COORDINATE_FIELDS;
		}
		return Set.of(
			resolver.mapFieldName("named", MOUSE_HANDLER_CLASS, "xpos", "D"),
			resolver.mapFieldName("named", MOUSE_HANDLER_CLASS, "ypos", "D")
		);
	}

	private static Set<String> runtimeCursorCriticalMethods() {
		var resolver = FabricLoader.getInstance().getMappingResolver();
		if (!resolver.getNamespaces().contains("named")) {
			return "named".equals(resolver.getCurrentRuntimeNamespace())
				? Set.of("setup", "grabMouse", "releaseMouse", "onMove")
				: INTERMEDIARY_CURSOR_CRITICAL_METHODS;
		}
		return Set.of(
			resolver.mapMethodName("named", MOUSE_HANDLER_CLASS, "setup", "(Lcom/mojang/blaze3d/platform/Window;)V"),
			resolver.mapMethodName("named", MOUSE_HANDLER_CLASS, "grabMouse", "()V"),
			resolver.mapMethodName("named", MOUSE_HANDLER_CLASS, "releaseMouse", "()V"),
			resolver.mapMethodName("named", MOUSE_HANDLER_CLASS, "onMove", "(JDD)V")
		);
	}

	private static Set<String> runtimeMousePipelineMethods() {
		var resolver = FabricLoader.getInstance().getMappingResolver();
		if (!resolver.getNamespaces().contains("named")) {
			return "named".equals(resolver.getCurrentRuntimeNamespace())
				? Set.of("setup", "onMove")
				: INTERMEDIARY_MOUSE_PIPELINE_METHODS;
		}
		return Set.of(
			resolver.mapMethodName("named", MOUSE_HANDLER_CLASS, "setup", "(Lcom/mojang/blaze3d/platform/Window;)V"),
			resolver.mapMethodName("named", MOUSE_HANDLER_CLASS, "onMove", "(JDD)V")
		);
	}

	private static List<MixinInspection> inspectMixins(final ModContainer container) {
		List<MixinInspection> inspections = new ArrayList<>();
		for (Path root : container.getRootPaths()) {
			Path metadataPath = root.resolve("fabric.mod.json");
			if (!Files.isRegularFile(metadataPath)) {
				continue;
			}
			try {
				JsonObject metadata = JsonParser.parseString(Files.readString(metadataPath)).getAsJsonObject();
				JsonElement mixinsElement = metadata.get("mixins");
				if (mixinsElement == null || mixinsElement.isJsonNull()) {
					continue;
				}
				Iterable<JsonElement> entries = mixinsElement.isJsonArray()
					? mixinsElement.getAsJsonArray()
					: List.of(mixinsElement);
				for (JsonElement entry : entries) {
					if (!entry.isJsonPrimitive() && !entry.isJsonObject()) {
						continue;
					}
					String configPath = entry.isJsonPrimitive()
						? entry.getAsString()
						: stringMember(entry.getAsJsonObject(), "config");
					if (configPath == null || configPath.isBlank()) {
						continue;
					}
					inspectMixinConfig(container.getRootPaths(), root, configPath, inspections);
				}
			} catch (IOException | RuntimeException exception) {
					LOGGER.debug(
					"Could not inspect mixins for {} at {}",
					container.getMetadata().getId(),
					root,
					exception
				);
			}
		}
		return inspections;
	}

	private static void inspectMixinConfig(
		final List<Path> roots,
		final Path resourceRoot,
		final String configPath,
		final List<MixinInspection> inspections
	) throws IOException {
		Path path = resourceRoot.resolve(configPath);
		if (!Files.isRegularFile(path)) {
			return;
		}
		JsonObject config = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
		String mixinPackage = stringMember(config, "package");
		if (mixinPackage == null) {
			mixinPackage = "";
		}
		for (String key : List.of("mixins", "client")) {
			JsonArray classes = config.has(key) && config.get(key).isJsonArray() ? config.getAsJsonArray(key) : new JsonArray();
			for (JsonElement classNameElement : classes) {
				String className = classNameElement.getAsString();
				String qualified = mixinPackage.isBlank() ? className : mixinPackage + "." + className;
				Path classPath = null;
				for (Path root : roots) {
					Path candidate = root.resolve(qualified.replace('.', '/') + ".class");
					if (Files.isRegularFile(candidate)) {
						classPath = candidate;
						break;
					}
				}
				if (classPath == null) {
					continue;
				}
				try (InputStream input = Files.newInputStream(classPath)) {
					MixinClassVisitor visitor = new MixinClassVisitor();
					new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					if (!visitor.targets.isEmpty()) {
						inspections.add(new MixinInspection(
							visitor.targets,
							visitor.injectedMethods,
							visitor.criticalInjectedMethods,
							visitor.redirectHooks,
							visitor.overwrittenMethods,
							visitor.cursorPositionMutation
						));
					}
				}
			}
		}
	}

	private static String stringMember(final JsonObject object, final String name) {
		JsonElement value = object.get(name);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static String normalizeMethod(final String selector) {
		String method = selector;
		int ownerEnd = method.lastIndexOf(';');
		if (ownerEnd >= 0 && ownerEnd + 1 < method.length()) {
			method = method.substring(ownerEnd + 1);
		}
		int descriptor = method.indexOf('(');
		if (descriptor >= 0) {
			method = method.substring(0, descriptor);
		}
		int quantifier = method.indexOf('{');
		if (quantifier >= 0) {
			method = method.substring(0, quantifier);
		}
		return method.trim();
	}

	private static final class MixinClassVisitor extends ClassVisitor {
		private final Set<String> targets = new LinkedHashSet<>();
		private final Set<String> injectedMethods = new LinkedHashSet<>();
		private final Set<String> criticalInjectedMethods = new LinkedHashSet<>();
		private final Set<RedirectHook> redirectHooks = new LinkedHashSet<>();
		private final Set<String> overwrittenMethods = new LinkedHashSet<>();
		private final String inputConstantsOwner = runtimeInputConstantsOwner();
		private final String inputGrabMethod = runtimeInputGrabMethod();
		private final String mouseHandlerOwner = runtimeMouseHandlerOwner();
		private final Set<String> mouseCoordinateFields = runtimeMouseCoordinateFields();
		private boolean cursorPositionMutation;

		private MixinClassVisitor() {
			super(Opcodes.ASM9);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
			if (!MIXIN_DESCRIPTOR.equals(descriptor)) {
				return null;
			}
			return new AnnotationVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitArray(final String name) {
					if (!"value".equals(name) && !"targets".equals(name)) {
						return null;
					}
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override
						public void visit(final String ignored, final Object value) {
							if (value instanceof Type type) {
								targets.add(type.getClassName());
							} else if (value instanceof String target) {
								targets.add(target.replace('/', '.'));
							}
						}
					};
				}
			};
		}

		@Override
		public MethodVisitor visitMethod(
			final int access,
			final String name,
			final String descriptor,
			final String signature,
			final String[] exceptions
		) {
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(
					final int opcode,
					final String owner,
					final String methodName,
					final String methodDescriptor,
					final boolean isInterface
				) {
					if (("org/lwjgl/glfw/GLFW".equals(owner) && "glfwSetCursorPos".equals(methodName))
						|| (owner.endsWith("/User32") && "SetCursorPos".equals(methodName))
						|| (inputConstantsOwner.equals(owner)
							&& inputGrabMethod.equals(methodName))) {
						cursorPositionMutation = true;
					}
				}

				@Override
				public void visitFieldInsn(
					final int opcode,
					final String owner,
					final String fieldName,
					final String fieldDescriptor
				) {
					if ((opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)
						&& mouseHandlerOwner.equals(owner)
						&& mouseCoordinateFields.contains(fieldName)) {
						cursorPositionMutation = true;
					}
				}

				@Override
				public AnnotationVisitor visitAnnotation(final String annotationDescriptor, final boolean visible) {
					if (OVERWRITE_DESCRIPTOR.equals(annotationDescriptor)) {
						overwrittenMethods.add(normalizeMethod(name));
						return null;
					}
					if (!annotationDescriptor.startsWith("Lorg/spongepowered/asm/mixin/injection/")
						&& !annotationDescriptor.startsWith(MIXINEXTRAS_INJECTION_PREFIX)) {
						return null;
					}
					return new InjectionAnnotationVisitor(annotationDescriptor);
				}
			};
		}

		private final class InjectionAnnotationVisitor extends AnnotationVisitor {
			private boolean critical;
			private final boolean redirect;
			private final Set<String> methods = new LinkedHashSet<>();
			private String invocationTarget;

			private InjectionAnnotationVisitor(final String descriptor) {
				super(Opcodes.ASM9);
				this.critical = REDIRECT_DESCRIPTOR.equals(descriptor) || WRAP_METHOD_DESCRIPTOR.equals(descriptor);
				this.redirect = REDIRECT_DESCRIPTOR.equals(descriptor);
			}

			@Override
			public void visit(final String name, final Object value) {
				if ("method".equals(name) && value instanceof String selector) {
					methods.add(normalizeMethod(selector));
				} else if ("cancellable".equals(name) && Boolean.TRUE.equals(value)) {
					critical = true;
				}
			}

			@Override
			public AnnotationVisitor visitArray(final String name) {
				if ("method".equals(name)) {
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override
						public void visit(final String ignored, final Object value) {
							if (value instanceof String selector) {
								methods.add(normalizeMethod(selector));
							}
						}
					};
				}
				if ("at".equals(name)) {
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitAnnotation(final String ignored, final String descriptor) {
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public void visit(final String property, final Object value) {
									if ("target".equals(property) && value instanceof String target) {
										invocationTarget = target.trim();
									}
								}
							};
						}
					};
				}
				return null;
			}

			@Override
			public void visitEnd() {
				injectedMethods.addAll(this.methods);
				if (this.critical) {
					criticalInjectedMethods.addAll(this.methods);
				}
				if (this.redirect && this.invocationTarget != null && !this.invocationTarget.isBlank()) {
					for (String method : this.methods) {
						redirectHooks.add(new RedirectHook(method, this.invocationTarget));
					}
				}
			}
		}
	}

	private record MixinInspection(
		Set<String> targets,
		Set<String> injectedMethods,
		Set<String> criticalInjectedMethods,
		Set<RedirectHook> redirectHooks,
		Set<String> overwrittenMethods,
		boolean cursorPositionMutation
	) {
		private MixinInspection {
			targets = Set.copyOf(targets);
			injectedMethods = Set.copyOf(injectedMethods);
			criticalInjectedMethods = Set.copyOf(criticalInjectedMethods);
			redirectHooks = Set.copyOf(redirectHooks);
			overwrittenMethods = Set.copyOf(overwrittenMethods);
		}
	}

	private record TargetMethod(String target, String method) {
	}

	private record RedirectHook(String method, String invocationTarget) {
	}

	private record RedirectPoint(String target, String method, String invocationTarget) {
	}
}
