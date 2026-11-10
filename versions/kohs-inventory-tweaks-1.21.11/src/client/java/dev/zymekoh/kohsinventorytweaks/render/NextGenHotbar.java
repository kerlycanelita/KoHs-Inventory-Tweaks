package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.HotbarStyle;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import dev.zymekoh.kohsinventorytweaks.ui.ZMotion;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The hotbar as the player chooses to see it: a different shape, a little life of its own as they
 * move, and a reaction when a totem pops, hearts change or a hit lands.
 *
 * <p>All of it is drawing. The hotbar's selected slot and contents are read from the game and
 * nothing is sent, so it cannot change what the player does or what a server sees.</p>
 */
public final class NextGenHotbar {
	@FunctionalInterface
	public interface SlotDrawer {
		void draw(int x, int y, Player player, ItemStack stack, int seed);
	}

	private static final Identifier BAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
	private static final Identifier OFFHAND_LEFT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
	private static final Identifier OFFHAND_RIGHT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
	private static final Identifier ATTACK_BACKGROUND = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
	private static final Identifier ATTACK_PROGRESS = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");
	private static final int SPLIT_GAP = 12;
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 22;
	/** The sprite's own column boundary between the fifth and sixth slots. */
	private static final int SPLIT_AT = 101;
	private static final int VERTICAL_X = 2;
	private static final float TAU = (float) (Math.PI * 2.0);
	private static final int GOLD = 0xFFD54A;
	private static final int HEAL = 0x6BFF7A;

	private static long lastNanos;
	private static boolean hasLastLook;
	private static float lastYaw;
	private static float swayX;
	private static float swayY;
	private static float tilt;
	private static float walkPhase;
	private static float selectedShown = -1.0F;
	private static float lastHealth = -1.0F;
	private static int lastHurtTime;
	private static float totem;
	private static float hurt;
	private static float heal;
	private static float strike;
	private static float frameX;
	private static float frameY;
	private static float frameTilt;
	private static float framePulse = 1.0F;
	private static float frameHeartbeat;
	private static boolean frameReady;
	private static boolean statusFollowing;

	private NextGenHotbar() {
	}

	/** A totem of undying has just gone off on the local player. */
	public static void onItemActivation(final ItemStack stack) {
		if (stack.is(Items.TOTEM_OF_UNDYING)) {
			totem = 1.0F;
		}
	}

	/** The local player has just swung at something. */
	public static void onAttack() {
		strike = 1.0F;
	}

	/** Read-only instrumentation surface used by KoHs Inventory Debug. */
	public static String labSnapshot() {
		return "swayX=" + swayX + "; swayY=" + swayY + "; totem=" + totem + "; hurt=" + hurt + "; heal=" + heal + "; strike=" + strike
			+ "; following=" + (statusFollowing ? 1 : 0);
	}

