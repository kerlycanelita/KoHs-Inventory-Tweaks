package dev.zymekoh.kohsinventorydebug.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingDebugAccessor {
	@Accessor("clickCount")
	int kohsInventoryDebug$getClickCount();

	@Accessor("key")
	InputConstants.Key kohsInventoryDebug$getKey();
}
