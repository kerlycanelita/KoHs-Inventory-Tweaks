package dev.zymekoh.kohsinventorytweaks.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int kohsInventoryTweaks$getClickCount();

	@Accessor("key")
	InputConstants.Key kohsInventoryTweaks$getKey();
}
