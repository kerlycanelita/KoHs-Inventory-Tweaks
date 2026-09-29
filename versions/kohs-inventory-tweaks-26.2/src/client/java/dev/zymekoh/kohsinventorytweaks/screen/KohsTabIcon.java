package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.ui.ZIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The icons of feature and settings tabs, drawn from code by {@link ZIcons}. */
public enum KohsTabIcon {
	CURSOR(ZIcons.CURSOR),
	TWEAKS(ZIcons.TWEAKS),
	ISSUES(ZIcons.ISSUES),
	CUSTOMIZATION(ZIcons.CUSTOMIZATION),
	HIGHLIGHTER(ZIcons.HIGHLIGHTER),
	SCALER(ZIcons.SCALER),
	ACCESSIBILITY(ZIcons.VISIBILITY),
	PERFORMANCE(ZIcons.PERFORMANCE),
	SAFETY(ZIcons.SAFETY),
	INVENTORY(ZIcons.INVENTORY),
	CHEST(ZIcons.CHEST),
	SHULKER(ZIcons.SHULKER),
	ENDER_CHEST(ZIcons.ENDER_CHEST),
	BARREL(ZIcons.BARREL),
	KEYBIND(ZIcons.KEYBIND);

	private final ZIcons icon;

	KohsTabIcon(final ZIcons icon) {
		this.icon = icon;
	}

	public void draw(final GuiGraphicsExtractor graphics, final int x, final int y, final int size, final int primary, final float opacity) {
		this.icon.draw(graphics, x, y, size, primary, opacity);
	}
}
