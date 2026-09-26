package dev.zymekoh.kohsinventorytweaks.screen;

import net.minecraft.resources.Identifier;

/** Cohesive transparent KoHs pixel icons used by feature and settings tabs. */
public enum KohsTabIcon {
	CURSOR("cursor"),
	TWEAKS("tweaks"),
	ISSUES("issues"),
	CUSTOMIZATION("customization"),
	HIGHLIGHTER("highlighter"),
	SCALER("scaler"),
	ACCESSIBILITY("accessibility"),
	PERFORMANCE("performance"),
	SAFETY("safety"),
	INVENTORY("inventory"),
	CHEST("chest"),
	SHULKER("shulker"),
	ENDER_CHEST("ender_chest"),
	BARREL("barrel");

	private final Identifier texture;

	KohsTabIcon(final String id) {
		this.texture = Identifier.fromNamespaceAndPath("kohs_inventory_tweaks", "textures/gui/icons/" + id + ".png");
	}

	public Identifier texture() {
		return this.texture;
	}
}
