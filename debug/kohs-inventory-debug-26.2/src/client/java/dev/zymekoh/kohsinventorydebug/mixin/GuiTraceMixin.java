package dev.zymekoh.kohsinventorydebug.mixin;

import dev.zymekoh.kohsinventorydebug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen lifecycle instrumentation for the 26.2 Gui owner. */
@Mixin(value = Gui.class, priority = 3000)
abstract class GuiTraceMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void kohsInventoryDebug$screenHead(final @Nullable Screen screen, final CallbackInfo callbackInfo) {
		DebugCollector.onSetScreen(Minecraft.getInstance(), screen, false);
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void kohsInventoryDebug$screenReturn(final @Nullable Screen screen, final CallbackInfo callbackInfo) {
		DebugCollector.onSetScreen(Minecraft.getInstance(), screen, true);
	}
}
