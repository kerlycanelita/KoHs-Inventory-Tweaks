package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Picture-in-picture states store screen coordinates and do not consume the GUI pose. */
@Mixin(GuiGraphics.class)
public abstract class ContainerSceneMixin {
	@ModifyArgs(method = "submitSkinRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/state/pip/GuiSkinRenderState;<init>(Lnet/minecraft/client/model/player/PlayerModel;Lnet/minecraft/resources/Identifier;FFFIIIIFLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
	private void kohsInventoryTweaks$skin(final Args args) {
		if (!InventoryGuiScaler.isScaledSurfaceActive()) return;
		args.set(5, InventoryGuiScaler.toScreenX(args.get(5)));
		args.set(6, InventoryGuiScaler.toScreenY(args.get(6)));
		args.set(7, InventoryGuiScaler.toScreenX(args.get(7)));
		args.set(8, InventoryGuiScaler.toScreenY(args.get(8)));
		args.set(9, (float) ((float) args.get(9) * InventoryGuiScaler.activeSurfaceScale()));
	}
	@ModifyArgs(method = "submitEntityRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/state/pip/GuiEntityRenderState;<init>(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;IIIIFLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
	private void kohsInventoryTweaks$entity(final Args args) {
		if (!InventoryGuiScaler.isScaledSurfaceActive()) return;
		args.set(4, InventoryGuiScaler.toScreenX(args.get(4)));
		args.set(5, InventoryGuiScaler.toScreenY(args.get(5)));
		args.set(6, InventoryGuiScaler.toScreenX(args.get(6)));
		args.set(7, InventoryGuiScaler.toScreenY(args.get(7)));
		args.set(8, (float) ((float) args.get(8) * InventoryGuiScaler.activeSurfaceScale()));
	}

	@ModifyArgs(method = "submitBookModelRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/state/pip/GuiBookModelRenderState;<init>(Lnet/minecraft/client/model/object/book/BookModel;Lnet/minecraft/resources/Identifier;FFIIIIFLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
	private void kohsInventoryTweaks$book(final Args args) {
		if (!InventoryGuiScaler.isScaledSurfaceActive()) return;
		args.set(4, InventoryGuiScaler.toScreenX(args.get(4)));
		args.set(5, InventoryGuiScaler.toScreenY(args.get(5)));
		args.set(6, InventoryGuiScaler.toScreenX(args.get(6)));
		args.set(7, InventoryGuiScaler.toScreenY(args.get(7)));
		args.set(8, (float) ((float) args.get(8) * InventoryGuiScaler.activeSurfaceScale()));
	}

	@ModifyArgs(method = "submitBannerPatternRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/state/pip/GuiBannerResultRenderState;<init>(Lnet/minecraft/client/model/object/banner/BannerFlagModel;Lnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;IIIILnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
	private void kohsInventoryTweaks$banner(final Args args) {
		if (!InventoryGuiScaler.isScaledSurfaceActive()) return;
		args.set(3, InventoryGuiScaler.toScreenX(args.get(3)));
		args.set(4, InventoryGuiScaler.toScreenY(args.get(4)));
		args.set(5, InventoryGuiScaler.toScreenX(args.get(5)));
		args.set(6, InventoryGuiScaler.toScreenY(args.get(6)));
	}

	@ModifyArgs(method = "submitSignRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/state/pip/GuiSignRenderState;<init>(Lnet/minecraft/client/model/Model$Simple;Lnet/minecraft/world/level/block/state/properties/WoodType;IIIIFLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
	private void kohsInventoryTweaks$sign(final Args args) {
		if (!InventoryGuiScaler.isScaledSurfaceActive()) return;
		args.set(2, InventoryGuiScaler.toScreenX(args.get(2)));
		args.set(3, InventoryGuiScaler.toScreenY(args.get(3)));
		args.set(4, InventoryGuiScaler.toScreenX(args.get(4)));
		args.set(5, InventoryGuiScaler.toScreenY(args.get(5)));
		args.set(6, (float) ((float) args.get(6) * InventoryGuiScaler.activeSurfaceScale()));
	}
}
