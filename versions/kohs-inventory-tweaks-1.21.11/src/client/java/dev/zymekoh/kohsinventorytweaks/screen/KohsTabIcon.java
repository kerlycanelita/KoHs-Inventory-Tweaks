package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.ui.ZScene;
import dev.zymekoh.kohsinventorytweaks.ui.ZTheme;
import net.minecraft.client.gui.GuiGraphics;

/** The icons of feature and settings tabs: small animated scenes of Minecraft items, by {@link ZScene}. */
public enum KohsTabIcon {
	CURSOR(ZScene.CURSOR),
	TWEAKS(ZScene.TWEAKS),
	ISSUES(ZScene.ISSUES),
	CUSTOMIZATION(ZScene.CUSTOMIZATION),
	HIGHLIGHTER(ZScene.HIGHLIGHTER),
	SCALER(ZScene.SCALER),
	ACCESSIBILITY(ZScene.VISIBILITY),
	PERFORMANCE(ZScene.PERFORMANCE),
	SAFETY(ZScene.SAFETY),
	INVENTORY(ZScene.INVENTORY),
	CHEST(ZScene.CHEST),
	SHULKER(ZScene.SHULKER),
	ENDER_CHEST(ZScene.ENDER_CHEST),
	BARREL(ZScene.BARREL),
	KEYBIND(ZScene.KEYBIND),
	KOHS(ZScene.KOHS);

	private final ZScene scene;

	KohsTabIcon(final ZScene scene) {
		this.scene = scene;
	}

	/** {@code energy} is 0 at rest and 1 hovered or selected; an inactive tab rests under a veil of its own glass. */
	public void draw(final GuiGraphics graphics, final int x, final int y, final int size, final float energy, final boolean active) {
		this.scene.draw(graphics, x, y, size, active ? energy : 0.0F);
		if (!active) {
			graphics.fill(x, y, x + size, y + size, ZTheme.alpha(ZTheme.SURFACE, 150));
		}
	}
}
