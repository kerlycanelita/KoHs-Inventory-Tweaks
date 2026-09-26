package dev.zymekoh.kohsinventorytweaks.screen;

/** Lets the client entry point drop the cached hover previews on a resource reload. */
public final class FeaturePreviewReload {
	private FeaturePreviewReload() {
	}

	public static void run() {
		FeaturePreview.invalidate();
	}
}
