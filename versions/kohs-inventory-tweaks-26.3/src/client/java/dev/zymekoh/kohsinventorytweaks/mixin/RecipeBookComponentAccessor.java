package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
	@Accessor("searchBox")
	@Nullable EditBox kohsInventoryTweaks$getSearchBox();
}
