package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.batik.svggen.DefaultImageHandler;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.w3c.dom.Element;

/** Writes one content-addressed PNG asset and links all page uses to it. */
final class PagedSVGImageHandler extends DefaultImageHandler {
	private static final String XLINK = "http://www.w3.org/1999/xlink";
	private final PagedSVGResources resources;

	PagedSVGImageHandler(final PagedSVGResources resources) {
		this.resources = resources;
	}

	@Override
	protected void handleHREF(final Image image, final Element element, final SVGGeneratorContext context)
			throws SVGGraphics2DIOException {
		if (image instanceof RenderedImage rendered) {
			this.handleHREF(rendered, element, context);
			return;
		}
		final int width = image.getWidth(null);
		final int height = image.getHeight(null);
		final BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D gc = buffered.createGraphics();
		try {
			gc.drawImage(image, 0, 0, null);
		} finally {
			gc.dispose();
		}
		this.handleHREF((RenderedImage) buffered, element, context);
	}

	@Override
	protected void handleHREF(final RenderableImage image, final Element element, final SVGGeneratorContext context)
			throws SVGGraphics2DIOException {
		this.handleHREF(image.createDefaultRendering(), element, context);
	}

	@Override
	protected void handleHREF(final RenderedImage image, final Element element, final SVGGeneratorContext context)
			throws SVGGraphics2DIOException {
		try {
			// 元のJPEGをそのまま出す画像では、ここで作るPNGは捨てられる。作らない。
			byte[] fallbackPng = null;
			if (!this.resources.hasOriginal(image)) {
				final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				if (!ImageIO.write(image, "png", bytes)) {
					throw new IOException("No PNG writer is available");
				}
				fallbackPng = bytes.toByteArray();
			}
			final PagedSVGResources.ImageAsset asset = this.resources.image(image, fallbackPng, image.getWidth(),
					image.getHeight());
			element.setAttributeNS(XLINK, "xlink:href", "../" + asset.uri());
		} catch (final IOException e) {
			throw new SVGGraphics2DIOException("Cannot externalize SVG image", e);
		}
	}
}
