package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.render.InventoryTextureManager.SlotRegion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Vanilla inventory surfaces represented by this early-version family. */
public enum CustomizationPreview {
	INVENTORY("inventory", Kind.PLAYER, null, 176, 166, 256, 256, 0, List.of()),
	CHEST_SINGLE("chest_single", Kind.GENERIC, null, 176, 168, 256, 256, 3, List.of()),
	CHEST_DOUBLE("chest_double", Kind.GENERIC, null, 176, 222, 256, 256, 6, List.of()),
	ENDER_CHEST("ender_chest", Kind.GENERIC, null, 176, 168, 256, 256, 3, List.of()),
	BARREL("barrel", Kind.GENERIC, null, 176, 168, 256, 256, 3, List.of()),
	CRAFTING_TABLE("crafting_table", "crafting_table.png", 176, 166,
		withPlayer(84, grid(30, 17, 3, 3), slots(124, 35))),
	CRAFTER("crafter", "crafter.png", 176, 166,
		withPlayer(84, grid(26, 17, 3, 3), slots(134, 35))),
	FURNACE("furnace", "furnace.png", 176, 166,
		withPlayer(84, slots(56, 17, 56, 53, 116, 35))),
	BLAST_FURNACE("blast_furnace", "blast_furnace.png", 176, 166,
		withPlayer(84, slots(56, 17, 56, 53, 116, 35))),
	SMOKER("smoker", "smoker.png", 176, 166,
		withPlayer(84, slots(56, 17, 56, 53, 116, 35))),
	DISPENSER("dispenser", "dispenser.png", 176, 166,
		withPlayer(84, grid(62, 17, 3, 3))),
	DROPPER("dropper", "dispenser.png", 176, 166,
		withPlayer(84, grid(62, 17, 3, 3))),
	HOPPER("hopper", "hopper.png", 176, 133,
		withPlayer(51, grid(44, 20, 5, 1))),
	BREWING_STAND("brewing_stand", "brewing_stand.png", 176, 166,
		withPlayer(84, slots(17, 17, 79, 17, 56, 51, 79, 58, 102, 51))),
	ENCHANTING_TABLE("enchanting_table", "enchanting_table.png", 176, 166,
		withPlayer(84, slots(15, 47, 35, 47))),
	ANVIL("anvil", "anvil.png", 176, 166,
		withPlayer(84, slots(27, 47, 76, 47, 134, 47))),
	SMITHING_TABLE("smithing_table", "smithing.png", 176, 166,
		withPlayer(84, slots(8, 48, 26, 48, 44, 48, 98, 48))),
	GRINDSTONE("grindstone", "grindstone.png", 176, 166,
		withPlayer(84, slots(49, 19, 49, 40, 129, 34))),
	STONECUTTER("stonecutter", "stonecutter.png", 176, 166,
		withPlayer(84, slots(20, 33, 143, 33))),
	LOOM("loom", "loom.png", 176, 166,
		withPlayer(84, slots(13, 26, 33, 26, 23, 45, 143, 58))),
	CARTOGRAPHY_TABLE("cartography_table", "cartography_table.png", 176, 166,
		withPlayer(84, slots(15, 15, 15, 52, 145, 39))),
	BEACON("beacon", "beacon.png", 230, 219, 256, 256,
		combine(playerAt(36, 137), slots(136, 110))),
	SHULKER_BOX("shulker_box", "shulker_box.png", 176, 167,
		withPlayer(85, grid(8, 19, 9, 3))),
	VILLAGER("villager", "villager.png", 276, 166, 512, 256,
		combine(playerAt(108, 84), slots(136, 37, 162, 37, 220, 37))),
	HORSE("horse", "horse.png", 176, 166,
		withPlayer(84, slots(8, 18, 8, 36, 80, 18, 98, 18, 116, 18))),
	CREATIVE("creative", "creative_inventory/tab_items.png", 195, 136,
		combine(grid(9, 18, 9, 5), grid(9, 112, 9, 1))),
	BUNDLE("bundle", Kind.BUNDLE, null, 104, 88, 256, 256, 0, List.of());

	public enum Kind {
		PLAYER,
		GENERIC,
		SURFACE,
		BUNDLE
	}

	private final String id;
	private final Kind kind;
	private final ResourceLocation texture;
	private final int width;
	private final int height;
	private final int textureWidth;
	private final int textureHeight;
	private final int rows;
	private final List<SlotRegion> slots;

	CustomizationPreview(
		final String id,
		final Kind kind,
		final ResourceLocation texture,
		final int width,
		final int height,
		final int textureWidth,
		final int textureHeight,
		final int rows,
		final List<SlotRegion> slots
	) {
		this.id = id;
		this.kind = kind;
		this.texture = texture;
		this.width = width;
		this.height = height;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.rows = rows;
		this.slots = List.copyOf(slots);
	}

	CustomizationPreview(
		final String id,
		final String textureFile,
		final int width,
		final int height,
		final List<SlotRegion> slots
	) {
		this(id, textureFile, width, height, 256, 256, slots);
	}

	CustomizationPreview(
		final String id,
		final String textureFile,
		final int width,
		final int height,
		final int textureWidth,
		final int textureHeight,
		final List<SlotRegion> slots
	) {
		this(
			id,
			Kind.SURFACE,
			ResourceLocation.withDefaultNamespace("textures/gui/container/" + textureFile),
			width,
			height,
			textureWidth,
			textureHeight,
			0,
			slots
		);
	}

	public String translationKey() {
		return "screen.kohs_inventory_tweaks.customization.surface." + this.id;
	}

	public Kind kind() {
		return this.kind;
	}

	public ResourceLocation texture() {
		return this.texture;
	}

	public int previewWidth() {
		return this.width;
	}

	public int previewHeight() {
		return this.height;
	}

	public int textureWidth() {
		return this.textureWidth;
	}

	public int textureHeight() {
		return this.textureHeight;
	}

	public int containerRows() {
		return this.rows;
	}

	public List<SlotRegion> slots() {
		return this.slots;
	}

	@SafeVarargs
	private static List<SlotRegion> withPlayer(final int playerY, final List<SlotRegion>... topSlotGroups) {
		return combine(combine(topSlotGroups), playerAt(8, playerY));
	}

	private static List<SlotRegion> playerAt(final int x, final int y) {
		return combine(grid(x, y, 9, 3), grid(x, y + 58, 9, 1));
	}

	private static List<SlotRegion> grid(
		final int x,
		final int y,
		final int columns,
		final int rows
	) {
		List<SlotRegion> result = new ArrayList<>(columns * rows);
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				result.add(new SlotRegion(x + column * 18, y + row * 18));
			}
		}
		return result;
	}

	private static List<SlotRegion> slots(final int... coordinates) {
		List<SlotRegion> result = new ArrayList<>(coordinates.length / 2);
		for (int i = 0; i + 1 < coordinates.length; i += 2) {
			result.add(new SlotRegion(coordinates[i], coordinates[i + 1]));
		}
		return result;
	}

	@SafeVarargs
	private static List<SlotRegion> combine(final List<SlotRegion>... groups) {
		List<SlotRegion> result = new ArrayList<>();
		Arrays.stream(groups).forEach(result::addAll);
		return result;
	}
}
