package dev.zymekoh.kohsinventorytweaks.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class InventoryTweaksMixinPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(final String mixinPackage) {
		CompatibilityIssueManager.initialize();
		CompatibilityIssueManager.activateEarlyMixinGate();
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
		return !CompatibilityIssueManager.isSafelyBlocked();
	}

	@Override
	public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(
		final String targetClassName,
		final ClassNode targetClass,
		final String mixinClassName,
		final IMixinInfo mixinInfo
	) {
	}

	@Override
	public void postApply(
		final String targetClassName,
		final ClassNode targetClass,
		final String mixinClassName,
		final IMixinInfo mixinInfo
	) {
	}
}
