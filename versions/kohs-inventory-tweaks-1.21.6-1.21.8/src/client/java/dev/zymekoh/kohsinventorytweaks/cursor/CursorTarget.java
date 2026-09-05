package dev.zymekoh.kohsinventorytweaks.cursor;

public enum CursorTarget {
	INVENTORY("inventory", "screen.kohs_inventory_tweaks.target.inventory", 176, 166),
	CHEST_SINGLE("chest_single", "screen.kohs_inventory_tweaks.target.chest_single", 176, 168),
	CHEST_DOUBLE("chest_double", "screen.kohs_inventory_tweaks.target.chest_double", 176, 222),
	SHULKER("shulker", "screen.kohs_inventory_tweaks.target.shulker", 176, 167),
	ENDER_CHEST("ender_chest", "screen.kohs_inventory_tweaks.target.ender_chest", 176, 168),
	BARREL("barrel", "screen.kohs_inventory_tweaks.target.barrel", 176, 168);

	private final String serializedName;
	private final String translationKey;
	private final int previewWidth;
	private final int previewHeight;

	CursorTarget(final String serializedName, final String translationKey, final int previewWidth, final int previewHeight) {
		this.serializedName = serializedName;
		this.translationKey = translationKey;
		this.previewWidth = previewWidth;
		this.previewHeight = previewHeight;
	}

	public String serializedName() {
		return this.serializedName;
	}

	public String translationKey() {
		return this.translationKey;
	}

	public int previewWidth() {
		return this.previewWidth;
	}

	public int previewHeight() {
		return this.previewHeight;
	}

	public int containerRows() {
		return this == CHEST_DOUBLE ? 6 : 3;
	}
}

