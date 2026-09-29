package dev.zymekoh.kohsinventorydebug;

import dev.zymekoh.kohsinventorydebug.mixin.MouseHandlerDebugInvoker;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.ContainerScaleTarget;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.screen.AffectContainersScreen;
import dev.zymekoh.kohsinventorytweaks.screen.GuiScalerScreen;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.*;
import org.lwjgl.glfw.GLFW;

/** Real Vanilla screens, rendering, hit targets and read-only dispatch probes. Debug only. */
public final class ContainerScaleRegressionLab {
	private static int checks, failures;
	private static final Set<ContainerScaleTarget> CAPTURES = Set.of(
		ContainerScaleTarget.CRAFTING_TABLE, ContainerScaleTarget.ENCHANTING_TABLE,
		ContainerScaleTarget.SMITHING_TABLE, ContainerScaleTarget.VILLAGER, ContainerScaleTarget.HORSE);
	private ContainerScaleRegressionLab() {}

	private static void check(boolean result, String message) {
		checks++;
		if (!result) {
			failures++;
			DebugCollector.issue("CONTAINER_SCALE_FAIL", message);
		}
	}

	public static void run(Minecraft mc) throws Exception {
		if (!MacroTestController.isSafeLocalWorld(mc)) throw new IllegalStateException("Singleplayer only");
		var saved = ConfigStore.get().copy();
		int savedGui = mc.options.guiScale().get();
		var books = new EnumMap<RecipeBookType, Boolean>(RecipeBookType.class);
		checks = failures = 0;
		try {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				for (var type : RecipeBookType.values()) {
					books.put(type, mc.player.getRecipeBook().isOpen(type));
					mc.player.getRecipeBook().setOpen(type, false);
				}
				ConfigStore.get().inventoryGuiScalerEnabled = true;
				ConfigStore.get().affectAllContainers = true;
				ConfigStore.get().containerProfilesEnabled = false;
				ConfigStore.get().inventoryGuiScale = 1.75;
			});
			for (int gui : new int[]{2, 4}) {
				CloseHotbarRegressionLab.atPoll(mc, () -> {
					mc.options.guiScale().set(gui);
					mc.resizeDisplay();
				});
				for (var target : ContainerScaleTarget.values()) {
					CloseHotbarRegressionLab.atPoll(mc, () -> {
						var screen = create(mc, target);
						mc.setScreen(screen);
						check(ContainerScaleTarget.classify(screen) == target, "classify " + target);
						var access = (AbstractContainerScreenAccessor) screen;
						check(access.kohsInventoryTweaks$getImageWidth() == target.previewWidth()
							&& access.kohsInventoryTweaks$getImageHeight() == target.previewHeight(), "preview geometry " + target);
						var slot = screen.getMenu().slots.getLast();
						move(mc, screen, access.kohsInventoryTweaks$getLeftPos() + slot.x + 8,
							access.kohsInventoryTweaks$getTopPos() + slot.y + 8);
					});
					// Allow the actual screen and PiP renderers to run, not just init().
					Thread.sleep(180);
					CloseHotbarRegressionLab.atPoll(mc, () -> {
						var screen = (AbstractContainerScreen<?>) mc.screen;
						var access = (AbstractContainerScreenAccessor) screen;
						check(access.kohsInventoryTweaks$getHoveredSlot() == screen.getMenu().slots.getLast(),
							"render hit target " + target + " GUI " + gui);
						check(!InventoryGuiScaler.hasActiveSurfaceScope(), "render scope restored " + target);
						var config = ConfigStore.get();
						config.affectAllContainers = false;
						check(InventoryGuiScaler.appliedSurfaceScale(screen, config) == 1.0, "disabled container identity " + target);
						config.affectAllContainers = true;
						config.inventoryGuiScalerEnabled = false;
						check(InventoryGuiScaler.appliedSurfaceScale(screen, config) == 1.0, "disabled scaler identity " + target);
						config.inventoryGuiScalerEnabled = true;
					});
					if (CAPTURES.contains(target)) screenshot(mc, "container-" + target.name().toLowerCase(java.util.Locale.ROOT) + "-gui" + gui);
				}
				checkDispatch(mc);
				checkPreviews(mc, gui);
				checkRecipeBook(mc, gui);
			}
			DebugCollector.info("CONTAINER_SCALE_SUMMARY", "screens=50; checks=" + checks + "; failures=" + failures);
			if (failures != 0) throw new IllegalStateException("Container regression failures=" + failures);
		} finally {
			mc.executeBlocking(() -> {
				mc.setScreen(null);
				var config = ConfigStore.get();
				config.inventoryGuiScalerEnabled = saved.inventoryGuiScalerEnabled;
				config.affectAllContainers = saved.affectAllContainers;
				config.containerProfilesEnabled = saved.containerProfilesEnabled;
				config.inventoryGuiScale = saved.inventoryGuiScale;
				books.forEach((type, open) -> mc.player.getRecipeBook().setOpen(type, open));
				mc.options.guiScale().set(savedGui);
				mc.resizeDisplay();
			});
		}
	}

	private static void move(Minecraft mc, Screen screen, double x, double y) {
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		double px = (screen.width * 0.5 + (x - screen.width * 0.5) * scale) * mc.getWindow().getScreenWidth() / screen.width;
		double py = (screen.height * 0.5 + (y - screen.height * 0.5) * scale) * mc.getWindow().getScreenHeight() / screen.height;
		GLFW.glfwSetCursorPos(mc.getWindow().handle(), px, py);
		((MouseHandlerDebugInvoker) mc.mouseHandler).kohsInventoryDebug$invokeMove(mc.getWindow().handle(), px, py);
	}

	private static void checkDispatch(Minecraft mc) throws Exception {
		for (double multiplier : new double[]{0.65, 1.75, 3.15}) {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				ConfigStore.get().inventoryGuiScale = multiplier;
				var probe = new DispatchProbe(mc);
				mc.setScreen(probe);
				probe.expectedX = probe.width / 2.0 + 29;
				probe.expectedY = probe.height / 2.0 - 31;
				probe.observing = true;
				move(mc, probe, probe.expectedX, probe.expectedY);
				mc.mouseHandler.handleAccumulatedMovement();
				var input = (MouseHandlerDebugInvoker) mc.mouseHandler;
				input.kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(0, 0), GLFW.GLFW_PRESS);
				probe.expectedX += 7;
				probe.expectedY += 9;
				move(mc, probe, probe.expectedX, probe.expectedY);
				mc.mouseHandler.handleAccumulatedMovement();
				input.kohsInventoryDebug$invokeButton(mc.getWindow().handle(), new MouseButtonInfo(0, 0), GLFW.GLFW_RELEASE);
				input.kohsInventoryDebug$invokeScroll(mc.getWindow().handle(), 0, -1);
				check(probe.clicks == 1 && probe.releases == 1 && probe.moves >= 1 && probe.drags == 1 && probe.scrolls == 1,
					"all dispatch paths at scale " + multiplier + "; counts=" + probe.clicks + "," + probe.releases + "," + probe.moves + "," + probe.drags + "," + probe.scrolls);
				probe.observing = false;
			});
		}
		CloseHotbarRegressionLab.atPoll(mc, () -> ConfigStore.get().inventoryGuiScale = 1.75);
	}

	private static void checkPreviews(Minecraft mc, int gui) throws Exception {
		CloseHotbarRegressionLab.atPoll(mc, () -> mc.setScreen(new AffectContainersScreen(new GuiScalerScreen(null))));
		var field = AffectContainersScreen.class.getDeclaredField("targetIndex");
		field.setAccessible(true);
		for (var target : ContainerScaleTarget.values()) {
			CloseHotbarRegressionLab.atPoll(mc, () -> {
				try { field.setInt(mc.screen, target.ordinal()); }
				catch (IllegalAccessException error) { throw new IllegalStateException(error); }
			});
			Thread.sleep(80);
			if (CAPTURES.contains(target)) screenshot(mc, "preview-" + target.name().toLowerCase(java.util.Locale.ROOT) + "-gui" + gui);
		}
	}

	private static void checkRecipeBook(Minecraft mc, int gui) throws Exception {
		CloseHotbarRegressionLab.atPoll(mc, () -> {
			mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
			var screen = create(mc, ContainerScaleTarget.CRAFTING_TABLE);
			mc.setScreen(screen);
			check(InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get()) * 379 <= screen.width - 15,
				"open recipe book fits GUI " + gui);
		});
		screenshot(mc, "container-crafting-book-gui" + gui);
		CloseHotbarRegressionLab.atPoll(mc, () -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false));
	}

	private static void screenshot(Minecraft mc, String name) throws Exception {
		Thread.sleep(150);
		CompletableFuture<Void> done = new CompletableFuture<>();
		mc.executeBlocking(() -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, message -> done.complete(null)));
		done.get(10, TimeUnit.SECONDS);
	}

	private static AbstractContainerScreen<?> create(Minecraft mc, ContainerScaleTarget target) {
		var inv = mc.player.getInventory();
		var title = Component.translatable(target.translationKey());
		return switch (target) {
			case CHEST_SINGLE -> new ContainerScreen(ChestMenu.threeRows(0, inv), inv, title);
			case CHEST_DOUBLE -> new ContainerScreen(ChestMenu.sixRows(0, inv), inv, title);
			case BARREL -> new ContainerScreen(ChestMenu.threeRows(0, inv), inv, Component.translatable("container.barrel"));
			case ENDER_CHEST -> new ContainerScreen(ChestMenu.threeRows(0, inv), inv, Component.translatable("container.enderchest"));
			case SHULKER -> new ShulkerBoxScreen(new ShulkerBoxMenu(0, inv), inv, title);
			case CRAFTING_TABLE -> new CraftingScreen(new CraftingMenu(0, inv), inv, title);
			case CRAFTER -> new CrafterScreen(new CrafterMenu(0, inv), inv, title);
			case FURNACE -> new FurnaceScreen(new FurnaceMenu(0, inv), inv, title);
			case BLAST_FURNACE -> new BlastFurnaceScreen(new BlastFurnaceMenu(0, inv), inv, title);
			case SMOKER -> new SmokerScreen(new SmokerMenu(0, inv), inv, title);
			case DISPENSER -> new DispenserScreen(new DispenserMenu(0, inv), inv, title);
			case DROPPER -> new DispenserScreen(new DispenserMenu(0, inv), inv, Component.translatable("container.dropper"));
			case HOPPER -> new HopperScreen(new HopperMenu(0, inv), inv, title);
			case BREWING_STAND -> new BrewingStandScreen(new BrewingStandMenu(0, inv), inv, title);
			case ENCHANTING_TABLE -> new EnchantmentScreen(new EnchantmentMenu(0, inv), inv, title);
			case ANVIL -> new AnvilScreen(new AnvilMenu(0, inv), inv, title);
			case SMITHING_TABLE -> new SmithingScreen(new SmithingMenu(0, inv), inv, title);
			case GRINDSTONE -> new GrindstoneScreen(new GrindstoneMenu(0, inv), inv, title);
			case STONECUTTER -> new StonecutterScreen(new StonecutterMenu(0, inv), inv, title);
			case LOOM -> new LoomScreen(new LoomMenu(0, inv), inv, title);
			case CARTOGRAPHY_TABLE -> new CartographyTableScreen(new CartographyTableMenu(0, inv), inv, title);
			case BEACON -> new BeaconScreen(new BeaconMenu(0, inv), inv, title);
			case VILLAGER -> new MerchantScreen(new MerchantMenu(0, inv), inv, title);
			case HORSE -> {
				var horse = EntityType.HORSE.create(mc.level, EntitySpawnReason.LOAD);
				// A preview entity is never added to the level; 26.2 renders only entities with an id.
				horse.setId(Integer.MIN_VALUE + 1);
				yield new HorseInventoryScreen(new HorseInventoryMenu(0, inv, new SimpleContainer(2), horse, 0), inv, horse, 0);
			}
			case NAUTILUS -> {
				var nautilus = EntityType.NAUTILUS.create(mc.level, EntitySpawnReason.LOAD);
				nautilus.setId(Integer.MIN_VALUE + 2);
				yield new NautilusInventoryScreen(new NautilusInventoryMenu(0, inv, new SimpleContainer(2), nautilus, 0), inv, nautilus, 0);
			}
		};
	}

	/** Receives real MouseHandler dispatch but prevents test clicks from becoming item actions. */
	private static final class DispatchProbe extends CraftingScreen {
		boolean observing;
		double expectedX, expectedY;
		int clicks, releases, moves, drags, scrolls;
		DispatchProbe(Minecraft mc) { super(new CraftingMenu(0, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Dispatch QA")); }
		private void position(double x, double y, String kind) {
			if (!observing) return;
			check(Math.abs(x - expectedX) < 0.6 && Math.abs(y - expectedY) < 0.6,
				kind + " maps once; expected=" + expectedX + "," + expectedY + "; received=" + x + "," + y);
		}
		@Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) { clicks++; position(event.x(), event.y(), "click"); return true; }
		@Override public boolean mouseReleased(MouseButtonEvent event) { releases++; position(event.x(), event.y(), "release"); return true; }
		@Override public void mouseMoved(double x, double y) { moves++; position(x, y, "move"); }
		@Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
			drags++; position(event.x(), event.y(), "drag");
			check(Math.abs(dx - 7) < 0.6 && Math.abs(dy - 9) < 0.6, "drag delta maps once: " + dx + "," + dy);
			return true;
		}
		@Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
			scrolls++; position(x, y, "scroll"); check(vertical == -1.0, "scroll amount preserved"); return true;
		}
	}
}
