package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenCreative;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Creative inventory, laid out and drawn by {@link NextGenCreative} when a style is chosen. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class NextGenCreativeMixin {
	private static final int VANILLA_MODEL_X = 73;
	private static final int VANILLA_MODEL_Y = 6;

	@Shadow
	private static CreativeModeTab selectedTab;

	@Inject(method = "selectTab", at = @At("RETURN"))
	private void kohsInventoryTweaks$moveSlots(final CreativeModeTab tab, final CallbackInfo callbackInfo) {
		NextGenCreative.apply((CreativeModeInventoryScreen) (Object) this);
	}

	@Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$noTitleWithoutAPanel(final GuiGraphics graphics, final int mouseX, final int mouseY, final CallbackInfo callbackInfo) {
		if (NextGenCreative.isFloating((CreativeModeInventoryScreen) (Object) this)) {
			callbackInfo.cancel();
		}
	}

	@WrapOperation(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
		)
	)
	private void kohsInventoryTweaks$drawLayoutPanel(
		final GuiGraphics graphics,
		final RenderPipeline pipeline,
		final Identifier texture,
		final int x,
		final int y,
		final float u,
		final float v,
		final int width,
		final int height,
		final int textureWidth,
		final int textureHeight,
		final Operation<Void> original
	) {
		if (!NextGenCreative.drawPanel(graphics, (CreativeModeInventoryScreen) (Object) this, pipeline, texture, selectedTab, x, y)) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		}
	}

	@WrapOperation(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
		)
	)
	private void kohsInventoryTweaks$moveScrollbarHandle(
		final GuiGraphics graphics,
		final RenderPipeline pipeline,
		final Identifier sprite,
		final int x,
		final int y,
		final int width,
		final int height,
		final Operation<Void> original
	) {
		int moved = x - 175 + NextGenCreative.scrollbarX((CreativeModeInventoryScreen) (Object) this);
		original.call(graphics, pipeline, sprite, moved, y, width, height);
	}

	@WrapOperation(
		method = "renderBg",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"
		)
	)
	private void kohsInventoryTweaks$moveModel(
		final GuiGraphics graphics,
		final int x0,
		final int y0,
		final int x1,
		final int y1,
		final int size,
		final float offsetY,
		final float mouseX,
		final float mouseY,
		final LivingEntity entity,
		final Operation<Void> original
	) {
		int[] box = NextGenCreative.modelBox((CreativeModeInventoryScreen) (Object) this);
		if (box == null) {
			original.call(graphics, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity);
		} else if (box[4] > 0) {
			int left = x0 - VANILLA_MODEL_X;
			int top = y0 - VANILLA_MODEL_Y;
			original.call(graphics, left + box[0], top + box[1], left + box[2], top + box[3], box[4], offsetY, mouseX, mouseY, entity);
		}
	}

	@Inject(method = "insideScrollbar", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$scrollbarWhereItIsDrawn(final double x, final double y, final CallbackInfoReturnable<Boolean> callbackInfo) {
		CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
		int left = ((AbstractContainerScreenAccessor) screen).kohsInventoryTweaks$getLeftPos();
		int top = ((AbstractContainerScreenAccessor) screen).kohsInventoryTweaks$getTopPos();
		int scrollLeft = left + NextGenCreative.scrollbarX(screen);
		callbackInfo.setReturnValue(x >= scrollLeft && y >= top + 18 && x < scrollLeft + 14 && y < top + 130 && selectedTab.canScroll());
	}

	@Inject(method = "getTabX", at = @At("RETURN"), cancellable = true)
	private void kohsInventoryTweaks$tabStaysWithItsHalf(final CreativeModeTab tab, final CallbackInfoReturnable<Integer> callbackInfo) {
		int x = callbackInfo.getReturnValueI();
		callbackInfo.setReturnValue(x + NextGenCreative.tabShift((CreativeModeInventoryScreen) (Object) this, x));
	}

	@ModifyVariable(method = "hasClickedOutside", at = @At("STORE"), ordinal = 0)
	private boolean kohsInventoryTweaks$outsideTheLayout(final boolean vanilla, final double mouseX, final double mouseY, final int left, final int top) {
		return NextGenCreative.clickedOutside((CreativeModeInventoryScreen) (Object) this, vanilla, mouseX, mouseY, left, top);
	}
}
