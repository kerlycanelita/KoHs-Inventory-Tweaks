package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.render.VisiblePlayerGlowController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A render-state-only walking preview; the live player entity is never mutated. */
public final class AnimatedPlayerPreview {
	private AnimatedPlayerPreview() {
	}

	public static void draw(
		final GuiGraphics graphics,
		final LivingEntity entity,
		final int left,
		final int top,
		final int right,
		final int bottom,
		final int size,
		final InventoryTweaksConfig config
	) {
		Minecraft minecraft = Minecraft.getInstance();
		EntityRenderState state = minecraft.getEntityRenderDispatcher().extractEntity(entity, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		float time = System.nanoTime() / 1_000_000_000.0F;
		if (state instanceof LivingEntityRenderState living) {
			float turn = (float) Math.sin(time * 0.72F) * 8.0F;
			living.bodyRot = 180.0F + turn;
			living.yRot = turn;
			living.xRot = 0.0F;
			living.walkAnimationPos = time * 7.2F;
			living.walkAnimationSpeed = 0.78F;
			living.ageInTicks = time * 20.0F;
			living.boundingBoxWidth /= Math.max(0.001F, living.scale);
			living.boundingBoxHeight /= Math.max(0.001F, living.scale);
			living.scale = 1.0F;
		}
		if (state instanceof AvatarRenderState avatar) {
			VisiblePlayerGlowController.registerPreview(avatar, config);
		}
		Quaternionf orientation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-4.0F));
		Vector3f offset = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		graphics.submitEntityRenderState(state, Math.max(1, size), offset, orientation, camera, left, top, right, bottom);
	}
}

