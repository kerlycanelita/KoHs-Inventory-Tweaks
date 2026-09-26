package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.screen.CustomizationPreview;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.NautilusInventoryScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ChestMenu;
import org.jspecify.annotations.Nullable;

/** Vanilla container geometry shared with Customization and scale calibration. */
public enum ContainerScaleTarget {
 	CHEST_SINGLE("chest_single", CustomizationPreview.CHEST_SINGLE),
	CHEST_DOUBLE("chest_double", CustomizationPreview.CHEST_DOUBLE),
	SHULKER("shulker", CustomizationPreview.SHULKER_BOX),
	BARREL("barrel", CustomizationPreview.BARREL),
	ENDER_CHEST("ender_chest", CustomizationPreview.ENDER_CHEST),
	CRAFTING_TABLE("crafting_table", CustomizationPreview.CRAFTING_TABLE),
	CRAFTER("crafter", CustomizationPreview.CRAFTER),
	FURNACE("furnace", CustomizationPreview.FURNACE),
	BLAST_FURNACE("blast_furnace", CustomizationPreview.BLAST_FURNACE),
	SMOKER("smoker", CustomizationPreview.SMOKER),
	DISPENSER("dispenser", CustomizationPreview.DISPENSER),
	DROPPER("dropper", CustomizationPreview.DROPPER),
	HOPPER("hopper", CustomizationPreview.HOPPER),
	BREWING_STAND("brewing_stand", CustomizationPreview.BREWING_STAND),
	ENCHANTING_TABLE("enchanting_table", CustomizationPreview.ENCHANTING_TABLE),
	ANVIL("anvil", CustomizationPreview.ANVIL),
	SMITHING_TABLE("smithing_table", CustomizationPreview.SMITHING_TABLE),
	GRINDSTONE("grindstone", CustomizationPreview.GRINDSTONE),
	STONECUTTER("stonecutter", CustomizationPreview.STONECUTTER),
	LOOM("loom", CustomizationPreview.LOOM),
	CARTOGRAPHY_TABLE("cartography_table", CustomizationPreview.CARTOGRAPHY_TABLE),
	BEACON("beacon", CustomizationPreview.BEACON),
	VILLAGER("villager", CustomizationPreview.VILLAGER),
	HORSE("horse", CustomizationPreview.HORSE),
	NAUTILUS("nautilus", CustomizationPreview.NAUTILUS);

	private final String id;
	private final CustomizationPreview preview;

	ContainerScaleTarget(final String id, final CustomizationPreview preview) {
		this.id = id;
		this.preview = preview;
	}
	public CustomizationPreview preview() { return this.preview; }
	public String translationKey() { return "screen.kohs_inventory_tweaks.target." + this.id; }
	public int previewWidth() { return this.preview.previewWidth(); }
	public int previewHeight() { return this.preview.previewHeight(); }
	public int rows() { return this.preview.containerRows(); }

	public static @Nullable ContainerScaleTarget classify(final @Nullable Screen screen) {
		if (screen == null) return null;
		String key = screen.getTitle().getContents() instanceof TranslatableContents translated
			? translated.getKey().toLowerCase(java.util.Locale.ROOT) : "";
		if (screen instanceof DispenserScreen) return key.contains("dropper") ? DROPPER : DISPENSER;
		if (screen instanceof ShulkerBoxScreen) return SHULKER;
		if (screen instanceof CraftingScreen) return CRAFTING_TABLE;
		if (screen instanceof CrafterScreen) return CRAFTER;
		if (screen instanceof FurnaceScreen) return FURNACE;
		if (screen instanceof BlastFurnaceScreen) return BLAST_FURNACE;
		if (screen instanceof SmokerScreen) return SMOKER;
		if (screen instanceof HopperScreen) return HOPPER;
		if (screen instanceof BrewingStandScreen) return BREWING_STAND;
		if (screen instanceof EnchantmentScreen) return ENCHANTING_TABLE;
		if (screen instanceof AnvilScreen) return ANVIL;
		if (screen instanceof SmithingScreen) return SMITHING_TABLE;
		if (screen instanceof GrindstoneScreen) return GRINDSTONE;
		if (screen instanceof StonecutterScreen) return STONECUTTER;
		if (screen instanceof LoomScreen) return LOOM;
		if (screen instanceof CartographyTableScreen) return CARTOGRAPHY_TABLE;
		if (screen instanceof BeaconScreen) return BEACON;
		if (screen instanceof MerchantScreen) return VILLAGER;
		if (screen instanceof HorseInventoryScreen) return HORSE;
		if (screen instanceof NautilusInventoryScreen) return NAUTILUS;
		if (!(screen instanceof ContainerScreen) || !(screen instanceof MenuAccess<?> access)) return null;
		if (key.contains("enderchest")) return ENDER_CHEST;
		if (key.contains("barrel")) return BARREL;
		return access.getMenu() instanceof ChestMenu menu && menu.getRowCount() >= 6 ? CHEST_DOUBLE : CHEST_SINGLE;
	}
}
