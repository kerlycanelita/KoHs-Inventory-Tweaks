package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Highlight against target: the slot an inventory draws as hovered must be the slot
 * a click or a key at that pointer acts on.
 *
 * <p>Vanilla gets this for free, because it truncates the pointer to whole pixels and
 * every slot edge sits on one. A scaled inventory has to keep it. The lab walks the
 * pointer across every column and row edge of the player inventory, a screen pixel
 * at a time, waits for a real frame and compares the highlight that frame drew with
 * the slot a click at the same pointer would take. Pixel-perfect scale is held off
 * so the uneven scales that used to disagree are measured as they are.</p>
 *
 * <p>Only APIs that 1.1.x already had are used, so it can run against an older build
 * with {@code -PinventoryTweaksJar}. The pointer moves through the ordinary move
 * callback only; the player's real pointer is untouched, and no window focus is
 * needed. Singleplayer lab world only.</p>
 */
public final class HoverAgreementLab {
	/** Long enough for a frame even under the AFK frame limit. */
	private static final long FRAME_WAIT_MILLIS = 110L;
	private static final int[] COLUMN_EDGES = new int[10];
	/** Hover edges of the three main rows and the hotbar, in inventory pixels. */
	private static final int[] ROW_EDGES = {83, 101, 119, 137, 141, 159};
	private static int checks;
	private static int mismatches;

	static {
		for (int column = 0; column <= 9; column++) {
			COLUMN_EDGES[column] = 7 + column * 18;
		}
	}

	private HoverAgreementLab() {
	}

	public static void run(final Minecraft mc) {
		try {
			checked(mc);
		} catch (RuntimeException error) {
			throw error;
		} catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static void checked(final Minecraft mc) throws Exception {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		InventoryTweaksConfig saved = ConfigStore.get().copy();
		int gui = mc.options.guiScale().get();
		checks = 0;
		mismatches = 0;
		try {
			// GUI scale, physical inventory scale: the user's 180% and 200%, two more
			// uneven ones, and 150% at GUI 2 (three screen pixels per GUI pixel).
			double[][] cases = {{3, 1.799152933573374}, {3, 2.000097708843035}, {2, 1.37}, {4, 1.15}, {2, 1.5}};
			for (double[] testCase : cases) {
				sweep(mc, (int) testCase[0], testCase[1]);
			}
			DebugCollector.info("HOVER_AGREEMENT_SUMMARY", "checks=" + checks + "; mismatches=" + mismatches);
		} finally {
			mc.executeBlocking(() -> {
				if (mc.screen != null) mc.setScreen(null);
				InventoryTweaksConfig config = ConfigStore.get();
				config.inventoryGuiScalerEnabled = saved.inventoryGuiScalerEnabled;
				config.inventoryGuiScale = saved.inventoryGuiScale;
				setPixelPerfect(config, pixelPerfect(saved));
				mc.options.guiScale().set(gui);
				mc.resizeDisplay();
			});
		}
	}

	private static void sweep(final Minecraft mc, final int guiScale, final double physical) throws Exception {
		mc.executeBlocking(() -> {
			InventoryTweaksConfig config = ConfigStore.get();
			config.inventoryGuiScalerEnabled = true;
			config.inventoryGuiScale = physical;
			setPixelPerfect(config, false);
			mc.options.guiScale().set(guiScale);
			mc.resizeDisplay();
			mc.setScreen(new InventoryScreen(mc.player));
		});
		Thread.sleep(500);
		double[] layout = new double[5];
		mc.executeBlocking(() -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			layout[0] = access.kohsInventoryTweaks$getLeftPos();
			layout[1] = access.kohsInventoryTweaks$getTopPos();
			layout[2] = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
			layout[3] = mc.getWindow().getScreenWidth() / (double) screen.width;
			layout[4] = mc.getWindow().getScreenHeight() / (double) screen.height;
		});
		List<double[]> points = new ArrayList<>();
		double rowY = layout[1] + 142 + 8;
		for (int edge : COLUMN_EDGES) {
			double edgeX = toPhysical(mc, layout[0] + edge, true, layout);
			for (int offset = -3; offset <= 3; offset++) {
				points.add(new double[] {edgeX + offset, toPhysical(mc, rowY, false, layout)});
			}
		}
		double columnX = layout[0] + 8 + 4 * 18 + 8;
		for (int edge : ROW_EDGES) {
			double edgeY = toPhysical(mc, layout[1] + edge, false, layout);
			for (int offset = -3; offset <= 3; offset++) {
				points.add(new double[] {toPhysical(mc, columnX, true, layout), edgeY + offset});
			}
		}
		int before = mismatches;
		for (double[] point : points) {
			check(mc, Math.floor(point[0]), Math.floor(point[1]), guiScale, physical);
		}
		DebugCollector.info("HOVER_AGREEMENT_CASE", "gui=" + guiScale + "; scale=" + physical
			+ "; surface=" + layout[2] + "; points=" + points.size() + "; mismatches=" + (mismatches - before));
		mc.executeBlocking(() -> mc.setScreen(null));
		Thread.sleep(200);
	}

	/** An inventory-pixel coordinate on screen, in window pixels, through the surface scale. */
	private static double toPhysical(final Minecraft mc, final double inventory, final boolean horizontal, final double[] layout) {
		double size = horizontal ? mc.getWindow().getGuiScaledWidth() : mc.getWindow().getGuiScaledHeight();
		double center = size * 0.5;
		double gui = center + (inventory - center) * layout[2];
		return gui * (horizontal ? layout[3] : layout[4]);
	}

	private static void check(final Minecraft mc, final double x, final double y, final int guiScale, final double physical) throws Exception {
		mc.executeBlocking(() -> ((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), x, y));
		Thread.sleep(FRAME_WAIT_MILLIS);
		mc.executeBlocking(() -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
			double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
			Slot target = access.kohsInventoryTweaks$findHoveredSlot(
				InventoryGuiScaler.toInventoryCoordinate(mc.mouseHandler.getScaledXPos(mc.getWindow()), screen.width, scale),
				InventoryGuiScaler.toInventoryCoordinate(mc.mouseHandler.getScaledYPos(mc.getWindow()), screen.height, scale));
			Slot drawn = access.kohsInventoryTweaks$getHoveredSlot();
			checks++;
			if (drawn != target) {
				mismatches++;
				DebugCollector.issue("HOVER_AGREEMENT_FAIL", "gui=" + guiScale + "; scale=" + physical
					+ "; pointer=" + (int) x + "," + (int) y + "; drawn=" + describe(drawn) + "; clickTarget=" + describe(target));
			}
		});
	}

	private static String describe(final Slot slot) {
		return slot == null ? "none" : "slot" + slot.index;
	}

	/** The switch exists from 1.2.0; older builds draw every scale as it is anyway. */
	private static void setPixelPerfect(final InventoryTweaksConfig config, final boolean value) {
		try {
			InventoryTweaksConfig.class.getField("pixelPerfectScale").setBoolean(config, value);
		} catch (ReflectiveOperationException ignored) {
			// An older build without the switch.
		}
	}

	private static boolean pixelPerfect(final InventoryTweaksConfig config) {
		try {
			return InventoryTweaksConfig.class.getField("pixelPerfectScale").getBoolean(config);
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}
}
