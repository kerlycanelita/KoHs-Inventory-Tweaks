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
	private static final String INVENTORY_SCALE_FIX_ID = "inventoryscalefix";
	private static final String INVENTORY_SCALE_FIX_CRASH_VERSION = "1.0.0+mc26.1.2";
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
		try {
			ModContainer ownContainer = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.orElse(null);
			if (ownContainer == null) {
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

			List<CompatibilityIssue> detected = new ArrayList<>();
			for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
				String modId = container.getMetadata().getId();
				if (modId.equals(MOD_ID) || modId.startsWith("fabric-") || modId.equals("minecraft")) {
					continue;
				}
				CompatibilityIssue issue = explicitIssueFor(container);
				if (issue == null) {
					issue = inspectForeignMod(container, ownHooks, criticalOwnHooks, ownRedirects);
				}
				if (issue != null) {
					detected.add(issue);
				}
			}
			detected.sort(Comparator.comparing(CompatibilityIssue::modName, String.CASE_INSENSITIVE_ORDER));
			issues = List.copyOf(detected);
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
			LOGGER.warn("Compatibility scan failed open; normal startup will continue", exception);
			issues = List.of();
		}
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

	public static boolean hasBlockingIssues() {
		return !blockingIssues().isEmpty();
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
		} else if (!directPoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.DIRECT_MUTATION;
			severity = CompatibilityIssue.Severity.ADAPTABLE;
			points = directPoints.stream().limit(8).toList();
		} else if (!overwritePoints.isEmpty()) {
			reason = CompatibilityIssue.Reason.CRITICAL_OVERWRITE;
			severity = CompatibilityIssue.Severity.ADAPTABLE;
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
			points
		);
	}

	private static CompatibilityIssue explicitIssueFor(final ModContainer container) {
		if (!INVENTORY_SCALE_FIX_ID.equals(container.getMetadata().getId())
			|| !INVENTORY_SCALE_FIX_CRASH_VERSION.equals(container.getMetadata().getVersion().getFriendlyString())) {
			return null;
		}
		return new CompatibilityIssue(
			container.getMetadata().getId(),
			container.getMetadata().getName(),
			container.getMetadata().getVersion().getFriendlyString(),
			creatorsOf(container),
			CompatibilityIssue.Severity.BLOCKING,
			CompatibilityIssue.Reason.REDIRECT_COLLISION,
			List.of("net.minecraft.client.gui.screens.inventory.InventoryScreen#renderBackground -> " + INVENTORY_ENTITY_INVOCATION)
		);
	}

	private static String creatorsOf(final ModContainer container) {
		return container.getMetadata().getAuthors().stream()
			.map(Person::getName)
			.filter(name -> !name.isBlank())
			.reduce((left, right) -> left + ", " + right)
			.orElse("Unknown");
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
				if (mixinsElement == null || !mixinsElement.isJsonArray()) {
					continue;
				}
				for (JsonElement entry : mixinsElement.getAsJsonArray()) {
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
					new ClassReader(input).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					if (!visitor.targets.isEmpty()) {
						inspections.add(new MixinInspection(
							visitor.targets,
							visitor.injectedMethods,
							visitor.criticalInjectedMethods,
							visitor.redirectHooks,
							visitor.overwrittenMethods
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
				public AnnotationVisitor visitAnnotation(final String annotationDescriptor, final boolean visible) {
					if (OVERWRITE_DESCRIPTOR.equals(annotationDescriptor)) {
						overwrittenMethods.add(normalizeMethod(name));
						return null;
					}
					if (!annotationDescriptor.startsWith("Lorg/spongepowered/asm/mixin/injection/")) {
						return null;
					}
					return new InjectionAnnotationVisitor(annotationDescriptor);
				}
			};
		}

		private final class InjectionAnnotationVisitor extends AnnotationVisitor {
			private final boolean critical;
			private final boolean redirect;
			private final Set<String> methods = new LinkedHashSet<>();
			private String invocationTarget;

			private InjectionAnnotationVisitor(final String descriptor) {
				super(Opcodes.ASM9);
				this.critical = !descriptor.endsWith("/Inject;");
				this.redirect = REDIRECT_DESCRIPTOR.equals(descriptor);
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
		Set<String> overwrittenMethods
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
