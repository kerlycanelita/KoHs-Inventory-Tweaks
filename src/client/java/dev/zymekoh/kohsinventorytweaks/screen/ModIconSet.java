package dev.zymekoh.kohsinventorytweaks.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zymekoh.kohsinventorytweaks.KoHsInventoryTweaksClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

final class ModIconSet implements AutoCloseable {
	private final Map<String, Identifier> identifiers = new HashMap<>();
	private final Set<String> missing = new HashSet<>();

	Identifier load(final String modId) {
		if (this.missing.contains(modId)) {
			return null;
		}
		Identifier identifier = this.identifiers.computeIfAbsent(modId, this::loadIcon);
		if (identifier == null) {
			this.missing.add(modId);
		}
		return identifier;
	}

	private Identifier loadIcon(final String modId) {
		ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
		if (container == null) {
			return null;
		}
		Path icon = container.getMetadata().getIconPath(64).flatMap(container::findPath).orElse(null);
		if (icon == null || !Files.isRegularFile(icon)) {
			return null;
		}
		try (InputStream input = Files.newInputStream(icon)) {
			NativeImage image = NativeImage.read(input);
			Identifier identifier = Identifier.fromNamespaceAndPath(
				KoHsInventoryTweaksClient.MOD_ID,
				"dynamic/issues/" + modId.replaceAll("[^a-z0-9_.-]", "_")
			);
			DynamicTexture texture = new DynamicTexture(() -> "KoHs issue icon: " + modId, image);
			Minecraft.getInstance().getTextureManager().register(identifier, texture);
			return identifier;
		} catch (IOException | RuntimeException exception) {
			KoHsInventoryTweaksClient.LOGGER.debug("Could not load icon for compatibility issue {}", modId, exception);
			return null;
		}
	}

	@Override
	public void close() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Identifier identifier : this.identifiers.values()) {
			if (identifier != null) {
				minecraft.getTextureManager().release(identifier);
			}
		}
		this.identifiers.clear();
		this.missing.clear();
	}
}
