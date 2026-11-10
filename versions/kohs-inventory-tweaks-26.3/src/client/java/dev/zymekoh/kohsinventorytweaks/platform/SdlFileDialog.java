package dev.zymekoh.kohsinventorytweaks.platform;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/**
 * The system file picker on 26.3, which ships SDL3 instead of TinyFileDialogs.
 *
 * <p>Kept by the port script for 26.3 only. SDL shows the dialog without blocking and
 * answers through a callback that may run on another thread, so the answer is handed
 * back to the client thread. The filter and the callback stay alive until then.</p>
 */
public final class SdlFileDialog {
	private static final Set<SDL_DialogFileCallback> PENDING = ConcurrentHashMap.newKeySet();

	private SdlFileDialog() {
	}

	/**
	 * Opens the picker; {@code onChosen} runs on the client thread with the chosen path,
	 * or with null when the dialog was cancelled or failed.
	 *
	 * @param patterns extensions without dots, separated by semicolons, such as {@code "png;gif"}
	 */
	public static void open(
		final @Nullable String defaultLocation,
		final String filterName,
		final String patterns,
		final Consumer<@Nullable String> onChosen
	) {
		Minecraft minecraft = Minecraft.getInstance();
		ByteBuffer name = MemoryUtil.memUTF8(filterName);
		ByteBuffer pattern = MemoryUtil.memUTF8(patterns);
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.get(0).name(name).pattern(pattern);
		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		callback[0] = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
			String chosen = null;
			if (fileList != MemoryUtil.NULL) {
				long first = MemoryUtil.memGetAddress(fileList);
				if (first != MemoryUtil.NULL) {
					chosen = MemoryUtil.memUTF8(first);
				}
			}
			String result = chosen;
			minecraft.execute(() -> {
				PENDING.remove(callback[0]);
				callback[0].free();
				filters.free();
				MemoryUtil.memFree(name);
				MemoryUtil.memFree(pattern);
				onChosen.accept(result);
			});
		});
		PENDING.add(callback[0]);
		SDLDialog.SDL_ShowOpenFileDialog(callback[0], MemoryUtil.NULL, minecraft.getWindow().handle(), filters, defaultLocation, false);
	}
}
