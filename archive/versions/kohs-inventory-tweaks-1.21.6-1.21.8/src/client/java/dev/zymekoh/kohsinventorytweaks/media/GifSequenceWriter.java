package dev.zymekoh.kohsinventorytweaks.media;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

final class GifSequenceWriter implements Closeable {
	private static final String METADATA_FORMAT = "javax_imageio_gif_image_1.0";
	private final ImageWriter writer;
	private final ImageWriteParam parameters;
	private final IIOMetadata metadata;

	GifSequenceWriter(final ImageOutputStream output, final int delayMs) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("gif");
		if (!writers.hasNext()) {
			throw new IOException("No GIF writer is available");
		}
		this.writer = writers.next();
		this.parameters = this.writer.getDefaultWriteParam();
		ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
		this.metadata = this.writer.getDefaultImageMetadata(type, this.parameters);

		IIOMetadataNode root = (IIOMetadataNode) this.metadata.getAsTree(METADATA_FORMAT);
		IIOMetadataNode control = child(root, "GraphicControlExtension");
		control.setAttribute("disposalMethod", "none");
		control.setAttribute("userInputFlag", "FALSE");
		control.setAttribute("transparentColorFlag", "TRUE");
		control.setAttribute("delayTime", Integer.toString(Math.max(2, delayMs / 10)));
		control.setAttribute("transparentColorIndex", "0");

		IIOMetadataNode extensions = child(root, "ApplicationExtensions");
		IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
		extension.setAttribute("applicationID", "NETSCAPE");
		extension.setAttribute("authenticationCode", "2.0");
		extension.setUserObject(new byte[] {1, 0, 0});
		extensions.appendChild(extension);
		this.metadata.setFromTree(METADATA_FORMAT, root);

		this.writer.setOutput(output);
		this.writer.prepareWriteSequence(null);
	}

	void write(final BufferedImage image) throws IOException {
		this.writer.writeToSequence(new IIOImage(image, null, this.metadata), this.parameters);
	}

	@Override
	public void close() throws IOException {
		this.writer.endWriteSequence();
		this.writer.dispose();
	}

	private static IIOMetadataNode child(final IIOMetadataNode root, final String name) {
		for (int i = 0; i < root.getLength(); i++) {
			if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
				return (IIOMetadataNode) root.item(i);
			}
		}
		IIOMetadataNode node = new IIOMetadataNode(name);
		root.appendChild(node);
		return node;
	}
}