	/**
	 * Moves the whole lower HUD with the hotbar when the player asked for it: health, armor, food
	 * and the experience bar. A column hotbar is off to one side, so it keeps its own pivot.
	 */
	public static void extractHud(final GuiGraphics graphics, final Runnable hud) {
		InventoryTweaksConfig config = ConfigStore.get();
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		boolean available = CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION);
		if (!available || !config.hotbarStatusFollow || !config.hotbarMotion && !config.hotbarReactions
			|| player == null || minecraft.getCameraEntity() != player) {
			hud.run();
			return;
		}
		prepareFrame(player, config);
		frameReady = true;
		statusFollowing = true;
		float pivotX = graphics.guiWidth() * 0.5F;
		float pivotY = graphics.guiHeight() - 11.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(pivotX + frameX, pivotY + frameY);
		if (config.hotbarStyle != HotbarStyle.VERTICAL) {
			graphics.pose().rotate(frameTilt);
			graphics.pose().scale(framePulse, framePulse);
		}
		graphics.pose().translate(-pivotX, -pivotY);
		try {
			hud.run();
		} finally {
			graphics.pose().popMatrix();
			statusFollowing = false;
			frameReady = false;
		}
	}

	/** Works out this frame's movement once, whichever part of the HUD asks first. */
	private static void prepareFrame(final LocalPlayer player, final InventoryTweaksConfig config) {
		boolean reduced = ZMotion.reduced();
		boolean motion = config.hotbarMotion && !reduced;
		boolean reactions = config.hotbarReactions;
		update(player, motion);
		float time = ZMotion.seconds();
		float shake = reactions && !reduced ? hurt * hurt : 0.0F;
		float ratio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
		frameHeartbeat = reactions && ratio <= 0.35F && !reduced ? (float) Math.pow(Math.max(0.0, Math.sin(time * TAU * 1.2F)), 6.0) : 0.0F;
		float kick = reactions && !reduced ? strike * strike * 2.0F : 0.0F;
		float bob = motion ? (float) Math.sin(walkPhase) * walkStrength(player) : 0.0F;
		frameX = swayX + (float) Math.sin(time * 55.0F) * shake * 4.0F;
		frameY = swayY + bob + kick + (float) Math.cos(time * 47.0F) * shake * 3.0F;
		frameTilt = tilt;
		framePulse = 1.0F + frameHeartbeat * 0.035F + totem * totem * 0.05F;
	}

	/** Draws the hotbar for this frame, handing Vanilla's own drawing over when nothing changes it. */
	public static void extract(final GuiGraphics graphics, final SlotDrawer slots, final Runnable vanilla) {
		InventoryTweaksConfig config = ConfigStore.get();
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		boolean available = CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION);
		HotbarStyle style = available ? config.hotbarStyle : HotbarStyle.VANILLA;
		boolean motion = available && config.hotbarMotion;
		boolean reactions = available && config.hotbarReactions;
		if (player == null || minecraft.getCameraEntity() != player || style == HotbarStyle.VANILLA && !motion && !reactions) {
			vanilla.run();
			return;
		}
		if (!frameReady) {
			prepareFrame(player, config);
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int[] box = bounds(style, width, height);
		float pivotX = (box[0] + box[2]) * 0.5F;
		float pivotY = (box[1] + box[3]) * 0.5F;
		float time = ZMotion.seconds();
		// When the whole HUD is already moving, only a column hotbar still needs its own pivot.
		boolean moved = statusFollowing && style != HotbarStyle.VERTICAL;
		graphics.pose().pushMatrix();
		if (!moved) {
			if (statusFollowing) {
				graphics.pose().translate(-frameX, -frameY);
			}
			graphics.pose().translate(pivotX + frameX, pivotY + frameY);
			graphics.pose().rotate(frameTilt);
			graphics.pose().scale(framePulse, framePulse);
			graphics.pose().translate(-pivotX, -pivotY);
		}
		if (style == HotbarStyle.VANILLA) {
			vanilla.run();
		} else {
			ItemHighlighterController.beginHotbar();
			drawStyled(graphics, style, width, height, player, config, slots);
			drawAttackIndicator(graphics, style, width, height, player, minecraft);
		}
		if (reactions) {
			drawReactions(graphics, style, width, height, box, player, time, frameHeartbeat);
		}
		graphics.pose().popMatrix();
	}

	private static float walkStrength(final LocalPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		return Math.min(1.4F, (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 7.0F);
	}

	private static void update(final LocalPlayer player, final boolean motion) {
		long now = System.nanoTime();
		float delta = lastNanos == 0L ? 1.0F / 60.0F : Mth.clamp((now - lastNanos) / 1_000_000_000.0F, 0.0001F, 0.1F);
		lastNanos = now;

		float yaw = player.getYRot();
		float yawRate = hasLastLook ? Mth.wrapDegrees(yaw - lastYaw) / delta : 0.0F;
		lastYaw = yaw;
		hasLastLook = true;

		Vec3 velocity = player.getDeltaMovement();
		double radians = Math.toRadians(yaw);
		double strafe = velocity.x * -Math.cos(radians) + velocity.z * -Math.sin(radians);
		float targetX = motion ? Mth.clamp((float) -strafe * 22.0F - yawRate * 0.010F, -9.0F, 9.0F) : 0.0F;
		float targetY = motion ? Mth.clamp((float) velocity.y * 10.0F, -7.0F, 7.0F) : 0.0F;
		float targetTilt = motion ? Mth.clamp((float) -strafe * 0.12F - yawRate * 0.00018F, -0.06F, 0.06F) : 0.0F;
		swayX = ZMotion.approach(swayX, targetX, 11.0F, delta);
		swayY = ZMotion.approach(swayY, targetY, 11.0F, delta);
		tilt = ZMotion.approach(tilt, targetTilt, 11.0F, delta);
		walkPhase = (walkPhase + delta * 10.0F * Math.min(1.0F, walkStrength(player))) % TAU;

		int selected = player.getInventory().getSelectedSlot();
		selectedShown = selectedShown < 0.0F ? selected : ZMotion.approach(selectedShown, selected, 26.0F, delta);

		float health = player.getHealth();
		if (lastHealth >= 0.0F) {
			if (health > lastHealth + 0.01F) {
				heal = 1.0F;
			} else if (health < lastHealth - 0.01F) {
				hurt = 1.0F;
			}
		}
		lastHealth = health;
		if (player.hurtTime > lastHurtTime) {
			hurt = 1.0F;
		}
		lastHurtTime = player.hurtTime;

		totem = Math.max(0.0F, totem - delta / 1.1F);
		hurt = Math.max(0.0F, hurt - delta / 0.45F);
		heal = Math.max(0.0F, heal - delta / 0.6F);
		strike = Math.max(0.0F, strike - delta / 0.25F);
	}

	private static int slotX(final HotbarStyle style, final int index, final int width) {
		int center = width / 2;
		return switch (style) {
			case VISOR -> center - 8 + (index - 4) * 20;
			case SPLIT -> center - 88 + index * 20 + (index < 5 ? -SPLIT_GAP : SPLIT_GAP);
			case VERTICAL -> VERTICAL_X + 3;
			default -> center - 88 + index * 20;
		};
	}

	private static int slotY(final HotbarStyle style, final int index, final int height) {
		return switch (style) {
			case VISOR -> {
				int distance = Math.abs(index - 4);
				yield height - 19 - distance * distance / 4;
			}
			case VERTICAL -> height / 2 - BAR_WIDTH / 2 + 3 + index * 20;
			default -> height - 19;
		};
	}

	private static int[] bounds(final HotbarStyle style, final int width, final int height) {
		int center = width / 2;
		return switch (style) {
			case VISOR -> new int[] {center - 90, height - 24, center + 90, height - 2};
			case SPLIT -> new int[] {center - 91 - SPLIT_GAP, height - 22, center + 91 + SPLIT_GAP, height};
			case VERTICAL -> new int[] {VERTICAL_X, height / 2 - BAR_WIDTH / 2, VERTICAL_X + BAR_HEIGHT, height / 2 + BAR_WIDTH / 2};
			default -> new int[] {center - 91, height - 22, center + 91, height};
		};
	}

	private static void drawStyled(
		final GuiGraphics graphics,
		final HotbarStyle style,
		final int width,
		final int height,
		final Player player,
		final InventoryTweaksConfig config,
		final SlotDrawer slots
	) {
		float shown = Mth.clamp(selectedShown < 0.0F ? player.getInventory().getSelectedSlot() : selectedShown, 0.0F, 8.0F);
		int first = Mth.floor(shown);
		int second = Math.min(8, first + 1);
		float fraction = shown - first;
		float selectedX = Mth.lerp(fraction, slotX(style, first, width), slotX(style, second, width));
		float selectedY = Mth.lerp(fraction, slotY(style, first, height), slotY(style, second, height));
		switch (style) {
			case VISOR -> {
				for (int index = 0; index < 9; index++) {
					NextGenInventory.drawHologramTile(graphics, slotX(style, index, width), slotY(style, index, height), false, config);
				}
				drawSelectionOutline(graphics, Math.round(selectedX), Math.round(selectedY), 0xFFC24A, 255);
			}
			case GHOST -> {
				for (int index = 0; index < 9; index++) {
					drawGhostSlot(graphics, slotX(style, index, width), slotY(style, index, height), config);
				}
				drawSelectionOutline(graphics, Math.round(selectedX), Math.round(selectedY), 0xFFFFFF, 230);
			}
			case VERTICAL -> {
				int barTop = height / 2 - BAR_WIDTH / 2;
				graphics.pose().pushMatrix();
				graphics.pose().translate(VERTICAL_X + (float) BAR_HEIGHT, barTop);
				graphics.pose().rotate((float) (Math.PI / 2.0));
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR, 0, 0, BAR_WIDTH, BAR_HEIGHT);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, Math.round(shown * 20.0F) - 1, -1, 24, 23);
				graphics.pose().popMatrix();
			}
			default -> {
				int gap = style == HotbarStyle.SPLIT ? SPLIT_GAP : 0;
				int barLeft = width / 2 - BAR_WIDTH / 2;
				int barTop = height - BAR_HEIGHT;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR, BAR_WIDTH, BAR_HEIGHT, 0, 0, barLeft - gap, barTop, SPLIT_AT, BAR_HEIGHT);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR, BAR_WIDTH, BAR_HEIGHT, SPLIT_AT, 0, barLeft + SPLIT_AT + gap, barTop, BAR_WIDTH - SPLIT_AT, BAR_HEIGHT);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, Math.round(selectedX) - 4, Math.round(selectedY) - 4, 24, 23);
			}
		}
		int seed = 1;
		for (int index = 0; index < 9; index++) {
			slots.draw(slotX(style, index, width), slotY(style, index, height), player, player.getInventory().getItem(index), seed++);
		}
		ItemStack offhand = player.getOffhandItem();
		if (offhand.isEmpty()) {
			return;
		}
		boolean left = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
		int center = width / 2;
		int x;
		int y;
		switch (style) {
			case VISOR -> {
				x = left ? center - 112 : center + 96;
				y = height - 25;
				NextGenInventory.drawHologramTile(graphics, x, y, true, config);
			}
			case GHOST -> {
				x = left ? center - 117 : center + 101;
				y = height - 19;
				drawGhostSlot(graphics, x, y, config);
			}
			case VERTICAL -> {
				x = VERTICAL_X + 3;
				y = height / 2 + BAR_WIDTH / 2 + 8;
				drawGhostSlot(graphics, x, y, config);
			}
			default -> {
				int gap = style == HotbarStyle.SPLIT ? SPLIT_GAP : 0;
				x = left ? center - 117 - gap : center + 101 + gap;
				y = height - 19;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, left ? OFFHAND_LEFT : OFFHAND_RIGHT, left ? center - 120 - gap : center + 91 + gap, height - 23, 29, 24);
			}
		}
		slots.draw(x, y, player, offhand, seed);
	}

	private static void drawGhostSlot(final GuiGraphics graphics, final int x, final int y, final InventoryTweaksConfig config) {
		int opacity = Mth.clamp(config.slotOpacity, 0, 255);
		int edge = opacity * 110 / 255 << 24 | config.slotColor & 0xFFFFFF;
		graphics.fill(x - 1, y - 1, x + 17, y, edge);
		graphics.fill(x - 1, y + 16, x + 17, y + 17, edge);
		graphics.fill(x - 1, y, x, y + 16, edge);
		graphics.fill(x + 16, y, x + 17, y + 16, edge);
		graphics.fill(x, y, x + 16, y + 16, opacity * 22 / 255 << 24 | config.slotColor & 0xFFFFFF);
	}

	private static void drawSelectionOutline(final GuiGraphics graphics, final int x, final int y, final int color, final int strength) {
		int edge = strength << 24 | color;
		graphics.fill(x - 3, y - 3, x + 19, y - 1, edge);
		graphics.fill(x - 3, y + 17, x + 19, y + 19, edge);
		graphics.fill(x - 3, y - 1, x - 1, y + 17, edge);
		graphics.fill(x + 17, y - 1, x + 19, y + 17, edge);
	}

	private static void drawAttackIndicator(
		final GuiGraphics graphics,
		final HotbarStyle style,
		final int width,
		final int height,
		final LocalPlayer player,
		final Minecraft minecraft
	) {
		if (minecraft.options.attackIndicator().get() != AttackIndicatorStatus.HOTBAR) {
			return;
		}
		float strength = player.getAttackStrengthScale(0.0F);
		if (strength >= 1.0F) {
			return;
		}
		int center = width / 2;
		int gap = style == HotbarStyle.SPLIT ? SPLIT_GAP : 0;
		boolean right = player.getMainArm().getOpposite() == HumanoidArm.RIGHT;
		int x = style == HotbarStyle.VERTICAL ? VERTICAL_X + 2 : right ? center - 113 - gap : center + 97 + gap;
		int y = style == HotbarStyle.VERTICAL ? height / 2 - BAR_WIDTH / 2 - 22 : style == HotbarStyle.VISOR ? height - 26 : height - 20;
		int progress = (int) (strength * 19.0F);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ATTACK_BACKGROUND, x, y, 18, 18);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ATTACK_PROGRESS, 18, 18, 0, 18 - progress, x, y + 18 - progress, 18, progress);
	}

	private static void drawReactions(
		final GuiGraphics graphics,
		final HotbarStyle style,
		final int width,
		final int height,
		final int[] box,
		final LocalPlayer player,
		final float time,
		final float heartbeat
	) {
		if (hurt > 0.0F) {
			frame(graphics, box, ZTheme.CRIMSON_BRIGHT & 0xFFFFFF, Math.round(210.0F * hurt));
		}
		if (heartbeat > 0.01F) {
			frame(graphics, box, ZTheme.CRIMSON_BRIGHT & 0xFFFFFF, Math.round(120.0F * heartbeat));
		}
		if (heal > 0.0F) {
			frame(graphics, box, HEAL, Math.round(170.0F * heal));
			int centerX = (box[0] + box[2]) / 2;
			float rise = (1.0F - heal) * 26.0F;
			for (int dot = -3; dot <= 3; dot++) {
				int x = centerX + dot * 24;
				int y = box[1] - Math.round(rise + Math.abs(dot) * 2.0F);
				int color = Math.round(220.0F * heal) << 24 | HEAL;
				graphics.fill(x - 1, y, x + 2, y + 1, color);
				graphics.fill(x, y - 1, x + 1, y + 2, color);
			}
		}
		if (strike > 0.0F) {
			int selected = player.getInventory().getSelectedSlot();
			int x = slotX(style, selected, width);
			int y = slotY(style, selected, height);
			graphics.fill(x - 1, y - 1, x + 17, y + 17, Math.round(150.0F * strike) << 24 | 0xFFFFFF);
		}
		if (totem > 0.0F) {
			frame(graphics, box, GOLD, Math.round(235.0F * totem));
			float progress = ZMotion.easeOutCubic(1.0F - totem);
			float centerX = (box[0] + box[2]) * 0.5F;
			float centerY = (box[1] + box[3]) * 0.5F;
			int color = Math.round(235.0F * totem) << 24 | GOLD;
			for (int dot = 0; dot < 44; dot++) {
				float angle = dot * TAU / 44.0F + time * 0.5F;
				float reach = 16.0F + 150.0F * progress + (dot % 3) * 8.0F * progress;
				int x = Math.round(centerX + (float) Math.cos(angle) * reach * 1.5F);
				int y = Math.round(centerY + (float) Math.sin(angle) * reach * 0.62F);
				graphics.fill(x, y, x + 2, y + 2, color);
			}
		}
	}

	private static void frame(final GuiGraphics graphics, final int[] box, final int rgb, final int alpha) {
		int color = alpha << 24 | rgb;
		graphics.fill(box[0] - 2, box[1] - 2, box[2] + 2, box[1], color);
		graphics.fill(box[0] - 2, box[3], box[2] + 2, box[3] + 2, color);
		graphics.fill(box[0] - 2, box[1], box[0], box[3], color);
		graphics.fill(box[2], box[1], box[2] + 2, box[3], color);
	}

	/** The chosen hotbar, drawn as if this area were the screen, for the settings page. */
	public static void drawPreview(
		final GuiGraphics graphics,
		final Font font,
		final int x,
		final int y,
		final int width,
		final int height,
		final InventoryTweaksConfig config
	) {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		HotbarStyle style = config.hotbarStyle;
		float scale = style == HotbarStyle.VERTICAL ? Math.min(1.0F, height / 214.0F) : 1.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		drawStyled(graphics, style, Math.round(width / scale), Math.round(height / scale), player, config, (slotX, slotY, owner, stack, seed) -> {
			if (!stack.isEmpty()) {
				graphics.renderItem(owner, stack, slotX, slotY, seed);
				graphics.renderItemDecorations(font, stack, slotX, slotY);
			}
		});
		graphics.pose().popMatrix();
	}
}
