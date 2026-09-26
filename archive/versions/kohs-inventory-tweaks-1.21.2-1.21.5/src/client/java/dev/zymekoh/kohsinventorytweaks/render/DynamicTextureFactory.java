package dev.zymekoh.kohsinventorytweaks.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Bridges the DynamicTexture constructor change between 1.21.2 and 1.21.5. */
public final class DynamicTextureFactory {
	private DynamicTextureFactory() {
	}

	public static DynamicTexture create(final String label, final NativeImage image) {
		try {
			Constructor<DynamicTexture> named = DynamicTexture.class.getConstructor(Supplier.class, NativeImage.class);
			return named.newInstance((Supplier<String>) () -> label, image);
		} catch (NoSuchMethodException ignored) {
			try {
				return DynamicTexture.class.getConstructor(NativeImage.class).newInstance(image);
			} catch (ReflectiveOperationException exception) {
				throw creationFailure(label, exception);
			}
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
			throw creationFailure(label, exception);
		}
	}

	private static IllegalStateException creationFailure(final String label, final ReflectiveOperationException cause) {
		return new IllegalStateException("Unable to create dynamic texture: " + label, cause);
	}
}
