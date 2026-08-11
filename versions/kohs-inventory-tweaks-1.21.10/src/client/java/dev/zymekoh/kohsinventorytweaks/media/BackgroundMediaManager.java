package dev.zymekoh.kohsinventorytweaks.media;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

public final class BackgroundMediaManager {
	public static final int TARGET_WIDTH = 176;
	public static final int TARGET_HEIGHT = 166;
	public static final int MIN_VIDEO_WIDTH = TARGET_WIDTH;
	public static final int MIN_VIDEO_HEIGHT = TARGET_HEIGHT;
	public static final double MIN_VIDEO_SECONDS = 1.0;
	public static final double MAX_VIDEO_SECONDS = 10.0;
	private static final int VIDEO_FPS = 10;
	private static final int MAX_ANIMATION_FRAMES = 100;
	private static final int MAX_SOURCE_DIMENSION = 2048;
	private static final int MAX_PREVIEW_DIMENSION = 1024;
	private static final long MAX_IMAGE_BYTES = 32L * 1024L * 1024L;
	private static final long MAX_VIDEO_BYTES = 64L * 1024L * 1024L;
	private static final ExecutorService IMPORT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "KoHs inventory background importer");
		thread.setDaemon(true);
		return thread;
	});

	private BackgroundMediaManager() {
	}

	public static CompletableFuture<PrepareResult> prepareAsync(final Path source) {
		return CompletableFuture.supplyAsync(() -> prepareMedia(source), IMPORT_EXECUTOR);
	}

	public static CompletableFuture<ImportResult> importAsync(final Path source, final CropSettings crop) {
		return CompletableFuture.supplyAsync(() -> importMedia(source, crop), IMPORT_EXECUTOR);
	}

	public static AnimatedBackground load(final Path path) throws IOException {
		String extension = extension(path);
		if (extension.equals("gif")) {
			try {
				return readGif(path);
			} catch (MediaValidationException exception) {
				throw new IOException(exception.translationKey, exception);
			}
		}
		BufferedImage image = ImageIO.read(path.toFile());
		if (image == null) {
			throw new IOException("Unsupported image data");
		}
		return new AnimatedBackground(List.of(new AnimationFrame(toPixels(cropCover(image, CropSettings.DEFAULT)), 1000)));
	}

	private static PrepareResult prepareMedia(final Path source) {
		try {
			validateSource(source);
			BufferedImage preview = switch (extension(source)) {
				case "png", "jpg", "jpeg", "bmp" -> readStaticPreview(source);
				case "gif" -> readGifPreview(source);
				case "mp4", "mov" -> readVideoPreview(source);
				default -> throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.format");
			};
			BufferedImage normalized = scaleDownForPreview(preview);
			return PrepareResult.success(new PreparedMedia(
				source.toAbsolutePath().normalize(),
				normalized.getWidth(),
				normalized.getHeight(),
				normalized.getRGB(0, 0, normalized.getWidth(), normalized.getHeight(), null, 0, normalized.getWidth())
			));
		} catch (MediaValidationException exception) {
			return PrepareResult.failure(exception.translationKey, "");
		} catch (Exception exception) {
			String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			return PrepareResult.failure("screen.kohs_inventory_tweaks.background.error.decode", detail);
		}
	}

	private static ImportResult importMedia(final Path source, final CropSettings crop) {
		try {
			validateSource(source);
			String extension = extension(source);
			Files.createDirectories(ConfigStore.backgroundsDirectory());
			String id = UUID.randomUUID().toString().replace("-", "");

			return switch (extension) {
				case "png", "jpg", "jpeg", "bmp" -> importStaticImage(source, "background_" + id + ".png", crop);
				case "gif" -> importGif(source, "background_" + id + ".gif", crop);
				case "mp4", "mov" -> importVideo(source, "background_" + id + ".gif", crop);
				default -> throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.format");
			};
		} catch (MediaValidationException exception) {
			return ImportResult.failure(exception.translationKey, "");
		} catch (Exception exception) {
			String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			return ImportResult.failure("screen.kohs_inventory_tweaks.background.error.decode", detail);
		}
	}

	private static ImportResult importStaticImage(
		final Path source,
		final String fileName,
		final CropSettings crop
	) throws IOException, MediaValidationException {
		validateFileSize(source, MAX_IMAGE_BYTES);
		BufferedImage image = ImageIO.read(source.toFile());
		if (image == null) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.decode");
		}
		validateDimensions(image.getWidth(), image.getHeight());
		Path target = ConfigStore.resolveBackground(fileName);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		if (!ImageIO.write(cropCover(image, crop), "png", temporary.toFile())) {
			throw new IOException("PNG writer unavailable");
		}
		moveIntoPlace(temporary, target);
		return ImportResult.success(fileName, "screen.kohs_inventory_tweaks.background.ready.image");
	}

	private static ImportResult importGif(
		final Path source,
		final String fileName,
		final CropSettings crop
	) throws IOException, MediaValidationException {
		validateFileSize(source, MAX_IMAGE_BYTES);
		AnimatedBackground animation = readGif(source, crop);
		Path target = ConfigStore.resolveBackground(fileName);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		writeGif(animation, temporary);
		moveIntoPlace(temporary, target);
		return ImportResult.success(fileName, "screen.kohs_inventory_tweaks.background.ready.gif");
	}

	private static ImportResult importVideo(final Path source, final String fileName, final CropSettings crop) throws Exception {
		validateFileSize(source, MAX_VIDEO_BYTES);
		Path target = ConfigStore.resolveBackground(fileName);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		List<AnimationFrame> frames = new ArrayList<>();
		SeekableByteChannel channel = null;
		try {
			channel = NIOUtils.readableChannel(source.toFile());
			FrameGrab grab = FrameGrab.createFrameGrab(channel);
			DemuxerTrackMeta metadata = grab.getVideoTrack().getMeta();
			double duration = metadata.getTotalDuration();
			validateVideoDuration(duration);

			int requestedFrames = Math.min(MAX_ANIMATION_FRAMES, Math.max(1, (int) Math.ceil(duration * VIDEO_FPS)));
			for (int i = 0; i < requestedFrames; i++) {
				double second = Math.min(Math.max(0.0, duration - 0.001), i / (double) VIDEO_FPS);
				Picture picture = grab.seekToSecondPrecise(second).getNativeFrame();
				if (picture == null) {
					break;
				}
				BufferedImage image = AWTUtil.toBufferedImage(picture, metadata.getOrientation());
				if (i == 0) {
					validateVideoDimensions(image.getWidth(), image.getHeight());
				}
				frames.add(new AnimationFrame(toPixels(cropCover(image, crop)), 1000 / VIDEO_FPS));
			}
		} finally {
			NIOUtils.closeQuietly(channel);
		}
		if (frames.size() < VIDEO_FPS) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.video_short");
		}
		writeGif(new AnimatedBackground(List.copyOf(frames)), temporary);
		moveIntoPlace(temporary, target);
		return ImportResult.success(fileName, "screen.kohs_inventory_tweaks.background.ready.video");
	}

	private static AnimatedBackground readGif(final Path path) throws IOException, MediaValidationException {
		return readGif(path, CropSettings.DEFAULT);
	}

	private static AnimatedBackground readGif(
		final Path path,
		final CropSettings crop
	) throws IOException, MediaValidationException {
		try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
			if (input == null) {
				throw new IOException("Could not open image stream");
			}
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			try {
				reader.setInput(input, false, false);
				int frameCount = reader.getNumImages(true);
				if (frameCount < 1 || frameCount > MAX_ANIMATION_FRAMES) {
					throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.frames");
				}
				int[] canvasSize = gifCanvasSize(reader.getStreamMetadata());
				BufferedImage first = reader.read(0);
				int canvasWidth = canvasSize[0] > 0 ? canvasSize[0] : first.getWidth();
				int canvasHeight = canvasSize[1] > 0 ? canvasSize[1] : first.getHeight();
				validateDimensions(canvasWidth, canvasHeight);
				BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
				List<AnimationFrame> frames = new ArrayList<>(frameCount);
				GifFrameMetadata previous = null;
				BufferedImage restoreSnapshot = null;

				for (int i = 0; i < frameCount; i++) {
					if (previous != null) {
						if (previous.disposal.equals("restoreToBackgroundColor")) {
							Graphics2D clear = canvas.createGraphics();
							clear.setComposite(AlphaComposite.Clear);
							clear.fillRect(previous.left, previous.top, previous.width, previous.height);
							clear.dispose();
						} else if (previous.disposal.equals("restoreToPrevious") && restoreSnapshot != null) {
							canvas = copyImage(restoreSnapshot);
						}
					}

					BufferedImage frame = i == 0 ? first : reader.read(i);
					GifFrameMetadata metadata = gifFrameMetadata(reader.getImageMetadata(i), frame);
					BufferedImage currentSnapshot = metadata.disposal.equals("restoreToPrevious") ? copyImage(canvas) : null;
					Graphics2D graphics = canvas.createGraphics();
					graphics.setComposite(AlphaComposite.SrcOver);
					int drawX = frame.getWidth() == canvasWidth && frame.getHeight() == canvasHeight ? 0 : metadata.left;
					int drawY = frame.getWidth() == canvasWidth && frame.getHeight() == canvasHeight ? 0 : metadata.top;
					graphics.drawImage(frame, drawX, drawY, null);
					graphics.dispose();
					frames.add(new AnimationFrame(toPixels(cropCover(canvas, crop)), metadata.delayMs));
					previous = metadata;
					restoreSnapshot = currentSnapshot;
				}
				return new AnimatedBackground(List.copyOf(frames));
			} finally {
				reader.dispose();
			}
		}
	}

	private static void writeGif(final AnimatedBackground animation, final Path target) throws IOException {
		int averageDelay = (int) Math.round(animation.frames.stream().mapToInt(AnimationFrame::delayMs).average().orElse(100.0));
		try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile());
			 GifSequenceWriter writer = new GifSequenceWriter(output, averageDelay)) {
			for (AnimationFrame frame : animation.frames) {
				writer.write(fromPixels(frame.pixels));
			}
		}
	}

	private static BufferedImage readStaticPreview(final Path source) throws IOException, MediaValidationException {
		validateFileSize(source, MAX_IMAGE_BYTES);
		BufferedImage image = ImageIO.read(source.toFile());
		if (image == null) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.decode");
		}
		validateDimensions(image.getWidth(), image.getHeight());
		return image;
	}

	private static BufferedImage readGifPreview(final Path source) throws IOException, MediaValidationException {
		validateFileSize(source, MAX_IMAGE_BYTES);
		try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
			if (input == null) {
				throw new IOException("Could not open image stream");
			}
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			try {
				reader.setInput(input, false, false);
				int frameCount = reader.getNumImages(true);
				if (frameCount < 1 || frameCount > MAX_ANIMATION_FRAMES) {
					throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.frames");
				}
				BufferedImage first = reader.read(0);
				int[] canvasSize = gifCanvasSize(reader.getStreamMetadata());
				int canvasWidth = canvasSize[0] > 0 ? canvasSize[0] : first.getWidth();
				int canvasHeight = canvasSize[1] > 0 ? canvasSize[1] : first.getHeight();
				validateDimensions(canvasWidth, canvasHeight);
				GifFrameMetadata metadata = gifFrameMetadata(reader.getImageMetadata(0), first);
				BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = canvas.createGraphics();
				graphics.setComposite(AlphaComposite.SrcOver);
				int drawX = first.getWidth() == canvasWidth && first.getHeight() == canvasHeight ? 0 : metadata.left;
				int drawY = first.getWidth() == canvasWidth && first.getHeight() == canvasHeight ? 0 : metadata.top;
				graphics.drawImage(first, drawX, drawY, null);
				graphics.dispose();
				return canvas;
			} finally {
				reader.dispose();
			}
		}
	}

	private static BufferedImage readVideoPreview(final Path source) throws Exception {
		validateFileSize(source, MAX_VIDEO_BYTES);
		SeekableByteChannel channel = null;
		try {
			channel = NIOUtils.readableChannel(source.toFile());
			FrameGrab grab = FrameGrab.createFrameGrab(channel);
			DemuxerTrackMeta metadata = grab.getVideoTrack().getMeta();
			validateVideoDuration(metadata.getTotalDuration());
			Picture picture = grab.getNativeFrame();
			if (picture == null) {
				throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.decode");
			}
			BufferedImage image = AWTUtil.toBufferedImage(picture, metadata.getOrientation());
			validateVideoDimensions(image.getWidth(), image.getHeight());
			return image;
		} finally {
			NIOUtils.closeQuietly(channel);
		}
	}

	private static BufferedImage scaleDownForPreview(final BufferedImage source) {
		double scale = Math.min(1.0, Math.min(
			MAX_PREVIEW_DIMENSION / (double) source.getWidth(),
			MAX_PREVIEW_DIMENSION / (double) source.getHeight()
		));
		if (scale >= 1.0) {
			return source;
		}
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(source, 0, 0, width, height, null);
		graphics.dispose();
		return result;
	}

	private static BufferedImage cropCover(final BufferedImage source, final CropSettings crop) {
		BufferedImage result = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		double sourceWidth = source.getWidth();
		double sourceHeight = source.getHeight();
		double targetAspect = TARGET_WIDTH / (double) TARGET_HEIGHT;
		double sourceAspect = sourceWidth / sourceHeight;
		double maximumCropWidth;
		double maximumCropHeight;
		if (sourceAspect > targetAspect) {
			maximumCropHeight = sourceHeight;
			maximumCropWidth = sourceHeight * targetAspect;
		} else {
			maximumCropWidth = sourceWidth;
			maximumCropHeight = sourceWidth / targetAspect;
		}
		double cropWidth = maximumCropWidth / crop.zoom();
		double cropHeight = maximumCropHeight / crop.zoom();
		double centerX = clamp(crop.focusX() * sourceWidth, cropWidth / 2.0, sourceWidth - cropWidth / 2.0);
		double centerY = clamp(crop.focusY() * sourceHeight, cropHeight / 2.0, sourceHeight - cropHeight / 2.0);
		int sourceX0 = Math.max(0, (int) Math.floor(centerX - cropWidth / 2.0));
		int sourceY0 = Math.max(0, (int) Math.floor(centerY - cropHeight / 2.0));
		int sourceX1 = Math.min(source.getWidth(), Math.max(sourceX0 + 1, (int) Math.ceil(centerX + cropWidth / 2.0)));
		int sourceY1 = Math.min(source.getHeight(), Math.max(sourceY0 + 1, (int) Math.ceil(centerY + cropHeight / 2.0)));
		Graphics2D graphics = result.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(source, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, sourceX0, sourceY0, sourceX1, sourceY1, null);
		graphics.dispose();
		return result;
	}

	private static int[] toPixels(final BufferedImage image) {
		return image.getRGB(0, 0, TARGET_WIDTH, TARGET_HEIGHT, null, 0, TARGET_WIDTH);
	}

	private static BufferedImage fromPixels(final int[] pixels) {
		BufferedImage image = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, TARGET_WIDTH, TARGET_HEIGHT, pixels, 0, TARGET_WIDTH);
		return image;
	}

	private static BufferedImage copyImage(final BufferedImage source) {
		BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = copy.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(source, 0, 0, null);
		graphics.dispose();
		return copy;
	}

	private static int[] gifCanvasSize(final IIOMetadata metadata) {
		if (metadata == null) {
			return new int[] {0, 0};
		}
		IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_stream_1.0");
		IIOMetadataNode descriptor = findNode(root, "LogicalScreenDescriptor");
		return descriptor == null
			? new int[] {0, 0}
			: new int[] {integerAttribute(descriptor, "logicalScreenWidth", 0), integerAttribute(descriptor, "logicalScreenHeight", 0)};
	}

	private static GifFrameMetadata gifFrameMetadata(final IIOMetadata metadata, final BufferedImage image) {
		IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
		IIOMetadataNode descriptor = findNode(root, "ImageDescriptor");
		IIOMetadataNode control = findNode(root, "GraphicControlExtension");
		int left = descriptor == null ? 0 : integerAttribute(descriptor, "imageLeftPosition", 0);
		int top = descriptor == null ? 0 : integerAttribute(descriptor, "imageTopPosition", 0);
		int width = descriptor == null ? image.getWidth() : integerAttribute(descriptor, "imageWidth", image.getWidth());
		int height = descriptor == null ? image.getHeight() : integerAttribute(descriptor, "imageHeight", image.getHeight());
		int delay = control == null ? 100 : integerAttribute(control, "delayTime", 10) * 10;
		String disposal = control == null ? "none" : control.getAttribute("disposalMethod");
		return new GifFrameMetadata(left, top, width, height, Math.max(20, Math.min(1000, delay)), disposal);
	}

	private static IIOMetadataNode findNode(final IIOMetadataNode root, final String name) {
		if (root.getNodeName().equalsIgnoreCase(name)) {
			return root;
		}
		for (int i = 0; i < root.getLength(); i++) {
			if (root.item(i) instanceof IIOMetadataNode child) {
				IIOMetadataNode found = findNode(child, name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static int integerAttribute(final IIOMetadataNode node, final String name, final int fallback) {
		try {
			return Integer.parseInt(node.getAttribute(name));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static void validateFileSize(final Path source, final long maxBytes) throws IOException, MediaValidationException {
		if (Files.size(source) > maxBytes) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.size");
		}
	}

	private static void validateSource(final Path source) throws MediaValidationException {
		if (source == null || !Files.isRegularFile(source)) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.missing");
		}
	}

	private static void validateVideoDuration(final double duration) throws MediaValidationException {
		if (!Double.isFinite(duration) || duration < MIN_VIDEO_SECONDS) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.video_short");
		}
		if (duration > MAX_VIDEO_SECONDS) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.video_long");
		}
	}

	private static void validateVideoDimensions(final int width, final int height) throws MediaValidationException {
		if (width < MIN_VIDEO_WIDTH || height < MIN_VIDEO_HEIGHT) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.video_small");
		}
		validateDimensions(width, height);
	}

	private static void validateDimensions(final int width, final int height) throws MediaValidationException {
		if (width < 1 || height < 1 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) {
			throw new MediaValidationException("screen.kohs_inventory_tweaks.background.error.dimensions");
		}
	}

	private static void moveIntoPlace(final Path temporary, final Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String extension(final Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public record CropSettings(double focusX, double focusY, double zoom) {
		public static final CropSettings DEFAULT = new CropSettings(0.5, 0.5, 1.0);

		public CropSettings {
			focusX = clamp(Double.isFinite(focusX) ? focusX : 0.5, 0.0, 1.0);
			focusY = clamp(Double.isFinite(focusY) ? focusY : 0.5, 0.0, 1.0);
			zoom = clamp(Double.isFinite(zoom) ? zoom : 1.0, 1.0, 4.0);
		}
	}

	public record PreparedMedia(Path source, int previewWidth, int previewHeight, int[] previewPixels) {
	}

	public record PrepareResult(boolean success, PreparedMedia media, String messageKey, String detail) {
		private static PrepareResult success(final PreparedMedia media) {
			return new PrepareResult(true, media, "", "");
		}

		private static PrepareResult failure(final String messageKey, final String detail) {
			return new PrepareResult(false, null, messageKey, detail);
		}
	}

	public record ImportResult(boolean success, String fileName, String messageKey, String detail) {
		private static ImportResult success(final String fileName, final String messageKey) {
			return new ImportResult(true, fileName, messageKey, "");
		}

		private static ImportResult failure(final String messageKey, final String detail) {
			return new ImportResult(false, null, messageKey, detail);
		}
	}

	public record AnimationFrame(int[] pixels, int delayMs) {
	}

	public record AnimatedBackground(List<AnimationFrame> frames) {
		public AnimationFrame frameAt(final long elapsedMs) {
			if (this.frames.size() == 1) {
				return this.frames.getFirst();
			}
			long duration = 0L;
			for (AnimationFrame frame : this.frames) {
				duration += frame.delayMs;
			}
			long position = Math.floorMod(elapsedMs, Math.max(1L, duration));
			for (AnimationFrame frame : this.frames) {
				if (position < frame.delayMs) {
					return frame;
				}
				position -= frame.delayMs;
			}
			return this.frames.getLast();
		}
	}

	private record GifFrameMetadata(int left, int top, int width, int height, int delayMs, String disposal) {
	}

	private static final class MediaValidationException extends Exception {
		private final String translationKey;

		private MediaValidationException(final String translationKey) {
			this.translationKey = translationKey;
		}
	}
}
