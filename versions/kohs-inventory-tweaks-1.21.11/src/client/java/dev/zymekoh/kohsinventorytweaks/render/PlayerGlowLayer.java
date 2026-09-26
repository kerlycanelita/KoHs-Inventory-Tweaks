package dev.zymekoh.kohsinventorytweaks.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * A glowing silhouette around normally visible players while the inventory is open.
 *
 * <p>Two inflated copies of the player model are drawn inside out on the
 * {@code eyes} pipeline: emissive, translucent, depth-tested and never writing
 * depth. The body in front hides their far side, so only a rim outside the
 * silhouette shows, and blocks occlude that rim exactly like the body. This is
 * deliberately not Minecraft's glowing outline, which is drawn through walls.
 * The shells grow by less than the 1.0 Vanilla gives outer armor, so the rim
 * never reaches past where armor would already be drawn.</p>
 */
public final class PlayerGlowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
		"kohs_inventory_tweaks", "textures/misc/player_glow.png"
	);
	private static final float INNER_GROWTH = 0.35F;
	private static final float OUTER_GROWTH = 0.85F;
	private static final float OUTER_ALPHA = 0.42F;

	private final PlayerModel innerWide = shell(INNER_GROWTH, false);
	private final PlayerModel outerWide = shell(OUTER_GROWTH, false);
	private final PlayerModel innerSlim = shell(INNER_GROWTH, true);
	private final PlayerModel outerSlim = shell(OUTER_GROWTH, true);

	public PlayerGlowLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	private static PlayerModel shell(final float growth, final boolean slim) {
		return new PlayerModel(
			LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(growth), slim), 64, 64).bakeRoot(),
			slim
		);
	}

	@Override
	public void submit(
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final int light,
		final AvatarRenderState state,
		final float yRot,
		final float xRot
	) {
		int color = VisiblePlayerGlowController.silhouetteColor(state);
		if (color >>> 24 == 0) {
			return;
		}
		boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
		RenderType type = RenderTypes.eyes(TEXTURE);
		submitShell(poseStack, collector, type, state, slim ? this.outerSlim : this.outerWide, scaleAlpha(color, OUTER_ALPHA));
		submitShell(poseStack, collector, type, state, slim ? this.innerSlim : this.innerWide, color);
	}

	private static void submitShell(
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final RenderType type,
		final AvatarRenderState state,
		final PlayerModel model,
		final int color
	) {
		collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
			// Posed at draw time: the shells are shared by every player this frame.
			model.setupAnim(state);
			// The inflated base parts already cover the outer skin layer.
			model.hat.visible = false;
			model.jacket.visible = false;
			model.leftSleeve.visible = false;
			model.rightSleeve.visible = false;
			model.leftPants.visible = false;
			model.rightPants.visible = false;
			PoseStack local = new PoseStack();
			local.last().set(pose);
			model.root().render(local, new BackFaceVertexConsumer(consumer),
				LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		});
	}

	private static int scaleAlpha(final int color, final float factor) {
		int alpha = Math.round((color >>> 24) * factor);
		return alpha << 24 | color & 0xFFFFFF;
	}
}
